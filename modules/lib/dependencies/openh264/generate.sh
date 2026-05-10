#!/usr/bin/env bash
#
# Regenerates Java FFM bindings for openh264 via jextract. Output lands
# in modules/lib/src/main/java/com/github/auties00/cobalt/call/video/
# h264/bindings/, where the high-level H.264 encoder/decoder wrappers
# consume it.
#
# Note: openh264's runtime API is dispatched through the
# ISVCEncoderVtbl / ISVCDecoderVtbl function-pointer tables. The C
# create/destroy entry points are surfaced as jextract functions; the
# vtable members are reached via the generated struct layouts.
#
# Prerequisites: a JEXTRACT_HOME env var pointing at a jextract 22+
# install, OR jextract on PATH. Download from
# https://jdk.java.net/jextract/ if absent.
#
# Re-run this whenever the openh264 headers under headers/ change.
#

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
if [ -n "${JEXTRACT_HOME:-}" ]; then
  JEXTRACT="$JEXTRACT_HOME/bin/jextract"
else
  JEXTRACT="$(command -v jextract || true)"
fi
[ -n "$JEXTRACT" ] && [ -x "$JEXTRACT" ] || { echo "jextract not found; set JEXTRACT_HOME or add to PATH" >&2; exit 1; }

OUT="$ROOT/modules/lib/src/main/java"
PKG="com.github.auties00.cobalt.call.video.h264.bindings"

rm -f "$OUT/${PKG//.//}/OpenH264.java" "$OUT/${PKG//.//}/OpenH264\$shared.java"

"$JEXTRACT" \
  -t "$PKG" \
  -I "$DIR/headers" \
  --header-class-name OpenH264 \
  --output "$OUT" \
  --include-function WelsCreateSVCEncoder \
  --include-function WelsDestroySVCEncoder \
  --include-function WelsCreateDecoder \
  --include-function WelsDestroyDecoder \
  --include-function WelsGetDecoderCapability \
  --include-function WelsGetCodecVersion \
  --include-function WelsGetCodecVersionEx \
  --include-struct ISVCEncoderVtbl \
  --include-struct ISVCDecoderVtbl \
  --include-struct _tagVersion \
  --include-struct TagEncParamBase \
  --include-struct TagEncParamExt \
  --include-struct TagBitrateInfo \
  --include-struct TagSVCDecodingParam \
  --include-struct Source_Picture_s \
  --include-struct TagBufferInfo \
  --include-struct TagSysMemBuffer \
  --include-struct TagDecoderCapability \
  --include-struct TagParserBsInfo \
  --include-typedef SEncParamBase \
  --include-typedef SEncParamExt \
  --include-typedef SBitrateInfo \
  --include-typedef SSourcePicture \
  --include-typedef SFrameBSInfo \
  --include-typedef SLayerBSInfo \
  --include-typedef SDecodingParam \
  --include-typedef SBufferInfo \
  --include-typedef SParserBsInfo \
  --include-typedef SDecoderCapability \
  --include-typedef OpenH264Version \
  --include-typedef SVideoProperty \
  --include-typedef SSpatialLayerConfig \
  --include-typedef SSliceArgument \
  --include-typedef SLTRConfig \
  --include-typedef SLTRMarkingFeedback \
  --include-typedef SLTRRecoverRequest \
  --include-constant videoFormatI420 \
  --include-constant videoFrameTypeIDR \
  --include-constant videoFrameTypeI \
  --include-constant videoFrameTypeP \
  --include-constant videoFrameTypeSkip \
  --include-constant videoFrameTypeInvalid \
  --include-constant CAMERA_VIDEO_REAL_TIME \
  --include-constant SCREEN_CONTENT_REAL_TIME \
  --include-constant RC_BITRATE_MODE \
  --include-constant RC_QUALITY_MODE \
  --include-constant RC_OFF_MODE \
  --include-constant RC_BUFFERBASED_MODE \
  --include-constant RC_TIMESTAMP_MODE \
  --include-constant LOW_COMPLEXITY \
  --include-constant MEDIUM_COMPLEXITY \
  --include-constant HIGH_COMPLEXITY \
  --include-constant ENCODER_OPTION_BITRATE \
  --include-constant SPATIAL_LAYER_ALL \
  "$DIR/headers/codec_api.h"

echo "wrote $OUT/${PKG//.//}/OpenH264.java"
