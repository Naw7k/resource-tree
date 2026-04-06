package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- SEARCH ACCESSOR ---
// Exposes the search box from PackSelectionScreen so we can check if a search is active.
// This allows the mod to decide whether to show the folder tree or show all search results.
@Mixin(PackSelectionScreen.class)
public interface PackScreenSearchAccessor {

    // Grabs the private 'search' field from the vanilla PackSelectionScreen class
    @Accessor("search")
    EditBox getSearchBox();
}