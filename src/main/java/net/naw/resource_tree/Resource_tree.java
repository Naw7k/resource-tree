package net.naw.resource_tree;

import net.fabricmc.api.ModInitializer;

// --- MAIN MOD INITIALIZER ---
// Required by Fabric as the main mod entrypoint.
// Since all our logic is client-side (UI/Menus), this stays empty.
// Most of the heavy lifting will happen in Resource_treeClient.java instead.
public class Resource_tree implements ModInitializer {

    @Override
    public void onInitialize() {
        // This runs when the game starts, before the Main Menu appears.
        // We leave this blank because we don't need to register blocks or items.
    }
}