/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.file;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Crash-safe text and binary file replacement.
 *
 * <p>Writes are staged beside the destination, flushed to the OS, and then
 * renamed into place. On power loss the previous complete file remains if the
 * rename has not yet occurred.</p>
 */
public final class AtomicFileWriter {

    private AtomicFileWriter() {
        // Utility class.
    }

    public static void writeUtf8(Path target, String content) throws IOException {
        writeText(target, content, StandardCharsets.UTF_8);
    }

    public static void writeText(Path target, String content, Charset charset) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(charset, "charset");
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path stage = stagePath(target);
        try {
            try (FileChannel channel = FileChannel.open(stage,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                 OutputStream out = java.nio.channels.Channels.newOutputStream(channel);
                 OutputStreamWriter osw = new OutputStreamWriter(out, charset);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                writer.write(content);
                writer.flush();
                out.flush();
                channel.force(true);
            }
            replaceAtomically(stage, target);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(stage);
            } catch (IOException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw ex;
        }
    }

    public static void writeBytes(Path target, byte[] content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path stage = stagePath(target);
        try {
            try (FileChannel channel = FileChannel.open(stage,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                channel.write(java.nio.ByteBuffer.wrap(content));
                channel.force(true);
            }
            replaceAtomically(stage, target);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(stage);
            } catch (IOException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw ex;
        }
    }

    private static Path stagePath(Path target) {
        String name = target.getFileName() == null ? "qf" : target.getFileName().toString();
        Path parent = target.getParent() == null ? Path.of(".") : target.getParent();
        return parent.resolve("." + name + ".tmp." + ProcessHandle.current().pid()
                + "." + Thread.currentThread().getId() + "." + System.nanoTime());
    }

    private static void replaceAtomically(Path stage, Path target) throws IOException {
        try {
            Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
