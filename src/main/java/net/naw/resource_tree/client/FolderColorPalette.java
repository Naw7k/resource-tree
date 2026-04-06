package net.naw.resource_tree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.naw.resource_tree.mixin.PackScreenAccessor;
import net.naw.resource_tree.mixin.PackScreenAvailableListAccessor;
import net.naw.resource_tree.mixin.PackScreenOrganizerAccessor;
import net.minecraft.network.chat.Component;

import java.io.File;

// --- FOLDER CONTEXT MENU ---
// This handles the little popup that appears when you right-click a folder.
public class FolderColorPalette {
    public static final FolderColorPalette INSTANCE = new FolderColorPalette();

    // The 6 selectable colors for folder icons
    public static final int[] COLORS = {
            0xFF000000, // Default (Black/No Tint)
            0xFFCC3333, // Red
            0xFF3366CC, // Blue
            0xFF33AA44, // Green
            0xFF9933CC, // Purple
            0xFFFFFFFF, // White
    };

    // UI Layout Constants (Pixels)
    private static final int SWATCH_SIZE = 14;
    private static final int PADDING = 4;
    private static final int BORDER = 1;
    private static final int BUTTON_HEIGHT = 16;

    private boolean visible = false;
    private int x, y;
    private String targetFolderPath;

    // --- RENAME STATE ---
    // Tracks whether inline renaming is active and which folder is being renamed
    private boolean renaming = false;
    private String renameBuffer = "";
    private String renamingPath = null;
    private int cursorPos = 0;    // Which character the cursor is at
    private int scrollOffset = 0; // How many pixels to scroll the text left

    // --- DELETE STATE ---
    // Tracks whether the delete confirmation prompt is showing
    private boolean confirmingDelete = false;

    public void show(int x, int y, String folderPath) {
        this.x = x;
        this.y = y;
        this.targetFolderPath = folderPath;
        this.visible = true;
        this.renaming = false;
        this.renameBuffer = "";
        this.renamingPath = null;
        this.cursorPos = 0;
        this.scrollOffset = 0;
        this.confirmingDelete = false;
    }

    public void hide() {
        this.visible = false;
        this.targetFolderPath = null;
        this.renaming = false;
        this.renameBuffer = "";
        this.renamingPath = null;
        this.cursorPos = 0;
        this.scrollOffset = 0;
        this.confirmingDelete = false;
    }

    public boolean isVisible() { return visible; }

    // --- RENAME GETTERS ---
    // Used by ResourcePackEntryMixin to know if this folder entry should render as an input field
    public boolean isRenaming() { return renaming; }
    public String getRenamingPath() { return renamingPath; }
    public String getRenameBuffer() { return renameBuffer; }
    public int getCursorPos() { return cursorPos; }
    public int getScrollOffset() { return scrollOffset; }

    // --- SCROLL UPDATER ---
    // Keeps the cursor visible by scrolling the text when it goes out of bounds
    private void updateScroll() {
        var tr = Minecraft.getInstance().font;
        String beforeCursor = renameBuffer.substring(0, cursorPos);
        int cursorPixelX = tr.width(beforeCursor) - scrollOffset;

        // Scroll right when cursor goes past the right edge
        if (cursorPixelX > 150) {
            scrollOffset = tr.width(beforeCursor) - 150;
        }
        // Scroll left when cursor goes before the left edge
        if (cursorPixelX < 10) {
            scrollOffset = Math.max(0, tr.width(beforeCursor) - 10);
        }
    }

    // --- ENABLE / DISABLE ALL LOGIC ---
    // Checks if all resource packs inside the current folder are already active
    private boolean areAllEnabled() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof PackSelectionScreen screen)) return false;
        PackSelectionModel model = ((PackScreenOrganizerAccessor) screen).getOrganizer();
        String prefix = "file/" + targetFolderPath + "/";

        // Filter packs that start with our folder path and are not in deeper sub-folders
        boolean hasDisabled = model.getUnselected().anyMatch(pack -> {
            String name = pack.getId();
            if (!name.startsWith(prefix)) return false;
            return !name.substring(prefix.length()).contains("/");
        });

        boolean hasAny = model.getSelected().anyMatch(pack -> {
            String name = pack.getId();
            if (!name.startsWith(prefix)) return false;
            return !name.substring(prefix.length()).contains("/");
        }) || hasDisabled;

        if (!hasAny) return false;
        return !hasDisabled;
    }

    // Moves all packs in this folder to the "Selected" side
    private void enableAll() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof PackSelectionScreen screen)) return;

        PackSelectionModel model = ((PackScreenOrganizerAccessor) screen).getOrganizer();
        String prefix = "file/" + targetFolderPath + "/";

        // Save where the user currently is so we can return them there after
        String savedFilter = ResourceTreeScreen.currentFilterFolder;
        File savedFolder = (client.screen instanceof ResourceTreeScreen rts) ? rts.getCurrentFolder() : null;

        // Temporarily set filter to target folder to populate the list with its packs
        ResourceTreeScreen.currentFilterFolder = targetFolderPath;
        ((PackScreenAccessor) screen).invokeUpdatePackLists(null);

        // Collect all selectable packs from the target folder
        java.util.List<PackSelectionModel.Entry> toEnable = new java.util.ArrayList<>();
        for (var child : ((PackScreenAvailableListAccessor) screen).getAvailablePackList().children()) {
            if (child instanceof net.minecraft.client.gui.screens.packs.TransferableSelectionList.PackEntry packEntry) {
                PackSelectionModel.Entry pack = ((net.naw.resource_tree.mixin.ResourcePackEntryAccessor) packEntry).getPack();
                if (pack.canSelect()) toEnable.add(pack);
            }
        }

        toEnable.forEach(PackSelectionModel.Entry::select);

        // Restore user back to where they were before
        ResourceTreeScreen.currentFilterFolder = savedFilter;
        if (client.screen instanceof ResourceTreeScreen rts && savedFolder != null) {
            rts.navigateTo(savedFolder);
        }
    }

    // Moves all packs in this folder back to the "Available" side
    private void disableAll() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof PackSelectionScreen screen)) return;

        PackSelectionModel model = ((PackScreenOrganizerAccessor) screen).getOrganizer();
        String prefix = "file/" + targetFolderPath + "/";

        // Save where the user currently is so we can return them there after
        String savedFilter = ResourceTreeScreen.currentFilterFolder;
        File savedFolder = (client.screen instanceof ResourceTreeScreen rts) ? rts.getCurrentFolder() : null;

        // Temporarily clear filter to get ALL selected packs, then filter by prefix
        ResourceTreeScreen.currentFilterFolder = null;
        java.util.List<PackSelectionModel.Entry> toDisable = model.getSelected()
                .filter(pack -> {
                    String name = pack.getId();
                    if (!name.startsWith(prefix)) return false;
                    return !name.substring(prefix.length()).contains("/");
                })
                .toList();

        toDisable.forEach(PackSelectionModel.Entry::unselect);

        // Restore user back to where they were before
        ResourceTreeScreen.currentFilterFolder = savedFilter;
        if (client.screen instanceof ResourceTreeScreen rts && savedFolder != null) {
            rts.navigateTo(savedFolder);
        }
    }

    // --- RENAME LOGIC ---
    // Renames the actual folder on disk and refreshes the screen
    public void commitRename() {
        if (renameBuffer.isBlank()) { renaming = false; renamingPath = null; return; }
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof ResourceTreeScreen resourceTreeScreen)) return;

        String cleanName = renameBuffer.trim().replaceAll("[.\\s]+$", "");
        if (cleanName.isEmpty()) { renaming = false; renamingPath = null; return; }

        File rootFolder = resourceTreeScreen.getRootFolder();
        File oldFolder = new File(rootFolder, renamingPath.replace("/", File.separator));
        File newFolder = new File(oldFolder.getParentFile(), cleanName);

        if (oldFolder.renameTo(newFolder)) {
            // Update color config key to match new folder name
            int color = ResourceTreeConfig.getFolderColor(renamingPath);
            ResourceTreeConfig.folderColors.remove(renamingPath);
            String newRelative = rootFolder.toPath().relativize(newFolder.toPath()).toString().replace("\\", "/");
            if (color != ResourceTreeConfig.DEFAULT_COLOR) {
                ResourceTreeConfig.folderColors.put(newRelative, color);
                ResourceTreeConfig.save();
            }
            resourceTreeScreen.navigateTo(newFolder.getParentFile());
        }
        hide();
    }

    // Activates inline rename mode directly without showing the context menu
    public void startRename(String folderPath) {
        this.renaming = true;
        this.renamingPath = folderPath;
        this.renameBuffer = new File(folderPath).getName();
        this.cursorPos = renameBuffer.length();
        this.scrollOffset = 0;
        this.visible = false;
        this.targetFolderPath = folderPath;
    }

    // --- DELETE LOGIC ---
    // Deletes the folder from disk (only if empty) and refreshes the screen
    private void deleteFolder() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof ResourceTreeScreen resourceTreeScreen)) return;

        File rootFolder = resourceTreeScreen.getRootFolder();
        File folder = new File(rootFolder, targetFolderPath.replace("/", File.separator));

        // Only delete if the folder is empty — safety check
        String[] contents = folder.list();
        if (contents != null && contents.length > 0) {
            // Folder not empty — show confirmation instead
            confirmingDelete = true;
            return;
        }

        if (!folder.delete()) return;
        ResourceTreeConfig.folderColors.remove(targetFolderPath);
        ResourceTreeConfig.save();
        resourceTreeScreen.navigateTo(folder.getParentFile());
        hide();
    }

    // Force deletes the folder and everything inside it recursively
    private void forceDeleteFolder() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof ResourceTreeScreen resourceTreeScreen)) return;

        File rootFolder = resourceTreeScreen.getRootFolder();
        File folder = new File(rootFolder, targetFolderPath.replace("/", File.separator));

        deleteRecursively(folder);
        ResourceTreeConfig.folderColors.remove(targetFolderPath);
        ResourceTreeConfig.save();
        resourceTreeScreen.navigateTo(folder.getParentFile());
        hide();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    // Recursively deletes a folder and all its contents
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    // --- RENDERING ---
    // Draws the actual menu on the screen (Box, Text, and Color Swatches)
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!visible) return;

        // --- DELETE CONFIRMATION SCREEN ---
        // Shows a warning popup if the folder has contents inside
        if (confirmingDelete) {
            int w = 140;
            int h = 60;
            context.fill(x, y, x + w, y + h, 0xFF222222);
            context.fill(x, y, x + w, y + BORDER, 0xFF888888);
            context.fill(x, y + h - BORDER, x + w, y + h, 0xFF888888);
            context.fill(x, y, x + BORDER, y + h, 0xFF888888);
            context.fill(x + w - BORDER, y, x + w, y + h, 0xFF888888);

            context.text(Minecraft.getInstance().font,
                    Component.literal("§cFolder not empty!"),
                    x + PADDING, y + PADDING, 0xFFFFFFFF);
            context.text(Minecraft.getInstance().font,
                    Component.literal("Delete anyway?"),
                    x + PADDING, y + PADDING + 12, 0xFFAAAAAA);

            // Yes button
            boolean yesHovered = mouseX >= x + PADDING && mouseX < x + 60 && mouseY >= y + 38 && mouseY < y + 54;
            if (yesHovered) context.fill(x + PADDING, y + 38, x + 60, y + 54, 0xFF552222);
            context.text(Minecraft.getInstance().font,
                    Component.literal("§c✔ Yes"), x + PADDING + 2, y + 42, 0xFFFFFFFF);

            // No button
            boolean noHovered = mouseX >= x + 70 && mouseX < x + w - PADDING && mouseY >= y + 38 && mouseY < y + 54;
            if (noHovered) context.fill(x + 70, y + 38, x + w - PADDING, y + 54, 0xFF224422);
            context.text(Minecraft.getInstance().font,
                    Component.literal("§a✘ No"), x + 72, y + 42, 0xFFFFFFFF);
            return;
        }

        // Skip rendering the context menu while renaming — the entry itself handles that
        if (renaming) return;

        int w = getWidth();
        int totalH = getTotalHeight();

        // Background & Borders
        context.fill(x, y, x + w, y + totalH, 0xFF222222);
        context.fill(x, y, x + w, y + BORDER, 0xFF888888);
        context.fill(x, y + totalH - BORDER, x + w, y + totalH, 0xFF888888);
        context.fill(x, y, x + BORDER, y + totalH, 0xFF888888);
        context.fill(x + w - BORDER, y, x + w, y + totalH, 0xFF888888);

        // Divider below Enable/Disable button
        context.fill(x, y + BUTTON_HEIGHT + PADDING / 2, x + w, y + BUTTON_HEIGHT + PADDING / 2 + BORDER, 0xFF555555);

        // Button text (Changes color/symbol based on status)
        boolean allEnabled = areAllEnabled();
        String buttonText = allEnabled ? "§c« Disable All" : "§a» Enable All";
        boolean buttonHovered = mouseX >= x + PADDING && mouseX < x + w - PADDING
                && mouseY >= y + PADDING / 2 && mouseY < y + BUTTON_HEIGHT;

        if (buttonHovered) context.fill(x + 1, y + 1, x + w - 1, y + BUTTON_HEIGHT, 0xFF444444);

        context.text(Minecraft.getInstance().font,
                Component.literal(buttonText),
                x + PADDING + 2, y + PADDING, 0xFFFFFFFF);

        // --- RENAME BUTTON ---
        int renameY = y + BUTTON_HEIGHT + PADDING;
        boolean renameHovered = mouseX >= x + PADDING && mouseX < x + w - PADDING
                && mouseY >= renameY + 2 && mouseY < renameY + BUTTON_HEIGHT;
        if (renameHovered) context.fill(x + 1, renameY, x + w - 1, renameY + BUTTON_HEIGHT, 0xFF444444);
        context.text(Minecraft.getInstance().font,
                Component.literal("✏ Rename"),
                x + PADDING + 2, renameY + PADDING, 0xFFFFFFFF);

        // --- DELETE BUTTON ---
        int deleteY = renameY + BUTTON_HEIGHT;
        boolean deleteHovered = mouseX >= x + PADDING && mouseX < x + w - PADDING
                && mouseY >= deleteY + 2 && mouseY < deleteY + BUTTON_HEIGHT;
        if (deleteHovered) context.fill(x + 1, deleteY, x + w - 1, deleteY + BUTTON_HEIGHT, 0xFF442222);
        context.text(Minecraft.getInstance().font,
                Component.literal("§c✖ Delete"),
                x + PADDING + 2, deleteY + PADDING, 0xFFFFFFFF);

        // Divider above color swatches
        context.fill(x, deleteY + BUTTON_HEIGHT + PADDING / 2, x + w, deleteY + BUTTON_HEIGHT + PADDING / 2 + BORDER, 0xFF555555);

        // Drawing the 6 color squares
        int paletteY = deleteY + BUTTON_HEIGHT + PADDING;
        for (int i = 0; i < COLORS.length; i++) {
            int swatchX = x + PADDING + i * (SWATCH_SIZE + 2);
            int swatchY = paletteY + PADDING;

            boolean hovered = mouseX >= swatchX && mouseX < swatchX + SWATCH_SIZE
                    && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE;
            boolean selected = ResourceTreeConfig.getFolderColor(targetFolderPath) == COLORS[i];

            // White border if hovered or currently selected
            if (hovered || selected) {
                context.fill(swatchX - 1, swatchY - 1,
                        swatchX + SWATCH_SIZE + 1, swatchY + SWATCH_SIZE + 1,
                        0xFFFFFFFF);
            }
            context.fill(swatchX, swatchY, swatchX + SWATCH_SIZE, swatchY + SWATCH_SIZE, COLORS[i]);
        }
    }

    // --- INPUT HANDLING ---
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        // --- DELETE CONFIRMATION CLICKS ---
        if (confirmingDelete) {
            if (mouseX >= x + PADDING && mouseX < x + 60 && mouseY >= y + 38 && mouseY < y + 54) {
                forceDeleteFolder();
            } else {
                confirmingDelete = false;
            }
            return true;
        }

        // --- RENAME INPUT CLICKS ---
        // Clicking anywhere while renaming cancels it
        if (renaming) {
            renaming = false;
            renamingPath = null;
            renameBuffer = "";
            cursorPos = 0;
            scrollOffset = 0;
            return true;
        }

        int w = getWidth();
        int totalH = getTotalHeight();

        // Close if clicking outside the menu
        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + totalH) {
            hide();
            return false;
        }

        // Logic for clicking the Enable/Disable button
        if (mouseX >= x + PADDING && mouseX < x + w - PADDING && mouseY >= y + 2 && mouseY < y + BUTTON_HEIGHT) {
            if (areAllEnabled()) disableAll(); else enableAll();
            hide();
            return true;
        }

        // Logic for clicking the Rename button — activates inline rename on the entry
        int renameY = y + BUTTON_HEIGHT + PADDING;
        if (mouseX >= x + PADDING && mouseX < x + w - PADDING && mouseY >= renameY + 2 && mouseY < renameY + BUTTON_HEIGHT) {
            renaming = true;
            renamingPath = targetFolderPath;
            renameBuffer = new File(targetFolderPath).getName();
            cursorPos = renameBuffer.length(); // Start cursor at end of name
            scrollOffset = 0;
            visible = false; // Hide the context menu, entry takes over
            return true;
        }

        // Logic for clicking the Delete button
        int deleteY = renameY + BUTTON_HEIGHT;
        if (mouseX >= x + PADDING && mouseX < x + w - PADDING && mouseY >= deleteY + 2 && mouseY < deleteY + BUTTON_HEIGHT) {
            deleteFolder();
            return true;
        }

        // Logic for clicking a color square
        int paletteY = deleteY + BUTTON_HEIGHT + PADDING;
        for (int i = 0; i < COLORS.length; i++) {
            int swatchX = x + PADDING + i * (SWATCH_SIZE + 2);
            int swatchY = paletteY + PADDING;
            if (mouseX >= swatchX && mouseX < swatchX + SWATCH_SIZE
                    && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) {
                ResourceTreeConfig.setFolderColor(targetFolderPath, COLORS[i]);
                if (Minecraft.getInstance().screen instanceof ResourceTreeScreen screen) {
                    screen.navigateTo(screen.getCurrentFolder());
                }
                hide();
                return true;
            }
        }

        return true;
    }

    // --- KEYBOARD INPUT ---
    // Handles typing in the inline rename field
    public boolean charTyped(char chr) {
        if (!renaming) return false;
        // Only allow valid filename characters
        if (chr >= 32 && chr != '/' && chr != '\\' && chr != ':' && chr != '*'
                && chr != '?' && chr != '"' && chr != '<' && chr != '>' && chr != '|') {
            renameBuffer = renameBuffer.substring(0, cursorPos) + chr + renameBuffer.substring(cursorPos);
            cursorPos++;
            updateScroll();
        }
        return true;
    }

    // Handles backspace, enter, escape and arrow keys in the inline rename field
    public boolean keyPressed(int keyCode) {
        if (!renaming) return false;
        if (keyCode == 259 && cursorPos > 0) { // Backspace
            renameBuffer = renameBuffer.substring(0, cursorPos - 1) + renameBuffer.substring(cursorPos);
            cursorPos--;
            updateScroll();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
            commitRename();
            return true;
        }
        if (keyCode == 256) { // Escape — cancel rename
            renaming = false;
            renamingPath = null;
            renameBuffer = "";
            cursorPos = 0;
            scrollOffset = 0;
            return true;
        }
        if (keyCode == 263) { // Left arrow
            if (cursorPos > 0) { cursorPos--; updateScroll(); }
            return true;
        }
        if (keyCode == 262) { // Right arrow
            if (cursorPos < renameBuffer.length()) { cursorPos++; updateScroll(); }
            return true;
        }
        return false;
    }

    // Calculations for the menu size
    private int getWidth() { return COLORS.length * SWATCH_SIZE + (COLORS.length - 1) * 2 + PADDING * 2; }
    private int getPaletteHeight() { return SWATCH_SIZE + PADDING * 2; }
    private int getTotalHeight() { return BUTTON_HEIGHT + PADDING + BUTTON_HEIGHT + BUTTON_HEIGHT + PADDING + getPaletteHeight(); }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        return mouseX >= x && mouseX < x + getWidth()
                && mouseY >= y && mouseY < y + getTotalHeight();
    }
}