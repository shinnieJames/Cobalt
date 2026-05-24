#!/usr/bin/env bash
#
# Regenerates Java FFM bindings for libspeexdsp via jextract. Output
# lands in modules/lib/src/main/java/com/github/auties00/cobalt/call/
# audio/processing/bindings/, where the high-level wrapper classes
# (EchoCanceller, AudioPreprocessor) consume it.
#
# Prerequisites: a JEXTRACT_HOME env var pointing at a jextract 22+
# install, OR jextract on PATH. Download from
# https://jdk.java.net/jextract/ if absent.
#
# Re-run this whenever the speex headers under headers/ change.
#

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../../../.." && pwd)"
if [ -n "${JEXTRACT_HOME:-}" ]; then
  JEXTRACT="$JEXTRACT_HOME/bin/jextract"
else
  JEXTRACT="$(command -v jextract || true)"
fi
[ -n "$JEXTRACT" ] && [ -f "$JEXTRACT" ] || { echo "jextract not found; set JEXTRACT_HOME or add to PATH" >&2; exit 1; }

OUT="$ROOT/modules/lib/src/main/java"
PKG="com.github.auties00.cobalt.call.internal.audio.processing.bindings"

rm -f "$OUT/${PKG//.//}/SpeexDsp.java" "$OUT/${PKG//.//}/SpeexDsp\$shared.java"

"$JEXTRACT" \
  -t "$PKG" \
  -I "$DIR/headers" \
  -I "$DIR/headers/speex" \
  --header-class-name SpeexDsp \
  --output "$OUT" \
  --include-function speex_echo_state_init \
  --include-function speex_echo_state_destroy \
  --include-function speex_echo_cancellation \
  --include-function speex_echo_state_reset \
  --include-function speex_preprocess_state_init \
  --include-function speex_preprocess_state_destroy \
  --include-function speex_preprocess_run \
  --include-function speex_preprocess_ctl \
  --include-constant SPEEX_PREPROCESS_SET_DENOISE \
  --include-constant SPEEX_PREPROCESS_SET_AGC \
  --include-constant SPEEX_PREPROCESS_SET_VAD \
  --include-constant SPEEX_PREPROCESS_SET_DEREVERB \
  --include-constant SPEEX_PREPROCESS_SET_NOISE_SUPPRESS \
  --include-constant SPEEX_PREPROCESS_SET_ECHO_STATE \
  --include-constant SPEEX_PREPROCESS_SET_AGC_TARGET \
  "$DIR/speexdsp.h"

echo "wrote $OUT/${PKG//.//}/SpeexDsp.java"
