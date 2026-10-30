package com.vclient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The VSMP Hub: announcements, patch notes, friends and an activity feed in one place. */
final class HubPage {
    private final FeatureHost host;
    private final VBox root = new VBox(18);

    private HubPage(FeatureHost host) {
        this.host = host;
    }

    static VBox build(FeatureHost host) {
        HubPage page = new HubPage(host);
        page.root.getChildren().addAll(page.announcements(), page.friends(), page.patchNotes(), page.activity());
        return page.root;
    }

    private BackendClient client() {
        return host.backend();
    }

    private boolean online() {
        return client().configured() && !host.settings().offlineMode();
    }

    // ---- announcements -----------------------------------------------------------------------------

    private VBox announcements() {
        VBox card = UiKit.section("Announcements", null);
        VBox body = new VBox(8, UiKit.muted(online() ? "Loading..." : "Turn off Offline mode to load announcements."));
        card.getChildren().add(body);
        if (online()) {
            host.executor().execute(() -> {
                try {
                    List<BackendClient.Announcement> items = client().announcements();
                    Platform.runLater(() -> {
                        body.getChildren().clear();
                        if (items.isEmpty()) body.getChildren().add(UiKit.muted("No announcements right now."));
                        for (BackendClient.Announcement item : items) {
                            String color = switch (item.level()) {
                                case "warning" -> ModsUi.WARNING;
                                case "critical", "error" -> "#ff9aa5";
                                default -> ModsUi.TEXT;
                            };
                            Label text = ModsUi.text("📢  " + item.message(), 13, color, false);
                            text.setWrapText(true);
                            body.getChildren().add(text);
                        }
                    });
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    Platform.runLater(() -> body.getChildren().setAll(UiKit.muted("Could not load: " + e.getMessage())));
                }
            });
        }
        return card;
    }

    // ---- friends -----------------------------------------------------------------------------------

    private VBox friends() {
        VBox card = UiKit.section("Friends", null);
        String token = host.settings().backendToken();
        VBox body = new VBox(8);
        Button open = UiKit.button("OPEN FRIENDS", false, host::openFriends);
        if (!online() || token.isBlank()) {
            body.getChildren().add(UiKit.muted(online() ? "Sign in to a V-Client account on the News page to see your friends."
                    : "Turn off Offline mode to see your friends."));
        } else {
            body.getChildren().add(UiKit.muted("Loading..."));
            host.executor().execute(() -> {
                try {
                    BackendClient.FriendsOverview overview = client().friends(token);
                    Platform.runLater(() -> {
                        body.getChildren().clear();
                        long onlineCount = overview.friends().stream().filter(BackendClient.Friend::online).count();
                        body.getChildren().add(UiKit.muted(onlineCount + " of " + overview.friends().size()
                                + " friends online" + (overview.incoming().isEmpty() ? ""
                                : "  ·  " + overview.incoming().size() + " request(s) waiting")));
                        for (BackendClient.Friend friend : overview.friends()) {
                            if (!friend.online()) continue;
                            body.getChildren().add(ModsUi.text("● " + friend.username() + "  -  " + friend.activity()
                                    + (friend.server().isBlank() ? "" : " on " + friend.server()), 13, ModsUi.GOOD, false));
                        }
                    });
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    Platform.runLater(() -> body.getChildren().setAll(UiKit.muted("Could not load: " + e.getMessage())));
                }
            });
        }
        card.getChildren().addAll(body, open);
        return card;
    }

    // ---- patch notes -------------------------------------------------------------------------------

    private VBox patchNotes() {
        VBox card = UiKit.section("Patch notes", "Notes of the newest V-Client release (you have " + AppInfo.VERSION + ").");
        VBox body = new VBox(8, UiKit.muted(online() ? "Loading..." : "Turn off Offline mode to load patch notes."));
        card.getChildren().add(body);
        if (online()) {
            host.executor().execute(() -> {
                try {
                    BackendClient.UpdateInfo info = client().checkForUpdate(AppInfo.VERSION);
                    Platform.runLater(() -> {
                        body.getChildren().clear();
                        if (info.latest() == null) {
                            body.getChildren().add(UiKit.muted("No release notes published yet."));
                            return;
                        }
                        body.getChildren().add(ModsUi.text("Version " + info.latest().version()
                                + (info.updateAvailable() ? "  -  update available" : "  -  you are up to date"), 14,
                                info.updateAvailable() ? ModsUi.ACCENT : ModsUi.GOOD, true));
                        body.getChildren().add(UiKit.muted(info.latest().notes().isBlank()
                                ? "No notes for this release." : info.latest().notes()));
                    });
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    Platform.runLater(() -> body.getChildren().setAll(UiKit.muted("Could not load: " + e.getMessage())));
                }
            });
        }
        return card;
    }

    // ---- activity feed -----------------------------------------------------------------------------

    private VBox activity() {
        VBox card = UiKit.section("Activity feed", "What you have done in V-Client lately. Chat is not available yet: "
                + "it needs a chat service on the VSMP backend, which does not exist.");
        List<ActivityLog.Entry> entries = new ArrayList<>(host.activity().recent(25));
        if (entries.isEmpty()) {
            card.getChildren().add(UiKit.muted("Nothing yet. Launch the game, save a clip or make a backup."));
            return card;
        }
        for (ActivityLog.Entry entry : entries) {
            HBox row = new HBox(12, ModsUi.text(UiKit.date(entry.epochMillis()), 11, ModsUi.MUTED, false),
                    ModsUi.text(entry.text(), 13, ModsUi.TEXT, false));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-background-radius: 10;");
            card.getChildren().add(row);
        }
        return card;
    }
}
