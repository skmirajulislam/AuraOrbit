package view;

import model.Document;
import model.TextBuffer;

import java.util.List;
import java.util.Scanner;

/**
 * Console View rendering ANSI-styled text interface, line-numbered views,
 * paginated buffers, status bars, and interactive menus.
 */
public class ConsoleView {

    // ANSI Color Codes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String GRAY = "\u001B[90m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_DARK = "\u001B[100m";

    private final Scanner scanner;
    private boolean colorsEnabled = true;

    public ConsoleView() {
        this.scanner = new Scanner(System.in);
    }

    public void setColorsEnabled(boolean enabled) {
        this.colorsEnabled = enabled;
    }

    private String style(String text, String ansiCode) {
        return colorsEnabled ? ansiCode + text + RESET : text;
    }

    public void printBanner() {
        System.out.println(style("===============================================================", CYAN));
        System.out.println(style("           JAVA CONSOLE FILE EDITOR & BUFFER ENGINE           ", BOLD + CYAN));
        System.out.println(style("   Memory-Efficient | Atomic I/O | Undo/Redo | Secure NIO.2   ", GRAY));
        System.out.println(style("===============================================================", CYAN));
    }

    public void printStatusBar(Document document, TextBuffer buffer, int undoCount, int redoCount) {
        String docName = (document != null) ? document.getFileName() : "[No Document]";
        String dirtyIndicator = (buffer != null && buffer.isDirty()) ? style(" ● [MODIFIED]", YELLOW) : style(" ✔ [SAVED]", GREEN);
        int lineCount = (buffer != null) ? buffer.getLineCount() : 0;
        long charCount = (buffer != null) ? buffer.getEstimatedCharacterCount() : 0;

        System.out.println(style("---------------------------------------------------------------", GRAY));
        System.out.printf("%s %s | %s lines | %s chars | Undo: %d | Redo: %d%n",
                style("FILE:", BOLD + WHITE),
                style(docName, CYAN),
                style(String.valueOf(lineCount), BOLD + YELLOW),
                style(String.valueOf(charCount), GRAY),
                undoCount,
                redoCount
        );
        System.out.println("Status: " + dirtyIndicator);
        System.out.println(style("---------------------------------------------------------------", GRAY));
    }

    public void printBufferView(TextBuffer buffer, int startLine, int pageSize) {
        if (buffer == null || buffer.isEmpty()) {
            System.out.println(style("  ~ [Buffer is empty] ~", GRAY));
            return;
        }

        List<String> pageLines = buffer.getPage(startLine, pageSize);
        int currentLineNum = Math.max(1, startLine);

        System.out.println(style("--- Text Content (Lines " + currentLineNum + " - " + (currentLineNum + pageLines.size() - 1) + " of " + buffer.getLineCount() + ") ---", GRAY));
        for (String line : pageLines) {
            String gutter = String.format("%4d │ ", currentLineNum++);
            System.out.println(style(gutter, GRAY) + line);
        }
        System.out.println(style("--- End of Page ---", GRAY));
    }

    public void printMenu() {
        System.out.println();
        System.out.println(style("COMMANDS:", BOLD + WHITE));
        System.out.printf("  %s %-12s %s%n", style("[1]", CYAN), "View/Page", "View buffer contents with line numbers");
        System.out.printf("  %s %-12s %s%n", style("[2]", CYAN), "Insert", "Insert line at specific line number");
        System.out.printf("  %s %-12s %s%n", style("[3]", CYAN), "Append", "Append new line to end of buffer");
        System.out.printf("  %s %-12s %s%n", style("[4]", CYAN), "Replace", "Replace text at specific line");
        System.out.printf("  %s %-12s %s%n", style("[5]", CYAN), "Delete", "Delete specific line number");
        System.out.printf("  %s %-12s %s%n", style("[6]", CYAN), "Undo", "Undo last modification");
        System.out.printf("  %s %-12s %s%n", style("[7]", CYAN), "Redo", "Redo last undone modification");
        System.out.printf("  %s %-12s %s%n", style("[8]", CYAN), "Open", "Open file from disk (NIO.2)");
        System.out.printf("  %s %-12s %s%n", style("[9]", CYAN), "New", "Create new file or scaffold from template");
        System.out.printf("  %s %-12s %s%n", style("[10]", CYAN), "Save", "Save atomically to disk (with backup)");
        System.out.printf("  %s %-12s %s%n", style("[11]", CYAN), "Save As", "Save buffer to a new path");
        System.out.printf("  %s %-12s %s%n", style("[12]", CYAN), "File Info", "Inspect file attributes and metadata");
        System.out.printf("  %s %-12s %s%n", style("[0]", RED), "Exit", "Exit editor");
    }

    public String promptString(String prompt) {
        System.out.print(style(prompt + ": ", BOLD + WHITE));
        if (!scanner.hasNextLine()) {
            throw new java.util.NoSuchElementException("Standard input EOF reached.");
        }
        return scanner.nextLine().trim();
    }

    public int promptInt(String prompt, int defaultVal) {
        System.out.print(style(prompt + " [" + defaultVal + "]: ", BOLD + WHITE));
        if (!scanner.hasNextLine()) {
            return defaultVal;
        }
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            printWarning("Invalid integer input. Using default: " + defaultVal);
            return defaultVal;
        }
    }

    public void printSuccess(String message) {
        System.out.println(style("✔ " + message, GREEN));
    }

    public void printInfo(String message) {
        System.out.println(style("ℹ " + message, CYAN));
    }

    public void printWarning(String message) {
        System.out.println(style("⚠ " + message, YELLOW));
    }

    public void printError(String message) {
        System.out.println(style("✖ " + message, RED));
    }
}
