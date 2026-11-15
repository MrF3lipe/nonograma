#!/usr/bin/env bash
# Compila Solver+parser+arnés y corre los puzzles indicados (por defecto, los fixtures).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/home/box/jdk17}"
export PATH="${JAVA_HOME}/bin:/home/box/tools/kotlinc/bin:${PATH}"
TIMEOUT=25000
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --timeout) TIMEOUT="$2"; shift 2 ;;
    *) ARGS+=("$1"); shift ;;
  esac
done
if [[ ${#ARGS[@]} -eq 0 ]]; then
  ARGS=("$ROOT/app/src/test/resources/puzzles")
fi
if ! command -v kotlinc >/dev/null || ! command -v java >/dev/null; then
  echo "hace falta java y kotlinc en el PATH" >&2
  exit 2
fi
OUT="$ROOT/build/puzzle-harness.jar"
mkdir -p "$(dirname "$OUT")"
# Recompila si falta o si las fuentes son más nuevas
need=0
if [[ ! -f "$OUT" ]]; then need=1; fi
for src in "$ROOT/app/src/main/java/com/felipe/nonograma/Solver.kt" \
           "$ROOT/app/src/main/java/com/felipe/nonograma/PuzzleParser.kt" \
           "$ROOT/scripts/Harness.kt"; do
  if [[ "$src" -nt "$OUT" ]]; then need=1; fi
done
if [[ $need -eq 1 ]]; then
  echo "compilando arnés..."
  kotlinc \
    "$ROOT/app/src/main/java/com/felipe/nonograma/Solver.kt" \
    "$ROOT/app/src/main/java/com/felipe/nonograma/PuzzleParser.kt" \
    "$ROOT/scripts/Harness.kt" \
    -include-runtime -d "$OUT"
fi
java -jar "$OUT" --timeout "$TIMEOUT" "${ARGS[@]}"
