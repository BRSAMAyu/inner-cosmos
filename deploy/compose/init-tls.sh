#!/bin/sh
set -eu

rm -rf /tls/postgres /tls/redis /tls/edge /tls/.complete /tls/ca.srl
mkdir -p /tls/postgres /tls/redis /tls/edge
openssl req -x509 -newkey rsa:2048 -sha256 -days 2 -nodes \
  -subj '/CN=inner-cosmos-local-ca' -keyout /tls/ca.key -out /tls/ca.crt >/dev/null 2>&1

issue_cert() {
  name="$1"
  cn="$2"
  san="$3"
  dir="/tls/$name"
  openssl req -newkey rsa:2048 -nodes -keyout "$dir/server.key" -out "$dir/server.csr" \
    -subj "/CN=$cn" -addext "subjectAltName=$san" >/dev/null 2>&1
  openssl x509 -req -in "$dir/server.csr" -CA /tls/ca.crt -CAkey /tls/ca.key \
    -CAcreateserial -out "$dir/server.crt" -days 2 -sha256 -copy_extensions copy >/dev/null 2>&1
  cp /tls/ca.crt "$dir/ca.crt"
}

issue_cert postgres postgres 'DNS:postgres,DNS:localhost'
issue_cert redis redis 'DNS:redis,DNS:localhost'
issue_cert edge localhost 'DNS:localhost,IP:127.0.0.1'

chmod 600 /tls/*/server.key /tls/ca.key
chmod 644 /tls/*/server.crt /tls/*/ca.crt
chown -R 999:999 /tls/postgres /tls/redis
touch /tls/.complete
