#!/usr/bin/env bash
set -euo pipefail

pattern='(^|[^0-9])([0-9]{1,3}\.){3}[0-9]{1,3}([^0-9]|$)'
if git grep -I -E -q "$pattern" -- ':!tools/check-no-ip-literals.sh'; then
  echo 'Blocked: a dotted IPv4 literal exists in tracked public sources.' >&2
  exit 1
fi
