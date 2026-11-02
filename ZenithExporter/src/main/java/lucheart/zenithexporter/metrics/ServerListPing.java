package lucheart.zenithexporter.metrics;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * The status request a client makes for the server list. Zenith's Pinger isn't public, and it sends the resolved
 * address as the host, where proxies in front of a server (TCPShield, Velocity forced hosts) go by the name used.
 */
final class ServerListPing {
    private static final int MAX_RESPONSE_BYTES = 1 << 20;
    // what clients send when they don't know the server's version yet
    private static final int PROTOCOL_VERSION_DISCOVERY = -1;

    private ServerListPing() {}

    static String fetch(InetSocketAddress address, String hostname, int timeoutMs) throws IOException {
        try (var socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(timeoutMs);
            socket.connect(address, timeoutMs);
            var out = new DataOutputStream(socket.getOutputStream());
            var in = new DataInputStream(socket.getInputStream());

            var handshake = new ByteArrayOutputStream();
            var data = new DataOutputStream(handshake);
            data.writeByte(0x00);
            writeVarInt(data, PROTOCOL_VERSION_DISCOVERY);
            var host = hostname.getBytes(StandardCharsets.UTF_8);
            writeVarInt(data, host.length);
            data.write(host);
            data.writeShort(address.getPort());
            writeVarInt(data, 1);
            writeVarInt(out, handshake.size());
            handshake.writeTo(out);
            // empty status request
            out.writeByte(1);
            out.writeByte(0x00);
            out.flush();

            readVarInt(in);
            readVarInt(in);
            int length = readVarInt(in);
            if (length <= 0 || length > MAX_RESPONSE_BYTES) throw new IOException("Unexpected status length: " + length);
            var response = new byte[length];
            in.readFully(response);
            return new String(response, StandardCharsets.UTF_8);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = in.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }
}
