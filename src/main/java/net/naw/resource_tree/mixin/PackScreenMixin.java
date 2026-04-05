package net.naw.resource_tree.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.naw.resource_tree.client.DragDropManager;
import net.naw.resource_tree.client.DropdownMenuWidget;
import net.naw.resource_tree.client.FolderColorPalette;
import net.naw.resource_tree.client.NewFolderButton;
import net.naw.resource_tree.client.SortButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- UI INJECTOR ---
// This Mixin attaches the custom Dropdown and Color Palette to the vanilla PackScreen.
@Mixin(PackScreen.class)
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
        this.addDrawableChild(this.dropdownMenu);
        this.newFolderButton = new NewFolderButton(0, 0);
        this.addDrawableChild(this.newFolderButton);
        this.sortButton = new SortButton(0, 0);
        this.addDrawableChild(this.sortButton);
        searchAndAlign(this);
    }

    // Ensures the buttons stay in the correct spot if the window is resized.
    @Inject(at = @At("TAIL"), method = "refreshWidgetPositions")
    private void onRefreshWidgetPositions(CallbackInfo ci) {
        if (this.dropdownMenu == null) return;
        searchAndAlign(this);
    }

    // --- INPUT HANDLING ---
    // Overrides the mouse click logic so our custom menus get priority over vanilla buttons.
    public boolean mouseClicked(Click click, boolean bl) {
        if (FolderColorPalette.INSTANCE.isRenaming()) {
            FolderColorPalette.INSTANCE.keyPressed(256); // treat any click as Escape
            return true;
        }
        // Palette gets first priority for clicks
        if (folderColorPalette.mouseClicked(click.x(), click.y())) return true;
        // Then the options dropdown
        if (dropdownMenu != null && dropdownMenu.mouseClicked(click.x(), click.y(), 0)) return true;
        // Then the new folder button
        if (newFolderButton != null && newFolderButton.mouseClicked(click.x(), click.y(), 0)) return true;
        // Then the sort button
        if (sortButton != null && sortButton.mouseClicked(click.x(), click.y(), 0)) return true;
        return super.mouseClicked(click, bl);
    }

    // --- MOUSE RELEASE ---
    // When the user releases the mouse, check if we were dragging and handle the drop
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && (DragDropManager.isDragging() || DragDropManager.isPressing())) {
            DragDropManager.handleDrop((int) click.x(), (int) click.y());
            return true;
        }
        return super.mouseReleased(click);
    }

    // --- KEYBOARD INPUT ---
    // Routes keyboard input to the palette when it's in rename mode
    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (folderColorPalette.charTyped((char) input.codepoint())) return true;
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (folderColorPalette.keyPressed(input.key())) return true;
        return super.keyPressed(input);
    }

    // --- RENDERING ---
    // Handles the "blocking" logic so hovering over our menus doesn't highlight buttons behind them.
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean dropdownBlocking = dropdownMenu != null && dropdownMenu.isOpen() && dropdownMenu.isMouseOver(mouseX, mouseY);
        boolean paletteBlocking = FolderColorPalette.INSTANCE.isVisible() && FolderColorPalette.INSTANCE.isMouseOver(mouseX, mouseY);
        boolean sortBlocking = sortButton != null && sortButton.isOpen() && sortButton.isMouseOver(mouseX, mouseY);

        if (dropdownBlocking || paletteBlocking || sortBlocking) {
            // "Tricks" the super.render by passing -1 to hide the real mouse position
            super.render(context, -1, -1, delta);
            if (dropdownBlocking) dropdownMenu.render(context, mouseX, mouseY, delta);
            if (sortBlocking) sortButton.render(context, mouseX, mouseY, delta);
        } else {
            super.render(context, mouseX, mouseY, delta);
        }
        // Always draw the palette last so it sits on top
        FolderColorPalette.INSTANCE.render(context, mouseX, mouseY);

        // --- DRAG PREVIEW ---
        // Draw the floating pack name following the cursor while dragging
        DragDropManager.renderDrag(context, mouseX, mouseY);
    }

    // --- ALIGNMENT LOGIC ---
    // Recursively searches for the "Open Pack Folder" button to place our buttons next to it.
    // Sort button anchors to the left of the search box.
    @Unique
    private void searchAndAlign(ParentElement parent) {
        // --- SORT BUTTON ANCHOR ---
        // Place sort button to the left of the search box
        net.minecraft.client.gui.widget.TextFieldWidget searchBox =
                ((PackScreenSearchAccessor) this).getSearchBox();
        if (searchBox != null && sortButton != null) {
            sortButton.setX(searchBox.getX() - 18);
            sortButton.setY(searchBox.getY());
        }

        // --- BOTTOM BUTTONS ANCHOR ---
        for (Element child : parent.children()) {
            if (child instanceof ClickableWidget widget) {
                if (widget.getMessage().getString().equals(
                        Text.translatable("pack.openFolder").getString())) {
                    this.dropdownMenu.setX(widget.getX() - 24);
                    this.dropdownMenu.setY(widget.getY());
                    this.newFolderButton.setX(widget.getX() - 48);
                    this.newFolderButton.setY(widget.getY());
                    return;
                }
            }
            if (child instanceof ParentElement subParent) {
                searchAndAlign(subParent);
            }
        }
    }
}
