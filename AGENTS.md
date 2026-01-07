## santa-lang Implementation

This is **Donner**, a santa-lang reindeer implementation. santa-lang is a functional programming language designed for solving Advent of Code puzzles. Multiple implementations exist to explore different execution models.

## Project Overview

- **Donner**: JVM bytecode compiler written in Kotlin
- Direct compilation to bytecode via ASM (no intermediate VM)
- Batteries-included standard library for AoC patterns
- Java interop: `java_new`, `java_call`, `java_static`, method combinators

## Makefile

**Always use Makefile targets.** Never run build tools directly.

- Run `make help` to see all available targets
- `make fmt` for code formatting
- `make test` for running tests
- `make can-release` before submitting a PR (runs all checks)

This ensures consistent, reproducible builds across all environments.

## Setup

Requires Java 21+:

```bash
make build              # Build CLI
make test               # Run all tests
```

## Common Commands

```bash
make help               # Show available targets
make build              # Build CLI
make test               # Run all tests
make clean              # Clean build artifacts
make run FILE=<path>    # Execute script
make run-test FILE=<path>  # Run in test mode
make cli/jar            # Build fat JAR (shadowJar)
make cli/jpackage       # Build native binary
make docker/build       # Build Docker image
make can-release        # Run before submitting PR (all checks)
```

## Code Conventions

- **Kotlin**: 2.0.0, JVM 21 target
- **Packages**: `santa.compiler.*`, `santa.runtime.*`, `santa.cli.*`
- **Parser**: Pratt parser pattern with operator precedence
- **Immutability**: `kotlinx-collections-immutable` for persistent data structures
- **Testing**: JUnit 5 + Kotest assertions, snapshot tests via `SnapshotAssertions`
- **Bytecode**: ASM 9.7 for direct `.class` generation

## Tests & CI

- **CI** (`test.yml`): Runs `make can-release` on ubuntu-24.04 with Java 21
- **Build** (`build-cli.yml`): JAR + native binaries (linux-amd64, macos-amd64/arm64), Docker
- Auto-updates `draft-release` branch after tests pass

## PR & Workflow Rules

- **Branches**: `main` for development, `draft-release` auto-updated
- **CI gates**: Tests must pass
- **Release**: release-drafter generates notes, builds artifacts on publish

## Security & Gotchas

- **Java 21 required**: jpackage needs JDK 21+ for native builds
- **ASM 9.7 pinned**: Update carefully as it affects bytecode output
- **Shadow JAR**: CLI integration tests depend on shadowJar task
- **jpackage Linux**: Requires `fakeroot` and `binutils`
- **Version management**: sed-replaced in build.gradle.kts during release

## Related Implementations

Other santa-lang reindeer (for cross-reference and consistency checks):

| Codename | Type | Language | Local Path | Repository |
|----------|------|----------|------------|------------|
| **Comet** | Tree-walking interpreter | Rust | `~/Projects/santa-lang-comet` | `github.com/eddmann/santa-lang-comet` |
| **Blitzen** | Bytecode VM | Rust | `~/Projects/santa-lang-blitzen` | `github.com/eddmann/santa-lang-blitzen` |
| **Dasher** | LLVM native compiler | Rust | `~/Projects/santa-lang-dasher` | `github.com/eddmann/santa-lang-dasher` |
| **Donner** | JVM bytecode compiler | Kotlin | `~/Projects/santa-lang-donner` | `github.com/eddmann/santa-lang-donner` |
| **Prancer** | Tree-walking interpreter | TypeScript | `~/Projects/santa-lang-prancer` | `github.com/eddmann/santa-lang-prancer` |
| **Vixen** | Embedded bytecode VM | C | `~/Projects/santa-lang-vixen` | `github.com/eddmann/santa-lang-vixen` |

Language specification and documentation: `~/Projects/santa-lang` or `github.com/eddmann/santa-lang`
