package lucheart.zenithexporter.metrics;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ServerListPingTest {
    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; ; shift += 7) {
            byte b = in.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    @Test
    void speaksTheStatusProtocol() throws Exception {
        var json = "{\"players\":{\"online\":5,\"max\":10}}";
        var seenHost = new AtomicReference<String>();
        var seenProtocol = new AtomicReference<Integer>();
        var seenNextState = new AtomicReference<Integer>();
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var thread = Thread.ofVirtual().start(() -> {
                try (var socket = server.accept()) {
                    var in = new DataInputStream(socket.getInputStream());
                    readVarInt(in);
                    assertEquals(0, readVarInt(in));
                    seenProtocol.set(readVarInt(in));
                    seenHost.set(new String(in.readNBytes(readVarInt(in)), StandardCharsets.UTF_8));
                    in.readUnsignedShort();
                    seenNextState.set(readVarInt(in));
                    assertEquals(1, readVarInt(in));
                    assertEquals(0, readVarInt(in));

                    var body = new ByteArrayOutputStream();
                    var data = new DataOutputStream(body);
                    writeVarInt(data, 0);
                    var bytes = json.getBytes(StandardCharsets.UTF_8);
                    writeVarInt(data, bytes.length);
                    data.write(bytes);
                    var out = new DataOutputStream(socket.getOutputStream());
                    writeVarInt(out, body.size());
                    body.writeTo(out);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
            assertEquals(json, ServerListPing.fetch(address, "play.example.com", 3000));
            thread.join();
        }
        assertEquals("play.example.com", seenHost.get());
        assertEquals(-1, seenProtocol.get());
        assertEquals(1, seenNextState.get());
    }

    @Test
    void closedPortThrows() throws Exception {
        int port;
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        assertThrows(IOException.class, () -> ServerListPing.fetch(address, "localhost", 1000));
    }
}
