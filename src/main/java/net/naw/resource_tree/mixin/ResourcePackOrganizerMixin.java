package net.naw.resource_tree.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.resource.ResourcePackSource;
import net.naw.resource_tree.client.ResourceTreeConfig;
import net.naw.resource_tree.client.ResourceTreeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

// --- DYNAMIC FILTER MIXIN ---
// Mixin to the ResourcePackOrganizer to handle dynamic filtering of the pack list.
// This is the "brain" that hides/shows packs based on folders and config toggles.
@Mixin(ResourcePackOrganizer.class)
public class ResourcePackOrganizerMixin {

    // --- PACK LIST FILTERING ---
    // Injects into the end of getDisabledPacks to apply our custom filters.
    // This controls which packs are visible in the left-hand "Available" column.
    @Inject(at = @At("RETURN"), method = "getDisabledPacks", cancellable = true)
    private void onGetDisabledPacks(CallbackInfoReturnable<Stream<ResourcePackOrganizer.Pack>> cir) {
        final String folder = ResourceTreeScreen.currentFilterFolder;

        // --- BUILT-IN FILTER ---
        // Logic for hiding 'Built-in' packs (High Contrast, Programmer Art, etc.)
        if (ResourceTreeConfig.hideBuiltinPacks) {
            cir.setReturnValue(cir.getReturnValue().filter(pack ->
                    pack.getSource() != ResourcePackSource.BUILTIN));
        }

        // --- SEARCH OVERRIDE ---
        // Feature: Search Inside Folders.
        // If searching, we stop filtering by folder so the user can find packs hidden in sub-directories.
        if (ResourceTreeConfig.searchInsideFolders && isSearchActive()) {
            return;
        }

        // --- FOLDER FILTERING LOGIC ---
        // Ensures only packs inside the CURRENT active folder are displayed.
        if (folder == null) {
            // Root View: Only show packs that are NOT inside a sub-folder string (no "/")
            cir.setReturnValue(cir.getReturnValue().filter(pack -> {
                String name = pack.getName();
                if (!name.startsWith("file/")) return true;
                String relativePath = name.substring("file/".length());
                return !relativePath.contains("/");
            }));
        } else {
            // Folder View: Only show packs that strictly match the current sub-folder path
            cir.setReturnValue(cir.getReturnValue().filter(pack -> {
                String name = pack.getName();
                final String prefix = "file/" + folder + "/";
                if (!name.startsWith(prefix)) return false;
                String relativePath = name.substring(prefix.length());
                return !relativePath.contains("/");
            }));
        }
    }

    // --- SEARCH CHECKER ---
    // Utility to check if the user is currently typing in the search bar.
    // Uses an Accessor to grab the searchBox from the current PackScreen.
    @Unique
    private boolean isSearchActive() {
        if (MinecraftClient.getInstance().currentScreen instanceof PackScreen screen) {
            TextFieldWidget searchBox = ((PackScreenSearchAccessor) screen).getSearchBox();
            return searchBox != null && !searchBox.getText().isEmpty();
        }
        return false;
    }
}