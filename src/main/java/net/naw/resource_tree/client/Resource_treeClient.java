package net.naw.resource_tree.client;

import net.fabricmc.api.ClientModInitializer;

// --- CLIENT-SIDE INITIALIZER ---
// This runs specifically for the client-side (the actual game window).
public class Resource_treeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // --- CONFIG LOADING ---
        // This tells the mod to read your saved settings (like folder colors)
        // from the disk as soon as the game launches.
        ResourceTreeConfig.load();
    }
}