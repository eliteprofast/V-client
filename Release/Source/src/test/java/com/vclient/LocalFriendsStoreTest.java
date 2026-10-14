package com.vclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LocalFriendsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesLoadsAndRemovesFriendNamesLocally() throws Exception {
        LocalFriendsStore store = new LocalFriendsStore(temporaryDirectory.resolve("friends.json"));

        assertEquals(java.util.List.of("Alex"), store.add(" Alex "));
        assertEquals(java.util.List.of("Alex"), store.load());
        assertEquals(java.util.List.of(), store.remove("alex"));
        assertEquals(java.util.List.of(), store.load());
    }

    @Test
    void rejectsDuplicateAndInvalidFriendNames() throws Exception {
        LocalFriendsStore store = new LocalFriendsStore(temporaryDirectory.resolve("friends.json"));
        store.add("Alex");

        assertThrows(IOException.class, () -> store.add("alex"));
        assertThrows(IOException.class, () -> store.add("two words"));
    }
}
