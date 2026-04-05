package net.naw.resource_tree.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.naw.resource_tree.client.DragDropManager;
import net.naw.resource_tree.client.FolderPack;
import net.naw.resource_tree.client.ResourceTreeConfig;
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
import java.util.Comparator;
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
        // Folders are always sorted A-Z regardless of the user's sort setting —
        // they stay on top and have their own fixed order.
        File[] subfolders = currentFolder.listFiles(File::isDirectory);
        if (subfolders != null) {
            java.util.Arrays.sort(subfolders, Comparator.comparing(f -> f.getName().toLowerCase()));
            for (File folder : subfolders) {
                injected.add(new FolderPack(folder.getName(), folder, screen, false));
            }
        }

        // --- PACK SORTING ---
        // Collect the packs into a list so we can sort them before displaying.
        // Folders stay on top — only the actual resource packs below them are sorted.
        List<ResourcePackOrganizer.Pack> packList = packs.collect(java.util.stream.Collectors.toList());

        Comparator<ResourcePackOrganizer.Pack> comparator = switch (ResourceTreeConfig.sortMode) {
            case NAME_ASC -> Comparator.comparing(p -> p.getDisplayName().getString().toLowerCase());
            case NAME_DESC -> Comparator.comparing((ResourcePackOrganizer.Pack p) ->
                    p.getDisplayName().getString().toLowerCase()).reversed();
            case DATE_NEW -> Comparator.comparingLong((ResourcePackOrganizer.Pack p) ->
                    getPackFile(p, rootFolder).lastModified()).reversed();
            case DATE_OLD -> Comparator.comparingLong((ResourcePackOrganizer.Pack p) ->
                    getPackFile(p, rootFolder).lastModified());
        };

        packList.sort(comparator);

        // --- INJECTION ---
        // Prepend folder entries to the sorted pack stream and re-call set().
        // We use the boolean flag to tell the next call to skip this injection.
        Stream<ResourcePackOrganizer.Pack> combined = Stream.concat(injected.stream(), packList.stream());
        ci.cancel();
        injectingFolders = true;
        ((PackListWidget)(Object)this).set(combined, focused);
        injectingFolders = false;

        // --- CREATE BLANK ENTRY ---
        // Create and store a blank placeholder entry for drag and drop
        // Must be done after set() so the list widget exists
        try {
            java.lang.reflect.Constructor<?> constructor = PackListWidget.ResourcePackEntry.class
                    .getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            PackListWidget list = (PackListWidget)(Object)this;
            PackListWidget.ResourcePackEntry blank = (PackListWidget.ResourcePackEntry)
                    constructor.newInstance(list, MinecraftClient.getInstance(), list, FAKE_PACK);
            DragDropManager.setBlankEntry(blank);
        } catch (Exception e) {
            // If reflection fails, blank entry stays null
        }
    }

    // --- FILE RESOLVER ---
    // Converts a pack's name (like "file/subfolder/pack.zip") back into an actual File object
    // so we can check its last modified date for date sorting.
    @Unique
    private File getPackFile(ResourcePackOrganizer.Pack pack, File rootFolder) {
        String name = pack.getName();
        if (name.startsWith("file/")) {
            String relativePath = name.substring("file/".length()).replace("/", File.separator);
            return new File(rootFolder, relativePath);
        }
        // Fallback — return root folder so built-in packs don't crash the sort
        return rootFolder;
    }

    // --- FAKE PACK ---
    // A minimal invisible pack used to create the blank placeholder entry
    @Unique
    private static final ResourcePackOrganizer.Pack FAKE_PACK = new ResourcePackOrganizer.Pack() {
        @Override public net.minecraft.util.Identifier getIconId() { return net.minecraft.util.Identifier.ofVanilla("textures/misc/unknown_pack.png"); }
        @Override public net.minecraft.resource.ResourcePackCompatibility getCompatibility() { return net.minecraft.resource.ResourcePackCompatibility.COMPATIBLE; }
        @Override public String getName() { return "blank:placeholder"; }
        @Override public net.minecraft.text.Text getDisplayName() { return net.minecraft.text.Text.literal(""); }
        @Override public net.minecraft.text.Text getDescription() { return net.minecraft.text.Text.literal(""); }
        @Override public net.minecraft.resource.ResourcePackSource getSource() { return net.minecraft.resource.ResourcePackSource.NONE; }
        @Override public boolean isPinned() { return false; }
        @Override public boolean isAlwaysEnabled() { return false; }
        @Override public boolean isEnabled() { return false; }
        @Override public boolean canBeEnabled() { return false; }
        @Override public boolean canBeDisabled() { return false; }
        @Override public boolean canMoveTowardStart() { return false; }
        @Override public boolean canMoveTowardEnd() { return false; }
        @Override public void enable() {}
        @Override public void disable() {}
        @Override public void moveTowardStart() {}
        @Override public void moveTowardEnd() {}
    };
}
