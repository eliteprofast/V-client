package com.vclient.mod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The Right Shift mod menu: one card per module, click to toggle. */
public final class ModMenuScreen extends Screen {
    private static final int CARD_WIDTH = 120;
    private static final int CARD_HEIGHT = 40;
    private static final int CARD_GAP = 6;
    private static final int BUTTON_HEIGHT = 18;

    private record Area(int x, int y, int width, int height) {
        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    private final List<Area> cards = new ArrayList<>();
    private Area editButton;
    private Area resetButton;
    private Area doneButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public ModMenuScreen() {
        super(Text.literal("V-Client Mods"));
    }

    @Override
    protected void init() {
        Module[] modules = Module.values();
        int columns = Math.max(1, Math.min(3, (width - 40) / (CARD_WIDTH + CARD_GAP)));
        int rows = (modules.length + columns - 1) / columns;
        panelWidth = columns * CARD_WIDTH + (columns - 1) * CARD_GAP + 24;
        panelHeight = 34 + rows * CARD_HEIGHT + (rows - 1) * CARD_GAP + 12 + BUTTON_HEIGHT + 12;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, (height - panelHeight) / 2);

        cards.clear();
        for (int index = 0; index < modules.length; index++) {
            int column = index % columns;
            int row = index / columns;
            cards.add(new Area(panelX + 12 + column * (CARD_WIDTH + CARD_GAP),
                    panelY + 34 + row * (CARD_HEIGHT + CARD_GAP), CARD_WIDTH, CARD_HEIGHT));
        }
        int buttonY = panelY + panelHeight - 12 - BUTTON_HEIGHT;
        int buttonWidth = (panelWidth - 24 - CARD_GAP * 2) / 3;
        editButton = new Area(panelX + 12, buttonY, buttonWidth, BUTTON_HEIGHT);
        resetButton = new Area(editButton.x() + buttonWidth + CARD_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        doneButton = new Area(resetButton.x() + buttonWidth + CARD_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90080918);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xEE0F1027);
        context.drawStrokedRectangle(panelX, panelY, panelWidth, panelHeight, 0x66AA97CF);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(panelX + 12, panelY + 10);
        context.getMatrices().scale(1.5f, 1.5f);
        context.drawText(textRenderer, "V-CLIENT", 0, 0, Hud.ACCENT, true);
        context.getMatrices().popMatrix();
        context.drawText(textRenderer, "MODS", panelX + 12 + (int) (textRenderer.getWidth("V-CLIENT") * 1.5f) + 6,
                panelY + 14, Hud.MUTED, false);
        String hint = "Right Shift to close";
        context.drawText(textRenderer, hint, panelX + panelWidth - 12 - textRenderer.getWidth(hint),
                panelY + 14, 0xFF7F7FA6, false);

        Module[] modules = Module.values();
        for (int index = 0; index < modules.length; index++) {
            drawCard(context, modules[index], cards.get(index), mouseX, mouseY);
        }
        drawButton(context, editButton, "EDIT HUD", 0xFF292642, mouseX, mouseY);
        drawButton(context, resetButton, "RESET HUD", 0xFF482332, mouseX, mouseY);
        drawButton(context, doneButton, "DONE", Hud.ACCENT, mouseX, mouseY);
    }

    private void drawCard(DrawContext context, Module module, Area area, int mouseX, int mouseY) {
        boolean enabled = VConfig.enabled(module);
        boolean hovered = area.contains(mouseX, mouseY);
        context.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(),
                hovered ? 0xFF24223F : 0xFF1A1933);
        context.drawStrokedRectangle(area.x(), area.y(), area.width(), area.height(),
                enabled ? 0xAAE6BD59 : 0x44AA97CF);
        context.drawText(textRenderer, module.title(), area.x() + 6, area.y() + 6, 0xFFF3F6FC, false);
        String description = textRenderer.trimToWidth(module.description(), area.width() - 12);
        context.drawText(textRenderer, description, area.x() + 6, area.y() + 17, 0xFF8E8EB2, false);
        String state = enabled ? "ON" : "OFF";
        int pillWidth = textRenderer.getWidth(state) + 8;
        int pillX = area.x() + area.width() - 6 - pillWidth;
        int pillY = area.y() + area.height() - 13;
        context.fill(pillX, pillY, pillX + pillWidth, pillY + 10, enabled ? 0xFF244A38 : 0xFF482332);
        context.drawText(textRenderer, state, pillX + 4, pillY + 1, enabled ? 0xFF7CF29A : 0xFFFF8F8F, false);
    }

    private void drawButton(DrawContext context, Area area, String label, int color, int mouseX, int mouseY) {
        boolean hovered = area.contains(mouseX, mouseY);
        context.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), color);
        if (hovered) {
            context.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), 0x30FFFFFF);
        }
        int textColor = color == Hud.ACCENT ? 0xFF171326 : 0xFFF3F0FF;
        context.drawText(textRenderer, label, area.x() + (area.width() - textRenderer.getWidth(label)) / 2,
                area.y() + (area.height() - 8) / 2, textColor, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        double x = click.comp_4798();
        double y = click.comp_4799();
        Module[] modules = Module.values();
        for (int index = 0; index < modules.length; index++) {
            if (!cards.get(index).contains(x, y)) continue;
            Module module = modules[index];
            VConfig.setEnabled(module, !VConfig.enabled(module));
            if (module == Module.TOGGLE_SPRINT || module == Module.TOGGLE_SNEAK) {
                VClientMod.applyMovementToggles(client);
            }
            return true;
        }
        if (editButton.contains(x, y)) {
            client.setScreen(new HudEditorScreen(this));
            return true;
        }
        if (resetButton.contains(x, y)) {
            VConfig.resetPositions();
            return true;
        }
        if (doneButton.contains(x, y)) {
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
