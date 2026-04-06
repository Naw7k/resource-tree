package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.naw.resource_tree.client.DragDropManager;
import net.naw.resource_tree.client.DropdownMenuWidget;
import net.naw.resource_tree.client.FolderColorPalette;
import net.naw.resource_tree.client.NewFolderButton;
import net.naw.resource_tree.client.ResourceTreeScreen;
import net.naw.resource_tree.client.SortButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- UI INJECTOR ---
// This Mixin attaches the custom Dropdown and Color Palette to the vanilla PackSelectionScreen.
@Mixin(PackSelectionScreen.class)
public abstract class PackScreenMixin extends Screen {

    @Unique private DropdownMenuWidget dropdownMenu;
    @Unique private NewFolderButton newFolderButton;
    @Unique private SortButton sortButton;
    @Unique private final FolderColorPalette folderColorPalette = FolderColorPalette.INSTANCE;

    protected PackScreenMixin() { super(null); }

    // --- INITIALIZATION ---
    // Injects into the end of the init() method to create and add the dropdown, new folder, and sort buttons.
    @Inject(at = @At("TAIL"), method = "init")
    private void onInit(CallbackInfo ci) {
        this.dropdownMenu = new DropdownMenuWidget(0, 0);
        this.addRenderableWidget(this.dropdownMenu);
        this.newFolderButton = new NewFolderButton(0, 0);
        this.addRenderableWidget(this.newFolderButton);
        this.sortButton = new SortButton(0, 0);
        this.addRenderableWidget(this.sortButton);
        // Expose to ResourceTreeScreen so it can block mouse when menus are open
        ResourceTreeScreen.activeDropdown = this.dropdownMenu;
        ResourceTreeScreen.activeSortButton = this.sortButton;
        searchAndAlign(this);
    }

    // Ensures the buttons stay in the correct spot if the window is resized.
    @Inject(at = @At("TAIL"), method = "repositionElements")
    private void onRefreshWidgetPositions(CallbackInfo ci) {
        if (this.dropdownMenu == null) return;
        searchAndAlign(this);
    }

    // --- INPUT HANDLING ---
    // Overrides the mouse click logic so our custom menus get priority over vanilla buttons.
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (FolderColorPalette.INSTANCE.isRenaming()) {
            FolderColorPalette.INSTANCE.keyPressed(256); // treat any click as Escape
            return true;
        }
        // Palette gets first priority for clicks
        if (folderColorPalette.mouseClicked(event.x(), event.y())) return true;
        // Then the options dropdown
        if (dropdownMenu != null && dropdownMenu.mouseClicked(event.x(), event.y(), 0)) return true;
        // Then the new folder button
        if (newFolderButton != null && newFolderButton.mouseClicked(event.x(), event.y(), 0)) return true;
        // Then the sort button
        if (sortButton != null && sortButton.mouseClicked(event.x(), event.y(), 0)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    // --- MOUSE RELEASE ---
    // When the user releases the mouse, check if we were dragging and handle the drop
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && (DragDropManager.isDragging() || DragDropManager.isPressing())) {
            DragDropManager.handleDrop((int) event.x(), (int) event.y());
            return true;
        }
        return super.mouseReleased(event);
    }

    // --- KEYBOARD INPUT ---
    // Routes keyboard input to the palette when it's in rename mode
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (folderColorPalette.charTyped((char) event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (folderColorPalette.keyPressed(event.key())) return true;
        return super.keyPressed(event);
    }

    // --- ALIGNMENT LOGIC ---
    // Recursively searches for the "Open Pack Folder" button to place our buttons next to it.
    // Sort button anchors to the left of the search box.
    @Unique
    private void searchAndAlign(ContainerEventHandler parent) {
        // --- SORT BUTTON ANCHOR ---
        // Place sort button to the left of the search box
        EditBox searchBox = ((PackScreenSearchAccessor) this).getSearchBox();
        if (searchBox != null && sortButton != null) {
            sortButton.setX(searchBox.getX() - 18);
            sortButton.setY(searchBox.getY());
        }

        // --- BOTTOM BUTTONS ANCHOR ---
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getMessage().getString().equals(
                        Component.translatable("pack.openFolder").getString())) {
                    this.dropdownMenu.setX(widget.getX() - 24);
                    this.dropdownMenu.setY(widget.getY());
                    this.newFolderButton.setX(widget.getX() - 48);
                    this.newFolderButton.setY(widget.getY());
                    return;
                }
            }
            if (child instanceof ContainerEventHandler subParent) {
                searchAndAlign(subParent);
            }
        }
    }
}