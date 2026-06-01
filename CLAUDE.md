# Claude Code Guidelines (MyPlatform)

Based on Andrej Karpathy's programming insights for better LLM coding behavior.

## The Problems

- Models make wrong assumptions and run with them without checking
- They overcomplicate code, bloat abstractions, don't clean up dead code
- They sometimes change/remove code they don't understand

## The Four Principles

### 1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.

- State assumptions explicitly — If uncertain, ask rather than guess
- Present multiple interpretations — Don't pick silently when ambiguity exists
- Push back when warranted — If a simpler approach exists, say so
- Stop when confused — Name what's unclear and ask for clarification

### 2. Simplicity First
Minimum code that solves the problem. Nothing speculative.

- No features beyond what was asked
- No abstractions for single-use code
- No "flexibility" that wasn't requested
- If 200 lines could be 50, rewrite it

### 3. Surgical Changes
Touch only what you must. Clean up only your own mess.

- Don't "improve" adjacent code or formatting
- Match existing style, even if you'd do it differently
- Remove imports/variables that YOUR changes made unused
- Don't remove pre-existing dead code unless asked

### 4. Goal-Driven Execution
Define success criteria. Loop until verified.

- Transform imperative tasks into verifiable goals
- For multi-step tasks, state a brief plan:
  1. [Step] → verify: [check]
  2. [Step] → verify: [check]
- Strong success criteria let me loop independently

## Project-Specific Guidelines

- Follow the existing code style in the codebase
- Java: Use Spring Boot conventions
- Frontend: Use React + TypeScript conventions
- All API endpoints must follow REST conventions
- Use meaningful variable and method names (English)

## Tradeoff Note

These guidelines bias toward caution over speed. For trivial tasks, use judgment. The goal is reducing costly mistakes on non-trivial work.