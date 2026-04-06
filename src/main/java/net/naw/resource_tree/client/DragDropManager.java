package net.naw.resource_tree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.naw.resource_tree.mixin.EntryListWidgetAccessor;
import net.naw.resource_tree.mixin.PackScreenAvailableListAccessor;

import java.io.File;
import java.util.List;

/**
 * --- DRAG AND DROP MANAGER ---
 * Handles the logic for moving resource packs into folders by dragging them.
 * It tracks the "ghost" entry following the mouse and manages swapping the
 * real entry with a blank placeholder during the drag process.
 */
public class DragDropManager {

    // --- DRAG STATE ---
    // Tracks if a drag is active and what file/entry is being moved.
    private static boolean dragging = false;
    private static File dragPackFile = null;
    private static TransferableSelectionList.PackEntry dragEntry = null;
    private static boolean renderingGhost = false;
    private static int dragEntryIndex = -1;

    // --- GHOST OFFSET ---
    // The distance between the mouse cursor and the top-left of the dragged entry.
    private static int ghostOffsetX = 0;
    private static int ghostOffsetY = 0;

    // --- DROP TARGET ---
    // The folder currently being hovered by the mouse during a drag.
    private static File hoveredFolder = null;

    // --- DRAG THRESHOLD ---
    // Logic to prevent accidental drags; requires moving the mouse 5 pixels first.
    private static int pressX = 0;
    private static int pressY = 0;
    private static boolean pressing = false;
    private static File pendingPackFile = null;
    private static TransferableSelectionList.PackEntry pendingEntry = null;
    private static final int DRAG_THRESHOLD = 5;

    // --- BLANK ENTRY ---
    // An invisible entry that takes the place of the dragged pack in the list.
    private static TransferableSelectionList.PackEntry blankEntry = null;

    // --- ESSENTIAL CHECK ---
    // Helper to check if Essential mod is installed
    private static boolean isEssentialLoaded() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("essential");
    }

    // --- START PRESS ---
    // Records the initial mouse click position and the entry being clicked.
    // Works even with Essential installed so folder drops still work.
    public static void startPress(int x, int y, File packFile, TransferableSelectionList.PackEntry entry) {
        pressing = true;
        pressX = x;
        pressY = y;
        pendingPackFile = packFile;
        pendingEntry = entry;

        // Calculate offset so the ghost doesn't "snap" its center to the mouse.
        ghostOffsetX = x - entry.getX();
        ghostOffsetY = y - entry.getY();
    }

    // --- UPDATE MOUSE ---
    // Checks every frame to see if the user has moved the mouse far enough to start a drag.
    @SuppressWarnings("unchecked")
    public static void updateMouse(int x, int y) {
        if (pressing && !dragging) {
            int dx = Math.abs(x - pressX);
            int dy = Math.abs(y - pressY);

            if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                dragging = true;
                dragPackFile = pendingPackFile;
                dragEntry = pendingEntry;

                // Only do blank entry swap if Essential is not installed
                // Essential has its own drag system that conflicts with ours
                if (!isEssentialLoaded() && blankEntry != null) {
                    Minecraft client = Minecraft.getInstance();
                    if (client.screen instanceof PackSelectionScreen screen) {
                        TransferableSelectionList list = ((PackScreenAvailableListAccessor) screen).getAvailablePackList();
                        if (list != null) {
                            List<TransferableSelectionList.PackEntry> rawChildren =
                                    (List<TransferableSelectionList.PackEntry>) ((EntryListWidgetAccessor) list).getChildren();

                            int index = rawChildren.indexOf(dragEntry);
                            if (index != -1) {
                                dragEntryIndex = index;

                                rawChildren.set(index, blankEntry);
                                blankEntry.setWidth(dragEntry.getWidth());
                                blankEntry.setHeight(dragEntry.getHeight());
                                list.setScrollAmount(list.scrollAmount());
                            }
                        }
                    }
                }
            }
        }
    }

    // --- HANDLE DROP ---
    // Triggered when the mouse is released. Restores the entry and moves the file.
    @SuppressWarnings({"unchecked", "unused"})
    public static void handleDrop(int x, int y) {
        if (!dragging) {
            pressing = false;
            return;
        }

        // Restore the original pack entry back to the list
        // Only needed if Essential is not installed (we did the swap)
        if (!isEssentialLoaded() && blankEntry != null && dragEntry != null && dragEntryIndex != -1) {
            Minecraft client = Minecraft.getInstance();
            if (client.screen instanceof PackSelectionScreen screen) {
                TransferableSelectionList list = ((PackScreenAvailableListAccessor) screen).getAvailablePackList();
                if (list != null) {
                    List<TransferableSelectionList.PackEntry> rawChildren =
                            (List<TransferableSelectionList.PackEntry>) ((EntryListWidgetAccessor) list).getChildren();

                    if (dragEntryIndex < rawChildren.size() && rawChildren.get(dragEntryIndex) == blankEntry) {
                        rawChildren.set(dragEntryIndex, dragEntry);
                        list.setScrollAmount(list.scrollAmount());
                    }
                }
            }
        }

        if (hoveredFolder != null && dragPackFile != null) {
            File destination = new File(hoveredFolder, dragPackFile.getName());
            if (!destination.exists()) {
                boolean success = dragPackFile.renameTo(destination);
                if (success) {
                    refreshScreen();
                }
            }
        }

        reset();
    }

    // --- REFRESH SCREEN ---
    // Helper method to reload the UI after a file move.
    // Uses a delayed second refresh for Async Pack Scan compatibility
    // so the pack disappears properly after moving.
    private static void refreshScreen() {
        Minecraft client = Minecraft.getInstance();

        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("async-pack-scan")) {
            // First call triggers async scan
            doRefresh(client);
            // Delayed second call waits for scan to finish
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                client.execute(() -> doRefresh(client));
            }).start();
            return;
        }

        doRefresh(client);
    }

    private static void doRefresh(Minecraft client) {
        if (client.screen instanceof ResourceTreeScreen resourceTreeScreen) {
            resourceTreeScreen.navigateTo(resourceTreeScreen.getCurrentFolder());
        }
    }

    // --- SETTERS & HOVER LOGIC ---
    public static void setHoveredFolder(File folder) { hoveredFolder = folder; }
    public static void clearHoveredFolder() { hoveredFolder = null; }
    public static void setBlankEntry(TransferableSelectionList.PackEntry entry) { blankEntry = entry; }

    // --- RENDER ---
    // Draws the ghost entry following the cursor.
    // Skipped when Essential is installed as it has its own ghost render.
    public static void renderDrag(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Skip our ghost render if Essential is installed
        if (isEssentialLoaded()) return;
        if (!dragging || dragEntry == null || blankEntry == null) return;

        // Use mouse position minus offset to position ghost correctly
        int ghostX = mouseX - ghostOffsetX;
        int ghostY = mouseY - ghostOffsetY;

        int ghostW = dragEntry.getWidth();
        int ghostH = dragEntry.getHeight();

        // Background first
        context.fill(ghostX, ghostY, ghostX + ghostW, ghostY + ghostH, 0x55000000);
        // White outline
        context.fill(ghostX, ghostY, ghostX + ghostW, ghostY + 1, 0xFFFFFFFF);
        context.fill(ghostX, ghostY + ghostH - 1, ghostX + ghostW, ghostY + ghostH, 0xFFFFFFFF);
        context.fill(ghostX, ghostY, ghostX + 1, ghostY + ghostH, 0xFFFFFFFF);
        context.fill(ghostX + ghostW - 1, ghostY, ghostX + ghostW, ghostY + ghostH, 0xFFFFFFFF);

        // Semi-transparent background
        context.fill(ghostX, ghostY, ghostX + ghostW, ghostY + ghostH, 0x55000000);

        // --- GHOST PACK RENDERING ---
        // Move the entry to ghost position, render it, then restore.
        // No matrix translation — just direct position swap
        renderingGhost = true;
        int orgX = dragEntry.getX();
        int orgY = dragEntry.getY();
        dragEntry.setX(ghostX);
        dragEntry.setY(ghostY);
        dragEntry.extractContent(context, mouseX, mouseY, false, 0);
        dragEntry.setX(orgX);
        dragEntry.setY(orgY);
        renderingGhost = false;

        // Faded overlay on top of the ghost to make it semi-transparent
        context.fill(ghostX, ghostY, ghostX + ghostW, ghostY + ghostH, 0x55000000);

        // Scroll nudging
        handleScrollNudging(mouseY);
    }

    // --- SCROLL NUDGING ---
    // Automatically scrolls the list when a pack is held near the top or bottom edges.
    private static void handleScrollNudging(int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof PackSelectionScreen screen) {
            TransferableSelectionList list = ((PackScreenAvailableListAccessor) screen).getAvailablePackList();
            if (list != null) {
                int listTop = list.getY();
                int listBottom = list.getBottom();
                int scrollZone = 20;

                if (mouseY < listTop + scrollZone && mouseY > listTop) {
                    list.setScrollAmount(list.scrollAmount() - 3);
                } else if (mouseY > listBottom - scrollZone && mouseY < listBottom) {
                    list.setScrollAmount(list.scrollAmount() + 3);
                }
            }
        }
    }

    // --- GETTERS ---
    public static boolean isDragging() { return dragging; }
    public static boolean isPressing() { return pressing; }
    public static boolean isRenderingGhost() { return renderingGhost; }
    public static File getHoveredFolder() { return hoveredFolder; }
    public static TransferableSelectionList.PackEntry getDragEntry() { return dragEntry; }

    // --- RESET ---
    // Clears drag data and offsets to prepare for the next drag action.
    public static void reset() {
        dragging = false;
        pressing = false;
        dragPackFile = null;
        dragEntry = null;
        hoveredFolder = null;
        pendingPackFile = null;
        pendingEntry = null;
        renderingGhost = false;
        ghostOffsetX = 0;
        ghostOffsetY = 0;
        dragEntryIndex = -1;
    }
}