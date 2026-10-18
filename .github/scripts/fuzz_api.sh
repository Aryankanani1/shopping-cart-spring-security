#!/usr/bin/env bash
# Fuzzes the staging API against its contract, docs/openapi.json, with Schemathesis:
# thousands of generated requests, valid and malformed, and any 5xx fails the stage.
# Run by pipeline.yml's staging stage after the E2E tests, against the staging setup
# on https://localhost, with the admin's sign-in in E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD.
#
# With made-up ids, an operation on one order, cart or product stops at a 404 before
# the code behind it. So the script first creates, through the API, a customer with
# what those operations need: a category, a product with an image, two paid orders
# with $1 of each refunded, and a cart item. The fuzzer uses their ids for most of
# its requests; the rest are still generated, malformed ones included.
#
# Two runs, over every operation but deleteUser: as the customer, then as an admin,
# which reaches the admin-only operations. The admin's goes last, so its deletes
# can't take the data away from the customer's; staging is thrown away afterwards.
set -euo pipefail

base_url=${BASE_URL:-https://localhost}
out=${RUNNER_TEMP:?}/schemathesis
mkdir -p "$out/customer" "$out/admin"

# In the pipeline, keeps a secret out of the log.
mask() {
  if [[ ${GITHUB_ACTIONS:-} == true ]]; then echo "::add-mask::$1"; fi
}

# One call to the API, with or without a token; prints the response body. A
# failure prints the problem the API answered with and stops the script.
call() {
  local method=$1 path=$2 token=$3 body
  shift 3
  local request=(-sSk --fail-with-body -X "$method" "$base_url/api/v1$path")
  if [[ -n $token ]]; then request+=(-H "Authorization: Bearer $token"); fi
  if ! body=$(curl "${request[@]}" "$@"); then
    echo "$method $path failed: $body" >&2
    return 1
  fi
  printf '%s' "$body"
}

key() { openssl rand -hex 16; }

login() {
  call POST /auth/login "" --json "$(jq -nc --arg email "$1" --arg password "$2" '{$email, $password}')"
}

# The customer, and an admin.
stamp=$(date +%s)
email="fuzz-$stamp@staging.test"
password=$(openssl rand -hex 12)
mask "$password"
call POST /users "" --json "$(jq -nc --arg email "$email" --arg password "$password" \
  '{firstName: "Fuzz", lastName: "Tester", $email, $password}')" > /dev/null
customer=$(login "$email" "$password")
customer_token=$(jq -r .data.token <<< "$customer")
mask "$customer_token"
user_id=$(jq -r .data.id <<< "$customer")
admin_token=$(login "${E2E_ADMIN_EMAIL:?}" "${E2E_ADMIN_PASSWORD:?}" | jq -r .data.token)
mask "$admin_token"

# A category, and a product in it with an image (a 1x1 PNG).
category_id=$(call POST /categories "$admin_token" --json "$(jq -nc --arg name "Fuzz $stamp" '{$name}')" \
  | jq -r .data.id)
product_id=$(call POST /products "$admin_token" --json "$(jq -nc --arg name "Fuzz lamp $stamp" --arg category "Fuzz $stamp" \
  '{$name, brand: "Fuzz", price: 20, description: "Created by the API fuzzing.", inventory: 100, category: {name: $category}}')" \
  | jq -r .data.id)
base64 -d > "$out/pixel.png" <<< 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII='
image_id=$(call POST "/images?productId=$product_id" "$admin_token" -F "files=@$out/pixel.png;type=image/png" \
  | jq -r '.data[0].imageId')

# Two paid orders, one for each run, with $1 of each refunded.
address='{"recipientName": "Fuzz Tester", "addressLine1": "1 Test Street", "city": "Testville", "state": "TS", "postalCode": "12345", "country": "US"}'
orders=() payments=() refunds=()
for _ in 1 2; do
  call POST "/cartItems?productId=$product_id&quantity=2" "$customer_token" > /dev/null
  order_id=$(call POST /orders "$customer_token" --json "$address" | jq -r .data.id)
  payment=$(call POST "/orders/$order_id/payments" "$customer_token" -H "Idempotency-Key: $(key)" \
    --json '{"paymentMethod": "test_visa"}')
  jq -e '.data.status == "SUCCEEDED"' <<< "$payment" > /dev/null \
    || { echo "Order $order_id wasn't paid: $payment" >&2; exit 1; }
  refund_id=$(call POST "/orders/$order_id/refunds" "$admin_token" -H "Idempotency-Key: $(key)" \
    --json '{"amount": 1, "reason": "Refunded by the API fuzzing"}' | jq -r .data.id)
  orders+=("$order_id") payments+=("$(jq -r .data.id <<< "$payment")") refunds+=("$refund_id")
done

# And something in the customer's cart.
call POST "/cartItems?productId=$product_id&quantity=1" "$customer_token" > /dev/null
cart=$(call GET /carts/mine "$customer_token")
cart_id=$(jq -r .data.cartId <<< "$cart")
cart_item_id=$(jq -r '.data.cartItems[0].itemId' <<< "$cart")

for id in "$user_id" "$category_id" "$product_id" "$image_id" "$cart_id" "$cart_item_id" \
  "${orders[@]}" "${payments[@]}" "${refunds[@]}"; do
  [[ $id =~ ^[0-9]+$ ]] || { echo "The API returned '$id' for an id" >&2; exit 1; }
done

# A run's Schemathesis config, with the ids it uses. Written here because they have
# to be integers: Schemathesis's own ${VAR} gives a string, which an integer
# parameter's schema rejects, so it would never be used.
write_config() {
  local order_id=$1 payment_id=$2 refund_id=$3
  cat << EOF
# Each record's id, used for 80% of a parameter's values; the rest are generated.
[dictionaries]
user = { values = [$user_id] }
category = { values = [$category_id] }
product = { values = [$product_id] }
image = { values = [$image_id] }
order = { values = [$order_id] }
payment = { values = [$payment_id] }
refund = { values = [$refund_id] }
cart = { values = [$cart_id] }
cart_item = { values = [$cart_item_id] }

[parameters]
"path.userId" = { dictionary = "user", probability = 0.8 }
"path.productId" = { dictionary = "product", probability = 0.8 }
"query.productId" = { dictionary = "product", probability = 0.8 }
"path.imageId" = { dictionary = "image", probability = 0.8 }
"path.orderId" = { dictionary = "order", probability = 0.8 }
"path.paymentId" = { dictionary = "payment", probability = 0.8 }
"path.refundId" = { dictionary = "refund", probability = 0.8 }
"path.cartId" = { dictionary = "cart", probability = 0.8 }
"path.itemId" = { dictionary = "cart_item", probability = 0.8 }

# On these paths, id is a different kind of record on each.
[[operations]]
include-path = "/api/v1/users/{id}"
parameters = { "path.id" = { dictionary = "user", probability = 0.8 } }

[[operations]]
include-path = "/api/v1/categories/{id}"
parameters = { "path.id" = { dictionary = "category", probability = 0.8 } }

[[operations]]
include-path = "/api/v1/products/{id}"
parameters = { "path.id" = { dictionary = "product", probability = 0.8 } }
EOF
}

# One run, as whoever the token is for. It works in its own directory, where
# Schemathesis writes its coverage report and examples database.
fuzz() {
  local run=$1 token=$2
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    -v "$PWD/docs:/spec:ro" -v "$out/$run:/work" -w /work \
    "${SCHEMATHESIS_IMAGE:?}" --config-file /work/schemathesis.toml run /spec/openapi.json \
    --url "$base_url" --tls-verify false \
    --header "Authorization: Bearer $token" \
    --checks not_a_server_error --max-examples 25 \
    --exclude-operation-id deleteUser \
    --report junit --report-junit-path /work/junit.xml
}

write_config "${orders[0]}" "${payments[0]}" "${refunds[0]}" > "$out/customer/schemathesis.toml"
write_config "${orders[1]}" "${payments[1]}" "${refunds[1]}" > "$out/admin/schemathesis.toml"

# Both runs, even when the first finds a 5xx.
status=0
echo "=== Fuzzing as a customer"
fuzz customer "$customer_token" || status=1
echo "=== Fuzzing as an admin"
fuzz admin "$admin_token" || status=1
exit "$status"
