# Syft z/OS Patches

This directory contains patches for porting Anchore Syft to z/OS.

## Overview

The Syft port uses a **workspace-based approach** with locally patched dependencies and the **CGO-based SQLite driver** for database operations. Syft is a powerful SBOM (Software Bill of Materials) generation tool that catalogs packages and dependencies from container images, filesystems, and archives.

## Patches Applied

### 1. **syft-sqlite3-driver.gopatch** - Switch to CGO-based SQLite
- **Purpose:** Replace pure-Go `modernc.org/sqlite` with CGO-based `mattn/go-sqlite3` for z/OS compatibility
- **Files Modified:**
  - `cmd/syft/main.go` - Import statement
  - `cmd/syft/internal/test/integration/sqlite_rpmdb_test.go` - Test import
  - `examples/create_custom_sbom/main.go` - Example import
  - `examples/create_simple_sbom/main.go` - Example import
  - `examples/select_catalogers/main.go` - Example import
  - `examples/source_from_registry/main.go` - Example import
  - `syft/pkg/cataloger/nix/cataloger_test.go` - Test import
  - `syft/pkg/cataloger/redhat/cataloger_test.go` - Test import
  - `go.mod` - Add `github.com/mattn/go-sqlite3 v1.14.22` dependency
- **Files Created:**
  - `cmd/syft/sqlite_zos.go` - Register go-sqlite3 driver as "sqlite" for z/OS (Syft's RPM cataloger expects "sqlite" driver name)

### 2. **syft-go-sqlite3-zos.gopatch** - Pre-compiled SQLite Binding
- **Purpose:** Configure go-sqlite3 to use pre-compiled `sqlite3-binding.x` side-deck file to avoid z/OS CGO linker issues
- **Files Modified:**
  - `sqlite3.go` - Add z/OS-specific LDFLAGS: `#cgo zos LDFLAGS: ZOPEN-REPLACE-DIR/sqlite3-binding.x`
- **Note:** The `ZOPEN-REPLACE-DIR` placeholder is replaced with the actual path during build

### 3. **syft-containerd-zos.gopatch** - z/OS Container Operations
- **Purpose:** Provide z/OS stubs for unsupported containerd operations
- **Files Created:**
  - `core/mount/mount_zos.go` - Stub mount implementation returning `ErrNotImplementOnZOS`
  - `defaults/defaults_zos.go` - Default runtime constant for z/OS

### 4. **syft-continuity-zos.gopatch** - z/OS Filesystem Operations
- **Purpose:** Implement z/OS-specific filesystem operations and stat functions
- **Files Created:**
  - `fs/copy_zos.go` - File info copying (chmod, chtimes) and xattr stub
  - `fs/stat_zos.go` - Atime, Ctime, Mtime, StatAtime, StatCtime, StatMtime, StatATimeAsTime functions

### 5. **syft-xdg-zos.gopatch** - z/OS XDG Base Directory Support
- **Purpose:** Implement z/OS-specific XDG Base Directory Specification paths
- **Files Created:**
  - `internal/pathutil/pathutil_zos.go` - UserHomeDir, Exists, ExpandHome functions
  - `paths_zos.go` - initDirs, initBaseDirs, initUserDirs functions for z/OS paths

### 6. **syft-go-mtree-zos.gopatch** - z/OS File Time Operations
- **Purpose:** Implement lchtimes function for z/OS using syscall.Utimes fallback
- **Files Created:**
  - `lchtimes_zos.go` - lchtimes implementation with Timeval conversion

### 7. **syft-diskfs-zos.gopatch** - z/OS SquashFS Support
- **Purpose:** Implement z/OS-specific device number and file property extraction for SquashFS
- **Files Created:**
  - `filesystem/squashfs/finalize_zos.go` - getDeviceNumbers and getFileProperties functions

### 8. **syft-gguf-zos.gopatch** - z/OS Memory Mapping for GGUF
- **Purpose:** Implement z/OS-specific memory mapping for GGUF (GPT-Generated Unified Format) files
- **Files Created:**
  - `util/osx/file_mmap_zos.go` - mmap and munmap functions using unix.Mmap/Munmap

### 9. **syft-go-git-zos.gopatch** - z/OS Git Worktree Support
- **Purpose:** Add z/OS-specific worktree handling
- **Files Created:**
  - `worktree_zos.go` - isSymlinkWindowsNonAdmin stub (returns false on z/OS)

### 10. **syft-moby-sys-user-zos.gopatch** - z/OS User/Group Lookup
- **Purpose:** Implement z/OS-specific user and group lookup from /etc/passwd and /etc/group
- **Files Created:**
  - `user/lookup_zos.go` - Complete user/group lookup implementation including:
    - LookupUser, LookupUid, LookupGroup, LookupGid
    - CurrentUser, CurrentGroup
    - CurrentUserSubUIDs, CurrentUserSubGIDs
    - CurrentProcessUIDMap, CurrentProcessGIDMap

## Build Process

The build uses a **workspace-based approach** with CGO-enabled SQLite driver:

### Prerequisites

1. **Go toolchain** with z/OS support and CGO enabled
2. **Pre-compiled sqlite3-binding.c** to avoid z/OS CGO linker issues
3. **zopen build environment** for dependency management

### Build Steps (from buildenv)

1. **zopen_init:** Enable CGO and configure build environment
2. **zopen_wharf:** 
   - Clone and patch all dependencies:
     - containerd (mount stubs, defaults)
     - continuity (filesystem operations)
     - xdg (base directory support)
     - go-mtree (file time operations)
     - diskfs (squashfs support)
     - gguf (memory mapping)
     - go-git (worktree support)
     - moby/sys/user (user/group lookup)
   - Clone go-sqlite3 and pre-compile `sqlite3-binding.c` with z/OS-specific flags
   - Patch go-sqlite3 to use pre-compiled binding
   - Patch syft to use go-sqlite3 instead of modernc.org/sqlite
   - Initialize Go workspace with all patched modules
   - Run wharf for z/OS processing
3. **zopen_build:** Build syft binary with appropriate parallelism
4. **zopen_install:** Copy binary and sqlite3-binding.so to install directory

## Patch File Encoding

All `.gopatch` files must be tagged as `ISO8859-1` for proper application:

```bash
cd patches
for f in *.gopatch; do chtag -tc ISO8859-1 "$f"; done
```

## Testing

After successful build:

```bash
# Test binary
./install/bin/syft version

# Test SBOM generation from directory
./install/bin/syft dir:. -o json

# Test SBOM generation from container image (if container runtime available)
./install/bin/syft <image-name> -o spdx-json

# Test specific catalogers
./install/bin/syft packages dir:. --catalogers rpm,python
```

## Key Differences from Grype Port

While both Syft and Grype are Anchore tools, the Syft port has some differences:

1. **SQLite Driver Registration:** Syft's RPM cataloger expects the driver to be registered as "sqlite" (not "sqlite3"), requiring the custom `sqlite_zos.go` file
2. **Additional Dependencies:** Syft requires more filesystem and container-related dependencies (diskfs, gguf, moby/sys/user)
3. **SBOM Focus:** Syft is focused on SBOM generation and package cataloging, while Grype is focused on vulnerability scanning

## Known Issues

### CGO and SQLite

The CGO-based SQLite driver requires:
- Pre-compilation of `sqlite3-binding.c` to avoid z/OS CGO linker issues
- Proper LDFLAGS configuration pointing to the pre-compiled binding
- The `sqlite3-binding.so` shared library must be available at runtime

### Container Operations

Container-related operations (mount, containerd) are stubbed on z/OS and will return `ErrNotImplementOnZOS`. Syft can still catalog:
- Local filesystem directories
- Archive files (tar, zip)
- Git repositories
- Individual files

## References

- **Syft Documentation:** https://github.com/anchore/syft
- **z/OS Go Porting Guide:** Related zopen community documentation
- **SQLite CGO Driver:** https://github.com/mattn/go-sqlite3