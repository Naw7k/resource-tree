package net.naw.resource_tree.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.naw.resource_tree.client.DragDropManager;
import net.naw.resource_tree.client.FolderColorPalette;
import net.naw.resource_tree.client.FolderPack;
import net.naw.resource_tree.client.ResourceTreeConfig;
import net.naw.resource_tree.client.ResourceTreeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import java.io.File;

// --- ENTRY INTERACTION MIXIN ---
// This handles what happens when you click a folder in the list
// and manages the "Hide Pack Warnings" feature.
@Mixin(TransferableSelectionList.PackEntry.class)
public class ResourcePackEntryMixin {

    // --- MOUSE CLICK HANDLING ---
    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        PackSelectionModel.Entry pack = ((ResourcePackEntryAccessor)this).getPack();

        if (!(pack instanceof FolderPack folderPack)) {
            // --- SHIFT+CLICK TO ENABLE ---
            // shift+click system that correctly accounts for folder entries
            if (click.button() == 0 && click.hasShiftDown() && !DragDropManager.isDragging()) {
                if (pack.canSelect()) {
                    pack.select();
                    // Refresh the screen
                    Minecraft client = Minecraft.getInstance();
                    if (client.screen instanceof ResourceTreeScreen rts) {
                        rts.navigateTo(rts.getCurrentFolder());
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (click.button() == 0 && !DragDropManager.isDragging()) {
                String name = pack.getId();
                if (name.startsWith("file/")) {
                    Minecraft client = Minecraft.getInstance();
                    if (client.screen instanceof ResourceTreeScreen resourceTreeScreen) {
                        String relativePath = name.substring("file/".length()).replace("/", File.separator);
                        File packFile = new File(resourceTreeScreen.getRootFolder(), relativePath);
                        if (packFile.exists()) {
                            TransferableSelectionList.PackEntry thisEntry = (TransferableSelectionList.PackEntry)(Object)this;
                            DragDropManager.startPress((int) click.x(), (int) click.y(), packFile, thisEntry);
                        }
                    }
                }
            }
            return;
        }

        if (FolderColorPalette.INSTANCE.isRenaming()
                && folderPack.getRelativePath().equals(FolderColorPalette.INSTANCE.getRenamingPath())) {
            FolderColorPalette.INSTANCE.commitRename();
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == 1 && !folderPack.isBack()) {
            FolderColorPalette.INSTANCE.show(
                    (int) click.x(), (int) click.y(),
                    folderPack.getRelativePath()
            );
            cir.setReturnValue(true);
            return;
        }

        if (!doubled) {
            // --- SELECTION HIGHLIGHT ON SINGLE CLICK ---
            // Manually set this folder as selected so the white outline shows
            if (click.button() == 0) {
                Minecraft client = Minecraft.getInstance();
                if (client.screen instanceof PackSelectionScreen screen) {
                    TransferableSelectionList availableList =
                            ((PackScreenAvailableListAccessor) screen).getAvailablePackList();
                    if (availableList != null) {
                        availableList.setSelected((TransferableSelectionList.Entry)(Object)this);
                    }
                }
            }
            return;
        }
        folderPack.select();
        cir.setReturnValue(true);
    }

    // --- ESSENTIAL COMPATIBILITY ---
    // Prevents Essential mod from treating folder entries and blank placeholders as draggable
    @Inject(at = @At("TAIL"), method = "mouseClicked", cancellable = true)
    private void onMouseClickedTailEssential(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricLoader.getInstance().isModLoaded("essential")) return;
        PackSelectionModel.Entry pack = ((ResourcePackEntryAccessor)this).getPack();
        if (pack instanceof FolderPack || pack.getId().equals("blank:placeholder")) {
            cir.setReturnValue(false);
        }
    }

    // --- HIDE BLANK ENTRY ---
    // Prevents the blank placeholder entry from rendering anything
    @Inject(at = @At("HEAD"), method = "extractContent", cancellable = true)
    private void onRenderBlank(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float deltaTicks, CallbackInfo ci) {
        PackSelectionModel.Entry pack = ((ResourcePackEntryAccessor)this).getPack();
        if (pack.getId().equals("blank:placeholder")) {
            ci.cancel();
        }
    }

    // --- RENDER INJECT ---
    // Updates mouse position every frame and highlights folder if being hovered during drag
    @Inject(at = @At("TAIL"), method = "extractContent")
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float deltaTicks, CallbackInfo ci) {
        PackSelectionModel.Entry pack = ((ResourcePackEntryAccessor)this).getPack();
        TransferableSelectionList.PackEntry entry = (TransferableSelectionList.PackEntry)(Object)this;

        // --- DRAG TRANSPARENT OVERLAY ---
        // Paint over this entry while dragging to mimic transparency
        // Skip if Essential is installed as it handles its own ghost rendering
        if (DragDropManager.isDragging() && !DragDropManager.isRenderingGhost() &&
                !FabricLoader.getInstance().isModLoaded("essential") &&
                DragDropManager.getDragEntry() == (Object)this) {
            context.fill(
                    entry.getContentX() - 1,
                    entry.getContentY() - 1,
                    entry.getContentRight() + 1,
                    entry.getContentBottom() + 1,
                    0x99000000
            );
            return;
        }

        // --- UPDATE DRAG MOUSE POSITION ---
        if (DragDropManager.isPressing() || DragDropManager.isDragging()) {
            DragDropManager.updateMouse(mouseX, mouseY);
        }

        // --- FOLDER HOVER HIGHLIGHT ---
        // If dragging and hovering over a folder, highlight it as a drop target
        if (DragDropManager.isDragging() && pack instanceof FolderPack folderPack) {
            if (hovered) {
                DragDropManager.setHoveredFolder(folderPack.getFolder());
                context.fill(entry.getContentX() - 1, entry.getContentY() - 1, entry.getContentRight() + 1, entry.getContentBottom() + 1, 0x5500FF00);
            } else if (folderPack.getFolder().equals(DragDropManager.getHoveredFolder())) {
                DragDropManager.clearHoveredFolder();
            }
        }

        // --- INLINE RENAME RENDERING ---
        // If this folder is being renamed, draw a text input over the name area with scroll support
        if (pack instanceof FolderPack folderPack
                && FolderColorPalette.INSTANCE.isRenaming()
                && folderPack.getRelativePath().equals(FolderColorPalette.INSTANCE.getRenamingPath())) {

            int textX = entry.getContentX() + 34;
            int textY = entry.getContentY();
            int boxW = entry.getContentRight() - entry.getContentX() - 4;

            context.fill(textX - 2, textY - 2, textX + boxW - 28, textY - 1, 0xFF88AAFF);
            context.fill(textX - 2, textY + 10, textX + boxW - 28, textY + 11, 0xFF88AAFF);
            context.fill(textX - 2, textY - 2, textX - 1, textY + 11, 0xFF88AAFF);
            context.fill(textX + boxW - 29, textY - 2, textX + boxW - 28, textY + 11, 0xFF88AAFF);

            var tr = Minecraft.getInstance().font;
            String buffer = FolderColorPalette.INSTANCE.getRenameBuffer();
            int cursorPos = FolderColorPalette.INSTANCE.getCursorPos();
            int scrollOffset = FolderColorPalette.INSTANCE.getScrollOffset();

            String beforeCursor = buffer.substring(0, cursorPos);
            String afterCursor = buffer.substring(cursorPos);
            boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;

            int drawX = textX - scrollOffset;
            context.enableScissor(textX, textY - 2, textX + boxW - 28, textY + 12);
            context.text(tr, net.minecraft.network.chat.Component.literal(beforeCursor), drawX, textY + 1, 0xFFFFFFFF);
            if (showCursor) {
                int cursorDrawX = drawX + tr.width(beforeCursor);
                context.text(tr, net.minecraft.network.chat.Component.literal("§7|"), cursorDrawX, textY + 1, 0xFFFFFFFF);
            }
            context.text(tr, net.minecraft.network.chat.Component.literal(afterCursor), drawX + tr.width(beforeCursor) + (showCursor ? tr.width("|") : 0), textY + 1, 0xFFFFFFFF);
            context.disableScissor();
            return;
        }

        // --- HIDE WARNING FALLBACK ---
        if (!ResourceTreeConfig.hideIncompatiblePacks) return;
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) return;
        if (pack.getCompatibility().isCompatible()) return;

        context.fill(entry.getContentX() - 1, entry.getContentY() - 1, entry.getContentRight() + 1, entry.getContentBottom() + 1, 0x00000000);
    }

    // --- HIDE ARROWS WHILE DRAGGING ---
    // Hides hover overlay (arrows) on regular packs while dragging.
    // Folders are exempt — they don't have move arrows anyway.
    @ModifyReturnValue(method = "showHoverOverlay", at = @At("RETURN"))
    private boolean hideArrowsWhileDragging(boolean original) {
        if (DragDropManager.isDragging() || DragDropManager.isRenderingGhost()) {
            PackSelectionModel.Entry pack = ((ResourcePackEntryAccessor)this).getPack();
            if (!(pack instanceof FolderPack)) return false;
        }
        return original;
    }

    // --- HIDE ICON TINT WHILE DRAGGING ---
    // Hides the dark hover tint on pack icons while dragging so they look cleaner.
    @Redirect(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), require = 0)
    private void redirectFill(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        // -1601138544 is the vanilla hover tint color
        if (DragDropManager.isDragging() && color == -1601138544 &&
                !(((ResourcePackEntryAccessor)this).getPack() instanceof FolderPack)) return;
        context.fill(x1, y1, x2, y2, color);
    }

    // --- COMPATIBILITY WARNING REDIRECTS ---
    // When "Hide Pack Warnings" is on, we trick the game into thinking the pack is compatible.
    // require=0 ensures we don't crash if another mod (like 'no-resource-pack-warnings') got here first.
    @Redirect(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/PackCompatibility;isCompatible()Z"), require = 0)
    private boolean redirectIsCompatible(PackCompatibility compatibility) {
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) return compatibility.isCompatible();
        return ResourceTreeConfig.hideIncompatiblePacks || compatibility.isCompatible();
    }

    // Prevents the "Are you sure? This pack is old!" popup from appearing when enabled.
    @Redirect(method = "handlePackSelection", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/PackCompatibility;isCompatible()Z"), require = 0)
    private boolean redirectIsCompatibleEnable(PackCompatibility compatibility) {
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) return compatibility.isCompatible();
        return ResourceTreeConfig.hideIncompatiblePacks || compatibility.isCompatible();
    }
}