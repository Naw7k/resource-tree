package net.naw.resource_tree.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.naw.resource_tree.client.DragDropManager;
import net.naw.resource_tree.client.FolderPack;
import net.naw.resource_tree.client.ResourceTreeConfig;
import net.naw.resource_tree.client.ResourceTreeScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// --- LIST INJECTOR ---
// Injects folder entries into the available packs list.
// Vanilla's updateList() method only shows actual resource packs — we prepend
// folder entries and a ".." back entry so users can navigate the folder structure.
@Mixin(TransferableSelectionList.class)
public class PackListWidgetMixin {

    @Shadow @Final private Component title;

    // --- RECURSION GUARD ---
    // Prevents infinite recursion: we cancel updateList() and call it again with our
    // injected folders, so we need to skip our own injection on that second call.
    @Unique
    private static boolean injectingFolders = false;

    @Inject(at = @At("HEAD"), method = "updateList", cancellable = true)
    private void onUpdateList(Stream<PackSelectionModel.Entry> packs, PackSelectionModel.EntryBase focused, CallbackInfo ci) {

        if (injectingFolders) return;

        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof ResourceTreeScreen screen)) return;

        // --- FILTERING ---
        // Only modify the available (left) list, not the selected (right) list.
        // We check the widget title to make sure we're on the "Available" side.
        if (!isAvailableList()) return;

        File currentFolder = screen.getCurrentFolder();
        File rootFolder = screen.getRootFolder();

        List<PackSelectionModel.Entry> injected = new ArrayList<>();

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
        List<PackSelectionModel.Entry> packList = packs.collect(java.util.stream.Collectors.toList());

        Comparator<PackSelectionModel.Entry> comparator = switch (ResourceTreeConfig.sortMode) {
            case NAME_ASC -> Comparator.comparing(p -> p.getTitle().getString().toLowerCase());
            case NAME_DESC -> Comparator.comparing((PackSelectionModel.Entry p) ->
                    p.getTitle().getString().toLowerCase()).reversed();
            case DATE_NEW -> Comparator.comparingLong((PackSelectionModel.Entry p) ->
                    getPackFile(p, rootFolder).lastModified()).reversed();
            case DATE_OLD -> Comparator.comparingLong((PackSelectionModel.Entry p) ->
                    getPackFile(p, rootFolder).lastModified());
        };

        packList.sort(comparator);

        // --- INJECTION ---
        // Prepend folder entries to the sorted pack stream and re-call updateList().
        // We use the boolean flag to tell the next call to skip this injection.
        Stream<PackSelectionModel.Entry> combined = Stream.concat(injected.stream(), packList.stream());
        ci.cancel();
        injectingFolders = true;
        ((TransferableSelectionList)(Object)this).updateList(combined, focused);
        injectingFolders = false;

        // --- CREATE BLANK ENTRY ---
        // Create and store a blank placeholder entry for drag and drop
        // Must be done after updateList() so the list widget exists
        try {
            java.lang.reflect.Constructor<?> constructor = TransferableSelectionList.PackEntry.class
                    .getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            TransferableSelectionList list = (TransferableSelectionList)(Object)this;
            // In 26.1 the constructor takes: outer class, Minecraft, TransferableSelectionList, PackSelectionModel.Entry
            TransferableSelectionList.PackEntry blank = (TransferableSelectionList.PackEntry)
                    constructor.newInstance(list, Minecraft.getInstance(), list, FAKE_PACK);
            DragDropManager.setBlankEntry(blank);
        } catch (Exception e) {
            // If reflection fails, blank entry stays null — drag system will be disabled
        }
    }

    // --- AVAILABLE LIST CHECK ---
    // Checks if this list is the available (left) list by comparing its title
    @Unique
    private boolean isAvailableList() {
        return this.title.getString().equals(
                Component.translatable("pack.available.title").getString()
        );
    }

    // --- FILE RESOLVER ---
    // Converts a pack's id (like "file/subfolder/pack.zip") back into an actual File object
    // so we can check its last modified date for date sorting.
    @Unique
    private File getPackFile(PackSelectionModel.Entry pack, File rootFolder) {
        String name = pack.getId();
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
    private static final PackSelectionModel.Entry FAKE_PACK = new PackSelectionModel.Entry() {
        @Override public Identifier getIconTexture() { return Identifier.withDefaultNamespace("textures/misc/unknown_pack.png"); }
        @Override public PackCompatibility getCompatibility() { return PackCompatibility.COMPATIBLE; }
        @Override public String getId() { return "blank:placeholder"; }
        @Override public Component getTitle() { return Component.literal(""); }
        @Override public Component getDescription() { return Component.literal(""); }
        @Override public PackSource getPackSource() { return PackSource.DEFAULT; }
        @Override public boolean isFixedPosition() { return false; }
        @Override public boolean isRequired() { return false; }
        @Override public boolean isSelected() { return false; }
        @Override public boolean canMoveUp() { return false; }
        @Override public boolean canMoveDown() { return false; }
        @Override public void select() {}
        @Override public void unselect() {}
        @Override public void moveUp() {}
        @Override public void moveDown() {}
    };
}