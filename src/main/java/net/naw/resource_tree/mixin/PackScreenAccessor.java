package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor interface for PackScreen.
 * This allows us to trigger private vanilla methods, such as refreshing the
 * pack list after a config toggle has changed.
 */
@Mixin(PackScreen.class)
public interface PackScreenAccessor {

    /**
     * Forces the resource pack screen to rebuild its internal list of packs.
     * We call this after toggling "Hide Defaults" or "Hide Incompatible"
     * so the UI updates immediately without having to close and reopen the screen.
     */
    @Invoker("updatePackLists")
    void invokeUpdatePackLists(ResourcePackOrganizer.AbstractPack focused);
}