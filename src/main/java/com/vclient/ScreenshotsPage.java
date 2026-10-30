package com.vclient;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** The Screenshots tab: a gallery that follows the game's screenshots folder, full-screen preview, delete and share. */
final class ScreenshotsPage {
    private static final double CARD_WIDTH = 220;
    private static final double CARD_HEIGHT = 124;

    private final FeatureHost host;
    private final VBox root = new VBox(18);
    private final FlowPane gallery = new FlowPane(14, 14);
    private final Label notice = ModsUi.text("", 12, ModsUi.GOOD, false);
    private final Label count = ModsUi.chip("", ModsUi.MUTED);
    private String fingerprint = "";

    private ScreenshotsPage(FeatureHost host) {
        this.host = host;
    }

    static VBox build(FeatureHost host) {
        ScreenshotsPage page = new ScreenshotsPage(host);
        page.assemble();
        return page.root;
    }

    private Path gameDirectory() {
        return host.settings().minecraftDirectory();
    }

    private void assemble() {
        VBox card = UiKit.section("Gallery", "Screenshots from the game (press F2 in Minecraft) show up here by themselves.");
        Button open = UiKit.button("OPEN SCREENSHOTS FOLDER", false, () -> {
            try {
                java.nio.file.Files.createDirectories(ScreenshotLibrary.folder(gameDirectory()));
                Desktop.getDesktop().open(ScreenshotLibrary.folder(gameDirectory()).toFile());
            } catch (IOException | UnsupportedOperationException e) {
                notice.setText("Could not open the folder: " + e.getMessage());
            }
        });
        card.getChildren().addAll(UiKit.row(10, count, UiKit.grow(), open), notice, gallery);
        root.getChildren().add(card);
        reload();
        UiKit.poll(host, root, 3, () -> {
            if (!fingerprint.equals(ScreenshotLibrary.fingerprint(gameDirectory()))) reload();
        });
    }

    private void reload() {
        List<ScreenshotLibrary.Screenshot> shots = ScreenshotLibrary.list(gameDirectory());
        fingerprint = ScreenshotLibrary.fingerprint(gameDirectory());
        gallery.getChildren().clear();
        count.setText(shots.size() + (shots.size() == 1 ? " screenshot" : " screenshots"));
        if (shots.isEmpty()) {
            gallery.getChildren().add(ModsUi.emptyCard("▣", "No screenshots yet",
                    "Press F2 in Minecraft and the picture appears here."));
            return;
        }
        for (int i = 0; i < shots.size(); i++) gallery.getChildren().add(card(shots, i));
    }

    private VBox card(List<ScreenshotLibrary.Screenshot> shots, int index) {
        ScreenshotLibrary.Screenshot shot = shots.get(index);
        // Decode at thumbnail size in the background so a folder of 4K screenshots stays light.
        Image image = new Image(shot.file().toUri().toString(), CARD_WIDTH * 2, CARD_HEIGHT * 2, true, true, true);
        ImageView view = new ImageView(image);
        view.setFitWidth(CARD_WIDTH);
        view.setFitHeight(CARD_HEIGHT);
        view.setPreserveRatio(true);
        StackPane frame = new StackPane(view);
        frame.setMinSize(CARD_WIDTH, CARD_HEIGHT);
        frame.setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        frame.setStyle("-fx-background-color: rgba(0,0,0,0.45); -fx-background-radius: 8; -fx-cursor: hand;");
        frame.setOnMouseClicked(event -> preview(shots, index));
        Label name = ModsUi.text(shot.file().getFileName().toString(), 10, ModsUi.MUTED, false);
        name.setMaxWidth(CARD_WIDTH);
        Button share = UiKit.button("SHARE", true, () -> share(shot.file()));
        Button delete = UiKit.dangerButton("DELETE", () -> delete(shot.file()));
        VBox card = new VBox(8, frame, name, UiKit.row(8, share, delete));
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-background-radius: 12;");
        return card;
    }

    private void share(Path file) {
        // Put the picture itself on the clipboard (paste into Discord, a browser or a chat) and offer the file path.
        Image full = new Image(file.toUri().toString());
        ClipboardContent content = new ClipboardContent();
        content.putImage(full);
        content.putFiles(List.of(new File(file.toString())));
        Clipboard.getSystemClipboard().setContent(content);
        notice.setText("Copied " + file.getFileName() + " - paste it into Discord or any chat to share it.");
    }

    private void delete(Path file) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + file.getFileName() + " from your PC?",
                ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText("Delete screenshot");
        alert.initOwner(host.stage());
        alert.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            notice.setText(ScreenshotLibrary.delete(gameDirectory(), file)
                    ? "Deleted " + file.getFileName() + "." : "Could not delete " + file.getFileName() + ".");
            reload();
        });
    }

    private void preview(List<ScreenshotLibrary.Screenshot> shots, int start) {
        Stage stage = new Stage();
        stage.initOwner(host.stage());
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("Esc to close  -  arrow keys to browse  -  Delete to remove");
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        StackPane layout = new StackPane(view);
        layout.setStyle("-fx-background-color: black;");
        view.fitWidthProperty().bind(layout.widthProperty());
        view.fitHeightProperty().bind(layout.heightProperty());
        Label caption = ModsUi.text("", 13, "#ffffff", false);
        StackPane.setAlignment(caption, Pos.BOTTOM_CENTER);
        StackPane.setMargin(caption, new Insets(0, 0, 18, 0));
        layout.getChildren().add(caption);
        int[] current = {start};
        List<Path> files = new java.util.ArrayList<>(shots.stream().map(ScreenshotLibrary.Screenshot::file).toList());
        Runnable show = () -> {
            if (files.isEmpty()) {
                stage.close();
                return;
            }
            current[0] = Math.floorMod(current[0], files.size());
            view.setImage(new Image(files.get(current[0]).toUri().toString(), 0, 0, true, true));
            caption.setText(files.get(current[0]).getFileName() + "   (" + (current[0] + 1) + "/" + files.size() + ")");
            caption.setStyle(caption.getStyle() + " -fx-background-color: rgba(0,0,0,0.6); -fx-padding: 4 10 4 10;");
        };
        Scene scene = new Scene(layout);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) stage.close();
            else if (event.getCode() == KeyCode.RIGHT) {
                current[0]++;
                show.run();
            } else if (event.getCode() == KeyCode.LEFT) {
                current[0]--;
                show.run();
            } else if (event.getCode() == KeyCode.DELETE) {
                Path file = files.get(current[0]);
                if (ScreenshotLibrary.delete(gameDirectory(), file)) {
                    files.remove(current[0]);
                    show.run();
                }
            }
        });
        layout.setOnMouseClicked(event -> {
            current[0]++;
            show.run();
        });
        stage.setScene(scene);
        stage.setOnHidden(event -> reload());
        show.run();
        stage.show();
    }
}
