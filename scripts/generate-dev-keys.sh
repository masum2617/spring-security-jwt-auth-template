#!/usr/bin/env bash
set -euo pipefail
umask 077

# Claim a new directory atomically: never replace keys used by another running instance.
key_dir="${1:-.keys}"
if ! mkdir "$key_dir"; then
    echo "Refusing to overwrite an existing key directory: $key_dir" >&2
    exit 1
fi
trap 'rm -f "$key_dir/private.pem" "$key_dir/public.pem"; rmdir "$key_dir"' ERR
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$key_dir/private.pem"
openssl pkey -in "$key_dir/private.pem" -pubout -out "$key_dir/public.pem"
echo "Created development keys in $key_dir. Keep the private key out of Git."
