#!/usr/bin/env bash
#
# Regenerates Java FFM bindings for libvpx (VP8) via jextract. Output
# lands in modules/lib/src/main/java/com/github/auties00/cobalt/call/
# video/vpx/bindings/, where the high-level VP8 encoder/decoder
# wrappers consume it.
#
# Prerequisites: a JEXTRACT_HOME env var pointing at a jextract 22+
# install, OR jextract on PATH. Download from
# https://jdk.java.net/jextract/ if absent.
#
# Re-run this whenever the libvpx headers under headers/ change.
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
PKG="com.github.auties00.cobalt.call.internal.video.vpx.bindings"

rm -f "$OUT/${PKG//.//}/LibVpx.java" "$OUT/${PKG//.//}/LibVpx\$shared.java"

"$JEXTRACT" \
  -t "$PKG" \
  -I "$DIR/headers" \
  --header-class-name LibVpx \
  --output "$OUT" \
  --include-function vpx_codec_err_to_string \
  --include-function vpx_codec_destroy \
  --include-function vpx_codec_control_ \
  --include-function vpx_codec_enc_init_ver \
  --include-function vpx_codec_enc_config_default \
  --include-function vpx_codec_enc_config_set \
  --include-function vpx_codec_encode \
  --include-function vpx_codec_get_cx_data \
  --include-function vpx_codec_dec_init_ver \
  --include-function vpx_codec_decode \
  --include-function vpx_codec_get_frame \
  --include-function vpx_codec_register_put_frame_cb \
  --include-function vpx_img_alloc \
  --include-function vpx_img_wrap \
  --include-function vpx_img_set_rect \
  --include-function vpx_img_free \
  --include-function vpx_codec_vp8_cx \
  --include-function vpx_codec_vp8_dx \
  --include-struct vpx_codec_ctx \
  --include-struct vpx_codec_enc_cfg \
  --include-struct vpx_codec_dec_cfg \
  --include-struct vpx_image \
  --include-struct vpx_codec_cx_pkt \
  --include-struct vpx_fixed_buf \
  --include-struct vpx_rational \
  --include-constant VPX_IMG_FMT_I420 \
  --include-constant VPX_IMG_FMT_NV12 \
  --include-constant VPX_IMG_FMT_YV12 \
  --include-constant VPX_FRAME_IS_KEY \
  --include-constant VPX_DL_REALTIME \
  --include-constant VPX_DL_GOOD_QUALITY \
  --include-constant VPX_DL_BEST_QUALITY \
  --include-constant VPX_CODEC_OK \
  --include-constant VPX_CODEC_USE_PSNR \
  --include-constant VPX_CODEC_USE_OUTPUT_PARTITION \
  --include-constant VPX_CODEC_CX_FRAME_PKT \
  --include-constant VPX_RC_ONE_PASS \
  --include-constant VPX_EFLAG_FORCE_KF \
  --include-constant VPX_ENCODER_ABI_VERSION \
  --include-constant VPX_DECODER_ABI_VERSION \
  --include-constant VP8E_SET_CPUUSED \
  --include-constant VP8E_SET_NOISE_SENSITIVITY \
  --include-constant VP8E_SET_TOKEN_PARTITIONS \
  --include-constant VP8E_SET_SCREEN_CONTENT_MODE \
  --include-constant VP8E_SET_TUNING \
  --include-constant VP8E_SET_MAX_INTRA_BITRATE_PCT \
  "$DIR/libvpx.h"

echo "wrote $OUT/${PKG//.//}/LibVpx.java"
