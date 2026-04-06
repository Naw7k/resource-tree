package net.naw.resource_tree.client;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import java.io.File;

// --- FOLDER AS A PACK ---
// This class represents a folder but "disguises" it as a resource pack
// so it can appear in the vanilla available packs list.
public class FolderPack implements PackSelectionModel.Entry {
    private final String name;
    private final File folder;
    private final ResourceTreeScreen screen;
    private final boolean isBack; // True if this is the "Go Up" (..) button

    public FolderPack(String name, File folder, ResourceTreeScreen screen, boolean isBack) {
        this.name = name;
        this.folder = folder;
        this.screen = screen;
        this.isBack = isBack;
    }

    // --- COLOR & TEXTURE LOGIC ---
    // Converts the HEX color from config into a string for the texture filename
    private String colorToName(int color) {
        return switch (color) {
            case 0xFFCC3333 -> "red";
            case 0xFF3366CC -> "blue";
            case 0xFF33AA44 -> "green";
            case 0xFF9933CC -> "purple";
            case 0xFFFFFFFF -> "white";
            default -> "default";
        };
    }

    @Override public Identifier getIconTexture() {
        if (isBack) {
            // Icon for the "Back" arrow
            String relativePath = screen.getRootFolder().toPath()
                    .relativize(screen.getCurrentFolder().toPath())
                    .toString()
                    .replace("\\", "/");
            String colorName = colorToName(ResourceTreeConfig.getFolderColor(relativePath));
            return Identifier.fromNamespaceAndPath("resource_tree", "textures/folder_open_" + colorName + ".png");
        }
        // Icon for a normal folder
        return Identifier.fromNamespaceAndPath("resource_tree", "textures/folder_closed_" + colorToName(ResourceTreeConfig.getFolderColor(getRelativePath())) + ".png");
    }

    // --- NAVIGATION ---
    // In Minecraft, clicking the "Enable" arrow usually moves a pack to the right.
    // Here, we override it so it just opens the folder instead.
    @Override public void select() { screen.navigateTo(folder); }
    @Override public void unselect() { screen.navigateTo(folder); }

    // --- PACK SETTINGS ---
    // Standard overrides to make the folder behave like a compatible, non-movable pack
    @Override public PackCompatibility getCompatibility() { return PackCompatibility.COMPATIBLE; }
    @Override public String getId() { return "folder:" + folder.getAbsolutePath(); }

    @Override public Component getTitle() {
        if (isBack) return Component.literal("..");
        if (FolderColorPalette.INSTANCE.isRenaming() &&
                getRelativePath().equals(FolderColorPalette.INSTANCE.getRenamingPath())) {
            return Component.literal("");
        }
        return Component.literal(name);
    }

    @Override public Component getDescription() {
        if (isBack) return Component.literal("<Back>");
        int count = getDirectPackCount();
        return Component.literal(count > 0 ? "<Folder> (" + count + ")" : "<Folder>");
    }

    @Override public PackSource getPackSource() { return PackSource.DEFAULT; }
    // Folders shouldn't be "movable" or "pinnable" like actual packs
    @Override public boolean isFixedPosition() { return true; }
    @Override public boolean isRequired() { return false; }
    @Override public boolean isSelected() { return false; }
    @Override public boolean canSelect() { return !isBack; }
    @Override public boolean canUnselect() { return isBack; }
    @Override public boolean canMoveUp() { return false; }
    @Override public boolean canMoveDown() { return false; }
    @Override public void moveUp() {}
    @Override public void moveDown() {}

    // Helper to get path for color lookups
    public String getRelativePath() {
        return screen.getRootFolder().toPath()
                .relativize(folder.toPath())
                .toString()
                .replace("\\", "/");
    }

    // --- PACK COUNT ---
    // Counts how many .zip resource packs are directly inside this folder (no subfolders counted)
    public int getDirectPackCount() {
        if (isBack) return 0;
        File[] files = folder.listFiles(f -> !f.isDirectory() && f.getName().endsWith(".zip"));
        return files == null ? 0 : files.length;
    }

    public boolean isBack() { return isBack; }
    public File getFolder() { return folder; }
}