package net.naw.resource_tree.client;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;

// --- RECURSIVE PACK SCANNER ---
// Vanilla Minecraft only looks at the top level of the /resourcepacks/ folder.
// This class tells the game: "If you see a folder, go inside it and keep looking for .zip files."
public class SubfolderResourcePackProvider implements RepositorySource {

    private final File resourcePacksDir;

    public SubfolderResourcePackProvider(File resourcePacksDir) {
        this.resourcePacksDir = resourcePacksDir;
    }

    @Override
    public void loadPacks(Consumer<Pack> profileAdder) {
        // Start the scan at the root /resourcepacks/ folder
        scanFolder(resourcePacksDir, profileAdder);
    }

    // --- SCAN LOGIC ---
    // This method is "Recursive" — it calls itself every time it finds a new subfolder.
    private void scanFolder(File folder, Consumer<Pack> profileAdder) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Check if this folder is actually an unzipped resource pack
                // by looking for a pack.mcmeta file inside it
                File mcmeta = new File(file, "pack.mcmeta");
                if (mcmeta.exists()) {
                    // This folder is a valid resource pack — register it like a zip
                    String relativePath = getRelativePath(file);
                    String packId = "file/" + relativePath;
                    String displayName = file.getName();

                    PackLocationInfo info = new PackLocationInfo(
                            packId,
                            Component.literal(displayName),
                            PackSource.DEFAULT,
                            Optional.empty()
                    );

                    PackSelectionConfig selectionConfig = new PackSelectionConfig(
                            false,
                            Pack.Position.TOP,
                            false
                    );

                    Pack pack = Pack.readMetaAndCreate(
                            info,
                            new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(file.toPath()),
                            PackType.CLIENT_RESOURCES,
                            selectionConfig
                    );

                    if (pack != null) {
                        profileAdder.accept(pack);
                    }
                } else {
                    // RECURSION: No pack.mcmeta found, so it's a real folder — scan inside it.
                    scanFolder(file, profileAdder);
                }
            } else if (file.getName().endsWith(".zip")) {
                // We found a pack! Now we need to tell Minecraft how to identify it.

                // Build a pack ID like "file/subfolder/pack.zip"
                String relativePath = getRelativePath(file);
                String packId = "file/" + relativePath;
                String displayName = file.getName().replace(".zip", "");

                // Create the location info for the pack
                PackLocationInfo info = new PackLocationInfo(
                        packId,
                        Component.literal(displayName),
                        PackSource.DEFAULT,
                        Optional.empty()
                );

                // Create the selection config (not required, goes to top, not fixed)
                PackSelectionConfig selectionConfig = new PackSelectionConfig(
                        false,
                        Pack.Position.TOP,
                        false
                );

                // Create the actual Pack that Minecraft uses to load it
                Pack pack = Pack.readMetaAndCreate(
                        info,
                        new net.minecraft.server.packs.FilePackResources.FileResourcesSupplier(file.toPath()),
                        PackType.CLIENT_RESOURCES,
                        selectionConfig
                );

                if (pack != null) {
                    // "Accept" the pack to officially add it to the game's list
                    profileAdder.accept(pack);
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
