"""Schemathesis hooks for the staging fuzzing (fuzz_api.sh).

Generates the image files an upload's parts are in (the spec lists the image types
ImageService accepts, and Schemathesis has nothing to make those types from): bytes,
which the API stores as they are, so they needn't be a real picture.

Signs each generated webhook delivery the way the simulated payment provider does
(WebhookSignature): a Payment-Signature header of t=<seconds>,v1=<hex HMAC-SHA256 of
"<t>.<raw body>"> under PAYMENT_WEBHOOK_SECRET. Unsigned, every delivery stopped at
the 401, before the code that handles an event; signed, the fuzzer reaches it.

The signature covers the raw body, so the body is serialized here, and those bytes
are what Schemathesis sends: it passes a bytes body through as it is.
"""

import hashlib
import hmac
import json
import os
import time

import schemathesis
from hypothesis import strategies as st
from schemathesis.core import NOT_SET

WEBHOOKS = "/api/v1/payments/webhooks"
SECRET = os.environ["PAYMENT_WEBHOOK_SECRET"].encode()

schemathesis.openapi.media_type(
    "image/png", st.binary(min_size=1, max_size=2048), aliases=["image/jpeg", "image/webp", "image/gif"]
)


def raw_body(body):
    """The bytes Schemathesis sends for a JSON body (transport/serialization.py)."""
    if body is NOT_SET:
        return b""
    if isinstance(body, bytes):
        return body
    if body is None:
        return b"null"
    return json.dumps(body).encode()


def signature(body, seconds):
    digest = hmac.new(SECRET, f"{seconds}.".encode() + body, hashlib.sha256).hexdigest()
    return f"t={seconds},v1={digest}"


@schemathesis.hook
def before_call(context, case, kwargs):
    if case.method.upper() != "POST" or case.path != WEBHOOKS:
        return
    try:
        body = raw_body(case.body)
    except (TypeError, ValueError):
        return  # not JSON (a negative test's other media type): left unsigned
    if case.body is not NOT_SET:
        case.body = body
    headers = dict(case.headers or {})
    headers["Payment-Signature"] = signature(body, int(time.time()))
    case.headers = headers
