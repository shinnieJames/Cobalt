/*
 * Compiled into the libmdbx shared library alongside the amalgamation.
 *
 * mdbx_env_openU accepts a UTF-8 path on every platform and routes to the
 * platform-correct upstream open: on Windows it converts to UTF-16 and calls
 * mdbx_env_openW (the recommended wide-char open), elsewhere it forwards to
 * mdbx_env_open. This keeps the path-encoding gate in C so the Java caller
 * always passes a UTF-8 path.
 */
#include "mdbx.h"

#if defined(_WIN32) || defined(_WIN64)
#include <windows.h>
#include <stdlib.h>

__declspec(dllexport) int mdbx_env_openU(MDBX_env *env, const char *pathname, MDBX_env_flags_t flags, mdbx_mode_t mode) {
  int wlen = MultiByteToWideChar(CP_UTF8, 0, pathname, -1, NULL, 0);
  if (wlen <= 0) {
    return MDBX_EINVAL;
  }
  wchar_t *wpath = (wchar_t *)malloc((size_t)wlen * sizeof(wchar_t));
  if (wpath == NULL) {
    return MDBX_ENOMEM;
  }
  MultiByteToWideChar(CP_UTF8, 0, pathname, -1, wpath, wlen);
  int rc = mdbx_env_openW(env, wpath, flags, mode);
  free(wpath);
  return rc;
}

#else

__attribute__((visibility("default"))) int mdbx_env_openU(MDBX_env *env, const char *pathname, MDBX_env_flags_t flags, mdbx_mode_t mode) {
  return mdbx_env_open(env, pathname, flags, mode);
}

#endif
