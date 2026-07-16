/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Incremental UTF-8 file tailer that never exposes half-decoded characters or
 * incomplete lines to numerical parsers.
 *
 * <p>Handles truncation/rotation by rewinding when the file shrinks. Partial
 * trailing lines are buffered until a newline arrives or {@link #flushPartial()}
 * is requested at end-of-run.</p>
 */
public final class LiveFileTailer {

    private final Path path;
    private final CharsetDecoder decoder;
    private final StringBuilder lineBuffer = new StringBuilder();
    private long position;
    private final ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
    private final CharBuffer charBuffer = CharBuffer.allocate(8192);

    public LiveFileTailer(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.position = 0L;
    }

    public Path getPath() {
        return this.path;
    }

    public long getPosition() {
        return this.position;
    }

    /**
     * Read newly available complete lines since the last call.
     */
    public List<String> pollLines() throws IOException {
        List<String> lines = new ArrayList<>();
        if (!Files.isRegularFile(this.path)) {
            return lines;
        }
        long size = Files.size(this.path);
        if (size < this.position) {
            // Truncation or rotation.
            this.position = 0L;
            this.lineBuffer.setLength(0);
            this.decoder.reset();
        }
        if (size == this.position) {
            return lines;
        }

        try (FileChannel channel = FileChannel.open(this.path, StandardOpenOption.READ)) {
            channel.position(this.position);
            while (true) {
                this.byteBuffer.clear();
                int read = channel.read(this.byteBuffer);
                if (read < 0) {
                    break;
                }
                this.position = channel.position();
                this.byteBuffer.flip();
                this.charBuffer.clear();
                this.decoder.decode(this.byteBuffer, this.charBuffer, false);
                this.charBuffer.flip();
                appendDecoded(lines);
                if (read == 0) {
                    break;
                }
            }
        }
        return lines;
    }

    /**
     * Emit any incomplete trailing line (end of calculation).
     */
    public List<String> flushPartial() {
        List<String> lines = new ArrayList<>();
        if (this.lineBuffer.length() > 0) {
            lines.add(this.lineBuffer.toString());
            this.lineBuffer.setLength(0);
        }
        return lines;
    }

    private void appendDecoded(List<String> lines) {
        while (this.charBuffer.hasRemaining()) {
            char ch = this.charBuffer.get();
            if (ch == '\n') {
                // Drop optional CR before LF.
                int len = this.lineBuffer.length();
                if (len > 0 && this.lineBuffer.charAt(len - 1) == '\r') {
                    this.lineBuffer.setLength(len - 1);
                }
                lines.add(this.lineBuffer.toString());
                this.lineBuffer.setLength(0);
            } else {
                this.lineBuffer.append(ch);
            }
        }
    }
}
