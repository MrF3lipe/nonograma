#!/usr/bin/env bash
# Descarga la encuesta de Jan Wolter (uso personal) y, opcionalmente, ids de export.cgi.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${DEST:-$ROOT/puzzles/downloaded}"
BASE="https://webpbn.com/survey/puzzles"
IDS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --id) IDS+=("$2"); shift 2 ;;
    --dest) DEST="$2"; shift 2 ;;
    *) DEST="$1"; shift ;;
  esac
done
mkdir -p "$DEST"
download_archive() {
  local name="$1"
  if [[ -d "$DEST/${name#sample-}" ]] || [[ -d "$DEST/$name" ]]; then
    echo "ya está: $name"
    return
  fi
  local tmp
  tmp="$(mktemp)"
  echo "bajando $name..."
  curl -fsSL -A "Nonograma-harness (personal use)" -o "$tmp" "$BASE/$name.tgz"
  tar -xzf "$tmp" -C "$DEST"
  rm -f "$tmp"
}
download_archive sample-simpson
download_archive sample-wolter
for id in "${IDS[@]+"${IDS[@]}"}"; do
  pad="$(printf '%06d' "$id")"
  out="$DEST/webpbn-$pad.non"
  if [[ -f "$out" ]]; then echo "ya está: $out"; continue; fi
  echo "exportando webpbn #$id..."
  curl -fsSL -A "Nonograma-harness (personal use)" -G "https://webpbn.com/export.cgi" \
    --data-urlencode "go=1" --data-urlencode "id=$id" --data-urlencode "fmt=ss" \
    --data-urlencode "ss_soln=on" -o "$out"
done
echo "listo en $DEST"
find "$DEST" -type f \( -name '*.non' -o -name '*.xml' \) | wc -l
