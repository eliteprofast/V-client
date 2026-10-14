package com.vclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class AccountStoreTest {
    @TempDir
    Path dataDirectory;

    @Test
    void savesAndLoadsProtectedElyByAccount() throws IOException {
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        AccountStore store = new AccountStore(dataDirectory);
        List<String> progress = new ArrayList<>();
        PlayerAccount account = new PlayerAccount("elyby:0123456789abcdef0123456789abcdef",
                PlayerAccount.Type.ELY_BY, "TestPlayer", "0123456789abcdef0123456789abcdef",
                "access-token", "", "client-token", "", "[]", "", "");

        store.saveProtectedAccounts(List.of(account), progress::add);

        assertTrue(Files.isRegularFile(dataDirectory.resolve("accounts.dat")));
        assertEquals(List.of("Protecting account credentials with Windows DPAPI...",
                "Writing the protected account file..."), progress);
        List<PlayerAccount> loaded = store.load();
        assertEquals(List.of(account), loaded);
    }
}
