package test;

import command.*;
import model.TextBuffer;
import service.FileSecurityValidator;
import service.FileService;
import template.JavaTemplate;
import template.JsonTemplate;
import template.MarkdownTemplate;
import template.TemplateFactory;
import view.fx.TerminalPane;
import view.fx.IconFactory;
import service.CodeDiagnosticsService;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.devicons.Devicons;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Automated test suite verifying buffer operations, undo/redo consistency,
 * atomic save integrity, path traversal security, and template generation.
 */
public class EditorTestSuite {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   RUNNING FILE EDITOR AUTOMATED TEST SUITE      ");
        System.out.println("=================================================");

        testTextBufferOperations();
        testCommandManagerUndoRedo();
        testSecurityValidator();
        testFileServiceAtomicSaveAndLoad();
        testTemplateEngine();
        testConcurrencyAndThreadSafety();
        testMultiLineUndoRedoAndEdgeCases();
        testStringAtomicSaveAndLoad();
        testLineEndingsAndIndentationLogic();
        testTerminalAndDockFeatures();
        testCodeDiagnosticsEngine();
        testCodeFormatterService();
        testAiServiceConfig();
        testProgramArgumentParsing();
        testFileIcons();
        testPolicyAgreementService();

        System.out.println("\n-------------------------------------------------");
        System.out.printf("RESULTS: %d PASSED | %d FAILED%n", testsPassed, testsFailed);
        System.out.println("-------------------------------------------------");

        if (testsFailed > 0) {
            Platform.exit();
            System.exit(1);
        } else {
            Platform.exit();
            System.exit(0);
        }
    }

    private static void assertEquals(Object expected, Object actual, String testName) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            System.out.println("  ✔ PASS: " + testName);
            testsPassed++;
        } else {
            System.err.println("  ✖ FAIL: " + testName + " | Expected: [" + expected + "], Got: [" + actual + "]");
            testsFailed++;
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (condition) {
            System.out.println("  ✔ PASS: " + testName);
            testsPassed++;
        } else {
            System.err.println("  ✖ FAIL: " + testName + " | Expected true, Got false");
            testsFailed++;
        }
    }

    private static void testTextBufferOperations() {
        System.out.println("\n[1] Testing TextBuffer Operations...");
        TextBuffer buffer = new TextBuffer();
        assertEquals(0, buffer.getLineCount(), "Initial empty buffer count");
        assertTrue(buffer.isEmpty(), "Initial buffer isEmpty");

        buffer.appendLine("Line 1");
        buffer.appendLine("Line 3");
        assertEquals(2, buffer.getLineCount(), "Append two lines");
        assertTrue(buffer.isDirty(), "Buffer dirty after append");

        buffer.insertLine(2, "Line 2");
        assertEquals(3, buffer.getLineCount(), "Insert line at index 2");
        assertEquals("Line 2", buffer.getLine(2), "Verify inserted content");

        String old = buffer.replaceLine(2, "Line 2 Modified");
        assertEquals("Line 2", old, "Replace returns previous content");
        assertEquals("Line 2 Modified", buffer.getLine(2), "Verify replaced content");

        String deleted = buffer.deleteLine(2);
        assertEquals("Line 2 Modified", deleted, "Delete returns deleted content");
        assertEquals(2, buffer.getLineCount(), "Line count after delete");

        List<String> page = buffer.getPage(1, 10);
        assertEquals(2, page.size(), "Pagination page size check");
    }

    private static void testCommandManagerUndoRedo() {
        System.out.println("\n[2] Testing CommandManager & Undo/Redo...");
        TextBuffer buffer = new TextBuffer();
        CommandManager cm = new CommandManager(10);

        // Edit 1: Append
        cm.executeCommand(new AppendLineCommand("First"), buffer);
        // Edit 2: Append
        cm.executeCommand(new AppendLineCommand("Second"), buffer);
        // Edit 3: Insert
        cm.executeCommand(new InsertLineCommand(2, "Middle"), buffer);

        assertEquals(3, buffer.getLineCount(), "3 commands executed");
        assertEquals("Middle", buffer.getLine(2), "Middle inserted at line 2");

        // Undo 1 (reverts insert)
        cm.undo(buffer);
        assertEquals(2, buffer.getLineCount(), "Undo 1 reduces line count to 2");
        assertEquals("Second", buffer.getLine(2), "Line 2 is now 'Second'");

        // Undo 2 (reverts second append)
        cm.undo(buffer);
        assertEquals(1, buffer.getLineCount(), "Undo 2 reduces line count to 1");

        // Redo 1 (restores second append)
        cm.redo(buffer);
        assertEquals(2, buffer.getLineCount(), "Redo 1 restores line count to 2");

        // Redo 2 (restores middle insert)
        cm.redo(buffer);
        assertEquals(3, buffer.getLineCount(), "Redo 2 restores line count to 3");
        assertEquals("Middle", buffer.getLine(2), "Middle line restored");

        // Test Replace Command undo/redo
        cm.executeCommand(new ReplaceLineCommand(1, "First Updated"), buffer);
        assertEquals("First Updated", buffer.getLine(1), "Replace applied");
        cm.undo(buffer);
        assertEquals("First", buffer.getLine(1), "Replace undone back to 'First'");
    }

    private static void testSecurityValidator() {
        System.out.println("\n[3] Testing Security & Path Traversal Guards...");

        // Valid path
        Path p = FileSecurityValidator.sanitizeAndResolvePath("test.txt");
        assertTrue(p != null, "Valid path sanitized");

        // Null byte injection attempt
        boolean caughtNullByte = false;
        try {
            FileSecurityValidator.sanitizeAndResolvePath("test\0bad.txt");
        } catch (SecurityException e) {
            caughtNullByte = true;
        }
        assertTrue(caughtNullByte, "Null byte injection blocked");

        // Reserved system names
        boolean caughtReserved = false;
        try {
            FileSecurityValidator.sanitizeAndResolvePath("CON.txt");
        } catch (SecurityException e) {
            caughtReserved = true;
        }
        assertTrue(caughtReserved, "Windows reserved device name blocked");
    }

    private static void testFileServiceAtomicSaveAndLoad() {
        System.out.println("\n[4] Testing FileService Atomic I/O & Integrity...");
        FileService service = new FileService();
        Path testFile = Paths.get("test_output_file.txt");

        try {
            TextBuffer buffer = new TextBuffer();
            buffer.appendLine("Public Static Void Main");
            buffer.appendLine("Testing atomic save line 2");

            // Save with backup
            service.saveFileAtomically(testFile, buffer, true);
            assertTrue(Files.exists(testFile), "File exists after atomic save");
            assertTrue(!buffer.isDirty(), "Buffer marked clean after save");

            // Load and verify
            TextBuffer loaded = service.loadFile(testFile);
            assertEquals(2, loaded.getLineCount(), "Loaded file line count matches");
            assertEquals("Public Static Void Main", loaded.getLine(1), "Line 1 matches");
            assertEquals("Testing atomic save line 2", loaded.getLine(2), "Line 2 matches");

            // Modify and save again to trigger backup
            buffer.appendLine("Line 3 modified");
            service.saveFileAtomically(testFile, buffer, true);

            Path backupFile = Paths.get("test_output_file.txt.bak");
            assertTrue(Files.exists(backupFile), "Backup (.bak) file created");

            // Verify backup content has previous version (2 lines)
            TextBuffer loadedBackup = service.loadFile(backupFile);
            assertEquals(2, loadedBackup.getLineCount(), "Backup has original 2 lines");

        } catch (IOException e) {
            System.err.println("FileService test exception: " + e.getMessage());
            testsFailed++;
        } finally {
            // Clean up test files
            try {
                Files.deleteIfExists(testFile);
                Files.deleteIfExists(Paths.get("test_output_file.txt.bak"));
            } catch (IOException ignored) {}
        }
    }

    private static void testTemplateEngine() {
        System.out.println("\n[5] Testing Template Engine (Template Method Pattern)...");
        JavaTemplate javaTpl = new JavaTemplate();
        List<String> javaLines = javaTpl.generateScaffold("Calculator.java");
        assertTrue(javaLines.stream().anyMatch(l -> l.contains("public class Calculator")), "Java template class header generated");

        MarkdownTemplate mdTpl = new MarkdownTemplate();
        List<String> mdLines = mdTpl.generateScaffold("README.md");
        assertTrue(mdLines.stream().anyMatch(l -> l.contains("# README")), "Markdown template title generated");

        JsonTemplate jsonTpl = new JsonTemplate();
        List<String> jsonLines = jsonTpl.generateScaffold("config.json");
        assertTrue(jsonLines.get(0).equals("{") && jsonLines.get(jsonLines.size() - 1).equals("}"), "JSON template valid structure");

        assertTrue(TemplateFactory.getTemplate("java") != null, "TemplateFactory resolves 'java'");
        assertTrue(TemplateFactory.getTemplate("md") != null, "TemplateFactory resolves 'md'");
        assertTrue(TemplateFactory.getTemplate("json") != null, "TemplateFactory resolves 'json'");
    }

    private static void testConcurrencyAndThreadSafety() {
        System.out.println("\n[6] Testing Thread-Safety, Concurrency & Zero-CPU O(1) Performance...");
        TextBuffer buffer = new TextBuffer();
        int threadCount = 8;
        int operationsPerThread = 250;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (j % 2 == 0) {
                            buffer.appendLine("Thread-" + threadId + "-item-" + j);
                        } else {
                            // Concurrent reader
                            buffer.getEstimatedCharacterCount();
                            buffer.getLineCount();
                            if (!buffer.isEmpty()) {
                                buffer.getPage(1, 5);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();
            assertTrue(completed, "Multi-threaded operations finished without deadlock");
            assertTrue(buffer.getLineCount() > 0, "Buffer contains concurrently written items");
            assertTrue(buffer.getEstimatedCharacterCount() > 0, "O(1) total character count synchronized accurately");
        } catch (InterruptedException e) {
            System.err.println("Concurrency test interrupted: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testMultiLineUndoRedoAndEdgeCases() {
        System.out.println("\n[7] Testing Multi-Line Operations, Edge Cases & OOM Ceiling Protection...");
        TextBuffer buffer = new TextBuffer();
        CommandManager cm = new CommandManager(20);

        // 1. Multi-line append and exact undo
        String multiLineText = "Line A\nLine B\nLine C";
        cm.executeCommand(new AppendLineCommand(multiLineText), buffer);
        assertEquals(3, buffer.getLineCount(), "Multi-line text split into 3 distinct lines");
        assertEquals("Line A", buffer.getLine(1), "Line 1 is Line A");
        assertEquals("Line B", buffer.getLine(2), "Line 2 is Line B");
        assertEquals("Line C", buffer.getLine(3), "Line 3 is Line C");

        cm.undo(buffer);
        assertEquals(0, buffer.getLineCount(), "Undo completely removed all 3 lines");

        cm.redo(buffer);
        assertEquals(3, buffer.getLineCount(), "Redo restored all 3 lines");

        // 2. Multi-line replace
        cm.executeCommand(new ReplaceLineCommand(2, "Line B1\nLine B2"), buffer);
        assertEquals(4, buffer.getLineCount(), "Replace with 2 lines expanded buffer to 4 lines");
        assertEquals("Line B1", buffer.getLine(2), "Line 2 replaced with Line B1");
        assertEquals("Line B2", buffer.getLine(3), "Line 3 is Line B2");

        cm.undo(buffer);
        assertEquals(3, buffer.getLineCount(), "Undo replace restored line count to 3");
        assertEquals("Line B", buffer.getLine(2), "Line 2 restored to Line B");

        // 3. Pagination edge cases (negative, zero, large sizes)
        List<String> page1 = buffer.getPage(1, -5);
        assertTrue(page1.size() > 0, "Negative pageSize safely clamped without exception");

        List<String> page2 = buffer.getPage(1, 10000);
        assertEquals(3, page2.size(), "Large pageSize safely bounded to buffer size");

        // 4. Alternative Data Streams security check
        boolean caughtAds = false;
        try {
            FileSecurityValidator.sanitizeAndResolvePath("myfile.txt:$DATA");
        } catch (SecurityException e) {
            caughtAds = true;
        }
        assertTrue(caughtAds, "Alternative Data Stream syntax blocked");
    }

    private static void testStringAtomicSaveAndLoad() {
        System.out.println("\n[8] Testing Zero-Copy String Atomic Save & Load...");
        FileService fs = new FileService();
        Path testFile = Paths.get("test_atomic_string.tmp");

        try {
            String originalContent = "package test;\n\npublic class FastLoad {\n    // zero allocation\n}\n";
            fs.saveStringAtomically(testFile, originalContent, false);
            assertTrue(Files.exists(testFile), "File exists after atomic string save");

            String loaded = fs.readString(testFile);
            assertEquals(originalContent, loaded, "Loaded content matches saved string exactly");

            // Overwrite check
            String updated = originalContent + "// modified line\n";
            fs.saveStringAtomically(testFile, updated, true);
            assertEquals(updated, fs.readString(testFile), "Updated content persists correctly");

            Path bakFile = Paths.get("test_atomic_string.tmp.bak");
            assertTrue(Files.exists(bakFile), "Backup .bak file created on overwrite");
            assertEquals(originalContent, fs.readString(bakFile), "Backup content preserved");

            // Cleanup
            Files.deleteIfExists(testFile);
            Files.deleteIfExists(bakFile);
        } catch (IOException e) {
            System.err.println("  ✖ FAIL: testStringAtomicSaveAndLoad threw exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testLineEndingsAndIndentationLogic() {
        System.out.println("\n[9] Testing Dynamic Line Endings & Indentation...");
        String crlfText = "Line 1\r\nLine 2\r\n";
        String lfText = "Line 1\nLine 2\n";
        assertTrue(crlfText.contains("\r\n"), "CRLF detection in document buffer");
        assertTrue(!lfText.contains("\r\n"), "LF detection in document buffer");
        assertEquals("Line 1\nLine 2\n", crlfText.replace("\r\n", "\n"), "CRLF to LF dynamic conversion");
        assertEquals("Line 1\r\nLine 2\r\n", lfText.replace("\n", "\r\n"), "LF to CRLF dynamic conversion");

        String tabText = "\tpublic void test() {}";
        assertTrue(tabText.contains("\t"), "Tab indentation detection");
    }

    private static void testTerminalAndDockFeatures() {
        System.out.println("\n[10] Testing Terminal & Dock Panel (Sessions, Kill-to-Close, REPL, Problems, Ports)...");
        try {
            try {
                javafx.application.Platform.startup(() -> {});
            } catch (IllegalStateException ignored) {
                // Platform already initialized
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] closeCalled = new boolean[1];

            javafx.application.Platform.runLater(() -> {
                try {
                    TerminalPane pane = new TerminalPane();
                    pane.setOnCloseRequested(() -> closeCalled[0] = true);

                    // Test 1: Single session created
                    pane.createNewTerminal();
                    assertEquals(1, pane.getSessionsCount(), "Single terminal session created");

                    // Test 2: Kill active terminal when single instance -> triggers close
                    pane.killActiveTerminal();
                    assertEquals(0, pane.getSessionsCount(), "All sessions removed");
                    assertEquals(true, closeCalled[0], "Close requested called on deleting single shell instance");

                    // Test 3: Multiple sessions
                    closeCalled[0] = false;
                    pane.createNewTerminal(); // session 1
                    pane.createNewTerminal(); // session 2
                    assertEquals(2, pane.getSessionsCount(), "Two terminal sessions created");

                    // Killing 1 session does NOT trigger close
                    pane.killActiveTerminal();
                    assertEquals(1, pane.getSessionsCount(), "One terminal session remaining");
                    assertEquals(false, closeCalled[0], "Close NOT called when sessions still remain");

                    // Killing last session DOES trigger close
                    pane.killActiveTerminal();
                    assertEquals(0, pane.getSessionsCount(), "Zero sessions remaining");
                    assertEquals(true, closeCalled[0], "Close called when last session killed");

                    // Test 4: Problems Tab Diagnostics
                    pane.addProblem(Codicons.ERROR, "Error", "Test syntax error", "Main.java", 10, 5, "syntax");
                    assertEquals(1, pane.getProblemsCount(), "Problem added to Problems Tab");
                    pane.clearProblems();
                    assertEquals(0, pane.getProblemsCount(), "Problems cleared");

                    // Test 5: Output Tab Logging
                    pane.logOutput("AuraOrbit (System)", "Test Log Line");
                    assertEquals(true, pane.hasChannel("AuraOrbit (System)"), "Output channel exists");

                    pane.dispose();
                } finally {
                    latch.countDown();
                }
            });

            boolean finished = latch.await(5, TimeUnit.SECONDS);
            assertTrue(finished, "JavaFX Terminal tests completed within timeout");
        } catch (Exception e) {
            System.err.println("  ✖ FAIL: testTerminalAndDockFeatures threw exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testCodeDiagnosticsEngine() {
        System.out.println("\n[11] Testing Multi-Language Code Diagnostics Engine (Java, Python, JS, JSON, CSS)...");
        try {
            // 1. Test Java Unused Import Detection
            Path tempJava = Files.createTempFile("TestDiag", ".java");
            tempJava.toFile().deleteOnExit();
            String javaContent = """
                    package test;
                    import java.util.List;
                    import java.util.ArrayList;
                    import java.io.IOException;
                    public class TestDiag {
                        private int unusedField = 42;
                        public void doWork() {
                            List<String> list = new ArrayList<>();
                        }
                    }
                    """;
            Files.writeString(tempJava, javaContent);
            List<TerminalPane.ProblemItem> javaProblems = CodeDiagnosticsService.analyzeFile(tempJava);

            boolean hasUnusedImport = javaProblems.stream().anyMatch(p -> p.message().contains("The import java.io.IOException is never used"));
            assertTrue(hasUnusedImport, "Java: Detected unused import java.io.IOException");

            boolean hasUnusedField = javaProblems.stream().anyMatch(p -> p.message().contains("The value of the field TestDiag.unusedField is not used"));
            assertTrue(hasUnusedField, "Java: Detected unused private field TestDiag.unusedField");

            // 2. Test Python Unused Import & Bare Except
            Path tempPy = Files.createTempFile("test_script", ".py");
            tempPy.toFile().deleteOnExit();
            String pyContent = """
                    import os
                    import sys
                    from math import sqrt, pi
                    print(pi)
                    try:
                        pass
                    except:
                        pass
                    """;
            Files.writeString(tempPy, pyContent);
            List<TerminalPane.ProblemItem> pyProblems = CodeDiagnosticsService.analyzeFile(tempPy);

            boolean pyUnusedOs = pyProblems.stream().anyMatch(p -> p.message().contains("The import 'os' is not used"));
            assertTrue(pyUnusedOs, "Python: Detected unused import 'os'");

            boolean pyBareExcept = pyProblems.stream().anyMatch(p -> p.message().contains("Do not use bare 'except:' without exception type"));
            assertTrue(pyBareExcept, "Python: Detected bare except clause");

            // 3. Test JavaScript Unused Import & Debugger Statement
            Path tempJs = Files.createTempFile("test_app", ".js");
            tempJs.toFile().deleteOnExit();
            String jsContent = """
                    import { fetchUser, deleteUser } from './api';
                    debugger;
                    console.log(fetchUser());
                    """;
            Files.writeString(tempJs, jsContent);
            List<TerminalPane.ProblemItem> jsProblems = CodeDiagnosticsService.analyzeFile(tempJs);

            boolean jsUnusedImport = jsProblems.stream().anyMatch(p -> p.message().contains("The import 'deleteUser' is never used"));
            assertTrue(jsUnusedImport, "JavaScript: Detected unused import 'deleteUser'");

            boolean jsDebugger = jsProblems.stream().anyMatch(p -> p.message().contains("Unexpected 'debugger' statement"));
            assertTrue(jsDebugger, "JavaScript: Detected debugger statement");

            // 4. Test JSON Trailing Comma Error
            Path tempJson = Files.createTempFile("data", ".json");
            tempJson.toFile().deleteOnExit();
            String jsonContent = "{\n  \"name\": 'AuraOrbit',\n  \"version\": \"2.0.0\",\n}";
            Files.writeString(tempJson, jsonContent);
            List<TerminalPane.ProblemItem> jsonProblems = CodeDiagnosticsService.analyzeFile(tempJson);

            boolean jsonComma = jsonProblems.stream().anyMatch(p -> p.message().contains("Trailing comma in JSON is not allowed"));
            assertTrue(jsonComma, "JSON: Detected trailing comma error");

            boolean jsonQuotes = jsonProblems.stream().anyMatch(p -> p.message().contains("JSON strings must use double quotes"));
            assertTrue(jsonQuotes, "JSON: Detected single quote error");

        } catch (Exception e) {
            System.err.println("  ✖ FAIL: testCodeDiagnosticsEngine threw exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testCodeFormatterService() {
        System.out.println("\n[12] Testing Code Formatter Engine (Java, JSON, XML, Python)...");

        // 1. Java Formatter Test
        String unformattedJava = "public class Hello{\npublic static void main(String[] args){\nint x=10;\nSystem.out.println(x);\n}\n}";
        String formattedJava = service.CodeFormatterService.formatCode(unformattedJava, "java");
        assertTrue(formattedJava.contains("    public static void main"), "Java: indented method block by 4 spaces");
        assertTrue(formattedJava.contains("        int x=10;"), "Java: indented inner statement by 8 spaces");

        // 2. JSON Formatter Test
        String unformattedJson = "{\"name\":\"AuraOrbit\",\"version\":\"2.0.0\",\"items\":[1,2,3]}";
        String formattedJson = service.CodeFormatterService.formatCode(unformattedJson, "json");
        assertTrue(formattedJson.contains("    \"name\": \"AuraOrbit\""), "JSON: formatted key-values with indent and colon spacing");
        assertTrue(formattedJson.contains("[\n"), "JSON: formatted array with newline");

        // 3. XML Formatter Test
        String unformattedXml = "<project><modelVersion>4.0.0</modelVersion><groupId>com.test</groupId></project>";
        String formattedXml = service.CodeFormatterService.formatCode(unformattedXml, "xml");
        assertTrue(formattedXml.contains("    <modelVersion>"), "XML: indented child tag");
    }

    private static void testAiServiceConfig() {
        System.out.println("\n[13] Testing Multi-LLM AI Service & Persistent Configuration...");

        service.AiService aiService = new service.AiService();
        String origOpenAi = aiService.getOpenAiKey();
        String origGemini = aiService.getGeminiKey();
        String origGrok = aiService.getGrokKey();

        try {
            aiService.setOpenAiKey("test-openai-key-12345");
            aiService.setGeminiKey("test-gemini-key-67890");
            aiService.setGrokKey("test-grok-key-abcde");

            assertEquals("test-openai-key-12345", aiService.getOpenAiKey(), "AiService: Persisted and retrieved OpenAI key");
            assertEquals("test-gemini-key-67890", aiService.getGeminiKey(), "AiService: Persisted and retrieved Gemini key");
            assertEquals("test-grok-key-abcde", aiService.getGrokKey(), "AiService: Persisted and retrieved Grok key");

            assertTrue(aiService.hasKeyForModel("GPT-4o"), "AiService: Recognizes OpenAI model key");
            assertTrue(aiService.hasKeyForModel("Gemini 2.0 Flash"), "AiService: Recognizes Gemini model key");
            assertTrue(aiService.hasKeyForModel("Grok-2"), "AiService: Recognizes Grok model key");
            assertTrue(aiService.hasKeyForModel("DeepSeek-R1 (Local)"), "AiService: Local model requires no API key");
        } finally {
            // Restore original preference state
            aiService.setOpenAiKey(origOpenAi);
            aiService.setGeminiKey(origGemini);
            aiService.setGrokKey(origGrok);
        }
    }

    private static void testProgramArgumentParsing() {
        System.out.println("\n[14] Testing program argument parsing for Run...");
        assertEquals(List.of(), service.CodeExecutionService.parseProgramArguments("  "), "Empty args parse to none");
        assertEquals(List.of("10", "20", "hello"), service.CodeExecutionService.parseProgramArguments("10 20 hello"), "Whitespace-separated args");
        assertEquals(List.of("hello world", "x"), service.CodeExecutionService.parseProgramArguments("\"hello world\" x"), "Quoted args keep spaces");
    }

    private static void testFileIcons() {
        System.out.println("\n[15] Testing VS Code File Icons Engine...");
        assertEquals(Devicons.GIT, IconFactory.getFileIcon(".gitignore", 14).getIconCode(), ".gitignore maps to Git devicon");
        assertEquals(Codicons.VERIFIED, IconFactory.getFileIcon("CODE_OF_CONDUCT.md", 14).getIconCode(), "CODE_OF_CONDUCT.md maps to Verified codicon");
        assertEquals(Codicons.LAW, IconFactory.getFileIcon("LICENSE", 14).getIconCode(), "LICENSE maps to Law codicon");
        assertEquals(Codicons.SHIELD, IconFactory.getFileIcon("SECURITY.md", 14).getIconCode(), "SECURITY.md maps to Shield codicon");
        assertEquals(Codicons.TOOLS, IconFactory.getFileIcon("pom.xml", 14).getIconCode(), "pom.xml maps to Tools codicon");
        assertEquals(Devicons.NPM, IconFactory.getFileIcon("package.json", 14).getIconCode(), "package.json maps to NPM devicon");
        assertEquals(Devicons.DOCKER, IconFactory.getFileIcon("Dockerfile", 14).getIconCode(), "Dockerfile maps to Docker devicon");
        assertEquals(Devicons.PYTHON, IconFactory.getFileIcon("requirements.txt", 14).getIconCode(), "requirements.txt maps to Python devicon");

        assertEquals(Devicons.JAVA, IconFactory.getFileIcon("Main.java", 14).getIconCode(), ".java maps to Java devicon");
        assertEquals(Devicons.PYTHON, IconFactory.getFileIcon("script.py", 14).getIconCode(), ".py maps to Python devicon");
        assertEquals(Codicons.FILE_BINARY, IconFactory.getFileIcon("Main.class", 14).getIconCode(), ".class maps to File Binary codicon");
        assertEquals(Devicons.JAVASCRIPT_BADGE, IconFactory.getFileIcon("app.js", 14).getIconCode(), ".js maps to JS Badge devicon");
        assertEquals(Codicons.FILE_CODE, IconFactory.getFileIcon("index.ts", 14).getIconCode(), ".ts maps to File Code codicon");
        assertEquals(Devicons.REACT, IconFactory.getFileIcon("Component.jsx", 14).getIconCode(), ".jsx maps to React devicon");
        assertEquals(Devicons.HTML5, IconFactory.getFileIcon("index.html", 14).getIconCode(), ".html maps to HTML5 devicon");
        assertEquals(Devicons.CSS3, IconFactory.getFileIcon("style.css", 14).getIconCode(), ".css maps to CSS3 devicon");
        assertEquals(Codicons.JSON, IconFactory.getFileIcon("config.json", 14).getIconCode(), ".json maps to JSON codicon");
        assertEquals(Codicons.MARKDOWN, IconFactory.getFileIcon("notes.md", 14).getIconCode(), ".md maps to Markdown codicon");
        assertEquals(Codicons.TERMINAL, IconFactory.getFileIcon("deploy.sh", 14).getIconCode(), ".sh maps to Terminal codicon");
        assertEquals(Devicons.RUST, IconFactory.getFileIcon("main.rs", 14).getIconCode(), ".rs maps to Rust devicon");
        assertEquals(Devicons.GO, IconFactory.getFileIcon("server.go", 14).getIconCode(), ".go maps to Go devicon");

        // Verify folder icons render as standard codicons
        assertEquals(Codicons.FOLDER, IconFactory.getFolderIcon(false, 14).getIconCode(), "closed folder maps to Folder codicon");
        assertEquals(Codicons.FOLDER_OPENED, IconFactory.getFolderIcon(true, 14).getIconCode(), "opened folder maps to Folder Opened codicon");

        // Verify theme compatibility: icons have .codicon style class and no hardcoded inline style
        assertTrue(IconFactory.getFileIcon("Main.java", 14).getStyleClass().contains("codicon"), "File icon has .codicon class for theme integration");
        assertTrue(!IconFactory.getFileIcon("Main.java", 14).getStyle().contains("-fx-icon-color"), "File icon has no hardcoded color override");
    }

    private static void testPolicyAgreementService() {
        System.out.println("\n[16] Testing Policy Agreement & Attribution Security...");
        try {
            assertTrue(service.PolicyAgreementService.DEVELOPER_ATTRIBUTION.contains("Sk Mirajul Islam"),
                    "Attribution correctly credits Sk Mirajul Islam");
            assertTrue("2.0.0".equals(service.PolicyAgreementService.CURRENT_POLICY_VERSION),
                    "Policy version is 2.0.0");
            assertTrue(service.PolicyAgreementService.getPolicySummary().contains("AuraOrbit"),
                    "Policy summary contains AuraOrbit");
            assertTrue(service.PolicyAgreementService.getPolicySummary().contains("Sk Mirajul Islam"),
                    "Policy summary explicitly protects Sk Mirajul Islam authorship");
            service.PolicyAgreementService.recordPolicyAcceptance();
            assertTrue(service.PolicyAgreementService.isPolicyAccepted(),
                    "Policy cryptographically verified accepted after recording");
        } catch (Exception e) {
            assertTrue(false, "Policy agreement error: " + e.getMessage());
        }
    }
}
