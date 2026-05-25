#!/usr/bin/env bash
#
# Builds every native dependency Cobalt's call + media layer links
# against, using the running host's NATIVE toolchain only. The host's
# uname is the classifier; the matching runner is picked by the matrix
# in .github/workflows/build-natives.yml.
#
# Outputs land at
#   modules/lib/dependencies/<dep>/bin/<classifier>/<lib*>
# under System.mapLibraryName-style names: lib<x>.so on Linux,
# lib<x>.dylib on darwin, <x>.dll on Windows (no lib prefix per
# Windows convention).
#
# Override any source tree by exporting <DEP>_SRC=/path/to/checkout
# before invocation. Otherwise the script clones the pinned ref into a
# temp dir per dep.
#
# Required tools on PATH (the workflow installs these per OS):
#   git python3 autoconf automake libtool pkg-config nasm yasm cmake
#   make and the host's C/C++ compiler.

set -Eeuo pipefail
trap 'echo "[build-natives] FAILED at ${BASH_SOURCE[0]}:${LINENO}: ${BASH_COMMAND}" >&2' ERR

case "$(uname -s)" in
    Linux)                OS=linux ;;
    Darwin)               OS=darwin ;;
    MINGW*|MSYS*|CYGWIN*) OS=windows ;;
    *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
    x86_64|amd64)  ARCH=x86_64 ;;
    aarch64|arm64) ARCH=aarch64 ;;
    *) echo "unsupported arch: $(uname -m)" >&2; exit 1 ;;
esac
CLASSIFIER="$OS-$ARCH"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
DEPS="$ROOT/modules/lib/dependencies"
BUILD="${BUILD_CACHE:-/tmp/cobalt-natives-build}"
case "$BUILD" in
    *[[:space:]]*)
        echo "[build-natives] FAIL: BUILD path contains whitespace: '$BUILD' (autotools rejects this). Set BUILD_CACHE to a space-free path." >&2
        exit 1
        ;;
esac
mkdir -p "$BUILD"
JOBS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

OPUS_REPO=https://github.com/xiph/opus.git
OPUS_REF=v1.5.2

OPENH264_REPO=https://github.com/cisco/openh264.git
OPENH264_REF=v2.4.1

SPEEXDSP_REPO=https://github.com/xiph/speexdsp.git
SPEEXDSP_REF=SpeexDSP-1.2.1

USRSCTP_REPO=https://github.com/sctplab/usrsctp.git
USRSCTP_REF=master

LIBVPX_REPO=https://chromium.googlesource.com/webm/libvpx
LIBVPX_REF=v1.15.1

LIBWEBP_REPO=https://chromium.googlesource.com/webm/libwebp
LIBWEBP_REF=v1.5.0

FFMPEG_REPO=https://github.com/FFmpeg/FFmpeg.git
FFMPEG_REF=n7.1

MDBX_REPO=https://github.com/erthink/libmdbx.git
MDBX_REF=v0.14.2

log()  { echo "[build-natives] $*"; }
fail() { echo "[build-natives] FAIL: $*" >&2; exit 1; }

ensure_src() {
    local var="$1" repo="$2" ref="$3" dirname="$4"
    local val="${!var-}"
    if [ -n "$val" ]; then
        [ -d "$val" ] || fail "$var=$val does not exist"
        log "$var override: $val"
        return 0
    fi
    local workdir="$BUILD/$dirname"
    if [ -d "$workdir/.git" ] && git -C "$workdir" rev-parse --verify "$ref^{commit}" >/dev/null 2>&1; then
        log "$dirname cached at $ref"
        git -C "$workdir" -c advice.detachedHead=false checkout --force "$ref"
        git -C "$workdir" clean -fdx
    else
        rm -rf "$workdir"
        if ! git clone --depth 1 --branch "$ref" "$repo" "$workdir"; then
            log "shallow clone of $repo @ $ref failed, retrying full clone"
            rm -rf "$workdir"
            git clone "$repo" "$workdir"
            git -C "$workdir" checkout "$ref"
        fi
    fi
    printf -v "$var" '%s' "$workdir"
    export "$var"
}

wipe_dest() {
    find "$1" -maxdepth 1 \( -type f -o -type l \) ! -name '.gitkeep' -delete 2>/dev/null || true
}

vendor_headers() {
    local src="$1" dest="$2"
    [ -d "$src" ] || fail "vendor_headers: source dir $src does not exist"
    mkdir -p "$dest"
    find "$dest" -maxdepth 1 -name '*.h' -type f -delete 2>/dev/null || true
    cp "$src"/*.h "$dest/"
}

ship_binary() {
    local dest="$1" name="$2" search="$3"
    local glob target
    case "$OS" in
        linux)   glob="lib${name}*.so*";   target="lib${name}.so" ;;
        darwin)  glob="lib${name}*.dylib"; target="lib${name}.dylib" ;;
        windows) glob="*${name}*.dll";     target="${name}.dll" ;;
    esac
    local biggest="" biggest_sz=0 f sz
    while IFS= read -r f; do
        [ -L "$f" ] && continue
        sz=$(stat -c %s "$f" 2>/dev/null || stat -f %z "$f" 2>/dev/null || echo 0)
        if [ "$sz" -gt "$biggest_sz" ]; then
            biggest="$f"
            biggest_sz="$sz"
        fi
    done < <(find "$search" -type f -name "$glob" 2>/dev/null)
    [ -n "$biggest" ] || fail "no '$glob' under $search ($name)"
    cp "$biggest" "$dest/$target"
}

build_opus() {
    local dest="$DEPS/libopus/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "opus"
    ensure_src OPUS_SRC "$OPUS_REPO" "$OPUS_REF" opus
    [ -x "$OPUS_SRC/configure" ] || ( cd "$OPUS_SRC" && autoreconf -isf )
    local b="$BUILD/build-opus"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && "$OPUS_SRC/configure" --prefix="$b/inst" \
        --enable-shared --disable-static \
        --disable-doc --disable-extra-programs \
        --disable-deep-plc --disable-dred )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    vendor_headers "$b/inst/include/opus" "$DEPS/libopus/headers/opus"
    wipe_dest "$dest"
    ship_binary "$dest" opus "$b/inst"
}

build_openh264() {
    local dest="$DEPS/openh264/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "openh264"
    ensure_src OPENH264_SRC "$OPENH264_REPO" "$OPENH264_REF" openh264
    local make_os make_arch
    case "$OS" in
        linux)   make_os=linux ;;
        darwin)  make_os=darwin ;;
        windows) make_os=mingw_nt ;;
    esac
    case "$ARCH" in
        x86_64)  make_arch=x86_64 ;;
        aarch64) make_arch=arm64 ;;
    esac
    make -C "$OPENH264_SRC" OS="$make_os" ARCH="$make_arch" clean 2>/dev/null || true
    make -C "$OPENH264_SRC" OS="$make_os" ARCH="$make_arch" -j "$JOBS"
    vendor_headers "$OPENH264_SRC/codec/api/wels" "$DEPS/openh264/headers"
    wipe_dest "$dest"
    ship_binary "$dest" openh264 "$OPENH264_SRC"
}

build_speexdsp() {
    local dest="$DEPS/speexdsp/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "speexdsp"
    ensure_src SPEEXDSP_SRC "$SPEEXDSP_REPO" "$SPEEXDSP_REF" speexdsp
    [ -x "$SPEEXDSP_SRC/configure" ] || ( cd "$SPEEXDSP_SRC" && ./autogen.sh )
    local b="$BUILD/build-speexdsp"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && "$SPEEXDSP_SRC/configure" --prefix="$b/inst" \
        --enable-shared --disable-static --disable-examples )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    vendor_headers "$b/inst/include/speex" "$DEPS/speexdsp/headers/speex"
    wipe_dest "$dest"
    ship_binary "$dest" speexdsp "$b/inst"
}

build_usrsctp() {
    local dest="$DEPS/usrsctp/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "usrsctp"
    ensure_src USRSCTP_SRC "$USRSCTP_REPO" "$USRSCTP_REF" usrsctp
    local b="$BUILD/build-usrsctp"
    rm -rf "$b" && mkdir -p "$b"
    cmake -S "$USRSCTP_SRC" -B "$b" \
        -DCMAKE_INSTALL_PREFIX="$b/inst" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=ON \
        -Dsctp_build_programs=OFF \
        -Dsctp_build_tests=OFF
    cmake --build "$b" -j "$JOBS"
    cmake --install "$b"
    vendor_headers "$b/inst/include" "$DEPS/usrsctp/headers"
    sed -i 's|void (\*)(const char \*format, \.\.\.)|void (*)(const char *format)|g' "$DEPS/usrsctp/headers/usrsctp.h"
    wipe_dest "$dest"
    ship_binary "$dest" usrsctp "$b/inst"
}

build_libvpx() {
    local dest="$DEPS/libvpx/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "libvpx"
    ensure_src LIBVPX_SRC "$LIBVPX_REPO" "$LIBVPX_REF" libvpx
    local target shared_args
    case "$OS-$ARCH" in
        linux-x86_64)   target=x86_64-linux-gcc ;;
        linux-aarch64)  target=arm64-linux-gcc ;;
        windows-x86_64) target=x86_64-win64-gcc ;;
        windows-aarch64) target=arm64-win64-gcc ;;
        darwin-x86_64)
            local dv; dv=$(uname -r | cut -d. -f1)
            target="x86_64-darwin${dv}-gcc"
            ;;
        darwin-aarch64)
            local dv; dv=$(uname -r | cut -d. -f1)
            target="arm64-darwin${dv}-gcc"
            ;;
    esac
    case "$OS" in
        windows) shared_args="--disable-shared --enable-static" ;;
        *)       shared_args="--enable-shared --disable-static" ;;
    esac
    local b="$BUILD/build-libvpx"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && "$LIBVPX_SRC/configure" \
        --target="$target" --prefix="$b/inst" \
        $shared_args --enable-pic \
        --enable-vp8 --enable-vp8-encoder --enable-vp8-decoder \
        --disable-vp9 --disable-vp9-encoder --disable-vp9-decoder \
        --disable-examples --disable-tools --disable-docs --disable-unit-tests )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    vendor_headers "$b/inst/include/vpx" "$DEPS/libvpx/headers/vpx"
    if [ "$OS" = windows ]; then
        "${CC:-gcc}" -shared -fPIC \
            -Wl,--whole-archive "$b/inst/lib/libvpx.a" -Wl,--no-whole-archive \
            -Wl,--export-all-symbols \
            -o "$b/inst/lib/vpx.dll" \
            -Wl,--out-implib="$b/inst/lib/libvpx.dll.a"
        wipe_dest "$dest"
        cp "$b/inst/lib/vpx.dll" "$dest/vpx.dll"
    else
        wipe_dest "$dest"
        ship_binary "$dest" vpx "$b/inst"
    fi
}

build_libwebp() {
    local dest="$DEPS/libwebp/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "libwebp"
    ensure_src LIBWEBP_SRC "$LIBWEBP_REPO" "$LIBWEBP_REF" libwebp
    [ -x "$LIBWEBP_SRC/configure" ] || ( cd "$LIBWEBP_SRC" && ./autogen.sh )
    local b="$BUILD/build-libwebp"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && "$LIBWEBP_SRC/configure" --prefix="$b/inst" \
        --enable-shared --disable-static \
        --disable-libwebpmux --disable-libwebpdemux \
        --disable-cwebp --disable-dwebp \
        --disable-png --disable-jpeg --disable-tiff --disable-gif --disable-wic )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    vendor_headers "$b/inst/include/webp" "$DEPS/libwebp/headers/webp"
    wipe_dest "$dest"
    ship_binary "$dest" webp "$b/inst"
}

vendor_ffmpeg_headers() {
    local inst_include="$1"
    local dest="$DEPS/ffmpeg/headers"
    for lib in libavformat libavcodec libavdevice libavfilter libavutil libswscale libswresample; do
        local src="$inst_include/$lib"
        local out="$dest/$lib"
        [ -d "$src" ] || continue
        rm -rf "$out" && mkdir -p "$out"
        cp "$src"/*.h "$out/"
    done
    if [ -f "$FFMPEG_SRC/VERSION" ]; then
        cp "$FFMPEG_SRC/VERSION" "$dest/UPSTREAM_VERSION"
    elif [ -f "$FFMPEG_SRC/RELEASE" ]; then
        cp "$FFMPEG_SRC/RELEASE" "$dest/UPSTREAM_VERSION"
    fi
}

build_ffmpeg() {
    local dest="$DEPS/ffmpeg/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "ffmpeg"
    ensure_src FFMPEG_SRC "$FFMPEG_REPO" "$FFMPEG_REF" ffmpeg
    local indevs=()
    case "$OS" in
        linux)   indevs+=(--enable-indev=v4l2 --enable-indev=kmsgrab --enable-indev=xcbgrab) ;;
        darwin)  indevs+=(--enable-indev=avfoundation) ;;
        windows) indevs+=(--enable-indev=dshow --enable-indev=gdigrab) ;;
    esac
    local pc_dir="$BUILD/build-ffmpeg/pc"
    rm -rf "$pc_dir" && mkdir -p "$pc_dir"
    local opus_inst="$BUILD/build-opus/inst"
    local vpx_inst="$BUILD/build-libvpx/inst"
    local webp_inst="$BUILD/build-libwebp/inst"
    local h264_stage="$BUILD/build-openh264/stage"
    rm -rf "$h264_stage" && mkdir -p "$h264_stage/lib" "$h264_stage/include/wels"
    find "$OPENH264_SRC" -maxdepth 1 \( \
            -name 'libopenh264*.so*' -o \
            -name 'libopenh264*.dylib' -o \
            -name 'libopenh264*.dll' -o \
            -name 'libopenh264*.dll.a' \
        \) -exec cp -P {} "$h264_stage/lib/" \;
    cp "$OPENH264_SRC"/codec/api/wels/*.h "$h264_stage/include/wels/"
    emit_pc() {
        local n="$1" lib="$2" ver="$3" libdir="$4" inc="$5"
        cat > "$pc_dir/${n}.pc" <<EOF
libdir=$libdir
includedir=$inc

Name: $n
Description: $n
Version: $ver
Libs: -L\${libdir} -l$lib
Cflags: -I\${includedir}
EOF
    }
    emit_pc opus     opus     1.5.2  "$opus_inst/lib"  "$opus_inst/include/opus"
    emit_pc vpx      vpx      1.15.1 "$vpx_inst/lib"   "$vpx_inst/include"
    emit_pc openh264 openh264 2.4.1  "$h264_stage/lib" "$h264_stage/include"
    emit_pc libwebp  webp     1.5.0  "$webp_inst/lib"  "$webp_inst/include"

    local b="$BUILD/build-ffmpeg/build"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && PKG_CONFIG_LIBDIR="$pc_dir" "$FFMPEG_SRC/configure" \
        --prefix="$b/inst" \
        --disable-everything --disable-programs \
        --disable-doc --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
        --disable-network --disable-static --enable-shared \
        --enable-pic --enable-lto \
        --enable-demuxer=mov,matroska,ogg,mp3,wav,flac,mp4,aac,image2,webp_pipe,jpeg_pipe \
        --enable-muxer=matroska,mov,wav,mp4,ipod,webp,image2,ogg,opus \
        --enable-parser=h264,aac,mpegaudio,vp8,opus \
        --enable-protocol=file \
        --enable-filter=scale,format,fps,crop,transpose,thumbnail \
        --enable-swscale --enable-swresample \
        --enable-avdevice \
        "${indevs[@]}" \
        --enable-encoder=mjpeg,aac \
        --enable-decoder=mjpeg,aac,flac,mp3,pcm_s16le,pcm_s16be,pcm_u8,vorbis \
        --enable-libopenh264 --enable-encoder=libopenh264 --enable-decoder=libopenh264 \
        --enable-libopus    --enable-encoder=libopus    --enable-decoder=libopus    \
        --enable-libvpx     --enable-encoder=libvpx_vp8 --enable-decoder=libvpx_vp8 \
        --enable-libwebp    --enable-encoder=libwebp    --enable-decoder=libwebp    \
        --disable-decoder=h264,opus,vp8 \
        --disable-encoder=vp9 )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    vendor_ffmpeg_headers "$b/inst/include"
    wipe_dest "$dest"
    for n in avutil avcodec avformat avfilter avdevice swscale swresample; do
        ship_binary "$dest" "$n" "$b/inst"
    done
}

build_mdbx() {
    local dest="$DEPS/libmdbx/bin/$CLASSIFIER"
    mkdir -p "$dest"
    log "mdbx"
    ensure_src MDBX_SRC "$MDBX_REPO" "$MDBX_REF" mdbx
    local dist="$MDBX_SRC"
    [ -f "$dist/mdbx.c" ] || fail "mdbx amalgamated source (mdbx.c) not found under $MDBX_SRC"
    local b="$BUILD/build-mdbx"
    rm -rf "$b" && mkdir -p "$b"
    local cc="${CC:-cc}"
    local wrap="$DEPS/libmdbx/mdbx_openu.c"
    local def="-DNDEBUG -DLIBMDBX_EXPORTS=1 -DMDBX_BUILD_SHARED_LIBRARY=1"
    case "$OS" in
        linux)   "$cc" -O3 $def -fPIC -shared -I "$dist" "$dist/mdbx.c" "$wrap" -o "$b/libmdbx.so"    -lpthread ;;
        darwin)  "$cc" -O3 $def -fPIC -shared -I "$dist" "$dist/mdbx.c" "$wrap" -o "$b/libmdbx.dylib" ;;
        windows) "$cc" -O3 $def       -shared -I "$dist" "$dist/mdbx.c" "$wrap" -o "$b/mdbx.dll"      -lntdll -lwinmm ;;
    esac
    vendor_headers "$dist" "$DEPS/libmdbx/headers"
    wipe_dest "$dest"
    ship_binary "$dest" mdbx "$b"
}

build_opus
build_openh264
build_speexdsp
build_usrsctp
build_libvpx
build_libwebp
build_ffmpeg
build_mdbx
log "done $CLASSIFIER"
