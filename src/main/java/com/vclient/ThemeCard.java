package com.vclient;

import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.Locale;

/** The colour theme chooser on the Settings page: Blue, Purple, Dark, Red, the default, or a custom RGB accent. */
final class ThemeCard {
    private ThemeCard() {}

    static VBox build(FeatureHost host) {
        LauncherSettings settings = host.settings();
        VBox card = UiKit.section("Theme", "Pick the colours of the launcher. The choice is saved on this PC.");
        FlowPane choices = new FlowPane(10, 10);
        for (Theme.Preset preset : Theme.PRESETS) {
            boolean selected = preset.id().equals(settings.themeId());
            Button button = UiKit.button((selected ? "✓ " : "") + preset.label().toUpperCase(Locale.ROOT), selected, () -> {
                settings.setTheme(preset.id(), settings.customAccent());
                host.saveSettings();
                Theme.apply(preset.id(), settings.customAccent());
                host.themeChanged();
            });
            button.setStyle(button.getStyle() + " -fx-border-color: " + preset.accent() + "; -fx-border-radius: 10;");
            choices.getChildren().add(button);
        }
        ColorPicker picker = new ColorPicker(Color.web(settings.customAccent()));
        Button apply = UiKit.button((Theme.CUSTOM.equals(settings.themeId()) ? "✓ " : "") + "USE CUSTOM RGB",
                Theme.CUSTOM.equals(settings.themeId()), () -> {
                    Color color = picker.getValue();
                    String hex = String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(color.getRed() * 255),
                            Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
                    settings.setTheme(Theme.CUSTOM, hex);
                    host.saveSettings();
                    Theme.apply(Theme.CUSTOM, hex);
                    host.themeChanged();
                });
        card.getChildren().addAll(choices, ModsUi.sectionTitle("Custom RGB accent"), UiKit.row(10, picker, apply));
        return card;
    }
}
