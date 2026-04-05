package net.naw.resource_tree.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Custom Dropdown Widget for Resource Tree settings.
 * Renders an upward-expanding menu with toggleable options and tooltips.
 */
public class DropdownMenuWidget extends ClickableWidget {

    private boolean isOpen = false;

    // The display names for the settings in the menu
    private static final String[] LABELS = {
            "Hide Defaults",
            "Hide Pack Warnings",
            "Search in Folders"
    };

    private static final int ITEM_HEIGHT = 16;
    private static final int MENU_WIDTH = 130;
    private static final int PADDING = 4;

    public DropdownMenuWidget(int x, int y) {
        super(x, y, 20, 20, Text.of("≡"));
    }

    // --- HITBOX CHECK ---
    // Determines if the mouse is hovering over the button OR the expanded menu
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        final boolean overButton = mouseX >= getX() && mouseX < getX() + 20
                && mouseY >= getY() && mouseY < getY() + 20;

        if (!isOpen) return overButton;

        // Calculate the area of the menu box above the button
        final int menuHeight = LABELS.length * ITEM_HEIGHT + PADDING * 2;
        final int menuY = getY() - menuHeight - 2;
        final boolean overMenu = mouseX >= getX() && mouseX < getX() + MENU_WIDTH
                && mouseY >= menuY && mouseY < getY();

        return overButton || overMenu;
    }

    /**
     * Handles clicks for both the main button and the internal menu items.
     * @param button The mouse button (0 for left-click). Parameter is required by method signature.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0) return false;

        // If clicking away while the menu is open, close it
        if (!isMouseOver(mouseX, mouseY)) {
            if (isOpen) {
                isOpen = false;
                return true;
            }
            return false;
        }

        final boolean overButton = mouseX >= getX() && mouseX < getX() + 20
                && mouseY >= getY() && mouseY < getY() + 20;

        // Toggle the menu visibility when clicking the main button
        if (overButton) {
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            this.isOpen = !this.isOpen;
            return true;
        }

        // Handle clicking on specific lines within the open menu
        if (isOpen) {
            final int menuHeight = LABELS.length * ITEM_HEIGHT + PADDING * 2;
            final int menuY = getY() - menuHeight - 2;

            if (mouseY >= menuY + PADDING && mouseY < menuY + menuHeight - PADDING) {
                int index = (int) ((mouseY - (menuY + PADDING)) / ITEM_HEIGHT);
                if (index >= 0 && index < LABELS.length) {
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    toggleOption(index);
                    return true;
                }
            }
        }

        return false;
    }

    // --- RENDERING ---
    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw the main square button
        final int btnColor = isOpen ? 0xFF555555 : (isHovered() ? 0xFF444444 : 0xFF333333);
        context.fill(getX(), getY(), getX() + 20, getY() + 20, btnColor);

        renderBorder(context, getX(), getY(), 20, 20);

        context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.of("≡"),
                getX() + 10, getY() + 6, 0xFFFFFFFF);

        if (!isOpen) return;

        // Render the Menu Box (positioned above the button)
        final int menuX = getX();
        final int menuHeight = LABELS.length * ITEM_HEIGHT + PADDING * 2;
        final int menuY = getY() - menuHeight - 2;

        context.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + menuHeight, 0xFF222222);
        renderBorder(context, menuX, menuY, MENU_WIDTH, menuHeight);

        // FIRST LOOP: Render the text and selection highlights
        for (int i = 0; i < LABELS.length; i++) {
            final int itemY = menuY + PADDING + i * ITEM_HEIGHT;
            final boolean isHoveringItem = mouseX >= menuX && mouseX < menuX + MENU_WIDTH
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            if (isHoveringItem) {
                context.fill(menuX + 1, itemY, menuX + MENU_WIDTH - 1, itemY + ITEM_HEIGHT, 0xFF444444);
            }

            // Draw Checkmark (✔) or Cross (✘) based on setting status
            final String checkbox = getOptionValue(i) ? "§a✔ " : "§7✘ ";
            context.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.of(checkbox + LABELS[i]),
                    menuX + PADDING + 2, itemY + 4, 0xFFFFFFFF);
        }

        // SECOND LOOP: Render Tooltips (Always last so they appear on top)
        for (int i = 0; i < LABELS.length; i++) {
            final int itemY = menuY + PADDING + i * ITEM_HEIGHT;
            if (mouseX >= menuX && mouseX < menuX + MENU_WIDTH
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                final String tooltipText = getTooltip(i);
                if (tooltipText != null) {
                    context.drawTooltip(
                            MinecraftClient.getInstance().textRenderer,
                            Text.of(tooltipText),
                            mouseX,
                            itemY
                    );
                }
            }
        }
    }

    // Draws the thin gray border around UI elements
    private void renderBorder(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + 1, 0xFF888888); // Top
        context.fill(x, y + h - 1, x + w, y + h, 0xFF888888); // Bottom
        context.fill(x, y, x + 1, y + h, 0xFF888888); // Left
        context.fill(x + w - 1, y, x + w, y + h, 0xFF888888); // Right
    }

    // Grabs the current boolean value from the config
    private boolean getOptionValue(int index) {
        return switch (index) {
            case 0 -> ResourceTreeConfig.hideBuiltinPacks;
            case 1 -> ResourceTreeConfig.hideIncompatiblePacks;
            case 2 -> ResourceTreeConfig.searchInsideFolders;
            default -> false;
        };
    }

    // Switches the setting on/off and saves it
    private void toggleOption(int index) {
        switch (index) {
            case 0 -> ResourceTreeConfig.hideBuiltinPacks = !ResourceTreeConfig.hideBuiltinPacks;
            case 1 -> ResourceTreeConfig.hideIncompatiblePacks = !ResourceTreeConfig.hideIncompatiblePacks;
            case 2 -> ResourceTreeConfig.searchInsideFolders = !ResourceTreeConfig.searchInsideFolders;
        }
        ResourceTreeConfig.save();

        // Refresh the Resource Pack screen lists instantly
        if (MinecraftClient.getInstance().currentScreen instanceof net.minecraft.client.gui.screen.pack.PackScreen screen) {
            ((net.naw.resource_tree.mixin.PackScreenAccessor) screen).invokeUpdatePackLists(null);
        }
    }

    // Tooltip descriptions for each menu item
    private String getTooltip(int index) {
        return switch (index) {
            case 0 -> "Hides High Contrast and Programmer Art from the list";
            case 1 -> "Hides warnings for outdated packs";
            case 2 -> "Allows searching for packs located in subfolders";
            default -> null;
        };
    }

    public boolean isOpen() { return isOpen; }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
