# Grype z/OS Patches

This directory contains patches for porting Anchore Grype to z/OS.

## Overview

The Grype port uses a **workspace-based approach** with locally patched dependencies and the **Grafana-style CGO workaround** for SQLite support.

## Patches Applied

### 1. **PR1.patch** - Switch to CGO-based SQLite
- **Purpose:** Replace pure-Go `glebarez/sqlite` with CGO-based `mattn/go-sqlite3` and `gorm.io/driver/sqlite`
- **Files Modified:**
  - `cmd/grype/main.go` - Import statement
  - `grype/db/internal/gormadapter/open.go` - Driver import and usage
  - `grype/db/v1/store/store.go` - Import statement
  - `grype/db/v2/store/store.go` - Import statement
  - `grype/db/v3/store/store.go` - Import statement
  - `grype/db/v4/store/store.go` - Import statement
  - `grype/db/v5/store/store.go` - Import statement
  - `go.mod` - Dependency replacement

### 2. **go-sqlite3.gopatch** - Pre-compiled SQLite Binding
- **Purpose:** Configure go-sqlite3 to use pre-compiled `sqlite3-binding.x` side-deck file
- **Files Modified:**
  - `sqlite3.go` - Add z/OS-specific LDFLAGS pointing to pre-compiled binding
- **Note:** The `ZOPEN-REPLACE-DIR` placeholder is replaced with actual path during build

### 3. **mmap-go.gopatch** - z/OS Memory Mapping Support
- **Purpose:** Add z/OS build tag to enable memory mapping on z/OS
- **Files Modified:**
  - `mmap_unix.go` - Add `zos` to build constraints

### 4. **xdg.gopatch** - z/OS XDG Base Directory Support
- **Purpose:** Implement z/OS-specific path utilities and directory initialization
- **Files Created:**
  - `internal/pathutil/pathutil_zos.go` - UserHomeDir, Exists, ExpandHome functions
  - `paths_zos.go` - initDirs, initBaseDirs, initUserDirs functions
- **Files Modified:**
  - `internal/userdirs/config_unix.go` - Add zos build tag

### 5. **continuity.gopatch** - z/OS Filesystem Operations
- **Purpose:** Implement z/OS-specific filesystem operations and stat functions
- **Files Created:**
  - `fs/copy_irregular_zos.go` - Stub for device/pipe/socket operations
  - `fs/stat_zos.go` - Atime, Ctime, Mtime, StatAtime, StatCtime, StatMtime functions
- **Files Modified:**
  - `fs/copy_unix.go` - Add zos build tag
  - `fs/copy_irregular_unix.go` - Exclude zos from build tags

### 6. **go-mtree.gopatch** - z/OS File Time Operations
- **Purpose:** Implement lchtimes function for z/OS using syscall.Utimes fallback
- **Files Created:**
  - `lchtimes_zos.go` - lchtimes implementation

### 7. **termenv.gopatch** - z/OS Terminal Support
- **Purpose:** Implement z/OS-specific terminal profile detection
- **Files Created:**
  - `termenv_zos.go` - colorProfile, foregroundColor, backgroundColor functions

### 8. **containerd.gopatch** - z/OS Container Operations
- **Purpose:** Provide z/OS stubs for unsupported container operations
- **Files Created:**
  - `mount/mount_zos.go` - Stub implementations returning ErrNotImplementOnZOS
  - `snapshots_zos.go` - DefaultSnapshotter constant
- **Files Modified:**
  - `mount/mount_unix.go` - Exclude zos from build tags

## Build Process

The build uses the **Grafana-style CGO workaround** which requires:

1. **Patched Go Toolchain** at `$GOROOT/go-build-zos/bin` (CRITICAL REQUIREMENT)
2. **Pre-compilation** of `sqlite3-binding.c` to avoid z/OS CGO linker issues
3. **Workspace-based** dependency management with local patched modules

### Build Steps (from buildenv)

1. **zopen_init:** Enable CGO and add patched Go toolchain to PATH
2. **zopen_wharf:** 
   - Clone and patch all dependencies (mmap-go, xdg, continuity, go-mtree, termenv, containerd)
   - Clone go-sqlite3 and pre-compile `sqlite3-binding.c` with z/OS-specific flags
   - Patch go-sqlite3 to use pre-compiled binding
   - Initialize Go workspace with all patched modules
   - Run wharf for z/OS processing
3. **zopen_build:** Build grype binary with limited parallelism
4. **zopen_install:** Copy binary and sqlite3-binding.so to install directory

## Known Issues

### CRITICAL: Patched Go Toolchain Required

The Grafana-style CGO workaround **requires a patched Go toolchain** at `$GOROOT/go-build-zos/bin`. This patched toolchain fixes the z/OS CGO linker bug that causes:

```
IEW2665S 40FF MODULE *NULL* IS NON-EXECUTABLE AND WAS NOT SAVED BECAUSE STORENX=NEVER.
IEW5033 The binder ended with return code 12.
```

**Without this patched toolchain, the build will fail with CGO linking errors.**

### User Action Required

1. **Obtain the patched Go toolchain** from:
   - IBM internal Go builds
   - zopen community custom Go build
   - Grafana port maintainers
   - Private fork with z/OS CGO fixes

2. **Install the patched toolchain** so that `$GOROOT/go-build-zos/bin` exists and contains the patched Go binaries

3. **Retry the build** with `zopen-build -v`

## Patch File Encoding

All `.gopatch` files must be tagged as `ISO8859-1` for proper application:

```bash
cd patches
for f in *.gopatch; do chtag -tc ISO8859-1 "$f"; done
```

## Testing

After successful build with patched toolchain:

```bash
# Test binary
./install/bin/grype version

# Test vulnerability scanning
./install/bin/grype dir:test_scan
```

## References

- **Grafana Port:** `/home/itodoro/projects/zos-porting/grafanaport/buildenv`
- **CGO Solution Analysis:** `/home/itodoro/projects/zos-porting/grypeport/CGO_SOLUTION.md`
- **Porting Status:** `/home/itodoro/projects/zos-porting/grypeport/PORTING_STATUS.md`
