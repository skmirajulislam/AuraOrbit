# AuraOrbit 🛸
### *Next-Gen Modern Desktop AI Code Studio & IDE*

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![JavaFX 21](https://img.shields.io/badge/JavaFX-21.0.4-02569B?style=for-the-badge&logo=flutter&logoColor=white)](https://openjfx.io/)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge&logo=github-actions&logoColor=white)]()
[![Tests](https://img.shields.io/badge/Tests-124%2F124%20Passing-success?style=for-the-badge&logo=junit5&logoColor=white)]()
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge&logo=open-source-initiative&logoColor=white)](LICENSE)

**AuraOrbit** is an industrial-grade, cross-platform code studio designed for high-performance software engineering. Built from the ground up on modern **Java 21** and **JavaFX 21**, AuraOrbit combines the lightweight speed of native desktop software with the developer experience of modern editors like VS Code—featuring virtualized code rendering, an asynchronous multi-language diagnostics engine, dual-pane split editing, native shell terminals, real-time cryptographic peer collaboration, and native multi-LLM AI orchestration.

---

## 📑 Table of Contents
- [Architecture & Layered Infrastructure](#-architecture--layered-infrastructure)
- [Key Features](#-key-features)
- [Project Metrics & Directory Structure](#-project-metrics--directory-structure)
- [Engineering Highlights & Performance Strategies](#-engineering-highlights--performance-strategies)
- [Security & Resilience Model](#-security--resilience-model)
- [Prerequisites & Getting Started](#-prerequisites--getting-started)
- [Keyboard Shortcuts](#-keyboard-shortcuts)
- [Automated Test Suite](#-automated-test-suite)
- [Contributing & Code of Conduct](#-contributing--code-of-conduct)

---

## 🏛 Architecture & Layered Infrastructure

AuraOrbit is strictly partitioned into clean architectural layers, avoiding tight coupling between UI components and backend business logic.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER (UI / UX)                              │
│  ┌────────────────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐  │
│  │   RichTextFX CodeArea  │  │   Split Editor Layout  │  │   Dock Panel Ecosystem   │  │
│  │   (O(1) Virtual DOM)   │  │   (Dual-Pane Views)    │  │ (Terminal/Problems/Ports)│  │
│  └────────────────────────┘  └────────────────────────┘  └──────────────────────────┘  │
│  ┌────────────────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐  │
│  │   Sidebar & Explorer   │  │    Command Palette     │  │  Multi-LLM AI Assistant  │  │
│  │   (VS Code File Icons) │  │    (Quick Launcher)    │  │  (OpenAI/Gemini/Grok/Oll)│  │
│  └────────────────────────┘  └────────────────────────┘  └──────────────────────────┘  │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Dispatches Actions & Observes State
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                           CONTROLLER & ORCHESTRATION LAYER                             │
│  ┌────────────────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐  │
│  │   FxEditorController   │  │  EditorTabController   │  │      CommandManager      │  │
│  │   (Global App State)   │  │  (Tab-Scoped Lifecycle)│  │   (GoF Undo/Redo Engine) │  │
│  └────────────────────────┘  └────────────────────────┘  └──────────────────────────┘  │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Executes Operations
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                             BACKEND ENGINE & CORE SERVICES                             │
│  ┌────────────────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐  │
│  │  CodeExecutionService  │  │ CodeDiagnosticsService │  │   CodeFormatterService   │  │
│  │ (ProcessBuilder Runner)│  │  (Debounced Linter)    │  │ (Multi-Language Indenter)│  │
│  └────────────────────────┘  └────────────────────────┘  └──────────────────────────┘  │
│  ┌────────────────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐  │
│  │      FileService       │  │ FileSecurityValidator  │  │       ThemeService       │  │
│  │(Atomic I/O + .bak nets)│  │(Anti-Traversal & Pois) │  │(6 Dynamic Theme Engines) │  │
│  └────────────────────────┘  └────────────────────────┘  └──────────────────────────┘  │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Synchronizes Real-Time State
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                         REAL-TIME COLLABORATION PIPELINE                               │
│  ┌─────────────────────────────────┐       ┌─────────────────────────────────────────┐ │
│  │ CollaborativeWebSocketServer    │       │ CollaborativeWebSocketClient            │ │
│  │ (Line-Framed Non-Blocking TCP)  │◄─────►│ (Auto-Reconnecting Client Pipeline)     │ │
│  └────────────────┬────────────────┘       └────────────────────┬────────────────────┘ │
│                   │                                             │                      │
│                   ▼                                             ▼                      │
│       ┌───────────────────────┐                     ┌───────────────────────┐          │
│       │ Operational Transform │                     │  Rate Limiter & Auth  │          │
│       │ (Vector-Clock Sync)   │                     │  (JWT + Audit Logger) │          │
│       └───────────────────────┘                     └───────────────────────┘          │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Layer Breakdown

1. **Presentation Layer (`view.fx`)**:
   - **Virtualized Viewport**: Utilizes RichTextFX's virtual flow—only lines rendered on the active viewport allocate JavaFX scene-graph nodes. Memory usage remains constant $O(1)$ whether viewing 10 or 100,000 lines of code.
   - **Modern Theming System**: 6 curated CSS themes (VS Code Dark Modern, VS Code Editor, Dracula, Monokai, Cyberpunk, GitHub Light) backed by responsive token variables.
   - **Icon Hierarchy**: Official VS Code Codicons and genuine Devicons powered by Kordamp Ikonli for file extensions (`.java`, `.py`, `.js`, `.ts`, `.rs`, `.go`, `.html`, `.css`, etc.) and system actions.

2. **Controller Layer (`controller`, `command`)**:
   - **`FxEditorController`**: Manages window-level coordination, split pane toggling, modal overlays, file dialogs, and clean application shutdown hooks.
   - **`EditorTabController`**: Encapsulates document state, dirty flags, line/column tracking, encoding (UTF-8, ISO-8859-1, US-ASCII), line endings (CRLF / LF), and tab navigation.
   - **`CommandManager`**: Implements the classic Gang of Four (GoF) Command Pattern for non-destructive, bounded Undo/Redo execution.

3. **Core Services Layer (`service`, `model`, `template`)**:
   - **`FileService`**: Guarantees zero data loss using POSIX atomic renames (`.tmp` file writes followed by `StandardCopyOption.ATOMIC_MOVE`) alongside `.bak` safety net backups.
   - **`FileSecurityValidator`**: Protects against path traversal attacks (`../`), null-byte poisoning, Windows reserved devices (`CON`, `PRN`, `AUX`, `COM1-9`), and NTFS Alternate Data Streams (`:$DATA`).
   - **`CodeExecutionService`**: Safe, platform-neutral compilation and execution pipeline for Java, Python, Node.js, Bash, Ruby, C, and C++ with asynchronous tool availability caching.
   - **`CodeDiagnosticsService`**: Multi-language linter utilizing a 500ms debounce daemon executor to eliminate CPU spikes during rapid typing.
   - **`AiService`**: Pluggable multi-provider REST client with support for OpenAI (GPT-4o), Google Gemini, xAI Grok, and local offline Ollama models.

4. **Real-Time Collaboration Layer (`collaboration.*`)**:
   - **Operational Transformation (`OT`)**: Real-time multi-user document synchronization with revision ordering and conflict resolution.
   - **Networking**: High-throughput, line-framed TCP client/server communication.
   - **Security**: Token-bucket rate limiting (50 ops/sec, burst 100), HMAC-SHA256 JWT authentication, and tamper-evident append-only audit logging.

---

## 🚀 Key Features

### 1. Dual-Pane Split Editor (`Cmd+\` / `Ctrl+\`)
- Work across two documents simultaneously in a 50/50 split layout.
- Compare code, edit side-by-side with documentation or reference configuration files (`pom.xml`, `README.md`).
- Independent tab headers with synchronized action controls.

### 2. Multi-Tab Dock Panel
- **Terminal**: Real system shell sessions (`zsh`, `bash`, `cmd.exe`, `powershell`) with full PTY-driven interactivity, ANSI color rendering, and auto-kill cleanup.
- **Problems**: Live diagnostic scanner displaying errors, warnings, and code smells with one-click code navigation.
- **Output**: Dedicated logging streams for Build, Run, Git, and Diagnostics.
- **Ports**: Real-time port discovery scanning listening TCP sockets on `localhost`.
- **Debug Console**: Interactive REPL and command evaluation environment.

### 3. Integrated Multi-LLM AI Studio
- One-click actions: **Explain Code**, **Find Bugs**, **Optimize Performance**, **Generate Unit Tests**, and **Custom Code Prompting**.
- Supports OpenAI, Google Gemini, xAI Grok, and Local Ollama with encrypted local credential storage.

### 4. Code Execution Engine
- One-click Run button (`Cmd+R` / `Ctrl+R`) with custom program argument support.
- Automatically handles compilation (`javac`, `gcc`, `g++`), execution, and post-run bytecode/binary cleanup.

### 5. VS Code File Icons & Design System
- Accurate file extension recognition with genuine language logos.
- Dynamically responds to theme switching with clean contrast.

---

## 📊 Project Metrics & Directory Structure

```
AuraOrbit/
├── pom.xml                                # Maven build definition (Java 21, JavaFX 21, RichTextFX, Ikonli)
├── CODE_OF_CONDUCT.md                    # Contributor covenant code of conduct
├── LICENSE                               # MIT License
├── SECURITY.md                           # Security reporting policy
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── Main.java                 # Bootstrap entry point (launches JavaFxEditorApp)
│   │   │   ├── app/
│   │   │   │   └── JavaFxEditorApp.java  # JavaFX Application lifecycle & window initialization
│   │   │   ├── collaboration/            # Real-time multi-user peer collaboration engine
│   │   │   │   ├── core/                 # Session, presence, and permission state models
│   │   │   │   ├── integration/          # Collaboration controller orchestrator
│   │   │   │   ├── network/              # Non-blocking WebSocket / TCP server & client
│   │   │   │   ├── security/             # RateLimiter, JWT tokens, and AuditLogger
│   │   │   │   ├── sync/                 # Operational Transformation (OT) engine
│   │   │   │   └── ui/                   # HostWorkspaceDialog & JoinWorkspaceDialog
│   │   │   ├── command/                  # GoF Command pattern (Insert, Delete, Replace, Append)
│   │   │   ├── controller/               # Core controllers (FxEditorController, EditorTabController)
│   │   │   ├── model/                    # TextBuffer, Document, and pagination data structures
│   │   │   ├── service/                  # Backend services (File, Exec, Diagnostics, Format, AI, Security)
│   │   │   ├── template/                 # Template Method pattern for file scaffolding (Java, MD, JSON)
│   │   │   └── view/fx/                  # Modern JavaFX UI components (Editor, Terminal, Sidebar, Palettes)
│   │   └── resources/
│   │       └── themes/                   # 6 Production CSS Themes (vscode-dark, dracula, monokai, etc.)
│   └── test/
│       └── java/
│           └── test/
│               └── EditorTestSuite.java  # Comprehensive 124-test automated verification suite
```

| Metric | Measurement |
| :--- | :--- |
| **Java Platform** | OpenJDK 21 (LTS) |
| **GUI Framework** | JavaFX 21.0.4 + RichTextFX 0.11.4 |
| **Icon Libraries** | Kordamp Ikonli (Codicons & Devicons packs) |
| **Total Source Files** | 52 Production Classes + 1 Comprehensive Test Suite |
| **Automated Unit Tests** | **124 Tests (100% Pass Rate, 0 Failures)** |
| **Compiler Hygiene** | Zero compiler warnings (`-Xlint:unused` compliant) |

---

## ⚡ Engineering Highlights & Performance Strategies

### 1. Zero-DOM-Thrashing Virtualization
Standard text components instantiate nodes for every line of text. AuraOrbit's integration with **RichTextFX** renders only the lines visible within the viewport scroll boundaries. Opening a 100,000-line log or source file executes with constant memory usage and instantaneous 60 FPS scrolling.

### 2. Daemon Asynchronous Syntax Highlighting
Syntax highlighting regex operations run in dedicated background daemon threads (`highlightExecutor`). Typing occurs on the JavaFX Application Thread with zero input lag, and syntax styling is applied asynchronously as text spans resolve.

### 3. Diagnostics Debounce Pipeline
To prevent CPU spikes during fast typing, `CodeDiagnosticsService` batches linting requests using a `ScheduledExecutorService` with a 500ms sliding window debounce. Intermediate keystrokes cancel stale scans.

### 4. Pre-Warmed Tool Availability Cache
Rather than spawning blocking shell processes (`which javac`, `python3 --version`) on every file switch, `CodeExecutionService` initializes a `ConcurrentHashMap` cache populated during application startup on a virtual thread.

### 5. Deterministic Resource Teardown
To prevent zombie processes or thread leaks:
- Tab closure triggers `EditorTabController.dispose()`, cleanly terminating the tab's highlight executor.
- Terminal destruction invokes graceful SIGTERM process destruction followed by `destroyForcibly()` (SIGKILL fallback) and executor shutdown.
- Global application close hook (`primaryStage.setOnCloseRequest`) disposes all controllers, active collaboration sockets, and worker threads.

---

## 🔒 Security & Resilience Model

1. **Path Traversal & Device Sanitization**:
   All file paths pass through `FileSecurityValidator`. Attempted directory traversal (`../../etc/passwd`), null-byte injections (`file.txt\0.exe`), Windows device names (`CON`, `PRN`), or NTFS Alternate Data Streams are rejected immediately.
2. **Crash-Resilient Atomic Storage**:
   Saves are written to a sibling `.tmp` file and committed using POSIX `ATOMIC_MOVE`. If an unexpected shutdown occurs mid-write, the original file remains uncorrupted. A `.bak` copy is retained as an additional safety net.
3. **Collaboration Flood & Tamper Protection**:
   - `RateLimiter` enforces a token-bucket policy (50 ops/sec, burst 100) preventing DoS floods from malicious clients.
   - Session messages are authenticated via HMAC-SHA256 tokens.
   - All session events are written to an append-only `AuditLogger`.

---

## 🛠 Prerequisites & Getting Started

### Prerequisites
- **JDK 21** or later (Temurin, Oracle, or OpenJDK).
- **Apache Maven 3.8+**.

### Build & Run from Source

```bash
# Clone the repository
git clone https://github.com/skmirajulislam/AuraOrbit.git
cd AuraOrbit

# Compile and verify all unit tests
mvn clean test

# Launch the application
mvn javafx:run
```

### Building an Executable Fat JAR

```bash
# Build the shaded fat JAR with all dependencies bundled
mvn clean package

# Run the packaged executable JAR
java -jar target/aura-orbit-2.0.0.jar
```

---

## ⌨ Keyboard Shortcuts

| Shortcut (macOS) | Shortcut (Win / Linux) | Action |
| :--- | :--- | :--- |
| `Cmd + N` | `Ctrl + N` | New File / Template Selector |
| `Cmd + O` | `Ctrl + O` | Open File |
| `Cmd + S` | `Ctrl + S` | Save File (Atomic) |
| `Cmd + Shift + S` | `Ctrl + Shift + S` | Save As... |
| `Cmd + W` | `Ctrl + W` | Close Active Tab |
| `Cmd + Z` | `Ctrl + Z` | Undo Edit |
| `Cmd + Shift + Z` / `Cmd + Y` | `Ctrl + Shift + Z` / `Ctrl + Y` | Redo Edit |
| `Cmd + F` | `Ctrl + F` | Find in Document |
| `Cmd + H` | `Ctrl + H` | Find & Replace Bar |
| `Cmd + \` | `Ctrl + \` | **Toggle Dual-Pane Split Editor** |
| `Cmd + R` | `Ctrl + R` | Run Active File |
| `Cmd + \`` (Backtick) | `Ctrl + \`` (Backtick) | Toggle Integrated Dock / Terminal |
| `Cmd + Shift + P` | `Ctrl + Shift + P` | **Command Palette (Quick Launcher)** |
| `Cmd + Shift + F` | `Ctrl + Shift + F` | Toggle Sidebar Explorer |

---

## 🧪 Automated Test Suite

AuraOrbit includes a comprehensive, multi-phase automated test harness covering all functional and non-functional requirements.

Run the test suite directly via Maven:
```bash
mvn clean test-compile
java -cp target/classes:target/test-classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tr '\n' ':') test.EditorTestSuite
```

### Test Coverage Summary:
```
=================================================
   RUNNING FILE EDITOR AUTOMATED TEST SUITE      
=================================================
[1]  Testing TextBuffer Operations ......................... [11/11 PASS]
[2]  Testing CommandManager & Undo/Redo .................... [10/10 PASS]
[3]  Testing Security & Path Traversal Guards .............. [ 3/ 3 PASS]
[4]  Testing FileService Atomic I/O & Integrity ............ [ 7/ 7 PASS]
[5]  Testing Template Engine (Template Method Pattern) ..... [ 6/ 6 PASS]
[6]  Testing Thread-Safety, Concurrency & O(1) Performance . [ 3/ 3 PASS]
[7]  Testing Multi-Line Operations, Edge Cases & OOM ....... [14/14 PASS]
[8]  Testing Zero-Copy String Atomic Save & Load ........... [ 5/ 5 PASS]
[9]  Testing Dynamic Line Endings & Indentation ............ [ 5/ 5 PASS]
[10] Testing Terminal & Dock Panel (Sessions, Kill-to-Close) [12/12 PASS]
[11] Testing Multi-Language Code Diagnostics Engine ........ [ 8/ 8 PASS]
[12] Testing Code Formatter Engine (Java, JSON, XML, Python) [ 5/ 5 PASS]
[13] Testing Multi-LLM AI Service & Persistent Config ...... [ 7/ 7 PASS]
[14] Testing Program Argument Parsing for Run .............. [ 3/ 3 PASS]
[15] Testing VS Code File Icons Engine & Theme Classes ..... [25/25 PASS]
-------------------------------------------------
RESULTS: 124 PASSED | 0 FAILED (100% Reliability)
-------------------------------------------------
```

---

## 🤝 Contributing & Code of Conduct

We welcome contributions! Please review our [Code of Conduct](CODE_OF_CONDUCT.md) and [Security Policy](SECURITY.md) before submitting pull requests.

1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'feat: add some amazing feature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
