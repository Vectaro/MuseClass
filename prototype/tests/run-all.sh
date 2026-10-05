#!/bin/sh
# Весь набір перевірок прототипу. Потрібен node і jsdom:
#   cd prototype && npm install
set -eu
cd "$(dirname "$0")/.."
fail=0

echo "── збірка: чи index.html зібраний з поточних частин"
python3 tools/build.py --check || fail=1

for t in tests/smoke2.js tests/smoke3.js tests/check.js; do
  echo "── $t"
  node "$t" || fail=1
done

[ "$fail" = 0 ] && echo "УСЕ ПРОЙШЛО" || echo "Є ПРОВАЛИ"
exit "$fail"
