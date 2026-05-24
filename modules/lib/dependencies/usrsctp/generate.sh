#!/usr/bin/env bash
#
# Regenerates Java FFM bindings for libusrsctp via jextract. Output
# lands in modules/lib/src/main/java/com/github/auties00/cobalt/call/
# transport/sctp/bindings/, where the high-level wrapper for the
# WebRTC DataChannel transport consumes it.
#
# Prerequisites: a JEXTRACT_HOME env var pointing at a jextract 22+
# install, OR jextract on PATH. Download from
# https://jdk.java.net/jextract/ if absent.
#
# Cross-platform note: the upstream usrsctp.h pulls in <sys/socket.h>+
# <netinet/in.h> on POSIX and <winsock2.h>+<ws2tcpip.h> on Windows,
# whose layouts diverge (Win32's sockaddr_in6 has a SCOPE_ID union;
# POSIX has a flat sin6_scope_id). To make the generated bindings
# platform-portable, jextract is invoked with -I sysstubs FIRST so that
# our portable replacements (sysstubs/cobalt_socket_compat.h) intercept
# the system header lookup. The generated layouts are POSIX-shaped on
# every host and remain ABI-compatible with libusrsctp.dll/.so/.dylib
# at runtime (sockaddr_in6 is 28 bytes everywhere, sockaddr_storage is
# 128 bytes everywhere). Cobalt itself only ever reads sockaddr_conn.
#
# Re-run this whenever the usrsctp header under headers/ changes.
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
PKG="com.github.auties00.cobalt.call.internal.transport.sctp.bindings"

rm -f "$OUT/${PKG//.//}/UsrSctp.java" "$OUT/${PKG//.//}/UsrSctp\$shared.java"

"$JEXTRACT" \
  -t "$PKG" \
  -I "$DIR/sysstubs" \
  -I "$DIR/headers" \
  --header-class-name UsrSctp \
  --output "$OUT" \
  --include-function usrsctp_init \
  --include-function usrsctp_init_nothreads \
  --include-function usrsctp_finish \
  --include-function usrsctp_socket \
  --include-function usrsctp_close \
  --include-function usrsctp_bind \
  --include-function usrsctp_listen \
  --include-function usrsctp_accept \
  --include-function usrsctp_connect \
  --include-function usrsctp_shutdown \
  --include-function usrsctp_sendv \
  --include-function usrsctp_recvv \
  --include-function usrsctp_setsockopt \
  --include-function usrsctp_getsockopt \
  --include-function usrsctp_register_address \
  --include-function usrsctp_deregister_address \
  --include-function usrsctp_conninput \
  --include-function usrsctp_set_upcall \
  --include-function usrsctp_set_non_blocking \
  --include-function usrsctp_set_ulpinfo \
  --include-function usrsctp_peeloff \
  --include-function usrsctp_handle_timers \
  --include-union sctp_sockstore \
  --include-struct sctp_rcvinfo \
  --include-struct sctp_sndinfo \
  --include-struct sctp_event \
  --include-struct sctp_event_subscribe \
  --include-struct sctp_initmsg \
  --include-struct sctp_assoc_value \
  --include-struct sctp_paddrparams \
  --include-struct sctp_rtoinfo \
  --include-struct sockaddr \
  --include-struct sockaddr_storage \
  --include-struct sockaddr_conn \
  --include-struct sockaddr_in \
  --include-struct sockaddr_in6 \
  --include-struct in_addr \
  --include-struct in6_addr \
  --include-constant AF_CONN \
  --include-constant AF_INET \
  --include-constant AF_INET6 \
  --include-constant SOCK_STREAM \
  --include-constant IPPROTO_SCTP \
  --include-constant MSG_NOTIFICATION \
  --include-constant MSG_EOR \
  --include-constant SCTP_FUTURE_ASSOC \
  --include-constant SCTP_EVENT_READ \
  --include-constant SCTP_EVENT_WRITE \
  --include-constant SCTP_EVENT_ERROR \
  --include-constant SCTP_RTOINFO \
  --include-constant SCTP_ASSOCINFO \
  --include-constant SCTP_INITMSG \
  --include-constant SCTP_NODELAY \
  --include-constant SCTP_DISABLE_FRAGMENTS \
  --include-constant SCTP_PEER_ADDR_PARAMS \
  --include-constant SCTP_EVENT \
  --include-constant SCTP_STATUS \
  --include-constant SCTP_RECVV_NOINFO \
  --include-constant SCTP_RECVV_RCVINFO \
  --include-constant SCTP_RECVV_NXTINFO \
  --include-constant SCTP_RECVV_RN \
  --include-constant SCTP_SENDV_NOINFO \
  --include-constant SCTP_SENDV_SNDINFO \
  --include-constant SCTP_SENDV_PRINFO \
  --include-constant SCTP_SEND_SNDINFO_VALID \
  --include-constant SCTP_ASSOC_CHANGE \
  --include-constant SCTP_PEER_ADDR_CHANGE \
  --include-constant SCTP_REMOTE_ERROR \
  --include-constant SCTP_SEND_FAILED \
  --include-constant SCTP_SHUTDOWN_EVENT \
  --include-constant SCTP_STREAM_RESET_EVENT \
  --include-constant SCTP_EOR \
  "$DIR/headers/usrsctp.h"

echo "wrote $OUT/${PKG//.//}/UsrSctp.java"
