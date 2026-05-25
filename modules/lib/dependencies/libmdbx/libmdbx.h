/*
 * Aggregator header for jextract: pulls in the pristine vendored mdbx.h.
 */
#include "mdbx.h"

// UTF-8 path open compiled into the shared library (mdbx_openu.c); routes to mdbx_env_openW on Windows.
int mdbx_env_openU(MDBX_env *env, const char *pathname, MDBX_env_flags_t flags, mdbx_mode_t mode);
