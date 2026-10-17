package com.vclient.mod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The V-Client mod menu: one card per module. Click a card to switch it on or off, or its
 * gear to open that module's settings.
 */
public final class ModMenuScreen extends Screen {
    private static final int CARD_WIDTH = 120;
    private static final int CARD_HEIGHT = 40;
    private static final int CARD_GAP = 6;
    private static final int BUTTON_HEIGHT = 18;
    private static final int HEADER = 34;
    private static final int GEAR = 14;

    record Area(int x, int y, int width, int height) {
        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    private int columns;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private Area grid;
    private int gridContentHeight;
    private int scroll;
    private Area editButton;
    private Area resetButton;
    private Area doneButton;

    public ModMenuScreen() {
        super(Text.literal("V-Client Mods"));
    }

    @Override
    protected void init() {
        Module[] modules = Module.available();
        columns = Math.max(1, Math.min(3, (width - 40) / (CARD_WIDTH + CARD_GAP)));
        int rows = (modules.length + columns - 1) / columns;
        gridContentHeight = rows * CARD_HEIGHT + (rows - 1) * CARD_GAP;
        panelWidth = columns * CARD_WIDTH + (columns - 1) * CARD_GAP + 24;
        int chrome = HEADER + 12 + BUTTON_HEIGHT + 12;
        panelHeight = Math.min(chrome + gridContentHeight, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        grid = new Area(panelX + 12, panelY + HEADER, panelWidth - 24, panelHeight - chrome);
        scroll = Math.clamp(scroll, 0, maxScroll());

        int buttonY = panelY + panelHeight - 12 - BUTTON_HEIGHT;
        int buttonWidth = (panelWidth - 24 - CARD_GAP * 2) / 3;
        editButton = new Area(panelX + 12, buttonY, buttonWidth, BUTTON_HEIGHT);
        resetButton = new Area(editButton.x() + buttonWidth + CARD_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        doneButton = new Area(resetButton.x() + buttonWidth + CARD_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, gridContentHeight - grid.height());
    }

    private Area card(int index) {
        int column = index % columns;
        int row = index / columns;
        return new Area(grid.x() + column * (CARD_WIDTH + CARD_GAP),
                grid.y() + row * (CARD_HEIGHT + CARD_GAP) - scroll, CARD_WIDTH, CARD_HEIGHT);
    }

    private static Area gear(Area card) {
        return new Area(card.x() + card.width() - GEAR - 3, card.y() + 3, GEAR, GEAR - 2);
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
        String hint = KeyNames.name(VConfig.integer(Module.CLIENT, "menuKey")) + " to close";
        context.drawText(textRenderer, hint, panelX + panelWidth - 12 - textRenderer.getWidth(hint),
                panelY + 14, 0xFF7F7FA6, false);

        boolean overGrid = grid.contains(mouseX, mouseY);
        context.enableScissor(grid.x(), grid.y(), grid.x() + grid.width(), grid.y() + grid.height());
        Module[] modules = Module.available();
        for (int index = 0; index < modules.length; index++) {
            Area area = card(index);
            if (area.y() + area.height() < grid.y() || area.y() > grid.y() + grid.height()) continue;
            drawCard(context, modules[index], area, overGrid ? mouseX : -1, overGrid ? mouseY : -1);
        }
        context.disableScissor();
        if (maxScroll() > 0) {
            int trackX = panelX + panelWidth - 6;
            int thumbHeight = Math.max(16, grid.height() * grid.height() / gridContentHeight);
            int thumbY = grid.y() + (grid.height() - thumbHeight) * scroll / maxScroll();
            context.fill(trackX, grid.y(), trackX + 2, grid.y() + grid.height(), 0x40FFFFFF);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, Hud.ACCENT);
        }

        drawButton(context, editButton, "EDIT HUD", 0xFF292642, mouseX, mouseY);
        drawButton(context, resetButton, "RESET HUD", 0xFF482332, mouseX, mouseY);
        drawButton(context, doneButton, "DONE", Hud.ACCENT, mouseX, mouseY);
    }

    private void drawCard(DrawContext context, Module module, Area area, int mouseX, int mouseY) {
        boolean enabled = VConfig.enabled(module);
        boolean hovered = area.contains(mouseX, mouseY);
        Area gear = gear(area);
        context.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(),
                hovered ? 0xFF24223F : 0xFF1A1933);
        context.drawStrokedRectangle(area.x(), area.y(), area.width(), area.height(),
                enabled ? 0xAAE6BD59 : 0x44AA97CF);
        context.drawText(textRenderer, textRenderer.trimToWidth(module.title(), area.width() - GEAR - 14),
                area.x() + 6, area.y() + 6, 0xFFF3F6FC, false);
        String description = textRenderer.trimToWidth(module.description(), area.width() - 12);
        context.drawText(textRenderer, description, area.x() + 6, area.y() + 17, 0xFF8E8EB2, false);

        boolean gearHovered = gear.contains(mouseX, mouseY);
        context.fill(gear.x(), gear.y(), gear.x() + gear.width(), gear.y() + gear.height(),
                gearHovered ? 0xFF3A3660 : 0xFF292642);
        context.drawText(textRenderer, "⚙", gear.x() + (gear.width() - textRenderer.getWidth("⚙")) / 2,
                gear.y() + 2, gearHovered ? Hud.ACCENT : 0xFFC9C3E6, false);

        String state = module.toggleable() ? (enabled ? "ON" : "OFF") : "SETTINGS";
        int pillWidth = textRenderer.getWidth(state) + 8;
        int pillX = area.x() + area.width() - 6 - pillWidth;
        int pillY = area.y() + area.height() - 13;
        int pillColor = !module.toggleable() ? 0xFF292642 : enabled ? 0xFF244A38 : 0xFF482332;
        int textColor = !module.toggleable() ? Hud.ACCENT : enabled ? 0xFF7CF29A : 0xFFFF8F8F;
        context.fill(pillX, pillY, pillX + pillWidth, pillY + 10, pillColor);
        context.drawText(textRenderer, state, pillX + 4, pillY + 1, textColor, false);
    }

    static void drawButton(DrawContext context, net.minecraft.client.font.TextRenderer font, Area area,
                           String label, int color, int mouseX, int mouseY) {
        context.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), color);
        if (area.contains(mouseX, mouseY)) {
            context.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), 0x30FFFFFF);
        }
        int textColor = color == Hud.ACCENT ? 0xFF171326 : 0xFFF3F0FF;
        context.drawText(font, label, area.x() + (area.width() - font.getWidth(label)) / 2,
                area.y() + (area.height() - 8) / 2, textColor, false);
    }

    private void drawButton(DrawContext context, Area area, String label, int color, int mouseX, int mouseY) {
        drawButton(context, textRenderer, area, label, color, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        double x = click.comp_4798();
        double y = click.comp_4799();
        if (grid.contains(x, y)) {
            Module[] modules = Module.available();
            for (int index = 0; index < modules.length; index++) {
                Area area = card(index);
                if (!area.contains(x, y)) continue;
                Module module = modules[index];
                if (gear(area).contains(x, y) || !module.toggleable()) {
                    client.setScreen(new ModuleSettingsScreen(this, module));
                    return true;
                }
                VConfig.setEnabled(module, !VConfig.enabled(module));
                if (module == Module.TOGGLE_SPRINT || module == Module.TOGGLE_SNEAK) {
                    VClientMod.applyMovementToggles(client);
                }
                return true;
            }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) Math.round(vertical * 24), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == VConfig.integer(Module.CLIENT, "menuKey")) {
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
