package net.naw.resource_tree.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.io.File;

// --- NEW FOLDER BUTTON ---
// A small "+" button that creates a new folder in the current directory
// and immediately activates inline rename mode so the user can name it.
public class NewFolderButton extends ClickableWidget {

    private long hoverStartTime = -1;

    public NewFolderButton(int x, int y) {
        super(x, y, 20, 20, Text.of("+"));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered();

        // Dark background always, subtle highlight on hover
        context.fill(getX(), getY(), getX() + 20, getY() + 20, hovered ? 0xFF444444 : 0xFF333333);

        // Gray border always
        context.fill(getX(), getY(), getX() + 20, getY() + 1, 0xFF888888);
        context.fill(getX(), getY() + 19, getX() + 20, getY() + 20, 0xFF888888);
        context.fill(getX(), getY(), getX() + 1, getY() + 20, 0xFF888888);
        context.fill(getX() + 19, getY(), getX() + 20, getY() + 20, 0xFF888888);

        // Green "+" on hover, white otherwise
        context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.of(hovered ? "§a+" : "+"),
                getX() + 10, getY() + 6, 0xFFFFFFFF);

        // --- DELAYED TOOLTIP ---
        // Only show after hovering for 0.5 seconds
        if (hovered) {
            if (hoverStartTime == -1) hoverStartTime = System.currentTimeMillis();
            if (System.currentTimeMillis() - hoverStartTime >= 500) {
                this.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("New Folder")));
            } else {
                this.setTooltip(null);
            }
        } else {
            hoverStartTime = -1;
            this.setTooltip(null);
        }
    }


    // --- CLICK HANDLING ---
    // Checks for a valid left-click and ensures we are on the right screen
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0) return false;
        if (mouseX < getX() || mouseX > getX() + 20 || mouseY < getY() || mouseY > getY() + 20) return false;

        this.playDownSound(MinecraftClient.getInstance().getSoundManager());

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ResourceTreeScreen resourceTreeScreen)) return true;

        File currentFolder = resourceTreeScreen.getCurrentFolder();

        // --- CREATE NEW FOLDER ---
        // Find a unique name that doesn't already exist
        String baseName = "New Folder";
        File newFolder = new File(currentFolder, baseName);
        int counter = 1;
        while (newFolder.exists()) {
            newFolder = new File(currentFolder, baseName + " (" + counter + ")");
            counter++;
        }
        if (!newFolder.mkdir()) return true;

        // Get the relative path for the new folder
        File rootFolder = resourceTreeScreen.getRootFolder();
        String relativePath = rootFolder.toPath().relativize(newFolder.toPath()).toString().replace("\\", "/");

        // Refresh the screen then immediately activate rename mode
        resourceTreeScreen.navigateTo(currentFolder);
        FolderColorPalette.INSTANCE.startRename(relativePath);
        return true;
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
