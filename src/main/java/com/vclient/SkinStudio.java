package com.vclient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** The skin tools on the Wardrobe page: upload, preview, saved presets and a random skin generator. */
final class SkinStudio {
    private final FeatureHost host;
    private final VBox card = UiKit.section("Skin studio",
            "Load a skin file, roll a random one, keep favourites as presets, then apply it to your account.");
    private final ImageView preview = new ImageView();
    private final Label message = ModsUi.text("", 12, ModsUi.MUTED, false);
    private final FlowPane presetFlow = new FlowPane(10, 10);
    private final ComboBox<String> model = new ComboBox<>();
    private final Random random = new Random();
    private BufferedImage current;

    private SkinStudio(FeatureHost host) {
        this.host = host;
    }

    static VBox build(FeatureHost host) {
        SkinStudio studio = new SkinStudio(host);
        studio.assemble();
        return studio.card;
    }

    private void assemble() {
        message.setWrapText(true);
        preview.setFitWidth(128);
        preview.setFitHeight(256);
        preview.setSmooth(false);
        model.getItems().addAll("Classic (wide arms)", "Slim (thin arms)");
        model.setValue(model.getItems().getFirst());

        Button choose = UiKit.button("UPLOAD SKIN FILE...", true, this::chooseFile);
        Button randomSkin = UiKit.button("RANDOM SKIN", false, () -> {
            setSkin(SkinTools.randomSkin(random));
            message.setText("Rolled a new random skin. Roll again, or save it as a preset.");
        });
        Button savePreset = UiKit.button("SAVE AS PRESET", false, this::savePreset);
        Button export = UiKit.button("EXPORT PNG...", false, this::export);
        Button apply = UiKit.button("APPLY TO MY ACCOUNT", true, this::apply);
        VBox buttons = new VBox(10, choose, randomSkin, savePreset, export, ModsUi.sectionTitle("Arm model"), model, apply);
        HBox top = new HBox(24, preview, buttons);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(6, 0, 6, 0));
        card.getChildren().addAll(top, message, ModsUi.sectionTitle("Saved presets"), presetFlow);
        reloadPresets();
        setSkin(SkinTools.randomSkin(random));
        message.setText("Showing a random starter skin until your own loads.");
        // Start with the account's current skin when there is one, otherwise a first random skin.
        PlayerAccount account = host.account();
        if (account != null && !account.skinUrl().isBlank()) {
            host.executor().execute(() -> {
                Image fx = host.downloadImage(account.skinUrl());
                if (fx == null) return;
                try {
                    BufferedImage skin = SkinTools.decode(downloadBytes(account.skinUrl()));
                    Platform.runLater(() -> {
                        if (skin != null && SkinTools.validate(skin) == null) {
                            setSkin(skin);
                            message.setText("Showing the skin saved with " + account.name() + ".");
                        }
                    });
                } catch (IOException e) {
                    // Leave the preview empty.
                }
            });
        }
    }

    private byte[] downloadBytes(String address) throws IOException {
        try (var in = new java.net.URL(address).openStream()) {
            return in.readNBytes(2_000_000);
        }
    }

    private void setSkin(BufferedImage skin) {
        current = skin;
        preview.setImage(toFx(SkinTools.frontPreview(skin, 8)));
    }

    private static Image toFx(BufferedImage image) {
        try {
            return new Image(new ByteArrayInputStream(SkinTools.png(image)));
        } catch (IOException e) {
            return null;
        }
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a skin (PNG)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Skin images", "*.png"));
        File file = chooser.showOpenDialog(host.stage());
        if (file == null) return;
        try {
            BufferedImage skin = SkinTools.read(file.toPath());
            String problem = SkinTools.validate(skin);
            if (problem != null) {
                message.setText(problem);
                return;
            }
            setSkin(skin);
            message.setText("Loaded " + file.getName() + ". Apply it to your account or save it as a preset.");
        } catch (IOException e) {
            message.setText("Could not read that file: " + e.getMessage());
        }
    }

    private void savePreset() {
        if (current == null) {
            message.setText("Load or roll a skin first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("My skin");
        dialog.setHeaderText("Name this preset");
        dialog.setContentText("Name:");
        dialog.initOwner(host.stage());
        dialog.showAndWait().ifPresent(name -> {
            try {
                Path saved = SkinTools.savePreset(SkinTools.presetsFolder(), name, current);
                message.setText("Saved preset " + saved.getFileName() + ".");
                reloadPresets();
            } catch (IOException e) {
                message.setText("Could not save the preset: " + e.getMessage());
            }
        });
    }

    private void export() {
        if (current == null) {
            message.setText("Load or roll a skin first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("skin.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        File file = chooser.showSaveDialog(host.stage());
        if (file == null) return;
        try {
            Files.write(file.toPath(), SkinTools.png(current));
            message.setText("Saved " + file.getName() + ".");
        } catch (IOException e) {
            message.setText("Could not save: " + e.getMessage());
        }
    }

    private void apply() {
        PlayerAccount account = host.account();
        if (current == null) {
            message.setText("Load or roll a skin first.");
            return;
        }
        if (account == null) {
            message.setText("Add an account in Settings first.");
            return;
        }
        switch (account.type()) {
            case MICROSOFT -> {
                BufferedImage skin = current;
                boolean slim = model.getSelectionModel().getSelectedIndex() == 1;
                message.setText("Uploading to your Microsoft account...");
                host.executor().execute(() -> {
                    try {
                        PlayerAccount fresh = host.refreshedAccount();
                        SkinTools.uploadMicrosoft(fresh.accessToken(), skin, slim);
                        Platform.runLater(() -> message.setText("Skin uploaded. It shows in game within a minute "
                                + "(restart the game to see it right away)."));
                    } catch (Exception e) {
                        Platform.runLater(() -> message.setText("Upload failed: " + e.getMessage()));
                    }
                });
            }
            case ELY_BY -> {
                message.setText("Ely.by only accepts skins on its website. Export the PNG and upload it there, then "
                        + "sign in to Ely.by again in Settings to refresh it here.");
                try {
                    Desktop.getDesktop().browse(URI.create("https://ely.by/skins/add"));
                } catch (Exception e) {
                    // The message already tells the player where to go.
                }
            }
            default -> message.setText("Offline accounts have no skin server. Save the skin as a preset or export it; "
                    + "it can be used with a Microsoft or Ely.by account.");
        }
    }

    private void reloadPresets() {
        presetFlow.getChildren().clear();
        List<Path> presets = SkinTools.presets(SkinTools.presetsFolder());
        if (presets.isEmpty()) {
            presetFlow.getChildren().add(UiKit.muted("No presets yet."));
            return;
        }
        for (Path file : presets) {
            try {
                BufferedImage skin = SkinTools.read(file);
                if (SkinTools.validate(skin) != null) continue;
                ImageView thumb = new ImageView(toFx(SkinTools.frontPreview(skin, 3)));
                thumb.setSmooth(false);
                String name = file.getFileName().toString().replaceFirst("\\.png$", "");
                Label label = ModsUi.text(name, 11, ModsUi.TEXT, false);
                label.setMaxWidth(110);
                Button use = UiKit.button("USE", false, () -> {
                    setSkin(skin);
                    message.setText("Loaded preset " + name + ".");
                });
                Button delete = UiKit.dangerButton("✕", () -> deletePreset(file, name));
                VBox box = new VBox(6, thumb, label, UiKit.row(6, use, delete));
                box.setAlignment(Pos.CENTER);
                box.setPadding(new Insets(10));
                box.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 12;");
                presetFlow.getChildren().add(box);
            } catch (IOException e) {
                // Skip an unreadable preset.
            }
        }
    }

    private void deletePreset(Path file, String name) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete preset " + name + "?",
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        alert.initOwner(host.stage());
        alert.showAndWait().filter(button -> button == javafx.scene.control.ButtonType.OK).ifPresent(button -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                message.setText("Could not delete the preset: " + e.getMessage());
            }
            reloadPresets();
        });
    }
}
