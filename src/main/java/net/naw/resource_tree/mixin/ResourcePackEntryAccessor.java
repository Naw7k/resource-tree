package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- ENTRY ACCESSOR ---
// Exposes the private 'pack' field inside PackEntry so we can read
// which Entry object a given entry represents.
// This is needed to detect if an entry in the list is a FolderPack or a regular pack.
@Mixin(TransferableSelectionList.PackEntry.class)
public interface ResourcePackEntryAccessor {

    // Grabs the private 'pack' field from the PackEntry inner class
    @Accessor("pack")
    PackSelectionModel.Entry getPack();
}