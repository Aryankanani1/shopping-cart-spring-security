#!/usr/bin/env bash
# Deploys one build of the shop on this server, and rolls back if it doesn't come up:
#
#   ./deploy.sh <image tag>        e.g. ./deploy.sh 3f2c1a9...  (a commit SHA on master)
#
# Run it in the deployment directory, the one with docker-compose.yml, Caddyfile
# and .env (see README.md). The CI/CD pipeline copies the new docker-compose.yml,
# Caddyfile and deploy.sh into release/ there first and runs it over SSH; this
# script then puts those files in place. Run by hand, it deploys the tag with the
# files that are already there.
#
# It sets IMAGE_TAG in .env, pulls and starts the containers, and waits up to five
# minutes for the API to report ready. If any of that fails, it puts the previous
# files and tag back, starts them again and exits 1. A database migration the new
# build ran stays applied: Flyway only moves forward, so a rollback across one can
# leave the old build facing a newer schema (README.md, Updating).
set -euo pipefail

RELEASE=release
PREVIOUS=previous

compose() { docker compose "$@"; }

image_tag() { sed -n 's/^IMAGE_TAG=//p' .env | tail -n 1; }

set_tag() {
  if [ -z "$1" ]; then
    sed -i.bak '/^IMAGE_TAG=/d' .env
  elif grep -q '^IMAGE_TAG=' .env; then
    sed -i.bak "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" .env
  else
    printf '\nIMAGE_TAG=%s\n' "$1" >> .env
  fi
  rm -f .env.bak
}

# Caddy reads the Caddyfile through a bind mount of that one file, which follows
# the file's inode: rewrite it in place (cat >), never replace it (cp, mv).
install_files() {
  cp "$1/docker-compose.yml" docker-compose.yml
  cat "$1/Caddyfile" > Caddyfile
}

ready() {
  for _ in $(seq 60); do
    if compose exec -T web wget -qO- http://api:8080/actuator/health/readiness 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

start() {
  compose pull --quiet &&
    compose up -d --remove-orphans &&
    compose exec -T web caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile &&
    ready
}

# Everything runs inside main, so bash has read the whole file before it starts:
# part-way through, the script installs a new copy of itself from release/.
main() {
  local tag=${1:?"usage: $0 <image tag>"}
  [ -f .env ] || { echo "No .env here; run this in the deployment directory (README.md)." >&2; return 1; }

  local old_tag
  old_tag=$(image_tag)
  mkdir -p "$PREVIOUS"
  cp docker-compose.yml "$PREVIOUS/docker-compose.yml"
  cp Caddyfile "$PREVIOUS/Caddyfile"

  if [ -d "$RELEASE" ]; then
    install_files "$RELEASE"
    if [ -f "$RELEASE/deploy.sh" ]; then
      cp "$RELEASE/deploy.sh" deploy.sh
      chmod +x deploy.sh
    fi
    rm -rf "$RELEASE"
  fi
  set_tag "$tag"

  echo "Deploying $tag (was ${old_tag:-latest})"
  if start; then
    echo "Deployed $tag: the API is ready."
    return 0
  fi

  echo "$tag did not come up; rolling back to ${old_tag:-latest}." >&2
  compose logs --tail=80 api >&2 || true
  install_files "$PREVIOUS"
  set_tag "$old_tag"
  if start; then
    echo "Rolled back to ${old_tag:-latest}." >&2
  else
    echo "The rollback did not come up either; check the server (docker compose ps, logs)." >&2
  fi
  return 1
}

main "$@"
exit $?
