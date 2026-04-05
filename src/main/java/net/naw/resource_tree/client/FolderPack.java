package net.naw.resource_tree.client;

import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.File;

// --- FOLDER AS A PACK ---
// This class represents a folder but "disguises" it as a resource pack
// so it can appear in the vanilla available packs list.
public class FolderPack implements ResourcePackOrganizer.Pack {
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

    @Override public Identifier getIconId() {
        if (isBack) {
            // Icon for the "Back" arrow
            String relativePath = screen.getRootFolder().toPath()
                    .relativize(screen.getCurrentFolder().toPath())
                    .toString()
                    .replace("\\", "/");
            String colorName = colorToName(ResourceTreeConfig.getFolderColor(relativePath));
            return Identifier.of("resource_tree", "textures/folder_open_" + colorName + ".png");
        }
        // Icon for a normal folder
        return Identifier.of("resource_tree", "textures/folder_closed_" + colorToName(ResourceTreeConfig.getFolderColor(getRelativePath())) + ".png");
    }

    // --- NAVIGATION ---
    // In Minecraft, clicking the "Enable" arrow usually moves a pack to the right.
    // Here, we override it so it just opens the folder instead.
    @Override public void enable() { screen.navigateTo(folder); }
    @Override public void disable() { screen.navigateTo(folder); }

    // --- PACK SETTINGS ---
    // Standard overrides to make the folder behave like a compatible, non-movable pack
    @Override public ResourcePackCompatibility getCompatibility() { return ResourcePackCompatibility.COMPATIBLE; }
    @Override public String getName() { return "folder:" + folder.getAbsolutePath(); }

    @Override public Text getDisplayName() {
        if (isBack) return Text.literal("..");
        if (FolderColorPalette.INSTANCE.isRenaming() &&
                getRelativePath().equals(FolderColorPalette.INSTANCE.getRenamingPath())) {
            return Text.literal("");
        }
        return Text.literal(name);
    }

    @Override public Text getDescription() {
        if (isBack) return Text.literal("<Back>");
        int count = getDirectPackCount();
        return Text.literal(count > 0 ? "<Folder> (" + count + ")" : "<Folder>");
    }
    @Override public ResourcePackSource getSource() { return ResourcePackSource.NONE; }

    // Folders shouldn't be "movable" or "pinnable" like actual packs
    @Override public boolean isPinned() { return false; }
    @Override public boolean isAlwaysEnabled() { return false; }
    @Override public boolean isEnabled() { return false; }
    @Override public boolean canBeEnabled() { return !isBack; }
    @Override public boolean canBeDisabled() { return isBack; }
    @Override public boolean canMoveTowardStart() { return false; }
    @Override public boolean canMoveTowardEnd() { return false; }
    @Override public void moveTowardStart() {}
    @Override public void moveTowardEnd() {}

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
