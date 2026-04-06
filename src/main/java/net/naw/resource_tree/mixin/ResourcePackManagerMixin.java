package net.naw.resource_tree.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.naw.resource_tree.client.SubfolderResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

// --- PACK MANAGER INJECTOR ---
// Injects subfolder packs into the PackRepository's available pack list.
// Vanilla only scans the root resourcepacks folder — this adds packs found inside subfolders too.
@Mixin(PackRepository.class)
public class ResourcePackManagerMixin {

    // --- PROFILE INJECTION ---
    @Inject(at = @At("RETURN"), method = "discoverAvailable", cancellable = true)
    private void onDiscoverAvailable(CallbackInfoReturnable<Map<String, Pack>> cir) {
        // Guard against being called before Minecraft is initialized
        Minecraft client = Minecraft.getInstance();
        // Only run our scanner when on main menu or pack screen — not during world load
        if (client.screen != null &&
                !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) &&
                !(client.screen instanceof net.minecraft.client.gui.screens.packs.PackSelectionScreen)) {
            return;
        }

        // --- MUTABLE CONVERSION ---
        // Vanilla returns an ImmutableMap (unchangeable).
        // We copy it into a mutable TreeMap so we can add our own discovered packs.
        Map<String, Pack> mutableProfiles = new TreeMap<>(cir.getReturnValue());

        // --- SUBFOLDER SCANNING ---
        // Scan the resourcepacks folder for subfolders and register any packs found inside.
        File resourcePacksDir = new File(client.gameDirectory, "resourcepacks");
        SubfolderResourcePackProvider provider = new SubfolderResourcePackProvider(resourcePacksDir);

        // Use our provider to find packs and put them into the profile map
        provider.loadPacks(pack -> mutableProfiles.put(pack.getId(), pack));

        // Return our expanded list of packs back to the game
        cir.setReturnValue(mutableProfiles);
    }
}
