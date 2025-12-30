#!/bin/bash

# continue.sh - Resume Donner development
# Usage: ./continue.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${CYAN}=== Donner Development Session ===${NC}"
echo ""

# Find current phase from PLAN.md
COMPLETED_GATES=$(grep -c "^- \[x\]" PLAN.md 2>/dev/null || echo "0")
TOTAL_GATES=$(grep -c "^- \[\]" PLAN.md 2>/dev/null || echo "0")
TOTAL_GATES=$((COMPLETED_GATES + TOTAL_GATES))

echo -e "${GREEN}Progress:${NC} $COMPLETED_GATES / $TOTAL_GATES release gates completed"
echo ""

# Run tests if Gradle wrapper exists
if [ -f "./gradlew" ]; then
    echo -e "${YELLOW}Running tests...${NC}"
    if ./gradlew test --quiet 2>/dev/null; then
        echo -e "${GREEN}✓ All tests pass${NC}"
    else
        echo -e "${YELLOW}⚠ Some tests failing - address this first${NC}"
    fi
    echo ""
fi

PROMPT=$(cat <<'PROMPT'
Continue developing Donner, a direct JVM bytecode compiler for santa-lang.

## ARCHITECTURE (MANDATORY)
This is a direct JVM bytecode compiler using ASM. No interpreter. No custom VM.
- Lexer -> Parser -> Desugar -> Resolver -> Bytecode Gen -> .class -> JVM
- Runtime module provides values, persistent collections, built-ins, and I/O
- Target Java 21 bytecode

## CRITICAL REQUIREMENTS
1. **NO INTERPRETER** - compile to JVM bytecode only
2. **ASM ONLY** - generate classfiles with ASM
3. **KOTLIN** - implementation language
4. **ICU4J** - grapheme cluster indexing for strings
5. **PERSISTENT COLLECTIONS** - use kotlinx.collections.immutable
6. **LANG.txt is the source of truth**

## MANDATORY GUIDELINES (from ~/Projects/agent-guidelines)
1. **Agent Philosophy** (foundation/agent-philosophy.md): plan first, research, ask when unclear
2. **Code Philosophy** (foundation/code-philosophy.md): boring, predictable, declarative, immutable by default
3. **Design Principles** (practices/design-principles.md): SRP, composition, separation of concerns
4. **Clean Code** (practices/clean-code-practices.md): small functions, descriptive names, guard clauses
5. **Testing** (practices/testing.md): classical TDD, state-based verification, no internal mocks
6. **Error Handling** (practices/error-handling.md): fail fast, meaningful errors
7. **Documentation** (practices/documentation.md): document the why

## WORKFLOW
1. Read PLAN.md to find the next unchecked release gate
2. Write a failing test first (RED)
3. Implement minimal code to pass (GREEN)
4. Refactor (REFACTOR)
5. Update PLAN.md gate checkbox
6. Run ./gradlew test
7. Commit using Conventional Commits and record the phase/gate in the commit scope (type(phase-X-gate-Y): ...)
8. Use `git log` to confirm the last completed phase/gate for continuity

Begin by reading PLAN.md and identify the next incomplete release gate.
PROMPT
)

echo -e "${CYAN}Starting Codex session...${NC}"
echo ""

codex --dangerously-bypass-approvals-and-sandbox --search --model gpt-5.2-codex -c model_reasoning_effort=\"high\" "$PROMPT"
