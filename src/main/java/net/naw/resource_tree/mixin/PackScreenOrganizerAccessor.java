package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- DATA ACCESSOR ---
// Exposes the ResourcePackOrganizer from PackScreen so we can enable/disable packs directly.
// This allows our custom folder logic to "talk" to the part of Minecraft that moves packs.
@Mixin(PackScreen.class)
public interface PackScreenOrganizerAccessor {

    // Grabs the private 'organizer' field from the vanilla PackScreen class
    @Accessor("organizer")
    ResourcePackOrganizer getOrganizer();
}