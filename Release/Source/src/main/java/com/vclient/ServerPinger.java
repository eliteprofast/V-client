package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

/** Pings Java Edition servers directly with the server list protocol, the same way the multiplayer screen does. */
final class ServerPinger {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int TIMEOUT_MS = 5000;
    private static final int PROTOCOL_VERSION = 767;

    record Result(boolean online, int players, int maxPlayers, String version, String motd, long latencyMs, String error) {
        static Result offline(String error) {
            return new Result(false, 0, 0, "", "", 0, error);
        }
    }

    private record Target(String host, int port) {}

    private ServerPinger() {
    }

    /** Never throws: an unreachable server comes back as an offline result with a short reason. */
    static Result ping(String host, int port) {
        Target target = resolve(host, port);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(DnsBlock.bypass(target.host()), target.port()), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(handshake);
            writeVarInt(body, 0x00);
            writeVarInt(body, PROTOCOL_VERSION);
            writeString(body, host);
            body.writeShort(port);
            writeVarInt(body, 1);
            writePacket(out, handshake.toByteArray());
            writePacket(out, new byte[]{0x00});

            readVarInt(in);
            if (readVarInt(in) != 0x00) return Result.offline("unexpected reply");
            byte[] jsonBytes = new byte[readVarInt(in)];
            in.readFully(jsonBytes);
            JsonNode status = JSON.readTree(new String(jsonBytes, StandardCharsets.UTF_8));

            long sent = System.nanoTime();
            ByteArrayOutputStream ping = new ByteArrayOutputStream();
            DataOutputStream pingBody = new DataOutputStream(ping);
            writeVarInt(pingBody, 0x01);
            pingBody.writeLong(sent);
            writePacket(out, ping.toByteArray());
            long latency;
            try {
                readVarInt(in);
                readVarInt(in);
                in.readLong();
                latency = (System.nanoTime() - sent) / 1_000_000;
            } catch (IOException e) {
                latency = -1; // Some proxies close the connection instead of answering the ping.
            }

            return new Result(true, status.path("players").path("online").asInt(),
                    status.path("players").path("max").asInt(),
                    plainText(status.path("version").path("name")), plainText(status.path("description")).strip(),
                    latency, "");
        } catch (IOException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Result.offline(reason);
        }
    }

    /** Looks up the _minecraft._tcp SRV record when the default port is used, like the game client does. */
    private static Target resolve(String host, int port) {
        if (port != 25565) return new Target(host, port);
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            javax.naming.directory.DirContext context = new javax.naming.directory.InitialDirContext(env);
            try {
                javax.naming.directory.Attribute srv = context
                        .getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("SRV");
                if (srv != null && srv.size() > 0) {
                    String[] parts = srv.get(0).toString().split(" ");
                    String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                    return new Target(target, Integer.parseInt(parts[2]));
                }
            } finally {
                context.close();
            }
        } catch (Exception | LinkageError ignored) {
            // No SRV record (or no DNS provider): connect to the name itself.
        }
        return new Target(host, port);
    }

    /** Flattens a chat component (string, {"text":…,"extra":[…]}) into plain text without § colour codes. */
    private static String plainText(JsonNode node) {
        StringBuilder text = new StringBuilder();
        appendText(node, text);
        return text.toString().replaceAll("§.", "");
    }

    private static void appendText(JsonNode node, StringBuilder text) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isTextual()) {
            text.append(node.asText());
        } else if (node.isArray()) {
            for (JsonNode child : node) appendText(child, text);
        } else if (node.isObject()) {
            text.append(node.path("text").asText(""));
            for (JsonNode child : node.path("extra")) appendText(child, text);
        }
    }

    private static void writePacket(DataOutputStream out, byte[] packet) throws IOException {
        writeVarInt(out, packet.length);
        out.write(packet);
        out.flush();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
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
