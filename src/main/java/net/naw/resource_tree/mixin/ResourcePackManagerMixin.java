package net.naw.resource_tree.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.naw.resource_tree.client.SubfolderResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

// --- PACK MANAGER INJECTOR ---
// Injects subfolder packs into the ResourcePackManager's profile list.
// Vanilla only scans the root resourcepacks folder — this adds packs found inside subfolders too.
@Mixin(ResourcePackManager.class)
public class ResourcePackManagerMixin {

    // --- PROFILE INJECTION ---
    @Inject(at = @At("RETURN"), method = "providePackProfiles", cancellable = true)
    private void onProvidePackProfiles(CallbackInfoReturnable<Map<String, ResourcePackProfile>> cir) {
        // Guard against being called before MinecraftClient is initialized
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // --- MUTABLE CONVERSION ---
        // Vanilla returns an ImmutableMap (unchangeable).
        // We copy it into a mutable TreeMap so we can add our own discovered packs.
        Map<String, ResourcePackProfile> mutableProfiles = new TreeMap<>(cir.getReturnValue());

        // --- SUBFOLDER SCANNING ---
        // Scan the resourcepacks folder for subfolders and register any packs found inside.
        File resourcePacksDir = new File(client.runDirectory, "resourcepacks");
        SubfolderResourcePackProvider provider = new SubfolderResourcePackProvider(resourcePacksDir);

        // Use our provider to find packs and put them into the profile map
        provider.register(profile -> mutableProfiles.put(profile.getId(), profile));

        // Return our expanded list of packs back to the game
        cir.setReturnValue(mutableProfiles);
    }
}