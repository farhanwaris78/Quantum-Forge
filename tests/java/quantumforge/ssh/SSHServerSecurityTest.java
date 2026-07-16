package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import quantumforge.operation.OperationStatus;

class SSHServerSecurityTest {
    @Test
    void passwordIsSessionOnlyAndNeverSerializedByGson() {
        SSHServer server = new SSHServer("cluster");
        server.setHost("cluster.example");
        server.setUser("researcher");
        server.setPassword("do-not-write-this");

        String json = new Gson().toJson(server);
        assertFalse(json.contains("do-not-write-this"));
        assertFalse(json.contains("password"));
        SSHServer restored = new Gson().fromJson(json, SSHServer.class);
        assertNull(restored.getPassword());
    }

    @Test
    void unfinishedRemoteOperationsReturnTypedUnsupportedStatus() {
        SSHFileTransfer transfer = new SSHFileTransfer(new SSHServer("cluster"));
        assertEquals(OperationStatus.UNSUPPORTED,
                transfer.downloadAllFilesResult("/remote", "/local").getStatus());
        assertFalse(transfer.downloadAllFiles("/remote", "/local"));
        assertEquals(OperationStatus.UNSUPPORTED,
                transfer.deleteAllOnServerResult("/remote").getStatus());
    }
}
