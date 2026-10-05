#!/bin/sh
# Качає еталонні записи інструментів, з яких міряються спектри.
# У репо їх немає: це 1.6 МБ чужих семплів, а нам потрібні лише числа,
# що з них виходять (srcfilter.json, decays.json).
#
#   sh fetch-samples.sh            → кладе в ./samples
#   MUSECLASS_SAMPLES=/шлях sh fetch-samples.sh
set -eu
BASE="https://raw.githubusercontent.com/gleitz/midi-js-soundfonts/gh-pages/FluidR3_GM"
DIR="${MUSECLASS_SAMPLES:-$(dirname "$0")/samples}"
mkdir -p "$DIR"

set -- \
  "acoustic_grand_piano:piano" \
  "acoustic_guitar_nylon:guitar" \
  "electric_bass_finger:bass" \
  "violin:violin" \
  "trumpet:trumpet" \
  "trombone:trombone" \
  "alto_sax:sax" \
  "flute:flute" \
  "choir_aahs:voice"

for pair in "$@"; do
  src="${pair%%:*}"; name="${pair##*:}"
  for note in C3 G3 C4 G4 C5 E5; do
    out="$DIR/${name}_${note}.mp3"
    [ -f "$out" ] && continue
    curl -sS -f -o "$out" "$BASE/${src}-mp3/${note}.mp3" || echo "не вдалось: $name $note"
  done
done
echo "семплів у $DIR: $(ls "$DIR"/*.mp3 2>/dev/null | wc -l)"
