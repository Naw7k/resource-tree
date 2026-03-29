package net.naw.resource_tree.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.naw.resource_tree.client.FolderPack;
import net.naw.resource_tree.client.ResourceTreeScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.text.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Unique;

// --- LIST INJECTOR ---
// Injects folder entries into the available packs list.
// Vanilla's set() method only shows actual resource packs — we prepend
// folder entries and a ".." back entry so users can navigate the folder structure.
@Mixin(PackListWidget.class)
public class PackListWidgetMixin {

    @Shadow @Final private Text title;

    // --- RECURSION GUARD ---
    // Prevents infinite recursion: we cancel set() and call it again with our
    // injected folders, so we need to skip our own injection on that second call.
    @Unique
    private static boolean injectingFolders = false;

    @Inject(at = @At("HEAD"), method = "set", cancellable = true)
    private void onSet(Stream<ResourcePackOrganizer.Pack> packs, ResourcePackOrganizer.AbstractPack focused, CallbackInfo ci) {
        if (injectingFolders) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ResourceTreeScreen screen)) return;

        // --- FILTERING ---
        // Only modify the available (left) list, not the selected (right) list.
        // We check the widget title to make sure we're on the "Available" side.
        if (!this.title.getString().equals(Text.translatable("pack.available.title").getString())) return;

        File currentFolder = screen.getCurrentFolder();
        File rootFolder = screen.getRootFolder();

        List<ResourcePackOrganizer.Pack> injected = new ArrayList<>();

        // --- FOLDER GENERATION ---
        // Add ".." back entry if we're currently inside a subfolder.
        if (!currentFolder.equals(rootFolder)) {
            injected.add(new FolderPack("..", currentFolder.getParentFile(), screen, true));
        }

        // Add a folder entry for each subfolder found in the current directory.
        File[] subfolders = currentFolder.listFiles(File::isDirectory);
        if (subfolders != null) {
            for (File folder : subfolders) {
                injected.add(new FolderPack(folder.getName(), folder, screen, false));
            }
        }

        // --- INJECTION ---
        // Prepend folder entries to the pack stream and re-call set().
        // We use the boolean flag to tell the next call to skip this injection.
        Stream<ResourcePackOrganizer.Pack> combined = Stream.concat(injected.stream(), packs);
        ci.cancel();
        injectingFolders = true;
        ((PackListWidget)(Object)this).set(combined, focused);
        injectingFolders = false;
    }
}