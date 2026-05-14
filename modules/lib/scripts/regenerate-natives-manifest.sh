#!/usr/bin/env bash
# Regenerates modules/lib/src/main/resources/META-INF/native-checksums.json
# from the binaries currently sitting under
# modules/lib/dependencies/<lib>/bin/<classifier>/.
#
# Captures the current HEAD commit SHA into the manifest's
# `commitSha` field; the loader pins runtime downloads to that SHA
# so the URL is immutable across tag deletions / retargeting.
#
# Output:
#   modules/lib/src/main/resources/META-INF/native-checksums.json

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DEPS="${ROOT}/modules/lib/dependencies"
OUT="${ROOT}/modules/lib/src/main/resources/META-INF/native-checksums.json"

VERSION=$(grep -m1 '<version>' "${ROOT}/pom.xml" | sed 's/[<\/]*version[> ]*//g' | tr -d ' ')
COMMIT_SHA=$(git -C "${ROOT}" rev-parse HEAD)
echo "Building checksum manifest for cobalt v${VERSION} @ ${COMMIT_SHA}"

LIBS=(libopus speexdsp libvpx openh264 usrsctp)
declare -A LIB_NAME_BY_DIR=(
    [libopus]=opus
    [speexdsp]=speexdsp
    [libvpx]=vpx
    [openh264]=openh264
    [usrsctp]=usrsctp
)

CLASSIFIERS=(linux-x86_64 linux-aarch64 darwin-x86_64 darwin-aarch64 windows-x86_64 windows-aarch64)

ENTRIES=()
for lib_dir in "${LIBS[@]}"; do
    lib_name="${LIB_NAME_BY_DIR[$lib_dir]}"
    for classifier in "${CLASSIFIERS[@]}"; do
        bin_dir="${DEPS}/${lib_dir}/bin/${classifier}"
        if [[ ! -d "${bin_dir}" ]]; then
            continue
        fi
        case "${classifier}" in
            linux-*)   ext=so;    prefix=lib ;;
            darwin-*)  ext=dylib; prefix=lib ;;
            windows-*) ext=dll;   prefix=    ;;
        esac
        binary="${bin_dir}/${prefix}${lib_name}.${ext}"
        if [[ ! -f "${binary}" ]]; then
            continue
        fi
        sha=$(sha256sum "${binary}" | awk '{print $1}')
        size=$(wc -c < "${binary}" | tr -d ' ')
        rel="modules/lib/dependencies/${lib_dir}/bin/${classifier}/${prefix}${lib_name}.${ext}"
        ENTRIES+=("    \"${lib_name}/${classifier}\": { \"sha256\": \"${sha}\", \"size\": ${size}, \"path\": \"${rel}\" }")
    done
done

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
} > "${OUT}"

echo "Wrote ${OUT} (${#ENTRIES[@]} entries)"
