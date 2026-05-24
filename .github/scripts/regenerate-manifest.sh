#!/usr/bin/env bash
#
# Regenerates modules/lib/src/main/resources/META-INF/native-checksums.json
# from whatever binaries are currently under
# modules/lib/dependencies/<dep>/bin/<classifier>/. The version field is
# read from the root pom.xml; the commitSha pins the GitHub download URL
# to the immutable HEAD of the run that wrote the manifest.

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
DEPS="$ROOT/modules/lib/dependencies"
MANIFEST="$ROOT/modules/lib/src/main/resources/META-INF/native-checksums.json"

VERSION=$(grep -m1 '<version>' "$ROOT/pom.xml" | sed 's/[<\/]*version[> ]*//g' | tr -d ' \r')
COMMIT_SHA=$(git -C "$ROOT" rev-parse HEAD)

CLASSIFIERS=(linux-x86_64 linux-aarch64 windows-x86_64 windows-aarch64 darwin-x86_64 darwin-aarch64)
LIB_ROWS=(
    "libopus    opus              opus"
    "libvpx     vpx               vpx"
    "libwebp    webp              webp"
    "openh264   openh264          openh264"
    "speexdsp   speexdsp          speexdsp"
    "usrsctp    usrsctp           usrsctp"
    "ffmpeg     ffmpeg-avformat   avformat"
    "ffmpeg     ffmpeg-avcodec    avcodec"
    "ffmpeg     ffmpeg-avdevice   avdevice"
    "ffmpeg     ffmpeg-avutil     avutil"
    "ffmpeg     ffmpeg-avfilter   avfilter"
    "ffmpeg     ffmpeg-swscale    swscale"
    "ffmpeg     ffmpeg-swresample swresample"
)

ENTRIES=()
for classifier in "${CLASSIFIERS[@]}"; do
    case "$classifier" in
        linux-*)   prefix='lib'; ext='.so' ;;
        darwin-*)  prefix='lib'; ext='.dylib' ;;
        windows-*) prefix='';    ext='.dll' ;;
    esac
    for row in "${LIB_ROWS[@]}"; do
        read -r dir key tag <<< "$row"
        binary="$DEPS/$dir/bin/$classifier/${prefix}${tag}${ext}"
        [ -f "$binary" ] || continue
        sha=$(sha256sum "$binary" | awk '{print $1}')
        size=$(wc -c < "$binary" | tr -d ' ')
        rel="modules/lib/dependencies/$dir/bin/$classifier/$(basename "$binary")"
        ENTRIES+=("    \"${key}/${classifier}\": { \"sha256\": \"${sha}\", \"size\": ${size}, \"path\": \"${rel}\" }")
    done
done

mkdir -p "$(dirname "$MANIFEST")"
{
    echo '{'
    echo "  \"version\": \"${VERSION}\","
    echo "  \"commitSha\": \"${COMMIT_SHA}\","
    echo '  "binaries": {'
    last_idx=$(( ${#ENTRIES[@]} - 1 ))
    for i in "${!ENTRIES[@]}"; do
        if (( i == last_idx )); then
            echo "${ENTRIES[$i]}"
        else
            echo "${ENTRIES[$i]},"
        fi
    done
    echo '  }'
    echo '}'
} > "$MANIFEST"

echo "wrote $MANIFEST (${#ENTRIES[@]} entries) at commit ${COMMIT_SHA}"
