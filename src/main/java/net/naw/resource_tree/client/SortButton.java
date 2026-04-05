package net.naw.resource_tree.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.naw.resource_tree.mixin.PackScreenAccessor;

// --- SORT BUTTON ---
// A small "⇅" button that opens a mini sort menu.
// Positioned to the left of the search bar.
public class SortButton extends ClickableWidget {

    // --- STATE ---
    // Tracks if the dropdown menu is currently visible
    private boolean isOpen = false;

    // --- LABELS ---
    // The display text shown for each option in the menu
    private static final String[] LABELS = {
            "Name (A-Z)",
            "Name (Z-A)",
            "Date (Newest)",
            "Date (Oldest)"
    };

    // --- MODES ---
    // The actual config settings that match the labels above
    private static final ResourceTreeConfig.SortMode[] MODES = {
            ResourceTreeConfig.SortMode.NAME_ASC,
            ResourceTreeConfig.SortMode.NAME_DESC,
            ResourceTreeConfig.SortMode.DATE_NEW,
            ResourceTreeConfig.SortMode.DATE_OLD
    };

    // --- DIMENSIONS ---
    // Sizing for the menu items and the main button icon
    private static final int ITEM_HEIGHT = 16;
    private static final int MENU_WIDTH = 90;
    private static final int PADDING = 4;
    private static final int BTN_W = 14;
    private static final int BTN_H = 14;

    public SortButton(int x, int y) {
        super(x, y, BTN_W, BTN_H, Text.of("⇅"));
    }

    // --- HITBOX ---
    // Detects if the mouse is over the icon or the open menu
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        final boolean overButton = mouseX >= getX() && mouseX < getX() + BTN_W
                && mouseY >= getY() && mouseY < getY() + BTN_H;
        if (!isOpen) return overButton;

        final int menuHeight = LABELS.length * ITEM_HEIGHT + PADDING * 2;
        final int menuY = getY() + BTN_H + 2;
        final boolean overMenu = mouseX >= getX() && mouseX < getX() + MENU_WIDTH
                && mouseY >= menuY && mouseY < menuY + menuHeight;

        return overButton || overMenu;
    }

    // --- CLICK HANDLING ---
    // Logic for toggling the menu and selecting sort options
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0) return false;

        // Close menu if user clicks anywhere outside of it
        if (!isMouseOver(mouseX, mouseY)) {
            if (isOpen) { isOpen = false; return true; }
            return false;
        }

        final boolean overButton = mouseX >= getX() && mouseX < getX() + BTN_W
                && mouseY >= getY() && mouseY < getY() + BTN_H;

        // Toggle menu open/closed when the ⇅ icon is clicked
        if (overButton) {
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            this.isOpen = !this.isOpen;
            return true;
        }

        // Logic for when the menu is open and an item is clicked
        if (isOpen) {
            final int menuHeight = LABELS.length * ITEM_HEIGHT + PADDING * 2;
            final int menuY = getY() + BTN_H + 2;

            if (mouseY >= menuY + PADDING && mouseY < menuY + menuHeight - PADDING) {
                int index = (int) ((mouseY - (menuY + PADDING)) / ITEM_HEIGHT);
                if (index >= 0 && index < MODES.length) {
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    ResourceTreeConfig.sortMode = MODES[index];
                    ResourceTreeConfig.save();

                    // Instantly refresh the pack list to apply the new order
                    if (MinecraftClient.getInstance().currentScreen instanceof PackScreen screen) {
                        ((PackScreenAccessor) screen).invokeUpdatePackLists(null);
                    }
                    isOpen = false;
                    return true;
                }
            }
        }

        return false;
    }

    // --- RENDERING ---
    // Draws the ⇅ icon and the dropdown menu background/text
    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw the main button icon (turns green if menu is open)
        context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.of(isOpen ? "§a⇅" : "⇅"),
                getX() + BTN_W / 2, getY() + 3, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA);

        if (!isOpen) return;

        // --- MENU DRAWING ---
        final int menuX = getX();
        final int menuY = getY() + BTN_H + 2;
        final int menuHeight = LABELS.length * ITEM_HEIGHT + PADDING * 2;

        // Draw menu background and borders
        context.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + menuHeight, 0xFF222222);
        context.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + 1, 0xFF888888);
        context.fill(menuX, menuY + menuHeight - 1, menuX + MENU_WIDTH, menuY + menuHeight, 0xFF888888);
        context.fill(menuX, menuY, menuX + 1, menuY + menuHeight, 0xFF888888);
        context.fill(menuX + MENU_WIDTH - 1, menuY, menuX + MENU_WIDTH, menuY + menuHeight, 0xFF888888);

        // Loop through each label to draw the text and hover highlights
        for (int i = 0; i < LABELS.length; i++) {
            final int itemY = menuY + PADDING + i * ITEM_HEIGHT;
            final boolean isHoveringItem = mouseX >= menuX && mouseX < menuX + MENU_WIDTH
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            final boolean isSelected = ResourceTreeConfig.sortMode == MODES[i];

            // Highlight the item the mouse is currently over
            if (isHoveringItem) {
                context.fill(menuX + 1, itemY, menuX + MENU_WIDTH - 1, itemY + ITEM_HEIGHT, 0xFF444444);
            }

            // Draw a green checkmark next to the currently active sort mode
            final String prefix = isSelected ? "§a✔ " : "   ";
            context.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.of(prefix + LABELS[i]),
                    menuX + PADDING + 2, itemY + 4, 0xFFFFFFFF);
        }
    }

    public boolean isOpen() { return isOpen; }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {}
}