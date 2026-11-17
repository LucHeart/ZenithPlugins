package lucheart.zenithchat.module;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import static lucheart.zenithchat.ZenithChatPlugin.LOG;

/**
 * Appends chat lines to one file per day, {@code <directory>/2026-11-11.log}:
 * <pre>[14:03:12] [CHAT] &lt;Steve&gt; hello</pre>
 * Continuation lines of a multi-line message are indented with a tab, so every line that
 * doesn't start with a tab is a new message.
 */
public final class ChatLog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Supplier<Path> directory;
    private final Clock clock;
    private BufferedWriter writer;
    private Path file;
    private boolean failed;

    public ChatLog(Supplier<Path> directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    public synchronized void write(String type, String text) {
        var now = LocalDateTime.now(clock);
        String line = "[" + TIME.format(now) + "] [" + type + "] "
            + text.strip().replace("\r\n", "\n").replace("\n", "\n\t");
        try {
            writerFor(now.toLocalDate()).write(line);
            writer.newLine();
            writer.flush();
            failed = false;
        } catch (IOException e) {
            // log once per failure streak, not for every message
            if (!failed) LOG.error("Failed to write chat log {}", file, e);
            failed = true;
            close();
        }
    }

    private BufferedWriter writerFor(LocalDate day) throws IOException {
        Path target = directory.get().resolve(day + ".log");
        if (writer != null && target.equals(file)) return writer;
        close();
        file = target;
        Files.createDirectories(target.toAbsolutePath().getParent());
        writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return writer;
    }

    /** Closes the current file, the next message opens it again. */
    public synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            // nothing left to save
        }
        writer = null;
    }

    /** The file today's messages go to. */
    public Path currentFile() {
        return directory.get().resolve(LocalDate.now(clock) + ".log").toAbsolutePath();
    }
}
