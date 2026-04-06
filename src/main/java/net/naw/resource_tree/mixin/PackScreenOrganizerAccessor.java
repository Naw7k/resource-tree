package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- DATA ACCESSOR ---
// Exposes the PackSelectionModel from PackSelectionScreen so we can enable/disable packs directly.
// This allows our custom folder logic to "talk" to the part of Minecraft that moves packs.
@Mixin(PackSelectionScreen.class)
public interface PackScreenOrganizerAccessor {

    // Grabs the private 'model' field from the vanilla PackSelectionScreen class
    @Accessor("model")
    PackSelectionModel getOrganizer();
}