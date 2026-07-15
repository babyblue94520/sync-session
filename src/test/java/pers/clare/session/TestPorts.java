package pers.clare.session;

import java.io.IOException;
import java.net.ServerSocket;

final class TestPorts {

    private TestPorts() {
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate a free port", e);
        }
    }
}
