package com.vclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaRuntimeManagerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void selectsTheOfficialAzul64BitJreAndRejectsFxOrOtherHosts() throws Exception {
        var packages = JSON.readTree("""
                [
                  {"product":"zulu","java_version":[21,0,12,1],"name":"zulu21-fx-jre-win_x64.zip","download_url":"https://cdn.azul.com/fx.zip"},
                  {"product":"zulu","java_version":[21,0,12,1],"name":"zulu21-jre-win_i686.zip","download_url":"https://cdn.azul.com/x86.zip"},
                  {"product":"other","java_version":[21,0,12,1],"name":"other-jre-win_x64.zip","download_url":"https://cdn.azul.com/other.zip"},
                  {"product":"zulu","java_version":[21,0,12,1],"name":"zulu21-ca-jre-win_x64.zip","download_url":"https://cdn.azul.com/zulu.zip"},
                  {"product":"zulu","java_version":[21,0,12,1],"name":"zulu21-ca-jre-win_x64.zip","download_url":"https://cdn.azul.com.evil.invalid/zulu.zip"}
                ]
                """);

        assertEquals("https://cdn.azul.com/zulu.zip", JavaRuntimeManager.selectDownloadUrl(packages, 21));
    }

    @Test
    void selectsTheOfficialJava25Runtime() throws Exception {
        var packages = JSON.readTree("""
                [
                  {"product":"zulu","java_version":[25,0,1],"name":"zulu25-fx-jre-win_x64.zip","download_url":"https://cdn.azul.com/fx25.zip"},
                  {"product":"zulu","java_version":[25,0,1],"name":"zulu25-ca-jre-win_x64.zip","download_url":"https://cdn.azul.com/zulu25.zip"}
                ]
                """);

        assertEquals("https://cdn.azul.com/zulu25.zip",
                JavaRuntimeManager.selectDownloadUrl(packages, 25));
    }

    @Test
    void rejectsRuntimePackagesFromUntrustedUrls() {
        ObjectNode packageInfo = JSON.createObjectNode().put("product", "zulu");
        packageInfo.putArray("java_version").add(21);
        packageInfo.put("name", "zulu21-ca-jre-win_x64.zip")
                .put("download_url", "http://cdn.azul.com/zulu.zip");
        var packages = JSON.createArrayNode().add(packageInfo);

        IOException error = assertThrows(IOException.class,
                () -> JavaRuntimeManager.selectDownloadUrl(packages, 21));

        assertTrue(error.getMessage().contains("64-bit Windows Zulu JRE"));
    }

    @Test
    void explainsTlsFailuresWithoutDisablingCertificateValidation() {
        IOException error = JavaRuntimeManager.explainTlsFailure(
                "https://piston-meta.mojang.com/version_manifest.json",
                new IOException("request failed", new SSLHandshakeException("handshake_failure")));

        assertTrue(error.getMessage().contains("piston-meta.mojang.com"));
        assertTrue(error.getMessage().contains("will not disable certificate validation"));
    }
}
