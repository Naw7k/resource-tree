package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- LIST ACCESSOR ---
// Exposes the available pack list widget from PackScreen so we can read its entries directly.
// This is used to "scrape" the list of packs currently visible in a folder.
@Mixin(PackScreen.class)
public interface PackScreenAvailableListAccessor {

    // Grabs the private 'availablePackList' field from the vanilla PackScreen class
    @Accessor("availablePackList")
    PackListWidget getAvailablePackList();
}