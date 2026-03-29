package net.naw.resource_tree.client;

import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import net.naw.resource_tree.mixin.PackScreenAccessor;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

// --- THE CUSTOM SCREEN ---
// Our custom resource pack screen that extends vanilla's PackScreen.
// Adds folder navigation by tracking which folder the user is currently browsing.
public class ResourceTreeScreen extends PackScreen {

    // --- GLOBAL FILTER ---
    // The current folder being displayed in the available packs list.
    // null means we're at the root resourcepacks folder.
    // Used by ResourcePackOrganizerMixin (a Mixin) to filter which packs are shown.
    public static String currentFilterFolder = null;

    private final File rootFolder;   // The root resourcepacks directory
    private File currentFolder;      // The folder currently being browsed

    public ResourceTreeScreen(ResourcePackManager resourcePackManager, Consumer<ResourcePackManager> applier, Path file) {
        super(resourcePackManager, applier, file, Text.translatable("resourcePack.title"));
        this.rootFolder = file.toFile();
        this.currentFolder = rootFolder;
    }

    @Override
    protected void init() {
        // Update the filter before vanilla builds the pack lists
        updateFilter();
        super.init();
    }

    // --- NAVIGATION LOGIC ---
    // Navigates into a folder and refreshes the pack lists without rebuilding the whole screen.
    // This makes the transition feel smooth instead of flashing the screen.
    public void navigateTo(File folder) {
        this.currentFolder = folder;
        updateFilter();

        // Tells the vanilla screen to refresh its lists using our new filter
        ((PackScreenAccessor)this).invokeUpdatePackLists(null);
    }

    // --- FILTER UPDATER ---
    // Updates the static filter folder used by ResourcePackOrganizerMixin.
    // Converts a deep path into a clean relative path like "Nature/Trees".
    private void updateFilter() {
        if (currentFolder.equals(rootFolder)) {
            currentFilterFolder = null;
        } else {
            currentFilterFolder = rootFolder.toPath().relativize(currentFolder.toPath()).toString().replace("\\", "/");
        }
    }

    // Standard getters for accessing folder info from other classes
    public File getRootFolder() { return rootFolder; }
    public File getCurrentFolder() { return currentFolder; }
}