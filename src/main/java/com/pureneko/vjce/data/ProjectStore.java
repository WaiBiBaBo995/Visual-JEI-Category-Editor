package com.pureneko.vjce.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class ProjectStore {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public final Path root;
    public final List<String> errors = new ArrayList<>();
    private record Origin(Path file, String id, String target, boolean canonical) {}
    private static final Map<CategoryDocument, Origin> ORIGINS = Collections.synchronizedMap(new WeakHashMap<>());
    public ProjectStore(Path root) { this.root = root; }
    public static CategoryDocument copy(CategoryDocument document) {
        var copy = JSON.fromJson(JSON.toJson(document), CategoryDocument.class);
        var origin = ORIGINS.get(document); if (origin != null) ORIGINS.put(copy, origin);
        return copy;
    }
    public static String filename(CategoryDocument document) { return filename(document.id, document.target); }
    private static String filename(String id, String target) {
        // ':' and '/' are escaped with characters that cannot occur in a resource ID.
        String name = id.replace(':', '~').replace('/', '+');
        if (!target.isEmpty()) name += "@" + target.replace(':', '~').replace('/', '+');
        if (name.length() > 160) {
            try { name = name.substring(0, 132) + "--" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        return name + ".json";
    }
    public String sourceFile(CategoryDocument document) {
        var origin = ORIGINS.get(document);
        return origin != null && origin.canonical ? origin.file.getFileName().toString() : "";
    }
    public void restoreSource(CategoryDocument document, String filename) {
        if (filename.isEmpty()) return;
        var directory = root.resolve("categories").toAbsolutePath().normalize();
        var file = directory.resolve(filename).normalize();
        if (!directory.equals(file.getParent()) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) return;
        try {
            var old = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CategoryDocument.class); old.validate();
            if (file.getFileName().toString().equals(filename(old))) ORIGINS.put(document, new Origin(file, old.id, old.target, true));
        } catch (Exception ignored) {}
    }

    public List<CategoryDocument> loadAll() {
        errors.clear();
        var documents = new ArrayList<CategoryDocument>();
        var ids = new HashSet<String>();
        var targets = new HashSet<String>();
        try {
            Files.createDirectories(root.resolve("categories"));
            try (var files = Files.list(root.resolve("categories"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    try (var reader = Files.newBufferedReader(file)) {
                        var document = JSON.fromJson(reader, CategoryDocument.class);
                        document.validate();
                        if (!ids.add(document.id)) throw new IllegalArgumentException("Duplicate category ID: " + document.id);
                        if (!document.target.isEmpty() && !targets.add(document.target)) throw new IllegalArgumentException("Duplicate target: " + document.target);
                        documents.add(document);
                        ORIGINS.put(document, new Origin(file.toAbsolutePath().normalize(), document.id, document.target,
                                file.getFileName().toString().equals(filename(document))));
                    } catch (Exception e) { errors.add(file.getFileName() + ": " + e.getMessage()); }
                }
            }
        } catch (IOException e) { errors.add(e.getMessage()); }
        return List.copyOf(documents);
    }

    public Path save(CategoryDocument document) throws IOException {
        document.validate();
        var directory = root.resolve("categories").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        var origin = ORIGINS.get(document);
        if (origin != null && !directory.equals(origin.file.getParent())) origin = null;
        try (var files = Files.list(directory)) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                if (Files.isSymbolicLink(file) || origin != null && origin.file.equals(file)) continue;
                CategoryDocument existing;
                try { existing = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CategoryDocument.class); existing.validate(); }
                catch (Exception ignored) { continue; }
                if (existing.id.equals(document.id)) {
                    if (origin != null) throw new IOException("Duplicate category ID: " + document.id);
                    origin = new Origin(file, existing.id, existing.target, file.getFileName().toString().equals(filename(existing)));
                } else if (!document.target.isEmpty() && document.target.equals(existing.target)) throw new IOException("Duplicate target: " + document.target);
            }
        }
        // Legacy/manual names are read and explicitly saved in place, never automatically migrated.
        var destination = origin != null && !origin.canonical ? origin.file : directory.resolve(filename(document));
        if (Files.isSymbolicLink(destination)) throw new IOException("Category file is a symbolic link: " + destination.getFileName());
        boolean renamed = origin != null && !origin.file.equals(destination);
        if (renamed && Files.exists(destination)) throw new IOException("Category filename already exists: " + destination.getFileName());
        if (origin == null && Files.exists(destination)) throw new IOException("Category filename already exists: " + destination.getFileName());
        var temporary = Files.createTempFile(destination.getParent(), ".vjce-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(document), StandardCharsets.UTF_8);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            if (renamed) {
                try { Files.deleteIfExists(origin.file); }
                catch (IOException failure) { Files.deleteIfExists(destination); throw failure; }
            }
        } finally { Files.deleteIfExists(temporary); }
        ORIGINS.put(document, new Origin(destination, document.id, document.target, destination.getFileName().toString().equals(filename(document))));
        return destination;
    }

    /** Produces a modpack-relative archive, including all managed assets and editor resources. */
    public void exportZip(Path destination, Path managedAssets) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(destination))) {
            writeTree(zip, root.resolve("categories"), "config/vjce/categories/");
            writeTree(zip, root.resolve("assets"), "config/vjce/assets/");
            writeTree(zip, root.resolve("projects"), "config/vjce/projects/");
            if (managedAssets != null) writeTree(zip, managedAssets, "ldlib2/assets/");
            zip.putNextEntry(new ZipEntry("config/vjce/README.txt"));
            zip.write("Visual JEI Category Editor - pureneko\nExtract at the Minecraft instance root. Requires VJCE, LDLib2 and JEI. Re-enter the world to apply.\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    private static void writeTree(ZipOutputStream zip, Path directory, String prefix) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.walk(directory)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(file)) continue;
                zip.putNextEntry(new ZipEntry(prefix + directory.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }
}
