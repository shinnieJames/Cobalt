#!/usr/bin/env bash
#
# Regenerates Java FFM bindings for libopus via jextract. Output lands
# in modules/lib/src/main/java/com/github/auties00/cobalt/call/audio/
# opus/bindings/, where the high-level OpusEncoder / OpusDecoder
# wrappers consume it.
#
# Prerequisites: a JEXTRACT_HOME env var pointing at a jextract 22+
# install, OR jextract on PATH. Download from
# https://jdk.java.net/jextract/ if absent.
#
# Re-run this whenever the opus headers under headers/ change.
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
PKG="com.github.auties00.cobalt.call.internal.audio.opus.bindings"

rm -f "$OUT/${PKG//.//}/Opus.java" "$OUT/${PKG//.//}/Opus\$shared.java"

"$JEXTRACT" \
  -t "$PKG" \
  -I "$DIR/headers" \
  --header-class-name Opus \
  --output "$OUT" \
  --include-function opus_encoder_create \
  --include-function opus_encoder_destroy \
  --include-function opus_encoder_ctl \
  --include-function opus_encode \
  --include-function opus_decoder_create \
  --include-function opus_decoder_destroy \
  --include-function opus_decoder_ctl \
  --include-function opus_decode \
  --include-function opus_strerror \
  --include-function opus_get_version_string \
  --include-constant OPUS_OK \
  --include-constant OPUS_BAD_ARG \
  --include-constant OPUS_BUFFER_TOO_SMALL \
  --include-constant OPUS_INTERNAL_ERROR \
  --include-constant OPUS_INVALID_PACKET \
  --include-constant OPUS_UNIMPLEMENTED \
  --include-constant OPUS_INVALID_STATE \
  --include-constant OPUS_ALLOC_FAIL \
  --include-constant OPUS_APPLICATION_VOIP \
  --include-constant OPUS_APPLICATION_AUDIO \
  --include-constant OPUS_APPLICATION_RESTRICTED_LOWDELAY \
  --include-constant OPUS_SET_BITRATE_REQUEST \
  --include-constant OPUS_GET_BITRATE_REQUEST \
  --include-constant OPUS_SET_COMPLEXITY_REQUEST \
  --include-constant OPUS_GET_COMPLEXITY_REQUEST \
  --include-constant OPUS_SET_DTX_REQUEST \
  --include-constant OPUS_GET_DTX_REQUEST \
  --include-constant OPUS_SET_INBAND_FEC_REQUEST \
  --include-constant OPUS_GET_INBAND_FEC_REQUEST \
  --include-constant OPUS_SET_PACKET_LOSS_PERC_REQUEST \
  --include-constant OPUS_GET_PACKET_LOSS_PERC_REQUEST \
  --include-constant OPUS_RESET_STATE \
  --include-constant OPUS_AUTO \
  --include-constant OPUS_BITRATE_MAX \
  --include-constant OPUS_SET_VBR_REQUEST \
  --include-constant OPUS_SET_VBR_CONSTRAINT_REQUEST \
  --include-constant OPUS_SET_BANDWIDTH_REQUEST \
  --include-constant OPUS_SET_SIGNAL_REQUEST \
  --include-constant OPUS_SIGNAL_VOICE \
  --include-constant OPUS_SIGNAL_MUSIC \
  "$DIR/headers/opus/opus.h"

echo "wrote $OUT/${PKG//.//}/Opus.java"
