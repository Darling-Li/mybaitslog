package com.plugins.mybaitslog.filter;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按日志线程关联 MyBatis 的 Preparing 与 Parameters。
 */
final class SqlLogPairer {

    private static final long PENDING_SQL_TIMEOUT_MILLIS = 30_000L;
    private static final int MAX_PENDING_SQL_PER_THREAD = 100;
    private static final Pattern SPRING_BOOT_THREAD = Pattern.compile("---\\s+(?:\\[[^\\]]+]\\s+)*\\[\\s*([^\\]]+)]");
    private static final Pattern THREAD_BEFORE_LEVEL = Pattern.compile("\\[([^\\]]+)]\\s+(?:TRACE|DEBUG|INFO|WARN|ERROR)\\b");
    private static final Pattern THREAD_AFTER_LEVEL = Pattern.compile("(?:TRACE|DEBUG|INFO|WARN|ERROR)\\s+\\[([^\\]]+)]");

    private final Map<String, Deque<PendingPreparing>> preparingLinesByThread = new ConcurrentHashMap<>();

    /**
     * @return 匹配完成的 SQL 对；未匹配或无法识别线程时返回 {@code null}
     */
    @Nullable
    SqlLogPair accept(String currentLine, String preparing, String parameters) {
        if (currentLine.contains(preparing)) {
            final String threadKey = extractThreadKey(currentLine);
            if (threadKey != null) {
                addPreparing(threadKey, currentLine);
            }
            return null;
        }
        if (!currentLine.contains(parameters)) {
            return null;
        }
        final String threadKey = extractThreadKey(currentLine);
        final String preparingLine = threadKey == null ? null : takePreparing(threadKey);
        return preparingLine == null ? null : new SqlLogPair(preparingLine, currentLine);
    }

    @Nullable
    String extractThreadKey(String line) {
        Matcher matcher = SPRING_BOOT_THREAD.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = THREAD_BEFORE_LEVEL.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = THREAD_AFTER_LEVEL.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void addPreparing(String threadKey, String preparingLine) {
        final Deque<PendingPreparing> pendingLines = preparingLinesByThread.computeIfAbsent(threadKey, key -> new ArrayDeque<>());
        synchronized (pendingLines) {
            clearExpired(pendingLines);
            while (pendingLines.size() >= MAX_PENDING_SQL_PER_THREAD) {
                pendingLines.removeFirst();
            }
            pendingLines.addLast(new PendingPreparing(preparingLine));
        }
    }

    @Nullable
    private String takePreparing(String threadKey) {
        final Deque<PendingPreparing> pendingLines = preparingLinesByThread.get(threadKey);
        if (pendingLines == null) {
            return null;
        }
        synchronized (pendingLines) {
            clearExpired(pendingLines);
            final PendingPreparing pendingPreparing = pendingLines.pollFirst();
            if (pendingLines.isEmpty()) {
                preparingLinesByThread.remove(threadKey, pendingLines);
            }
            return pendingPreparing == null ? null : pendingPreparing.line;
        }
    }

    private void clearExpired(Deque<PendingPreparing> pendingLines) {
        final long expireBefore = System.currentTimeMillis() - PENDING_SQL_TIMEOUT_MILLIS;
        while (!pendingLines.isEmpty() && pendingLines.peekFirst().createdAt < expireBefore) {
            pendingLines.removeFirst();
        }
    }

    static final class SqlLogPair {
        private final String preparingLine;
        private final String parametersLine;

        private SqlLogPair(String preparingLine, String parametersLine) {
            this.preparingLine = preparingLine;
            this.parametersLine = parametersLine;
        }

        String getPreparingLine() {
            return preparingLine;
        }

        String getParametersLine() {
            return parametersLine;
        }
    }

    private static final class PendingPreparing {
        private final String line;
        private final long createdAt;

        private PendingPreparing(String line) {
            this.line = line;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
