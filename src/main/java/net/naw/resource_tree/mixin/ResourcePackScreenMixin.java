package net.naw.resource_tree.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.naw.resource_tree.client.ResourceTreeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- SCREEN INTERCEPTOR ---
// Intercepts whenever Minecraft tries to open a screen.
// If it's a PackSelectionScreen (resource pack selection), we replace it with our
// custom ResourceTreeScreen which adds folder navigation support.
@Mixin(Minecraft.class)
public class ResourcePackScreenMixin {

    // --- SCREEN SWAP LOGIC ---
    @Inject(at = @At("HEAD"), method = "setScreen", cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // Only intercept PackSelectionScreen, and don't intercept our own ResourceTreeScreen
        if (screen instanceof PackSelectionScreen && !(screen instanceof ResourceTreeScreen)) {
            Minecraft client = Minecraft.getInstance();
            Screen parent = client.screen;
            ci.cancel();

            // --- REPLACEMENT ---
            // Replace with our custom screen, preserving the original applier and pack dir.
            // Must use reloadResourcePacks() instead of reloadResources()
            // so enabled packs get saved to options.txt correctly.
            client.setScreen(new ResourceTreeScreen(
                    client.getResourcePackRepository(),
                    manager -> {
                        client.options.updateResourcePacks(manager);
                        client.setScreen(parent);
                    },
                    client.getResourcePackDirectory()
            ));
        }
    }
}