package net.naw.resource_tree.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

// --- CONFIGURATION SYSTEM ---
// This class saves and loads mod settings to a .json file in the /config/ folder.
public class ResourceTreeConfig {

    // --- TOGGLE SETTINGS ---
    public static boolean hideBuiltinPacks = false;      // Hides "High Contrast" & "Programmer Art"
    public static boolean hideIncompatiblePacks = false; // Hides red warning packs
    public static boolean searchInsideFolders = false;   // Allows search to "dig" into subfolders

    // --- SORT SETTING ---
    // Controls how packs are ordered in the available list.
    // Folders always stay on top — only packs below them are affected.
    public enum SortMode {
        NAME_ASC,   // A → Z (default)
        NAME_DESC,  // Z → A
        DATE_NEW,   // Newest modified first
        DATE_OLD    // Oldest modified first
    }
    public static SortMode sortMode = SortMode.NAME_ASC; // Default sort

    // --- FOLDER COLOR STORAGE ---
    // A "Map" is like a dictionary: the Key is the Folder Name, and the Value is the Color Code.
    public static Map<String, Integer> folderColors = new HashMap<>();

    // Default color is black/white (no tint)
    public static final int DEFAULT_COLOR = 0xFF000000;

    // Helper to get a folder's color or return the default if none is set
    public static int getFolderColor(String folderPath) {
        return folderColors.getOrDefault(folderPath, DEFAULT_COLOR);
    }

    // Saves the color and immediately writes to the file
    public static void setFolderColor(String folderPath, int color) {
        if (color == DEFAULT_COLOR) {
            folderColors.remove(folderPath);
        } else {
            folderColors.put(folderPath, color);
        }
        save();
    }

    private static final Gson GSON = new Gson();

    // Locates the actual file: .minecraft/config/resource_tree.json
    private static Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/resource_tree.json");
    }

    // --- LOAD LOGIC ---
    // Reads the JSON file and updates the variables above when the game starts
    public static void load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) return;
            JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);

            if (json.has("hideBuiltinPacks"))
                hideBuiltinPacks = json.get("hideBuiltinPacks").getAsBoolean();
            if (json.has("hideIncompatiblePacks"))
                hideIncompatiblePacks = json.get("hideIncompatiblePacks").getAsBoolean();
            if (json.has("searchInsideFolders"))
                searchInsideFolders = json.get("searchInsideFolders").getAsBoolean();

            // Load sort mode from config, fall back to default if missing or invalid
            if (json.has("sortMode")) {
                try {
                    sortMode = SortMode.valueOf(json.get("sortMode").getAsString());
                } catch (IllegalArgumentException e) {
                    sortMode = SortMode.NAME_ASC; // Reset to default if saved value is unrecognized
                }
            }

            if (json.has("folderColors")) {
                JsonObject colors = json.getAsJsonObject("folderColors");
                for (String key : colors.keySet()) {
                    folderColors.put(key, colors.get(key).getAsInt());
                }
            }
        } catch (Exception e) {
            // If the file is broken or missing, it just uses the defaults above
        }
    }

    // --- SAVE LOGIC ---
    // Converts the current variables back into JSON text and writes them to the disk
    public static void save() {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("hideBuiltinPacks", hideBuiltinPacks);
            json.addProperty("hideIncompatiblePacks", hideIncompatiblePacks);
            json.addProperty("searchInsideFolders", searchInsideFolders);
            json.addProperty("sortMode", sortMode.name()); // Save sort mode as a string e.g. "NAME_ASC"

            JsonObject colors = new JsonObject();
            for (Map.Entry<String, Integer> entry : folderColors.entrySet()) {
                colors.addProperty(entry.getKey(), entry.getValue());
            }
            json.add("folderColors", colors);

            Files.writeString(path, GSON.toJson(json));
        } catch (Exception e) {
            // Fails silently to prevent game crashes during save errors
        }
    }
}