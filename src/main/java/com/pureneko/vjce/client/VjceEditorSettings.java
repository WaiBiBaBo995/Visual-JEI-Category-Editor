package com.pureneko.vjce.client;

import com.google.gson.*;
import com.lowdragmc.lowdraglib2.editor.settings.*;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import java.io.File;
import java.nio.file.Files;

/** Corrects the old whole-editor MC default once, while retaining the rest of the settings. */
final class VjceEditorSettings extends EditorSettings {
    private static final String REVISION = "vjceAppearanceRevision";
    private static final String RECIPE_SPLIT = "vjceRecipeBrowserPercentage";
    private static float recipeBrowserPercentage = 25;
    VjceEditorSettings(Editor editor, File file) { super(editor); setSettingsFile(file); }

    float recipeBrowserPercentage() { return recipeBrowserPercentage; }
    void rememberRecipeBrowser(float value) {
        if (!Float.isFinite(value)) return;
        value = Math.clamp(value, 5, 95);
        if (Math.abs(value - recipeBrowserPercentage) < .01f) return;
        recipeBrowserPercentage = value; saveAllSettingsToFile();
    }

    @Override public void loadAllSettingsFromFile() {
        recipeBrowserPercentage = 25;
        boolean migrated = false;
        boolean readable = true;
        try {
            if (getSettingsFile().isFile()) {
                var json = JsonParser.parseString(Files.readString(getSettingsFile().toPath())).getAsJsonObject();
                migrated = json.has(REVISION);
                if (json.has(RECIPE_SPLIT)) {
                    float value = json.get(RECIPE_SPLIT).getAsFloat();
                    if (Float.isFinite(value)) recipeBrowserPercentage = Math.clamp(value, 5, 95);
                }
            }
        } catch (Exception ignored) { readable = false; /* The normal loader reports malformed settings. */ }
        super.loadAllSettingsFromFile();
        if (!migrated && readable) {
            getSettings(AppearanceSettings.ID).filter(AppearanceSettings.class::isInstance).map(AppearanceSettings.class::cast).ifPresent(appearance -> {
                if (appearance.getStylesheet().equals(StylesheetManager.MC_MERGED) || appearance.getStylesheet().equals(StylesheetManager.MC))
                    appearance.setStylesheet(StylesheetManager.ORE_MERGED);
            });
            saveAllSettingsToFile();
        }
    }

    @Override public void saveAllSettingsToFile() {
        super.saveAllSettingsToFile();
        try {
            var path = getSettingsFile().toPath();
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            json.addProperty(REVISION, 1);
            json.addProperty(RECIPE_SPLIT, recipeBrowserPercentage);
            Files.writeString(path, json.toString());
        } catch (Exception error) { VisualJeiCategoryEditor.LOG.warn("Could not save VJCE appearance revision", error); }
    }
}
