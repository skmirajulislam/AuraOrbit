# Contributing to AuraOrbit 🛸

Thank you for your interest in contributing to **AuraOrbit**! To ensure that AuraOrbit maintains its industrial-grade stability, predictable memory/CPU footprints, zero-regression guarantees, and clean architectural boundaries, all contributors must strictly adhere to the guidelines set forth in this document.

> [!IMPORTANT]
> **Strict Quality Gate**: Pull Requests that introduce compiler warnings, memory leaks, unclosed thread executors, orphaned OS processes, hardcoded colors/paths, or breaking test suite failures will be rejected automatically.

---

## 📑 Table of Contents
- [1. Code of Conduct](#1-code-of-conduct)
- [2. Architectural Invariants & Core Principles](#2-architectural-invariants--core-principles)
- [3. Zero-Tolerance Quality Standards](#3-zero-tolerance-quality-standards)
- [4. Coding & Style Conventions](#4-coding--style-conventions)
- [5. Git & Commit Guidelines](#5-git--commit-guidelines)
- [6. Step-by-Step Contribution Workflow](#6-step-by-step-contribution-workflow)
- [7. Pull Request Submission Checklist](#7-pull-request-submission-checklist)

---

## 1. Code of Conduct

All contributors and maintainers are expected to uphold a professional, respectful, and inclusive environment. Please review and adhere to our [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

---

## 2. Architectural Invariants & Core Principles

AuraOrbit is an enterprise desktop code studio built on **Java 21** and **JavaFX 21**. When writing code, the following architectural invariants are non-negotiable:

### A. Strict Layer Separation
- **Presentation (`view.fx`)**: Pure UI controls, layout nodes, event bindings, and styling. No business logic, file I/O, or raw process execution belongs in view classes.
- **Controllers (`controller`, `command`)**: State orchestration and user action dispatching. Implements GoF patterns (Command pattern for Undo/Redo).
- **Core Services (`service`, `model`, `template`)**: Headless business logic, file manipulation, syntax analysis, diagnostics, and AI provider integration.
- **Collaboration Engine (`collaboration.*`)**: Isolated network, synchronization, and security subsystem. Must not directly access UI controls; communication occurs via event callbacks and listeners.

### B. Viewport Virtualization & Zero DOM Bloat
- **Never instantiate JavaFX nodes per line of text**.
- Code editing must exclusively leverage **RichTextFX virtualized flow**. Only lines visible within the viewport scroll bounds may allocate JavaFX scene-graph nodes ($O(1)$ memory).

### C. Threading Model & UI Concurrency
- **UI Thread Safety**: All scene-graph mutations and dialog invocations **must** execute on the JavaFX Application Thread via `Platform.runLater()`.
- **Zero UI Freezes**: Heavy computation (syntax regex parsing, code execution, linter diagnostics, AI HTTP queries, git status queries) **must** run asynchronously on daemon background threads or virtual threads.
- **Debounced Execution**: Diagnostics or live code analysis triggered by keystrokes **must** implement a sliding window debounce (e.g. 500ms) to prevent CPU spikes.

### D. Deterministic Lifecycle & Resource Teardown
- **No Resource Leaks**: Every `ExecutorService`, `Process`, `Socket`, `BufferedReader`, or `Stream` must have an explicit, deterministic cleanup lifecycle.
- Classes managing background tasks must implement a `dispose()` or `shutdown()` method.
- Process destruction must attempt a graceful termination before escalating to `destroyForcibly()` (SIGKILL fallback).
- Global hooks must cascade through `FxEditorController.shutdown()` -> `EditorTabController.dispose()` -> `TerminalPane.dispose()`.

---

## 3. Zero-Tolerance Quality Standards

Every Pull Request must pass the following quality gates before review:

| Standard | Requirement | Verification Command |
| :--- | :--- | :--- |
| **Compiler Warnings** | **0 Warnings / 0 Errors** with `-Xlint:unused` | `mvn clean test-compile -Dmaven.compiler.showWarnings=true -Dmaven.compiler.compilerArgs="-Xlint:unused"` |
| **Automated Tests** | **124/124 Tests Passing** (100% Pass Rate) | `java -cp target/classes:target/test-classes:... test.EditorTestSuite` |
| **New Test Coverage** | Every new feature or bug fix must add tests | Add corresponding assertions to `EditorTestSuite.java` |
| **File Security** | All file access must validate against traversal & poisons | Use `FileSecurityValidator` |
| **Atomic File I/O** | Zero direct overwrites of user files | Use `FileService` (`.tmp` write + `ATOMIC_MOVE` + `.bak`) |
| **Theme Uniformity** | No hardcoded CSS color overrides | Use CSS theme variables & `.codicon` classes |

---

## 4. Coding & Style Conventions

### Java Language Rules
- **Language Level**: Java 21 LTS. Take advantage of modern features (records, pattern matching for `switch`, sealed interfaces, text blocks, and virtual threads where appropriate).
- **Naming Conventions**:
  - Classes / Interfaces: `UpperCamelCase` (e.g., `CodeExecutionService`)
  - Methods / Variables: `lowerCamelCase` (e.g., `executeCommand`, `documentBuffer`)
  - Constants: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_DEBOUNCE_MS`)
- **Import Hygiene**:
  - No wildcard imports (`import java.util.*;` is prohibited in new code; import explicit types).
  - Unused imports must be eliminated prior to committing.
- **Defensive Null & Bounds Checking**:
  - Guard public APIs against `null`, negative indices, or empty input strings.
  - Return empty collections (`List.of()`, `Map.of()`) rather than `null`.
- **Exception Handling**:
  - Never swallow exceptions silently with empty `catch` blocks.
  - At minimum, log errors via `System.err.println()` with meaningful context, or propagate them using domain-specific exceptions.

### CSS & Theming Rules
- Styles must be defined across all **6 supported themes** (`vscode-dark.css`, `vscode-editor.css`, `dracula.css`, `monokai.css`, `cyberpunk.css`, `github-light.css`).
- Always test styling in both dark and light modes.
- Keep CSS braces balanced (`{}` pairs verified).

---

## 5. Git & Commit Guidelines

AuraOrbit strictly enforces [Conventional Commits](https://www.conventionalcommits.org/).

### Commit Message Format
```
<type>(<scope>): <short description in present tense>

[optional body explaining motivation and architectural impact]

[optional footer(s) such as Closes #123]
```

### Allowed Types
- `feat`: A new user-facing feature or capability
- `fix`: A bug fix or defect correction
- `perf`: Performance optimization (CPU, memory, rendering)
- `refactor`: Code reorganization without functional change
- `test`: Adding or enhancing unit / integration tests
- `docs`: Documentation additions or revisions
- `chore`: Build tooling, dependency updates, or internal scripts

### Examples
- `feat(editor): add bracket pair colorization to virtual code area`
- `fix(terminal): ensure zombie bash processes are killed on tab close`
- `perf(diagnostics): increase debounce window to reduce CPU thrashing`
- `docs(readme): update keyboard shortcuts for split editor`

---

## 6. Step-by-Step Contribution Workflow

### Step 1: Fork and Clone
```bash
git clone https://github.com/<your-username>/AuraOrbit.git
cd AuraOrbit
git remote add upstream https://github.com/skmirajulislam/AuraOrbit.git
```

### Step 2: Create a Dedicated Feature Branch
```bash
git checkout master
git pull upstream master
git checkout -b feat/your-feature-name
```

### Step 3: Implement and Validate Locally
Make your changes, maintaining strict adherence to architectural layers and memory rules. Run validation:

```bash
# 1. Verify clean compilation and zero unused imports
mvn clean test-compile -Dmaven.compiler.showWarnings=true -Dmaven.compiler.compilerArgs="-Xlint:unused"

# 2. Run the full automated test suite
mvn test
java -cp target/classes:target/test-classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tr '\n' ':') test.EditorTestSuite

# 3. Launch the app and manually inspect UI/UX
mvn javafx:run
```

### Step 4: Commit and Push
```bash
git add -A
git commit -m "feat(scope): concise description of changes"
git push origin feat/your-feature-name
```

### Step 5: Open a Pull Request
- Open the PR against the `master` branch of `skmirajulislam/AuraOrbit`.
- Provide a clear summary of changes, rationale, and confirmation that all quality standards are met.

---

## 7. Pull Request Submission Checklist

Before submitting your PR, verify each item:

- [ ] My code strictly compiles with **zero warnings** (`-Xlint:unused`).
- [ ] All **124 automated tests pass** without failures or flakiness.
- [ ] New unit tests have been added in `test.EditorTestSuite` for any new functionality or bug fixes.
- [ ] No background thread pools, sockets, or OS processes are left unclosed (`dispose()` implemented).
- [ ] File operations use `FileSecurityValidator` and `FileService` atomic routines.
- [ ] No hardcoded colors; styling adheres to CSS tokens across themes.
- [ ] Commit history follows Conventional Commits.
- [ ] The PR branch is rebased cleanly on latest `upstream/master`.

Thank you for helping make **AuraOrbit** the most robust, high-performance desktop code studio! 🚀
