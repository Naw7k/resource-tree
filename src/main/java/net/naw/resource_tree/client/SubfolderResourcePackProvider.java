package net.naw.resource_tree.client;

import net.minecraft.resource.*;
import net.minecraft.text.Text;
import net.minecraft.resource.ResourcePackPosition;
import java.io.File;
import java.util.function.Consumer;

// --- RECURSIVE PACK SCANNER ---
// Vanilla Minecraft only looks at the top level of the /resourcepacks/ folder.
// This class tells the game: "If you see a folder, go inside it and keep looking for .zip files."
public class SubfolderResourcePackProvider implements ResourcePackProvider {

    private final File resourcePacksDir;

    public SubfolderResourcePackProvider(File resourcePacksDir) {
        this.resourcePacksDir = resourcePacksDir;
    }

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        // Start the scan at the root /resourcepacks/ folder
        scanFolder(resourcePacksDir, profileAdder);
    }

    // --- SCAN LOGIC ---
    // This method is "Recursive" — it calls itself every time it finds a new subfolder.
    private void scanFolder(File folder, Consumer<ResourcePackProfile> profileAdder) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // RECURSION: We found a folder, so run this same scan INSIDE that folder.
                scanFolder(file, profileAdder);
            } else if (file.getName().endsWith(".zip")) {
                // We found a pack! Now we need to tell Minecraft how to identify it.

                // Build a pack ID like "file/subfolder/pack.zip"
                String relativePath = getRelativePath(file);
                String packId = "file/" + relativePath;
                String displayName = file.getName().replace(".zip", "");

                // Create the metadata for the pack
                ResourcePackInfo info = new ResourcePackInfo(
                        packId,
                        Text.literal(displayName),
                        ResourcePackSource.NONE,
                        java.util.Optional.empty()
                );

                // Create the actual Profile that Minecraft uses to load the pack
                ResourcePackProfile profile = ResourcePackProfile.create(
                        info,
                        new ZipResourcePack.ZipBackedFactory(file),
                        ResourceType.CLIENT_RESOURCES,
                        new ResourcePackPosition(false, ResourcePackProfile.InsertionPosition.TOP, false)
                );

                if (profile != null) {
                    // "Accept" the profile to officially add it to the game's list
                    profileAdder.accept(profile);
                }
            }
        }
    }

    // --- PATH CALCULATOR ---
    // Converts "C:/Users/Name/AppData/.../resourcepacks/Sub/Pack.zip"
    // into just "Sub/Pack.zip" so the game can find it easily.
    private String getRelativePath(File file) {
        String root = resourcePacksDir.getAbsolutePath();
        String full = file.getAbsolutePath();
        // substring(root.length() + 1) cuts off the "C:/.../resourcepacks/" part
        return full.substring(root.length() + 1).replace("\\", "/");
    }
}