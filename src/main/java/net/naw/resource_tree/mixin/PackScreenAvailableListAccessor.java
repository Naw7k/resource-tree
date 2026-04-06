package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- LIST ACCESSOR ---
// Exposes the available pack list widget from PackSelectionScreen so we can read its entries directly.
// This is used to "scrape" the list of packs currently visible in a folder.
@Mixin(PackSelectionScreen.class)
public interface PackScreenAvailableListAccessor {

    // Grabs the private 'availablePackList' field from the vanilla PackSelectionScreen class
    @Accessor("availablePackList")
    TransferableSelectionList getAvailablePackList();
}