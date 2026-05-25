#!/usr/bin/env bash
#
# Regenerates Java FFM bindings for libmdbx via jextract. Output lands in
# modules/lib/src/main/java/com/github/auties00/cobalt/store/persistent/mdbx/
# bindings/, where PersistentMessageStore consumes it.
#
# Prerequisites: a JEXTRACT_HOME env var pointing at a jextract 22+ install, OR
# jextract on PATH. Download from https://jdk.java.net/jextract/ if absent.
#
# Re-run this whenever headers/mdbx.h changes.
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
PKG="com.github.auties00.cobalt.store.persistent.mdbx.bindings"

rm -f "$OUT/${PKG//.//}/Mdbx.java" "$OUT/${PKG//.//}/Mdbx\$shared.java"

"$JEXTRACT" \
  -t "$PKG" \
  -I "$DIR/headers" \
  --header-class-name Mdbx \
  --output "$OUT" \
  --include-function mdbx_env_create \
  --include-function mdbx_env_set_option \
  --include-function mdbx_env_set_geometry \
  --include-function mdbx_env_openU \
  --include-function mdbx_env_close_ex \
  --include-function mdbx_dbi_open \
  --include-function mdbx_dbi_stat \
  --include-function mdbx_txn_begin_ex \
  --include-function mdbx_txn_commit_ex \
  --include-function mdbx_txn_abort_ex \
  --include-function mdbx_get \
  --include-function mdbx_put \
  --include-function mdbx_del \
  --include-function mdbx_cursor_open \
  --include-function mdbx_cursor_close2 \
  --include-function mdbx_cursor_get \
  --include-function mdbx_cursor_del \
  --include-function mdbx_strerror \
  --include-struct iovec \
  --include-typedef MDBX_val \
  --include-struct MDBX_stat \
  --include-constant MDBX_SUCCESS \
  --include-constant MDBX_RESULT_TRUE \
  --include-constant MDBX_NOTFOUND \
  --include-constant MDBX_MAP_FULL \
  --include-constant MDBX_TXN_FULL \
  --include-constant MDBX_UNABLE_EXTEND_MAPSIZE \
  --include-constant MDBX_ENV_DEFAULTS \
  --include-constant MDBX_NOSTICKYTHREADS \
  --include-constant MDBX_LIFORECLAIM \
  --include-constant MDBX_TXN_READWRITE \
  --include-constant MDBX_TXN_RDONLY \
  --include-constant MDBX_DB_DEFAULTS \
  --include-constant MDBX_CREATE \
  --include-constant MDBX_UPSERT \
  --include-constant MDBX_RESERVE \
  --include-constant MDBX_FIRST \
  --include-constant MDBX_LAST \
  --include-constant MDBX_NEXT \
  --include-constant MDBX_PREV \
  --include-constant MDBX_SET_RANGE \
  --include-constant MDBX_GET_CURRENT \
  --include-constant MDBX_opt_max_db \
  --include-constant MDBX_opt_max_readers \
  "$DIR/libmdbx.h"

echo "wrote $OUT/${PKG//.//}/Mdbx.java"
