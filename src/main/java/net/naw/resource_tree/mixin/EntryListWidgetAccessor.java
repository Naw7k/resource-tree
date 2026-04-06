package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// --- LIST WIDGET ACCESSOR ---
// Exposes the underlying list of entries (children) from the vanilla AbstractSelectionList.
// This is used by the DragDropManager to find which pack is under the mouse during a drag.
@Mixin(AbstractSelectionList.class)
public interface EntryListWidgetAccessor {

    // Grabs the private 'children' list.
    @Accessor("children")
    List<?> getChildren();
}