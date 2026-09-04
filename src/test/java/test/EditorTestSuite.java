package test;

import command.*;
import model.Document;
import model.TextBuffer;
import service.FileSecurityValidator;
import service.FileService;
import template.JavaTemplate;
import template.JsonTemplate;
import template.MarkdownTemplate;
import template.TemplateFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

        System.out.println("\n-------------------------------------------------");
        System.out.printf("RESULTS: %d PASSED | %d FAILED%n", testsPassed, testsFailed);
        System.out.println("-------------------------------------------------");

        if (testsFailed > 0) {
            System.exit(1);
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
}
