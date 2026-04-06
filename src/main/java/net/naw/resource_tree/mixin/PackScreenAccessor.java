package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor interface for PackSelectionScreen.
 * This allows us to trigger private vanilla methods, such as refreshing the
 * pack list after a config toggle has changed.
 */
@Mixin(PackSelectionScreen.class)
public interface PackScreenAccessor {

    /**
     * Forces the resource pack screen to rebuild its internal list of packs.
     * We call this after toggling "Hide Defaults" or "Hide Incompatible"
     * so the UI updates immediately without having to close and reopen the screen.
     */
    @Invoker("populateLists")
    void invokeUpdatePackLists(PackSelectionModel.EntryBase focused);

    /**
     * Calls the private reload() method which refreshes pack lists
     * without rebuilding the entire screen — avoiding the black duplication bug.
     */
    @Invoker("reload")
    void invokeReload();
}