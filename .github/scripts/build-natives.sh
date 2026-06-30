#!/usr/bin/env bash
#
# Builds every native dependency as a static archive, then links them into one
# combined shared library per platform: modules/lib/natives/bin/<classifier>/.
# build_combined forces in exactly the symbols the FFM bindings resolve (the
# union of every --include-function in the per-dep generate.sh, via gen_exports),
# exports only those, and dead-strips the rest.
#
# Override any source tree by exporting <DEP>_SRC before invocation; otherwise
# the pinned ref is cloned per dep.
#
# Required tools on PATH (the workflow installs these per OS): git python3
# autoconf automake libtool pkg-config nasm yasm cmake meson ninja make and the
# host's C/C++ compiler.

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
NATIVES="$ROOT/modules/lib/natives"
BUILD="${BUILD_CACHE:-/tmp/cobalt-natives-build}"
case "$BUILD" in
    *[[:space:]]*)
        echo "[build-natives] FAIL: BUILD path contains whitespace: '$BUILD' (autotools rejects this). Set BUILD_CACHE to a space-free path." >&2
        exit 1
        ;;
esac
mkdir -p "$BUILD"
JOBS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

# -fPIC is mandatory (archives link into a shared library); explicit CFLAGS
# override each build system's default optimization level, so -O2 -DNDEBUG must
# be set or opus/openh264/vpx/webp fall back to -O0 with asserts on.
# Section split feeds build_combined's -Wl,--gc-sections / -dead_strip.
SECTIONS_CFLAGS="-O2 -DNDEBUG -ffunction-sections -fdata-sections -fPIC"

# Realtime codecs lean -O3. Per-dep LTO is deliberately OFF: these archives are
# force-exported via -Wl,-u,<symbol> and GCC/Clang LTO archives do not reliably
# honor -u (FFmpeg excepted: it LTO-links its own self-contained closure).
CODEC_CFLAGS="-O3 -DNDEBUG -ffunction-sections -fdata-sections -fPIC"

# On Windows (MinGW) static-link the toolchain runtime so the combined library
# does not leak libgcc_s_seh-1/libstdc++-6/libwinpthread-1 DLL dependencies.
MINGW_CFLAGS=""
if [ "$OS" = windows ]; then
    MINGW_CFLAGS="-static-libgcc"
fi

EXTRA_CFLAGS="$SECTIONS_CFLAGS $MINGW_CFLAGS"
CODEC_EXTRA_CFLAGS="$CODEC_CFLAGS $MINGW_CFLAGS"

# openh264 is C++, so FFmpeg's static dependency probe and the combined link
# must resolve the C++ runtime; advertised via Libs.private in the pc shims below.
case "$OS" in
    darwin) CXXLIB="-lc++" ;;
    *)      CXXLIB="-lstdc++" ;;
esac

OPUS_REPO=https://github.com/xiph/opus.git
OPUS_REF=v1.5.2

OPENH264_REPO=https://github.com/cisco/openh264.git
OPENH264_REF=v2.4.1

USRSCTP_REPO=https://github.com/sctplab/usrsctp.git
USRSCTP_REF=master

LIBSRTP_REPO=https://github.com/cisco/libsrtp.git
LIBSRTP_REF=v2.8.0

LIBVPX_REPO=https://chromium.googlesource.com/webm/libvpx
LIBVPX_REF=v1.15.1

LIBYUV_REPO=https://chromium.googlesource.com/libyuv/libyuv
# libyuv is unversioned (no release tags); pin a known-good commit.
LIBYUV_REF=main

# AV1 DECODE ONLY (dav1d has no encoder).
DAV1D_REPO=https://code.videolan.org/videolan/dav1d.git
DAV1D_REF=1.4.3
RAV1E_REPO=https://github.com/xiph/rav1e.git
RAV1E_REF=v0.7.1

LIBWEBP_REPO=https://chromium.googlesource.com/webm/libwebp
LIBWEBP_REF=v1.5.0

FFMPEG_REPO=https://github.com/FFmpeg/FFmpeg.git
FFMPEG_REF=n7.1

# WebRTC Audio Processing Module: the PulseAudio-maintained standalone build of
# WebRTC's AEC3 + noise suppressor (incl. the ML denoiser) + gain controller, the
# capture-conditioning stack WhatsApp uses (wa_mobile_audio_processing.cc). Meson
# build, like dav1d. NO release tags on this mirror; pin a known-good ref.
WEBRTC_APM_REPO=https://gitlab.freedesktop.org/pulseaudio/webrtc-audio-processing.git
WEBRTC_APM_REF=v2.1

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

vendor_headers() {
    local src="$1" dest="$2"
    [ -d "$src" ] || fail "vendor_headers: source dir $src does not exist"
    mkdir -p "$dest"
    find "$dest" -maxdepth 1 -name '*.h' -type f -delete 2>/dev/null || true
    cp "$src"/*.h "$dest/"
}

build_opus() {
    log "opus (static, -O3)"
    ensure_src OPUS_SRC "$OPUS_REPO" "$OPUS_REF" opus
    [ -x "$OPUS_SRC/configure" ] || ( cd "$OPUS_SRC" && autoreconf -isf )
    local b="$BUILD/build-opus"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && CFLAGS="${CFLAGS:-} $CODEC_EXTRA_CFLAGS" \
        "$OPUS_SRC/configure" --prefix="$b/inst" \
        --disable-shared --enable-static --with-pic \
        --disable-doc --disable-extra-programs \
        --disable-deep-plc --disable-dred )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    # The opus/*.h headers are vendored only as the compile target for the
    # portable shim (cobalt_opus_shim.c includes "opus/opus.h"); generate.sh binds
    # the shim header cobalt_opus_shim.h, not the raw headers.
    vendor_headers "$b/inst/include/opus" "$DEPS/libopus/headers/opus"
    # Compile the portable extern-C shim against the opus headers and archive it.
    # build_combined adds libcobalt_opus_shim.a to extra_archives so the combined
    # library exports the cobalt_opus_* symbols (the real opus_* symbols they call
    # are pulled from libopus.a through ffmpeg's static closure in the same link
    # group). The shim is plain C (libopus is a C library).
    local cc="${CC:-cc}"
    local shim_src="$DEPS/libopus/cobalt_opus_shim.c"
    [ -f "$shim_src" ] || fail "libopus shim source not found: $shim_src"
    "$cc" -std=c11 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/libopus" -I "$b/inst/include" \
        -c "$shim_src" -o "$b/cobalt_opus_shim.o"
    ar rcs "$b/libcobalt_opus_shim.a" "$b/cobalt_opus_shim.o"
}

# Do not strip libopenh264.a here: the combined link still needs its symbols;
# build_combined's final -Wl,--gc-sections + strip handle size.
build_openh264() {
    log "openh264 (static, Release -O3)"
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
    make -C "$OPENH264_SRC" OS="$make_os" ARCH="$make_arch" BUILDTYPE=Release \
        CFLAGS="${CFLAGS:-} $CODEC_EXTRA_CFLAGS" \
        -j "$JOBS" libopenh264.a
    [ -f "$OPENH264_SRC/libopenh264.a" ] || fail "openh264 static archive not produced"
    # The wels/*.h headers are vendored only as the compile target for the
    # portable shim (cobalt_h264_shim.cpp includes "codec_api.h"); generate.sh
    # binds the shim header cobalt_h264_shim.h, not the raw headers.
    vendor_headers "$OPENH264_SRC/codec/api/wels" "$DEPS/openh264/headers"
    # Compile the portable C++ shim against the openh264 headers and archive it
    # into a dedicated artifact dir (openh264 itself builds in-source). build_combined
    # adds libcobalt_h264_shim.a to extra_archives so the combined library exports
    # the cobalt_h264_* symbols (the real Wels* symbols they call are pulled from
    # libopenh264.a through ffmpeg's static closure in the same link group). The
    # shim is C++ (extern "C" linkage): it includes codec_api.h and drives the
    # ISVCEncoder / ISVCDecoder C++ objects through their vtables.
    local b="$BUILD/build-openh264"
    rm -rf "$b" && mkdir -p "$b"
    local cxx="${CXX:-c++}"
    local shim_src="$DEPS/openh264/cobalt_h264_shim.cpp"
    [ -f "$shim_src" ] || fail "openh264 shim source not found: $shim_src"
    "$cxx" -std=c++17 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/openh264" -I "$DEPS/openh264/headers" \
        -c "$shim_src" -o "$b/cobalt_h264_shim.o"
    ar rcs "$b/libcobalt_h264_shim.a" "$b/cobalt_h264_shim.o"
}

build_usrsctp() {
    log "usrsctp (static)"
    ensure_src USRSCTP_SRC "$USRSCTP_REPO" "$USRSCTP_REF" usrsctp
    local b="$BUILD/build-usrsctp"
    rm -rf "$b" && mkdir -p "$b"
    cmake -S "$USRSCTP_SRC" -B "$b" \
        -DCMAKE_INSTALL_PREFIX="$b/inst" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_C_FLAGS="${CFLAGS:-} $EXTRA_CFLAGS" \
        -Dsctp_build_programs=OFF \
        -Dsctp_build_tests=OFF
    cmake --build "$b" -j "$JOBS"
    cmake --install "$b"
    vendor_headers "$b/inst/include" "$DEPS/usrsctp/headers"
    # jextract cannot model a (const char*, ...) function-pointer field; rewrite
    # the variadic debug-callback typedef to fixed arity. Vestigial now that
    # generate.sh binds the portable shim header instead of usrsctp.h, but kept
    # harmless: the shim compiles against the pristine install-tree usrsctp.h.
    sed -i 's|void (\*)(const char \*format, \.\.\.)|void (*)(const char *format)|g' "$DEPS/usrsctp/headers/usrsctp.h"
    # Compile the portable extern-C shim against the usrsctp install headers and
    # archive it. build_combined adds libcobalt_sctp_shim.a to extra_archives so
    # the combined library exports the cobalt_sctp_* symbols (the real usrsctp_*
    # symbols they call are pulled from libusrsctp.a in the same link group). The
    # shim is plain C; it includes <usrsctp.h> from the install tree, which also
    # pulls in the platform socket headers it needs.
    local cc="${CC:-cc}"
    local shim_src="$DEPS/usrsctp/cobalt_sctp_shim.c"
    [ -f "$shim_src" ] || fail "usrsctp shim source not found: $shim_src"
    "$cc" -std=c11 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/usrsctp" -I "$b/inst/include" \
        -c "$shim_src" -o "$b/cobalt_sctp_shim.o"
    ar rcs "$b/libcobalt_sctp_shim.a" "$b/cobalt_sctp_shim.o"
}

build_libsrtp() {
    log "libsrtp (static)"
    ensure_src LIBSRTP_SRC "$LIBSRTP_REPO" "$LIBSRTP_REF" libsrtp
    local b="$BUILD/build-libsrtp"
    rm -rf "$b" && mkdir -p "$b"
    # Internal crypto (no -DENABLE_OPENSSL) keeps the combined library free of an
    # external crypto dependency; warnings-as-errors off (SECTIONS_CFLAGS trips
    # libsrtp's strict default).
    cmake -S "$LIBSRTP_SRC" -B "$b" \
        -DCMAKE_INSTALL_PREFIX="$b/inst" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_C_FLAGS="${CFLAGS:-} $EXTRA_CFLAGS" \
        -DLIBSRTP_TEST_APPS=OFF \
        -DENABLE_WARNINGS_AS_ERRORS=OFF
    cmake --build "$b" -j "$JOBS"
    cmake --install "$b"
    # srtp.h is vendored only as the compile target for the portable shim
    # (cobalt_srtp_shim.cpp includes <srtp2/srtp.h>); generate.sh binds the shim
    # header cobalt_srtp_shim.h, not srtp.h.
    cp "$b/inst/include/srtp2/srtp.h" "$DEPS/libsrtp/headers/srtp.h"
    # Compile the portable extern-C shim against the libsrtp headers and archive
    # it. build_combined adds libcobalt_srtp_shim.a to extra_archives so the
    # combined library exports the cobalt_srtp_* symbols (the real srtp_* symbols
    # they call are pulled from libsrtp2.a in the same link group). The shim is
    # C++ (extern "C" linkage) so it is compiled with the C++ compiler.
    local cxx="${CXX:-c++}"
    local shim_src="$DEPS/libsrtp/cobalt_srtp_shim.cpp"
    [ -f "$shim_src" ] || fail "libsrtp shim source not found: $shim_src"
    "$cxx" -std=c++17 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/libsrtp" -I "$b/inst/include" \
        -c "$shim_src" -o "$b/cobalt_srtp_shim.o"
    ar rcs "$b/libcobalt_srtp_shim.a" "$b/cobalt_srtp_shim.o"
}

build_libvpx() {
    log "libvpx (static)"
    ensure_src LIBVPX_SRC "$LIBVPX_REPO" "$LIBVPX_REF" libvpx
    local target
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
    local b="$BUILD/build-libvpx"
    rm -rf "$b" && mkdir -p "$b"
    # Both VP8 and VP9 (enc+dec) are bound by generate.sh, so all four interface
    # symbols must compile into libvpx.a or the forced -Wl,-u export fails at the
    # combined link.
    ( cd "$b" && CFLAGS="${CFLAGS:-} $CODEC_EXTRA_CFLAGS" \
        "$LIBVPX_SRC/configure" \
        --target="$target" --prefix="$b/inst" \
        --disable-shared --enable-static --enable-pic \
        --enable-vp8 --enable-vp8-encoder --enable-vp8-decoder \
        --enable-vp9 --enable-vp9-encoder --enable-vp9-decoder \
        --enable-runtime-cpu-detect \
        --disable-examples --disable-tools --disable-docs --disable-unit-tests )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    # The vpx/*.h headers are vendored only as the compile target for the
    # portable shim (cobalt_vpx_shim.c includes "vpx/vpx_codec.h" etc.);
    # generate.sh binds the shim header cobalt_vpx_shim.h, not the raw headers.
    vendor_headers "$b/inst/include/vpx" "$DEPS/libvpx/headers/vpx"
    # Compile the portable extern-C shim against the libvpx headers and archive
    # it. build_combined adds libcobalt_vpx_shim.a to extra_archives so the
    # combined library exports the cobalt_vpx_* symbols (the real vpx_codec_*
    # symbols they call are pulled from libvpx.a through ffmpeg's static closure
    # in the same link group). The shim is plain C (libvpx is a C library).
    local cc="${CC:-cc}"
    local shim_src="$DEPS/libvpx/cobalt_vpx_shim.c"
    [ -f "$shim_src" ] || fail "libvpx shim source not found: $shim_src"
    "$cc" -std=c11 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/libvpx" -I "$b/inst/include" \
        -c "$shim_src" -o "$b/cobalt_vpx_shim.o"
    ar rcs "$b/libcobalt_vpx_shim.a" "$b/cobalt_vpx_shim.o"
}

build_libyuv() {
    log "libyuv (static)"
    ensure_src LIBYUV_SRC "$LIBYUV_REPO" "$LIBYUV_REF" libyuv
    local b="$BUILD/build-libyuv"
    rm -rf "$b" && mkdir -p "$b"
    # JPEG off keeps the combined library free of a libjpeg dependency (no bound
    # entry point touches libyuv's optional MJPEG path).
    cmake -S "$LIBYUV_SRC" -B "$b" \
        -DCMAKE_INSTALL_PREFIX="$b/inst" \
        -DCMAKE_INSTALL_LIBDIR=lib \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_C_FLAGS="${CFLAGS:-} $EXTRA_CFLAGS" \
        -DCMAKE_CXX_FLAGS="${CXXFLAGS:-} $EXTRA_CFLAGS" \
        -DLIBYUV_WITH_JPEG=OFF \
        -DJPEG_FOUND=OFF
    cmake --build "$b" -j "$JOBS"
    cmake --install "$b"
    # libyuv.h is vendored only as the compile target for the portable shim
    # (cobalt_yuv_shim.c includes <libyuv.h>); generate.sh binds the shim header
    # cobalt_yuv_shim.h, not the libyuv umbrella. Vendor the full public tree so
    # the umbrella's includes resolve when the shim is compiled.
    vendor_headers "$b/inst/include" "$DEPS/libyuv/headers"
    rm -rf "$DEPS/libyuv/headers/libyuv"
    cp -r "$b/inst/include/libyuv" "$DEPS/libyuv/headers/libyuv"
    # Compile the portable extern-C shim against the libyuv headers and archive
    # it. build_combined adds libcobalt_yuv_shim.a to extra_archives so the
    # combined library exports the cobalt_yuv_* symbols (the real libyuv symbols
    # they call are pulled from libyuv.a in the same link group). The shim is
    # compiled as C (not C++) so the libyuv headers expose their plain extern-C
    # prototypes rather than the namespace-wrapped C++ declarations.
    local cc="${CC:-cc}"
    local shim_src="$DEPS/libyuv/cobalt_yuv_shim.c"
    [ -f "$shim_src" ] || fail "libyuv shim source not found: $shim_src"
    "$cc" -std=c11 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/libyuv" -I "$b/inst/include" \
        -c "$shim_src" -o "$b/cobalt_yuv_shim.o"
    ar rcs "$b/libcobalt_yuv_shim.a" "$b/cobalt_yuv_shim.o"
}

# AV1 DECODE ONLY (dav1d has no encoder). Builds with meson + ninja, which must
# be on PATH (pip install meson ninja, or the distro packages).
build_av1() {
    log "dav1d / AV1 decode (static, release -O3, 8-bit only)"
    command -v meson >/dev/null 2>&1 || fail "dav1d needs meson on PATH (pip install meson ninja)"
    command -v ninja >/dev/null 2>&1 || fail "dav1d needs ninja on PATH (pip install meson ninja)"
    ensure_src DAV1D_SRC "$DAV1D_REPO" "$DAV1D_REF" dav1d
    local b="$BUILD/build-dav1d"
    rm -rf "$b"
    # -Dbitdepths=8: the call video path is 8-bit, so dropping the 16bpc DSP
    # templates roughly halves the DSP object size.
    meson setup "$b" "$DAV1D_SRC" \
        --prefix="$b/inst" \
        --libdir=lib \
        --default-library=static \
        --buildtype=release \
        -Dbitdepths=8 \
        -Denable_tools=false \
        -Denable_tests=false \
        -Db_staticpic=true \
        -Dc_args="${CFLAGS:-} $CODEC_EXTRA_CFLAGS"
    ninja -C "$b"
    ninja -C "$b" install
    # dav1d.h (and its common/data/picture/headers/version siblings) is vendored
    # only as the compile target for the portable shim (cobalt_dav1d_shim.c
    # includes "dav1d.h"); generate.sh binds the shim header cobalt_dav1d_shim.h,
    # not dav1d.h. Vendor the whole dav1d/ subtree so the umbrella's includes
    # resolve when the shim is compiled.
    rm -f "$DEPS/dav1d/headers"/*.h
    cp "$b/inst/include/dav1d"/*.h "$DEPS/dav1d/headers/"
    # Compile the portable extern-C shim against the dav1d headers and archive
    # it. build_combined adds libcobalt_dav1d_shim.a to extra_archives so the
    # combined library exports the cobalt_dav1d_* symbols (the real dav1d_*
    # symbols they call are pulled from libdav1d.a in the same link group). The
    # shim is plain C and includes "dav1d.h" from the install tree's dav1d/ dir.
    local cc="${CC:-cc}"
    local shim_src="$DEPS/dav1d/cobalt_dav1d_shim.c"
    [ -f "$shim_src" ] || fail "dav1d shim source not found: $shim_src"
    "$cc" -std=c11 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/dav1d" -I "$b/inst/include/dav1d" \
        -c "$shim_src" -o "$b/cobalt_dav1d_shim.o"
    ar rcs "$b/libcobalt_dav1d_shim.a" "$b/cobalt_dav1d_shim.o"
}

# AV1 ENCODE ONLY (rav1e is the Rust AV1 encoder; dav1d above is decode-only).
# Cobalt's chosen AV1 encoder: the wa-voip build names no AV1 encoder library, so
# this is a deliberate divergence. Built via cargo-c, which must be on PATH
# (cargo install cargo-c, with a Rust toolchain).
build_rav1e() {
    log "rav1e / AV1 encode (static, release, cargo-c)"
    command -v cargo >/dev/null 2>&1 || fail "rav1e needs cargo (Rust toolchain) on PATH"
    # cargo-c provides `cargo cinstall`, which emits the C API (librav1e.a +
    # include/rav1e/rav1e.h + rav1e.pc). Install it once if absent.
    cargo cinstall --help >/dev/null 2>&1 || cargo install cargo-c
    ensure_src RAV1E_SRC "$RAV1E_REPO" "$RAV1E_REF" rav1e
    local b="$BUILD/build-rav1e"
    rm -rf "$b"
    # Install the static C API into $b/inst: librav1e.a in $b/inst/lib, the
    # generated header in $b/inst/include/rav1e/rav1e.h, rav1e.pc under lib.
    ( cd "$RAV1E_SRC" && \
      CARGO_TARGET_DIR="$b/target" \
      cargo cinstall --release \
        --library-type=staticlib \
        --prefix="$b/inst" --libdir=lib --includedir=include )
    # rav1e.h is vendored only as the compile target for the portable shim
    # (cobalt_rav1e_shim.c includes "rav1e.h"); generate.sh binds the shim header
    # cobalt_rav1e_shim.h, not rav1e.h.
    rm -f "$DEPS/rav1e/headers"/*.h
    mkdir -p "$DEPS/rav1e/headers"
    cp "$b/inst/include/rav1e"/*.h "$DEPS/rav1e/headers/"
    # Compile the portable extern-C shim against the rav1e header and archive it.
    # build_combined adds BOTH librav1e.a and libcobalt_rav1e_shim.a to
    # extra_archives (rav1e is NOT in ffmpeg's pkg-config closure), so the combined
    # library both resolves the real rav1e_* symbols and exports the cobalt_rav1e_*
    # symbols (drawn from generate.sh's --include-function list). Plain C shim.
    local cc="${CC:-cc}"
    local shim_src="$DEPS/rav1e/cobalt_rav1e_shim.c"
    [ -f "$shim_src" ] || fail "rav1e shim source not found: $shim_src"
    "$cc" -std=c11 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/rav1e" -I "$b/inst/include/rav1e" \
        -c "$shim_src" -o "$b/cobalt_rav1e_shim.o"
    ar rcs "$b/libcobalt_rav1e_shim.a" "$b/cobalt_rav1e_shim.o"
}

# libwebp is NOT bound directly by a generate.sh; FFmpeg consumes it as its
# libwebp encoder (the sticker pipeline), linking it transitively through
# ffmpeg's pkg-config closure.
build_libwebp() {
    log "libwebp (static)"
    ensure_src LIBWEBP_SRC "$LIBWEBP_REPO" "$LIBWEBP_REF" libwebp
    [ -x "$LIBWEBP_SRC/configure" ] || ( cd "$LIBWEBP_SRC" && ./autogen.sh )
    local b="$BUILD/build-libwebp"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && CFLAGS="${CFLAGS:-} $EXTRA_CFLAGS" \
        "$LIBWEBP_SRC/configure" --prefix="$b/inst" \
        --disable-shared --enable-static --with-pic \
        --disable-libwebpmux --disable-libwebpdemux \
        --disable-cwebp --disable-dwebp \
        --disable-png --disable-jpeg --disable-tiff --disable-gif --disable-wic )
    make -C "$b" -j "$JOBS"
    make -C "$b" install
    vendor_headers "$b/inst/include/webp" "$DEPS/libwebp/headers/webp"
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

# Path to ffmpeg's install prefix, shared between build_ffmpeg (writer) and
# build_combined (reader, for the pkg-config closure).
FFMPEG_INST="$BUILD/build-ffmpeg/build/inst"
# Path to the pkg-config shims pointing at the codec static archives.
FFMPEG_PC_DIR="$BUILD/build-ffmpeg/pc"

build_ffmpeg() {
    log "ffmpeg (static)"
    ensure_src FFMPEG_SRC "$FFMPEG_REPO" "$FFMPEG_REF" ffmpeg
    local indevs=()
    case "$OS" in
        linux)   indevs+=(--enable-indev=v4l2 --enable-indev=kmsgrab --enable-indev=xcbgrab) ;;
        darwin)  indevs+=(--enable-indev=avfoundation) ;;
        windows) indevs+=(--enable-indev=dshow --enable-indev=gdigrab) ;;
    esac
    rm -rf "$FFMPEG_PC_DIR" && mkdir -p "$FFMPEG_PC_DIR"
    local opus_inst="$BUILD/build-opus/inst"
    local vpx_inst="$BUILD/build-libvpx/inst"
    local webp_inst="$BUILD/build-libwebp/inst"
    local h264_stage="$BUILD/build-openh264/stage"
    rm -rf "$h264_stage" && mkdir -p "$h264_stage/lib" "$h264_stage/include/wels"
    cp "$OPENH264_SRC/libopenh264.a" "$h264_stage/lib/"
    cp "$OPENH264_SRC"/codec/api/wels/*.h "$h264_stage/include/wels/"
    # $6 lists $lib's static-link private deps. FFmpeg reads Libs.private under
    # --pkg-config-flags=--static to resolve the runtime/math/thread libraries the
    # archive references but does not provide.
    emit_pc() {
        local n="$1" lib="$2" ver="$3" libdir="$4" inc="$5" priv="${6:-}"
        cat > "$FFMPEG_PC_DIR/${n}.pc" <<EOF
libdir=$libdir
includedir=$inc

Name: $n
Description: $n
Version: $ver
Libs: -L\${libdir} -l$lib
Libs.private: $priv
Cflags: -I\${includedir}
EOF
    }
    emit_pc opus     opus     1.5.2  "$opus_inst/lib"  "$opus_inst/include/opus" "-lm"
    emit_pc vpx      vpx      1.15.1 "$vpx_inst/lib"   "$vpx_inst/include"       "-lm -lpthread"
    emit_pc openh264 openh264 2.4.1  "$h264_stage/lib" "$h264_stage/include"     "$CXXLIB -lm"
    emit_pc libwebp  webp     1.5.0  "$webp_inst/lib"  "$webp_inst/include"      "-lsharpyuv -lm -lpthread"

    local b="$BUILD/build-ffmpeg/build"
    rm -rf "$b" && mkdir -p "$b"
    ( cd "$b" && PKG_CONFIG_LIBDIR="$FFMPEG_PC_DIR" "$FFMPEG_SRC/configure" \
        --prefix="$b/inst" \
        --extra-cflags="${CFLAGS:-} $EXTRA_CFLAGS" \
        --disable-everything --disable-programs \
        --disable-doc --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
        --disable-network --enable-static --disable-shared \
        --enable-pic --enable-lto \
        --disable-iconv \
        --pkg-config-flags=--static \
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
}

# WebRTC Audio Processing Module (static). Builds with meson + ninja (already
# required by build_av1), producing libwebrtc-audio-processing.a + its C++
# headers; the Cobalt extern-C shim cobalt_webrtc_apm_shim.cpp is compiled against
# those headers and archived so build_combined exports the cobalt_webrtc_apm_*
# symbols (the real webrtc::* symbols they call are pulled from the APM archive in
# the same link group). The shim is C++ (extern "C" linkage) so it is compiled
# with the C++ compiler. See modules/lib/dependencies/webrtc-apm/generate.sh and
# re/calls2-spec/NATIVE-BINDINGS.md.
#
# NOTE (gated): the shim source cobalt_webrtc_apm_shim.cpp is not yet committed
# (the Java binding CobaltWebRtcApm is hand-committed in jextract shape and the
# WebRtcAudioProcessor seam stays bypassed until the native artifact lands), so
# this step is skipped unless the shim source exists. Once it is added, the
# function builds and archives the APM normally.
build_webrtc_apm() {
    local shim_src="$DEPS/webrtc-apm/cobalt_webrtc_apm_shim.cpp"
    if [ ! -f "$shim_src" ]; then
        log "webrtc-apm: shim source $shim_src absent; skipping (binding stays gated)"
        return 0
    fi
    log "webrtc-audio-processing (static, release)"
    command -v meson >/dev/null 2>&1 || fail "webrtc-apm needs meson on PATH (pip install meson ninja)"
    command -v ninja >/dev/null 2>&1 || fail "webrtc-apm needs ninja on PATH (pip install meson ninja)"
    ensure_src WEBRTC_APM_SRC "$WEBRTC_APM_REPO" "$WEBRTC_APM_REF" webrtc-apm
    local b="$BUILD/build-webrtc-apm"
    rm -rf "$b"
    meson setup "$b" "$WEBRTC_APM_SRC" \
        --prefix="$b/inst" \
        --libdir=lib \
        --default-library=static \
        --buildtype=release \
        -Db_staticpic=true \
        -Dcpp_args="${CXXFLAGS:-} $EXTRA_CFLAGS"
    ninja -C "$b"
    ninja -C "$b" install
    # The webrtc-audio-processing headers are vendored only as the compile target
    # for the portable shim (cobalt_webrtc_apm_shim.cpp includes the APM headers);
    # generate.sh binds the shim header cobalt_webrtc_apm_shim.h, not the raw
    # WebRTC headers.
    local apm_inc; apm_inc=$(find "$b/inst/include" -maxdepth 1 -type d -name 'webrtc-audio-processing*' | head -1)
    [ -n "$apm_inc" ] || fail "webrtc-audio-processing include dir not produced"
    local cxx="${CXX:-c++}"
    "$cxx" -std=c++17 -O2 -DNDEBUG $EXTRA_CFLAGS \
        -I "$DEPS/webrtc-apm" -I "$apm_inc" \
        -c "$shim_src" -o "$b/cobalt_webrtc_apm_shim.o"
    ar rcs "$b/libcobalt_webrtc_apm_shim.a" "$b/cobalt_webrtc_apm_shim.o"
}

# Derives the combined library's export symbol set into $1 from the per-dep
# generate.sh --include-function flags: exactly what the FFM bindings resolve and
# nothing more, so the linker can dead-strip the rest. netmonitor is excluded (it
# uses generate-{linux,macos,windows} scripts and binds OS libraries Cobalt never
# ships).
gen_exports() {
    local out="$1"
    local gens
    mapfile -t gens < <(find "$DEPS" -maxdepth 2 -name generate.sh -type f | sort)
    [ "${#gens[@]}" -gt 0 ] || fail "no generate.sh under $DEPS/*/ to derive exports from"
    local g
    for g in "${gens[@]}"; do
        # Skip comment lines so prose that mentions the literal --include-function
        # token (followed by another word) does not leak a bogus export symbol.
        awk '/^[[:space:]]*#/ { next } { for (i = 1; i <= NF; i++) if ($i == "--include-function") print $(i + 1) }' "$g"
    done | sort -u > "$out"
    [ -s "$out" ] || fail "gen_exports produced no symbols"
    log "exports: $(wc -l < "$out" | tr -d ' ') symbols -> $out"
}

# Writes the platform-specific export-control file from exports.txt and echoes
# its path. ELF: a version script. Mach-O: an _underscored symbol list. PE: a
# .def EXPORTS block.
write_export_file() {
    local exports="$1" b="$2"
    case "$OS" in
        linux)
            local vs="$b/exports.map"
            { echo '{'; echo '  global:'; sed 's/^/    /; s/$/;/' "$exports"; \
              echo '  local:'; echo '    *;'; echo '};'; } > "$vs"
            echo "$vs"
            ;;
        darwin)
            local sl="$b/exports.syms"
            sed 's/^/_/' "$exports" > "$sl"
            echo "$sl"
            ;;
        windows)
            local def="$b/exports.def"
            { echo 'EXPORTS'; cat "$exports"; } > "$def"
            echo "$def"
            ;;
    esac
}

build_combined() {
    log "combined libcobalt-native"
    local b="$BUILD/build-combined"
    rm -rf "$b" && mkdir -p "$b"

    local exports="$b/exports.txt"
    gen_exports "$exports"

    # Archives NOT in ffmpeg's pkg-config closure (which already drags in
    # opus/vpx/openh264/webp via --static below), so they link here directly.
    local extra_archives=(
        "$BUILD/build-usrsctp/libcobalt_sctp_shim.a"
        "$BUILD/build-usrsctp/inst/lib/libusrsctp.a"
        "$BUILD/build-libsrtp/libcobalt_srtp_shim.a"
        "$BUILD/build-libsrtp/inst/lib/libsrtp2.a"
        "$BUILD/build-libyuv/libcobalt_yuv_shim.a"
        "$BUILD/build-libyuv/inst/lib/libyuv.a"
        "$BUILD/build-dav1d/libcobalt_dav1d_shim.a"
        "$BUILD/build-dav1d/inst/lib/libdav1d.a"
        "$BUILD/build-rav1e/libcobalt_rav1e_shim.a"
        "$BUILD/build-rav1e/inst/lib/librav1e.a"
        "$BUILD/build-libvpx/libcobalt_vpx_shim.a"
        "$BUILD/build-opus/libcobalt_opus_shim.a"
        "$BUILD/build-openh264/libcobalt_h264_shim.a"
    )
    # The WebRTC APM is gated: build_webrtc_apm only produces its archives once the
    # extern-C shim source lands, so append them only when present. Until then the
    # combined library omits the cobalt_webrtc_apm_* symbols and CobaltWebRtcApm
    # resolves them as absent (isAvailable()==false), keeping the conditioner
    # bypassed. The shim archive is listed BEFORE the WebRTC APM archive so the
    # cobalt_webrtc_apm_* wrappers resolve the webrtc::* symbols from it in the link
    # group.
    if [ -f "$BUILD/build-webrtc-apm/libcobalt_webrtc_apm_shim.a" ]; then
        local apm_archive; apm_archive=$(find "$BUILD/build-webrtc-apm/inst/lib" -maxdepth 1 -name 'libwebrtc-audio-processing*.a' | head -1)
        [ -n "$apm_archive" ] || fail "webrtc-audio-processing static archive not found next to its shim"
        extra_archives+=(
            "$BUILD/build-webrtc-apm/libcobalt_webrtc_apm_shim.a"
            "$apm_archive"
        )
    fi
    local a
    for a in "${extra_archives[@]}"; do
        [ -f "$a" ] || fail "missing static archive: $a"
    done

    # ffmpeg's complete static link closure (libav* + the codec archives wired
    # through the pkg-config shims + every system library ffmpeg needs).
    local ff_libs
    ff_libs=$(PKG_CONFIG_PATH="$FFMPEG_INST/lib/pkgconfig:$FFMPEG_PC_DIR" \
        pkg-config --static --libs \
        libavdevice libavfilter libavformat libavcodec libswscale libswresample libavutil) \
        || fail "pkg-config failed to resolve ffmpeg static closure"

    # Force each bound symbol in as a link root so its archive member is pulled
    # even when ffmpeg never references it; gc-sections then drops everything else.
    local uflags
    case "$OS" in
        darwin) uflags=$(sed 's/^/-Wl,-u,_/' "$exports" | tr '\n' ' ') ;;
        *)      uflags=$(sed 's/^/-Wl,-u,/'  "$exports" | tr '\n' ' ') ;;
    esac

    local expfile; expfile=$(write_export_file "$exports" "$b")
    local cxx="${CXX:-c++}"
    local out

    case "$OS" in
        linux)
            out="$b/libcobalt-native.so"
            # shellcheck disable=SC2086
            "$cxx" -shared -fPIC $uflags \
                -Wl,--version-script="$expfile" \
                -Wl,--gc-sections -Wl,--no-undefined \
                -Wl,-soname,libcobalt-native.so \
                -static-libgcc -static-libstdc++ \
                -Wl,--start-group \
                    "${extra_archives[@]}" $ff_libs \
                -Wl,--end-group \
                -lpthread -lm -ldl \
                -o "$out"
            # --strip-unneeded keeps .dynsym/.dynstr, so the FFM exports survive.
            strip --strip-unneeded "$out"
            ;;
        darwin)
            out="$b/libcobalt-native.dylib"
            # shellcheck disable=SC2086
            "$cxx" -dynamiclib -fPIC $uflags \
                -Wl,-exported_symbols_list,"$expfile" \
                -Wl,-dead_strip \
                -Wl,-install_name,@rpath/libcobalt-native.dylib \
                "${extra_archives[@]}" $ff_libs \
                -framework CoreFoundation -framework CoreMedia \
                -framework CoreVideo -framework AVFoundation \
                -framework AudioToolbox -framework VideoToolbox \
                -framework CoreServices -framework Security \
                -lc++ -lm \
                -o "$out"
            # -x removes local symbols only; the exported_symbols_list set stays
            # in the dynamic export table, so the FFM bindings resolve.
            strip -x "$out"
            ;;
        windows)
            out="$b/cobalt-native.dll"
            # Everything between -Bstatic and -Bdynamic links statically: without
            # this span ffmpeg's closure emits bare -lwebp/-lz/-lbz2 and openh264
            # emits -lstdc++, which MinGW resolves to shared import libs, leaking
            # libwebp-7/zlib1/libbz2-1/libstdc++-6/libwinpthread-1 dependencies. The
            # trailing system libs are import stubs, so they stay after -Bdynamic.
            # shellcheck disable=SC2086
            "$cxx" -shared $uflags \
                "$expfile" \
                -Wl,--gc-sections -Wl,--no-undefined \
                -static-libgcc -static-libstdc++ \
                -Wl,-Bstatic \
                -Wl,--start-group \
                    "${extra_archives[@]}" $ff_libs \
                -Wl,--end-group \
                -lstdc++ -lpthread \
                -Wl,-Bdynamic \
                -lws2_32 -liphlpapi -lntdll -lwinmm -lbcrypt -lsecur32 -lole32 -loleaut32 -lstrmiids -luuid -lgdi32 \
                -o "$out"
            # -s strips all non-dynamic symbols; the .def EXPORTS table is the
            # dynamic export set and is preserved, so the FFM bindings resolve.
            strip -s "$out"
            ;;
    esac

    [ -f "$out" ] || fail "combined library not produced: $out"
    local dest="$NATIVES/bin/$CLASSIFIER"
    mkdir -p "$dest"
    find "$dest" -maxdepth 1 \( -type f -o -type l \) ! -name '.gitkeep' -delete 2>/dev/null || true
    cp "$out" "$dest/$(basename "$out")"
    log "wrote $dest/$(basename "$out") ($(wc -c < "$out" | tr -d ' ') bytes)"
}

build_opus
build_openh264
build_usrsctp
build_libsrtp
build_libvpx
build_libyuv
build_av1
build_rav1e
build_libwebp
build_ffmpeg
build_webrtc_apm
build_combined
log "done $CLASSIFIER"
