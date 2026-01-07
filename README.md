<p align="center"><a href="https://eddmann.com/santa-lang/"><img src="./docs/logo.png" alt="santa-lang" width="400px" /></a></p>

# santa-lang Donner

JVM bytecode compiler implementation of [santa-lang](https://eddmann.com/santa-lang/), written in Kotlin.

## Overview

santa-lang is a functional, expression-oriented programming language designed for solving Advent of Code puzzles. This implementation compiles directly to JVM bytecode using ASM, with no interpreter or custom VM layer.

Key language features:

- First-class functions and closures with tail-call optimization
- Pipeline and composition operators for expressive data flow
- Persistent immutable data structures
- Lazy sequences and infinite ranges
- Pattern matching with guards
- [Rich built-in function library](https://eddmann.com/santa-lang/builtins/)
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
| **Runtime**      | Value types, operators, and built-in functions          |

For detailed implementation internals, see [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Java Interop

Donner provides access to the full JVM ecosystem through functional-style interop:

| Function                              | Description                                |
| ------------------------------------- | ------------------------------------------ |
| `require(class)`                      | Load a Java class                          |
| `java_new(class, args...)`            | Construct objects                          |
| `java_call(obj, method, args...)`     | Call instance methods                      |
| `java_static(class, method, args...)` | Call static methods                        |
| `method(name)`                        | Create pipeline-compatible method function |
| `static_method(class, name)`          | Create static method function              |

```santa
let Math = require("java.lang.Math")
let abs = static_method(Math, "abs")

[-5, 3, -2, 8, -1] |> map(abs) |> sum  // 19

// Method combinators work in pipelines
"  hello  " |> method("trim") |> method("toUpperCase")  // "HELLO"
```

See [`examples/java_interop.santa`](examples/java_interop.santa) for comprehensive examples.

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

| Platform              | Artifact                                             |
| --------------------- | ---------------------------------------------------- |
| JAR (all platforms)   | `santa-lang-donner-cli-{version}.jar`                |
| Linux (x86_64)        | `santa-lang-donner-cli-{version}-linux-amd64.tar.gz` |
| macOS (Intel)         | `santa-lang-donner-cli-{version}-macos-amd64.tar.gz` |
| macOS (Apple Silicon) | `santa-lang-donner-cli-{version}-macos-arm64.tar.gz` |

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
make build

# Run tests
make test

# Build fat JAR
make cli/jar

# Build native binary (current platform)
make cli/jpackage

# Build Docker image
make docker/build
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

## Other Reindeer

The language has been implemented multiple times to explore different execution models and technologies.

| Codename | Type | Language |
|----------|------|----------|
| [Comet](https://github.com/eddmann/santa-lang-comet) | Tree-walking interpreter | Rust |
| [Blitzen](https://github.com/eddmann/santa-lang-blitzen) | Bytecode VM | Rust |
| [Dasher](https://github.com/eddmann/santa-lang-dasher) | LLVM native compiler | Rust |
| [Donner](https://github.com/eddmann/santa-lang-donner) | JVM bytecode compiler | Kotlin |
| [Vixen](https://github.com/eddmann/santa-lang-vixen) | Embedded bytecode VM | C |
| [Prancer](https://github.com/eddmann/santa-lang-prancer) | Tree-walking interpreter | TypeScript |

## License

MIT License - see [LICENSE](LICENSE) for details.
