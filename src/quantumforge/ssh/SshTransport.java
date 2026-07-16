/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.io.Closeable;
import java.nio.file.Path;

import quantumforge.operation.OperationResult;

/**
 * Strict SSH/SFTP transport contract.
 *
 * <p>Implementations must enforce host-key verification before any transfer or
 * remote command. Fail closed on unknown/changed keys.</p>
 */
public interface SshTransport extends Closeable {

    OperationResult<Void> connect();

    boolean isConnected();

    OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile);

    OperationResult<Void> uploadFile(Path localFile, String remotePath);

    OperationResult<Void> downloadFile(String remotePath, Path localFile);

    OperationResult<Void> mkdirRemote(String remotePath);

    @Override
    void close();
}
