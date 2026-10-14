package com.vclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class PlayerAccountTest {
    @TempDir
    Path dataDirectory;

    @Test
    void offlineAccountsHaveStableUsernameBasedUuid() {
        PlayerAccount first = PlayerAccount.offline("Example_Player");
        PlayerAccount second = PlayerAccount.offline("Example_Player");

        assertEquals(first.uuid(), second.uuid());
        assertEquals(first.id(), second.id());
        assertEquals(32, first.uuid().length());
    }

    @Test
    void rejectsInvalidOfflineUsernames() {
        assertThrows(IllegalArgumentException.class, () -> PlayerAccount.offline("ab"));
        assertThrows(IllegalArgumentException.class, () -> PlayerAccount.offline("player name"));
    }

    @Test
    void persistsMultipleAccountsWithWindowsUserProtection() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"));
        PlayerAccount premium = new PlayerAccount("microsoft:player", PlayerAccount.Type.MICROSOFT,
                "Player", "0123456789abcdef0123456789abcdef", "access-secret", "refresh-secret",
                "client-token", "xuid", "[]", "", "");
        List<PlayerAccount> accounts = List.of(premium, PlayerAccount.offline("Offline_Player"));
        AccountStore store = new AccountStore(dataDirectory);

        store.save(accounts);

        String protectedFile = Files.readString(dataDirectory.resolve("accounts.dat"), StandardCharsets.US_ASCII);
        assertFalse(protectedFile.contains("access-secret"));
        assertFalse(protectedFile.contains("refresh-secret"));
        assertEquals(accounts, store.load());
    }

    @Test
    void savesOfflineAccountsWithoutWindowsCredentialEncryption() throws Exception {
        PlayerAccount offline = PlayerAccount.offline("Offline_Player");
        AccountStore store = new AccountStore(dataDirectory);

        store.saveOfflineAccounts(List.of(offline));

        assertTrue(Files.exists(dataDirectory.resolve("offline-accounts.json")));
        assertFalse(Files.exists(dataDirectory.resolve("accounts.dat")));
        assertEquals(List.of(offline), store.load());
    }
}
