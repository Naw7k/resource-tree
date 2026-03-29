package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- ENTRY ACCESSOR ---
// Exposes the private 'pack' field inside ResourcePackEntry so we can read
// which Pack object a given entry represents.
// This is needed to detect if an entry in the list is a FolderPack or a regular pack.
@Mixin(PackListWidget.ResourcePackEntry.class)
public interface ResourcePackEntryAccessor {

    // Grabs the private 'pack' field from the ResourcePackEntry inner class
    @Accessor("pack")
    ResourcePackOrganizer.Pack getPack();
}