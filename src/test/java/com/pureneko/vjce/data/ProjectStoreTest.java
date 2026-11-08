package com.pureneko.vjce.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreTest {
    @TempDir Path temporary;
    @Test void roundTripAndModpackExportPreserveLayoutAndAssets() throws Exception {
        var store = new ProjectStore(temporary.resolve("config/vjce"));
        var document = new CategoryDocument();
        document.layout = "{template:{bytes:[B;1b,2b],value:2.0f}}";
        store.save(document);
        assertEquals(document.layout, store.loadAll().getFirst().layout);
        var assets = temporary.resolve("ldlib2/assets/vjce/textures");
        Files.createDirectories(assets);
        Files.write(assets.resolve("test.png"), new byte[]{1, 2, 3});
        var archive = temporary.resolve("export.zip");
        store.exportZip(archive, temporary.resolve("ldlib2/assets"));
        try (var zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("ldlib2/assets/vjce/textures/test.png"));
            assertTrue(zip.stream().anyMatch(e -> e.getName().startsWith("config/vjce/categories/") && e.getName().endsWith(".json")));
        }
    }
    @Test void oneBrokenDocumentDoesNotDiscardOtherCategories() throws Exception {
        var store = new ProjectStore(temporary);
        store.save(new CategoryDocument());
        Files.writeString(temporary.resolve("categories/broken.json"), "{broken");
        assertEquals(1, store.loadAll().size());
        assertEquals(1, store.errors.size());
    }
    @Test void resourceIdsCannotEscapeTheCategoryDirectory() throws Exception {
        var store = new ProjectStore(temporary);
        var doc = new CategoryDocument(); doc.id = "vjce:../../still_a_resource_id";
        var file = store.save(doc);
        assertEquals(temporary.resolve("categories"), file.getParent());
    }
    @Test void invalidExpressionCannotOverwriteAnExistingProject() throws Exception {
        var store = new ProjectStore(temporary);
        var document = new CategoryDocument(); store.save(document);
        var binding = new CategoryDocument.Binding(); binding.expression = "missing / 0";
        document.bindings.add(binding); document.recipes.add(new CategoryDocument.Recipe());
        assertThrows(IllegalArgumentException.class, () -> store.save(document));
        assertTrue(store.loadAll().getFirst().bindings.isEmpty());
    }
    @Test void filenamesUseResourceIdsAndOptionalOriginalCategory() throws Exception {
        var store = new ProjectStore(temporary); var doc = new CategoryDocument(); doc.id = "vjce:demo/example";
        assertEquals("vjce~demo+example.json", store.save(doc).getFileName().toString());
        doc.target = "mekanism:crushing";
        var path = store.save(doc);
        assertEquals("vjce~demo+example@mekanism~crushing.json", path.getFileName().toString());
        assertFalse(Files.exists(temporary.resolve("categories/vjce~demo+example.json")));
        assertEquals(1, store.loadAll().size());
        doc.title = "New title"; assertEquals(path, store.save(doc));
    }
    @Test void resourceRenameSurvivesCopiesAndProjectReopening() throws Exception {
        var store = new ProjectStore(temporary); var doc = new CategoryDocument(); var old = store.save(doc);
        var copy = ProjectStore.copy(store.loadAll().getFirst()); copy.id = "vjce:renamed";
        var renamed = store.save(copy); assertFalse(Files.exists(old)); assertTrue(Files.exists(renamed));
        var reopened = ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(copy), CategoryDocument.class);
        var newStore = new ProjectStore(temporary); newStore.restoreSource(reopened, store.sourceFile(copy)); reopened.id = "vjce:renamed_again";
        assertEquals("vjce~renamed_again.json", newStore.save(reopened).getFileName().toString());
        assertFalse(Files.exists(renamed)); assertEquals(1, newStore.loadAll().size());
    }
    @Test void filenamesAreFlatDistinctAndBoundedOnWindows() throws Exception {
        var store = new ProjectStore(temporary); var a = new CategoryDocument(); a.id = "vjce:a/b";
        var b = new CategoryDocument(); b.id = "vjce:a_b";
        assertNotEquals(store.save(a), store.save(b));
        var longId = new CategoryDocument(); longId.id = "vjce:" + "a".repeat(350);
        var file = store.save(longId); assertTrue(file.getFileName().toString().length() < 180);
        assertEquals(temporary.resolve("categories"), file.getParent());
        assertFalse(file.getFileName().toString().matches(".*[<>:\"/\\\\|?*].*"));
    }
    @Test void loadingLeavesLegacyFilesUntouchedAndManualSaveCreatesNoDuplicate() throws Exception {
        var store = new ProjectStore(temporary); Files.createDirectories(temporary.resolve("categories"));
        var doc = new CategoryDocument(); var legacy = temporary.resolve("categories/dmpjZTpleGFtcGxl.json");
        var content = ProjectStore.JSON.toJson(doc); Files.writeString(legacy, content);
        assertEquals(1, store.loadAll().size()); assertEquals(content, Files.readString(legacy));
        assertTrue(Files.exists(legacy)); try (var files = Files.list(legacy.getParent())) { assertEquals(1, files.count()); }
        var loaded = store.loadAll().getFirst(); loaded.title = "Explicit user edit";
        assertEquals(legacy, store.save(loaded)); assertEquals(1, store.loadAll().size());
    }
    @Test void conflictingFilenameCannotOverwriteAnotherCategory() throws Exception {
        var store = new ProjectStore(temporary); Files.createDirectories(temporary.resolve("categories"));
        var doc = new CategoryDocument(); var other = new CategoryDocument(); other.id = "other:category";
        var file = temporary.resolve("categories").resolve(ProjectStore.filename(doc)); var content = ProjectStore.JSON.toJson(other); Files.writeString(file, content);
        assertThrows(java.io.IOException.class, () -> store.save(doc)); assertEquals(content, Files.readString(file));
    }
    @Test void legacyAndComponentCatalystsRoundTripTogether() throws Exception {
        var store = new ProjectStore(temporary);
        var document = ProjectStore.JSON.fromJson("""
                {"catalysts":["minecraft:crafting_table",
                {"id":"minecraft:potion","components":"{\\\"minecraft:potion_contents\\\":{potion:\\\"minecraft:water\\\"}}"},
                {"id":"minecraft:potion","components":"{\\\"minecraft:potion_contents\\\":{potion:\\\"minecraft:awkward\\\"}}"}]}
                """, CategoryDocument.class);
        store.save(document);
        var catalysts = store.loadAll().getFirst().catalysts;
        assertEquals(document.catalysts, catalysts);
        assertEquals("minecraft:crafting_table", catalysts.getFirst().getAsString());
        assertNotEquals(catalysts.get(1), catalysts.get(2));
    }

    @Test void removingSlotDataPreservesTheRemainingPageAndSourceIds() {
        var recipe = ProjectStore.JSON.fromJson("""
                {"source":"minecraft:sample", "slots":[{}, {"identifier":"alternate"}, {}, {"identifier":"added","sourceIndex":-1}]}
                """, CategoryDocument.Recipe.class);
        assertEquals("slot_0", recipe.slotIdentifier(0));
        recipe.removeSlot(0);
        var restored = ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(recipe), CategoryDocument.Recipe.class);
        assertEquals("alternate", restored.slotIdentifier(0));
        assertEquals("slot_2", restored.slotIdentifier(1));
        assertEquals(1, restored.sourceSlotIndex(0));
        assertEquals(2, restored.sourceSlotIndex(1));
        assertEquals(-1, restored.sourceSlotIndex(2));
        assertNull(restored.boundSlot("slot_0"));
    }
}
