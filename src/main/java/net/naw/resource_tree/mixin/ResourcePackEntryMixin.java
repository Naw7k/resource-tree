package net.naw.resource_tree.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackCompatibility;
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

import java.io.File;

// --- ENTRY INTERACTION MIXIN ---
// This handles what happens when you click a folder in the list
// and manages the "Hide Pack Warnings" feature.
@Mixin(PackListWidget.ResourcePackEntry.class)
public class ResourcePackEntryMixin {

    // --- MOUSE CLICK HANDLING ---
    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();

        if (!(pack instanceof FolderPack folderPack)) {
            // --- SHIFT+CLICK TO ENABLE ---
            // Our own shift+click system that correctly accounts for folder entries
            // This also blocks Essential's shift+click which gets the wrong pack index
            if (click.button() == 0 && click.hasShift() && !DragDropManager.isDragging()) {
                if (pack.canBeEnabled()) {
                    pack.enable();
                    // Refresh the screen
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.currentScreen instanceof net.minecraft.client.gui.screen.pack.PackScreen screen) {
                        ((PackScreenAccessor) screen).invokeUpdatePackLists(null);
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (click.button() == 0 && !DragDropManager.isDragging()) {
                String name = pack.getName();
                if (name.startsWith("file/")) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.currentScreen instanceof ResourceTreeScreen resourceTreeScreen) {
                        String relativePath = name.substring("file/".length()).replace("/", File.separator);
                        File packFile = new File(resourceTreeScreen.getRootFolder(), relativePath);
                        if (packFile.exists()) {
                            PackListWidget.ResourcePackEntry thisEntry = (PackListWidget.ResourcePackEntry)(Object)this;
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
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.currentScreen instanceof net.minecraft.client.gui.screen.pack.PackScreen screen) {
                    net.minecraft.client.gui.screen.pack.PackListWidget availableList =
                            ((PackScreenAvailableListAccessor) screen).getAvailablePackList();
                    if (availableList != null) {
                        availableList.setSelected((net.minecraft.client.gui.screen.pack.PackListWidget.Entry)(Object)this);
                    }
                }
            }
            return;
        }
        folderPack.enable();
        cir.setReturnValue(true);
    }

    // --- ESSENTIAL COMPATIBILITY ---
    // Prevents Essential mod from treating folder entries and blank placeholders as draggable
    @Inject(at = @At("TAIL"), method = "mouseClicked", cancellable = true)
    private void onMouseClickedTailEssential(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("essential")) return;
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();
        if (pack instanceof FolderPack || pack.getName().equals("blank:placeholder")) {
            cir.setReturnValue(false);
        }
    }


    // --- HIDE BLANK ENTRY ---
    // Prevents the blank placeholder entry from rendering anything
    @Inject(at = @At("HEAD"), method = "render", cancellable = true)
    private void onRenderBlank(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks, CallbackInfo ci) {
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();
        if (pack.getName().equals("blank:placeholder")) {
            ci.cancel();
        }
    }
    
    // --- RENDER INJECT ---
    // Updates mouse position every frame and highlights folder if being hovered during drag
    @Inject(at = @At("TAIL"), method = "render")
    private void onRender(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks, CallbackInfo ci) {
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();
        PackListWidget.ResourcePackEntry entry = (PackListWidget.ResourcePackEntry)(Object)this;

        // --- DRAG TRANSPARENT OVERLAY ---
        // Paint over this entry while dragging to mimic transparency
        // Skip if Essential is installed as it handles its own ghost rendering
        if (DragDropManager.isDragging() && !DragDropManager.isRenderingGhost() &&
                !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("essential") &&
                DragDropManager.getDragEntry() == (Object)this) {
            context.fill(
                    entry.getContentX() - 1,
                    entry.getContentY() - 1,
                    entry.getContentRightEnd() + 1,
                    entry.getContentBottomEnd() + 1,
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
                context.fill(entry.getContentX() - 1, entry.getContentY() - 1, entry.getContentRightEnd() + 1, entry.getContentBottomEnd() + 1, 0x5500FF00);
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
            int boxW = entry.getContentRightEnd() - entry.getContentX() - 4;

            context.fill(textX - 2, textY - 2, textX + boxW - 28, textY - 1, 0xFF88AAFF);
            context.fill(textX - 2, textY + 10, textX + boxW - 28, textY + 11, 0xFF88AAFF);
            context.fill(textX - 2, textY - 2, textX - 1, textY + 11, 0xFF88AAFF);
            context.fill(textX + boxW - 29, textY - 2, textX + boxW - 28, textY + 11, 0xFF88AAFF);

            var tr = MinecraftClient.getInstance().textRenderer;
            String buffer = FolderColorPalette.INSTANCE.getRenameBuffer();
            int cursorPos = FolderColorPalette.INSTANCE.getCursorPos();
            int scrollOffset = FolderColorPalette.INSTANCE.getScrollOffset();

            String beforeCursor = buffer.substring(0, cursorPos);
            String afterCursor = buffer.substring(cursorPos);
            boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;

            int drawX = textX - scrollOffset;
            context.enableScissor(textX, textY - 2, textX + boxW - 28, textY + 12);
            context.drawTextWithShadow(tr, net.minecraft.text.Text.of(beforeCursor), drawX, textY + 1, 0xFFFFFFFF);
            if (showCursor) {
                int cursorDrawX = drawX + tr.getWidth(beforeCursor);
                context.drawTextWithShadow(tr, net.minecraft.text.Text.of("§7|"), cursorDrawX, textY + 1, 0xFFFFFFFF);
            }
            context.drawTextWithShadow(tr, net.minecraft.text.Text.of(afterCursor), drawX + tr.getWidth(beforeCursor) + (showCursor ? tr.getWidth("|") : 0), textY + 1, 0xFFFFFFFF);
            context.disableScissor();
            return;
        }

        // --- HIDE WARNING FALLBACK ---
        if (!ResourceTreeConfig.hideIncompatiblePacks) return;
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) return;
        if (pack.getCompatibility().isCompatible()) return;

        context.fill(entry.getContentX() - 1, entry.getContentY() - 1, entry.getContentRightEnd() + 1, entry.getContentBottomEnd() + 1, 0x00000000);
    }

    // --- HIDE ARROW ON GHOST AND ALL PACKS WHILE DRAGGING ---
    // Hides arrows on all regular packs while dragging — only folder arrows still show
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"), require = 0)
    private void redirectDrawGuiTexture(DrawContext context, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                        net.minecraft.util.Identifier texture, int x, int y, int width, int height) {
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();
        // Hide arrows on all regular packs while dragging
        if (DragDropManager.isDragging() && !(pack instanceof FolderPack)) return;
        // Hide arrows on the ghost entry
        if (DragDropManager.isRenderingGhost()) return;
        context.drawGuiTexture(pipeline, texture, x, y, width, height);
    }

    // --- HIDE ICON TINT WHILE DRAGGING ---
    // Hides the dark hover tint on pack icons while dragging
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void redirectFill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        // -1601138544 is the vanilla hover tint color
        if (DragDropManager.isDragging() && color == -1601138544 &&
                !(((ResourcePackEntryAccessor)this).getPack() instanceof FolderPack)) return;
        context.fill(x1, y1, x2, y2, color);
    }

    // --- COMPATIBILITY WARNING REDIRECTS ---
    // When "Hide Pack Warnings" is on, we trick the game into thinking the pack is compatible.
    // require=0 ensures we don't crash if another mod (like 'no-resource-pack-warnings') got here first.
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ResourcePackCompatibility;isCompatible()Z"), require = 0)
    private boolean redirectIsCompatible(ResourcePackCompatibility compatibility) {
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) return compatibility.isCompatible();
        return ResourceTreeConfig.hideIncompatiblePacks || compatibility.isCompatible();
    }

    // Prevents the "Are you sure? This pack is old!" popup from appearing when enabled.
    @Redirect(method = "enable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ResourcePackCompatibility;isCompatible()Z"), require = 0)
    private boolean redirectIsCompatibleEnable(ResourcePackCompatibility compatibility) {
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) return compatibility.isCompatible();
        return ResourceTreeConfig.hideIncompatiblePacks || compatibility.isCompatible();
    }
}
