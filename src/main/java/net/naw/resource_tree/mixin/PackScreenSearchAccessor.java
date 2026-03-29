package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- SEARCH ACCESSOR ---
// Exposes the search box from PackScreen so we can check if a search is active.
// This allows the mod to decide whether to show the folder tree or show all search results.
@Mixin(PackScreen.class)
public interface PackScreenSearchAccessor {

    // Grabs the private 'searchBox' field from the vanilla PackScreen class
    @Accessor("searchBox")
    TextFieldWidget getSearchBox();
}