package net.naw.resource_tree.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

// --- THE CUSTOM SCREEN ---
// Our custom resource pack screen that extends vanilla's PackScreen.
// Adds folder navigation by tracking which folder the user is currently browsing.
public class ResourceTreeScreen extends PackSelectionScreen {

    // --- GLOBAL FILTER ---
    // The current folder being displayed in the available packs list.
    // null means we're at the root resourcepacks folder.
    // Used by ResourcePackOrganizerMixin (a Mixin) to filter which packs are shown.
    public static String currentFilterFolder = null;

    // --- MENU REFERENCES ---
    // Exposed so extractRenderState can block mouse when menus are open over pack entries.
    public static DropdownMenuWidget activeDropdown = null;
    public static SortButton activeSortButton = null;

    private final File rootFolder;   // The root resourcepacks directory
    private File currentFolder;      // The folder currently being browsed

    public ResourceTreeScreen(PackRepository resourcePackManager, Consumer<PackRepository> applier, Path file) {
        super(resourcePackManager, applier, file, Component.translatable("resourcePack.title"));
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
        ((net.naw.resource_tree.mixin.PackScreenAccessor)this).invokeReload();
    }

    // --- RENDERING ---
    // Draws the color palette and drag preview on top of everything else.
    // Passes -1,-1 as mouse coords to vanilla when our menus are open and blocking,
    // so vanilla doesn't highlight pack entries behind our dropdown/sort menus.
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean dropdownBlocking = activeDropdown != null
                && activeDropdown.isOpen()
                && activeDropdown.isMouseOver(mouseX, mouseY);
        boolean sortBlocking = activeSortButton != null
                && activeSortButton.isOpen()
                && activeSortButton.isMouseOver(mouseX, mouseY);
        boolean paletteBlocking = FolderColorPalette.INSTANCE.isVisible()
                && FolderColorPalette.INSTANCE.isMouseOver(mouseX, mouseY);

        // "Trick" vanilla render by hiding real mouse position when our menus are on top
        int effectiveX = (dropdownBlocking || sortBlocking || paletteBlocking) ? -1 : mouseX;
        int effectiveY = (dropdownBlocking || sortBlocking || paletteBlocking) ? -1 : mouseY;

        super.extractRenderState(graphics, effectiveX, effectiveY, a);

        // Re-render our widgets with real mouse coords so hover feedback still works
        if (activeDropdown != null) activeDropdown.extractRenderState(graphics, mouseX, mouseY, a);
        if (activeSortButton != null) activeSortButton.extractRenderState(graphics, mouseX, mouseY, a);

        FolderColorPalette.INSTANCE.render(graphics, mouseX, mouseY);
        DragDropManager.renderDrag(graphics, mouseX, mouseY);
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