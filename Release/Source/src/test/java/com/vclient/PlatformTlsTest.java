package com.vclient;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTlsTest {
    @Test
    void clientEnablesTls12AndTls13() {
        assertEquals(List.of("TLSv1.3", "TLSv1.2"),
                List.of(PlatformTls.secureProtocolParameters().getProtocols()));
    }

    @Test
    void identifiesCertificateHostnameMismatchSeparately() {
        SSLHandshakeException failure = new SSLHandshakeException("certificate validation failed");
        failure.initCause(new java.security.cert.CertificateException(
                "No subject alternative DNS name matching piston-meta.mojang.com found."));

        assertTrue(PlatformTls.hasCertificateFailure(failure));
        assertTrue(PlatformTls.hasHostnameMismatch(failure));
    }

    @Test
    void retriesTransientHttpResponsesAndLogsAttempts() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                try {
                    for (int attempt = 1; attempt <= 2; attempt++) {
                        try (Socket socket = server.accept();
                             BufferedReader input = new BufferedReader(new InputStreamReader(
                                     socket.getInputStream(), StandardCharsets.US_ASCII))) {
                            String line;
                            while ((line = input.readLine()) != null && !line.isEmpty()) {
                            }
                            String response = attempt == 1
                                    ? "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n"
                                    + "Retry-After: 0\r\nConnection: close\r\n\r\n"
                                    : "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n"
                                    + "Connection: close\r\n\r\nready";
                            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                            socket.getOutputStream().flush();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "platform-tls-test-server");
            responder.setDaemon(true);
            responder.start();

            URI endpoint = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/retry");
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            List<String> logs = new ArrayList<>();
            HttpResponse<String> response = PlatformTls.sendWithRetries(
                    client, request, HttpResponse.BodyHandlers.ofString(), logs::add);
            responder.join(3_000);

            assertEquals(200, response.statusCode());
            assertTrue(!responder.isAlive());
            assertTrue(logs.stream().anyMatch(line -> line.contains("retrying a transient server response")));
        }
    }
}
