package net.naw.resource_tree.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackCompatibility;
import net.naw.resource_tree.client.FolderColorPalette;
import net.naw.resource_tree.client.FolderPack;
import net.naw.resource_tree.client.ResourceTreeConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// --- ENTRY INTERACTION MIXIN ---
// This handles what happens when you click a folder in the list
// and manages the "Hide Pack Warnings" feature.
@Mixin(PackListWidget.ResourcePackEntry.class)
public class ResourcePackEntryMixin {

    // --- MOUSE CLICK HANDLING ---
    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();

        // If the entry isn't a folder, let vanilla handle it
        if (!(pack instanceof FolderPack folderPack)) return;

        // If we're currently renaming this folder, commit on click
        if (FolderColorPalette.INSTANCE.isRenaming()
                && folderPack.getRelativePath().equals(FolderColorPalette.INSTANCE.getRenamingPath())) {
            FolderColorPalette.INSTANCE.commitRename();
            cir.setReturnValue(true);
            return;
        }

        // Right-Click (button 1): Opens the Color Palette for the folder
        if (click.button() == 1 && !folderPack.isBack()) {
            FolderColorPalette.INSTANCE.show(
                    (int) click.x(), (int) click.y(),
                    folderPack.getRelativePath()
            );
            cir.setReturnValue(true);
            return;
        }

        // Double-Click: "Enter" the folder
        if (!doubled) return;
        folderPack.enable();
        cir.setReturnValue(true);
    }

    // --- COMPATIBILITY WARNING REDIRECTS ---
    // When "Hide Pack Warnings" is on, we trick the game into thinking the pack is compatible.
    // require=0 ensures we don't crash if another mod (like 'no-resource-pack-warnings') got here first.
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ResourcePackCompatibility;isCompatible()Z"), require = 0)
    private boolean redirectIsCompatible(ResourcePackCompatibility compatibility) {
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) {
            return compatibility.isCompatible();
        }
        if (ResourceTreeConfig.hideIncompatiblePacks) return true;
        return compatibility.isCompatible();
    }

    // Prevents the "Are you sure? This pack is old!" popup from appearing when enabled.
    @Redirect(method = "enable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ResourcePackCompatibility;isCompatible()Z"), require = 0)
    private boolean redirectIsCompatibleEnable(ResourcePackCompatibility compatibility) {
        if (FabricLoader.getInstance().isModLoaded("no-resource-pack-warnings")) {
            return compatibility.isCompatible();
        }
        if (ResourceTreeConfig.hideIncompatiblePacks) return true;
        return compatibility.isCompatible();
    }

    // --- FALLBACK RENDERER ---
    // This is a safety net. If the redirects above fail for some reason, this "paints over"
    // the red background at the very end of the rendering process.
    // Also handles inline rename rendering for folder entries.
    @Inject(at = @At("TAIL"), method = "render")
    private void onRenderHideWarning(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks, CallbackInfo ci) {
        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor)this).getPack();
        PackListWidget.ResourcePackEntry entry = (PackListWidget.ResourcePackEntry)(Object)this;

        // --- INLINE RENAME RENDERING ---
        // If this folder is being renamed, draw a text input over the name area with scroll support
        if (pack instanceof FolderPack folderPack
                && FolderColorPalette.INSTANCE.isRenaming()
                && folderPack.getRelativePath().equals(FolderColorPalette.INSTANCE.getRenamingPath())) {

            int textX = entry.getContentX() + 34;
            int textY = entry.getContentY();
            int boxW = entry.getContentRightEnd() - entry.getContentX() - 4;

            // Blue border to show the field is active
            context.fill(textX - 2, textY - 2, textX + boxW - 28, textY - 1, 0xFF88AAFF);
            context.fill(textX - 2, textY + 10, textX + boxW - 28, textY + 11, 0xFF88AAFF);
            context.fill(textX - 2, textY - 2, textX - 1, textY + 11, 0xFF88AAFF);
            context.fill(textX + boxW - 29, textY - 2, textX + boxW - 28, textY + 11, 0xFF88AAFF);

            var tr = MinecraftClient.getInstance().textRenderer;
            String buffer = FolderColorPalette.INSTANCE.getRenameBuffer();
            int cursorPos = FolderColorPalette.INSTANCE.getCursorPos();
            int scrollOffset = FolderColorPalette.INSTANCE.getScrollOffset();

            // --- CURSOR POSITION ---
            // Draw text before cursor, then cursor, then text after cursor
            String beforeCursor = buffer.substring(0, cursorPos);
            String afterCursor = buffer.substring(cursorPos);
            boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;

            // Offset text by scroll amount so long names stay visible
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

        // Fills the background with transparent color to effectively "erase" the red
        context.fill(
                entry.getContentX() - 1,
                entry.getContentY() - 1,
                entry.getContentRightEnd() + 1,
                entry.getContentBottomEnd() + 1,
                0x00000000
        );
    }
}