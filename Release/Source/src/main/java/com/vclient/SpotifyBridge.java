package com.vclient;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Spotify without an account login: the desktop app puts "Artist - Song" in its window title while a track plays,
 * and the system media keys control playback. Nothing is sent to Spotify and no Spotify account is needed.
 * It works with the Spotify desktop app on Windows (not the web player).
 */
final class SpotifyBridge {
    enum State { NOT_RUNNING, PAUSED, PLAYING }

    record Now(State state, String artist, String title) {
        String display() {
            return switch (state) {
                case NOT_RUNNING -> "Spotify is not running";
                case PAUSED -> "Nothing playing (paused)";
                case PLAYING -> artist.isBlank() ? title : artist + " - " + title;
            };
        }
    }

    private SpotifyBridge() {}

    static Now current() {
        if (!WinInput.available()) return new Now(State.NOT_RUNNING, "", "");
        List<String> titles = new ArrayList<>();
        try {
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true;
                if (WinInput.windowClass(hwnd).equals("Chrome_WidgetWin_1") && belongsToSpotify(hwnd)) {
                    titles.add(WinInput.windowTitle(hwnd));
                }
                return true;
            }, null);
        } catch (Throwable e) {
            return new Now(State.NOT_RUNNING, "", "");
        }
        return fromSpotifyTitles(titles);
    }

    private static boolean belongsToSpotify(com.sun.jna.platform.win32.WinDef.HWND hwnd) {
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        WinNT.HANDLE process = Kernel32.INSTANCE.OpenProcess(0x1000, false, pid.getValue());
        if (process == null) return false;
        try {
            char[] buffer = new char[1024];
            IntByReference size = new IntByReference(buffer.length);
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, size)) return false;
            return new String(buffer, 0, size.getValue()).toLowerCase(java.util.Locale.ROOT).endsWith("\\spotify.exe");
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }

    /** Interprets the titles of Spotify's own windows. Exposed for tests. */
    static Now fromSpotifyTitles(List<String> titles) {
        boolean open = false;
        for (String title : titles) {
            String trimmed = title.strip();
            if (trimmed.isEmpty()) continue;
            open = true;
            if (trimmed.startsWith("Spotify")) continue;
            int dash = trimmed.indexOf(" - ");
            if (dash > 0 && dash < trimmed.length() - 3) {
                return new Now(State.PLAYING, trimmed.substring(0, dash).strip(), trimmed.substring(dash + 3).strip());
            }
            // An advert or a title without an artist.
            return new Now(State.PLAYING, "", trimmed);
        }
        return new Now(open ? State.PAUSED : State.NOT_RUNNING, "", "");
    }

    static void playPause() {
        WinInput.playPause();
    }

    static void next() {
        WinInput.nextTrack();
    }

    static void previous() {
        WinInput.previousTrack();
    }
}
