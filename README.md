<p align="center"><a href="https://eddmann.com/santa-lang/"><img src="./logo.png" alt="santa-lang" width="400px" /></a></p>

# santa-lang Donner

JVM bytecode compiler implementation of [santa-lang](https://eddmann.com/santa-lang/), written in Kotlin.

## Overview

santa-lang is a functional, expression-oriented programming language designed for solving Advent of Code puzzles. This implementation compiles directly to JVM bytecode using ASM, with no interpreter or custom VM layer.

All santa-lang implementations support the same language features:

- Tail-call optimization (TCO)
- Persistent immutable data structures
- First-class functions and closures
- Lazy sequences and infinite ranges
- Pattern matching with guards
- [70+ built-in functions](https://eddmann.com/santa-lang/builtins/)
- AoC runner with automatic input fetching

## Architecture

```
Source Code → Lexer → Parser → Desugar → Resolver → Bytecode Gen (ASM) → .class → JVM
                                            ↓
                                     Runtime Library
```

| Component        | Description                                             |
| ---------------- | ------------------------------------------------------- |
| **Lexer**        | Tokenizes source into keywords, operators, literals     |
| **Parser**       | Builds an Abstract Syntax Tree (AST) using Pratt parser |
| **Desugar**      | Transforms placeholders, pipelines, pattern parameters  |
| **Resolver**     | Lexical scoping and symbol resolution                   |
| **Bytecode Gen** | Generates JVM bytecode using ASM                        |
| **Runtime**      | Value types, operators, and 70+ built-in functions      |

## Installation

### Docker

```bash
docker pull ghcr.io/eddmann/santa-lang-donner:cli-latest
docker run --rm ghcr.io/eddmann/santa-lang-donner:cli-latest --help
```

### Java JAR

Requires Java 21+:

```bash
# Download the JAR from releases
java -jar santa-lang-donner-cli-{version}.jar solution.santa
```

### Release Binaries

Download pre-built binaries from [GitHub Releases](https://github.com/eddmann/santa-lang-donner/releases):

| Platform              | Artifact                                              |
| --------------------- | ----------------------------------------------------- |
| JAR (all platforms)   | `santa-lang-donner-cli-{version}.jar`                 |
| Linux (x86_64)        | `santa-lang-donner-cli-{version}-linux-amd64.tar.gz`  |
| macOS (Intel)         | `santa-lang-donner-cli-{version}-macos-amd64.tar.gz`  |
| macOS (Apple Silicon) | `santa-lang-donner-cli-{version}-macos-arm64.tar.gz`  |

## Usage

```bash
# Run a solution
santa-cli solution.santa

# Run tests defined in a solution
santa-cli -t solution.santa

# Include slow tests (marked with @slow)
santa-cli -t -s solution.santa
```

## Example

Here's a complete Advent of Code solution (2015 Day 1):

```santa
input: read("aoc://2015/1")

part_one: {
  input |> fold(0) |floor, direction| {
    if direction == "(" { floor + 1 } else { floor - 1 };
  }
}

part_two: {
  zip(1.., input) |> fold(0) |floor, [index, direction]| {
    let next_floor = if direction == "(" { floor + 1 } else { floor - 1 };
    if next_floor < 0 { break index } else { next_floor };
  }
}

test: {
  input: "()())"
  part_one: -1
  part_two: 5
}
```

Key language features shown:

- **`input:`** / **`part_one:`** / **`part_two:`** - AoC runner sections
- **`|>`** - Pipeline operator (thread value through functions)
- **`fold`** - Reduce with early exit support via `break`
- **`test:`** - Inline test cases with expected values

## Building

Requires Java 21+:

```bash
# Build CLI
./gradlew :cli:build

# Run tests
./gradlew test

# Build fat JAR
./gradlew :cli:shadowJar

# Build native binary (current platform)
./gradlew :cli:jpackage
```

## Development

Run `make help` to see all available targets:

```bash
make help          # Show all targets
make can-release   # Run all CI checks
make test          # Run all tests
make build         # Build CLI
make run FILE=...  # Run a script
make run-test FILE=...  # Run script in test mode
make cli/jar       # Build fat JAR
make cli/jpackage  # Build native binary
make docker/build  # Build Docker image
```

## Project Structure

```
├── compiler/              # Core compiler library
│   └── src/main/kotlin/
│       ├── lexer/         # Tokenization
│       ├── parser/        # AST construction (Pratt parser)
│       ├── desugar/       # AST transformations
│       ├── resolver/      # Name resolution and scoping
│       ├── codegen/       # JVM bytecode generation (ASM)
│       └── error/         # Error formatting
├── runtime/               # Runtime library
│   └── src/main/kotlin/
│       ├── value/         # Value types (Int, String, List, etc.)
│       ├── Operators.kt   # Runtime operator implementations
│       └── Builtins.kt    # 70+ built-in functions
├── cli/                   # Command-line interface
└── .github/workflows/     # CI/CD pipelines
```

## See Also

- [eddmann/santa-lang](https://github.com/eddmann/santa-lang) - Language specification/documentation
- [eddmann/santa-lang-editor](https://github.com/eddmann/santa-lang-editor) - Web-based editor
- [eddmann/santa-lang-prancer](https://github.com/eddmann/santa-lang-prancer) - Tree-walking interpreter in TypeScript (Prancer)
- [eddmann/santa-lang-comet](https://github.com/eddmann/santa-lang-comet) - Tree-walking interpreter in Rust (Comet)
- [eddmann/santa-lang-blitzen](https://github.com/eddmann/santa-lang-blitzen) - Bytecode VM in Rust (Blitzen)
- [eddmann/santa-lang-dasher](https://github.com/eddmann/santa-lang-dasher) - LLVM native compiler in Rust (Dasher)
- [eddmann/santa-lang-donner](https://github.com/eddmann/santa-lang-donner) - JVM bytecode compiler in Kotlin (Donner)
