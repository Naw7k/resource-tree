package net.naw.resource_tree.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.naw.resource_tree.client.ResourceTreeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- SCREEN INTERCEPTOR ---
// Intercepts whenever Minecraft tries to open a screen.
// If it's a PackScreen (resource pack selection), we replace it with our
// custom ResourceTreeScreen which adds folder navigation support.
@Mixin(MinecraftClient.class)
public class ResourcePackScreenMixin {

    // --- SCREEN SWAP LOGIC ---
    @Inject(at = @At("HEAD"), method = "setScreen", cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // Only intercept PackScreen, and don't intercept our own ResourceTreeScreen
        if (screen instanceof PackScreen && !(screen instanceof ResourceTreeScreen)) {
            MinecraftClient client = MinecraftClient.getInstance();
            Screen parent = client.currentScreen;
            ci.cancel();

            // --- REPLACEMENT ---
            // Replace with our custom screen, preserving the original applier and pack dir.
            // Must use refreshResourcePacks() instead of reloadResources()
            // so enabled packs get saved to options.txt correctly.
            client.setScreen(new ResourceTreeScreen(
                    client.getResourcePackManager(),
                    manager -> {
                        client.options.refreshResourcePacks(manager);
                        client.setScreen(parent);
                    },
                    client.getResourcePackDir()
            ));
        }
    }
}