#!/usr/bin/env sh
set -eu
: "${ALOUTE_DOMAIN:?ALOUTE_DOMAIN is required}"
envsubst '${ALOUTE_DOMAIN}' \
  < /etc/nginx/templates/aloute.conf.template \
  > /etc/nginx/conf.d/aloute.conf
nginx -t
