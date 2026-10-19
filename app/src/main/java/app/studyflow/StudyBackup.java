package app.studyflow;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Versioned, bounded backup format. Archive paths are never used as filesystem paths. */
final class StudyBackup {
    private static final String FORMAT = "studyflow";
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_ATTACHMENT_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 500L * 1024 * 1024;
    private static final int MAX_DATA_BYTES = 16 * 1024 * 1024;
    private static final String[] COLLECTIONS = {
            "subjects", "chapters", "notes", "logs", "cards", "assignments", "inbox", "quizzes"
    };

    static final class RestorePlan {
        final JSONObject mergedRoot;
        final File stageDirectory;
        final Map<String, File> stagedAttachments;
        final Map<String, String> attachmentNames;

        RestorePlan(JSONObject mergedRoot, File stageDirectory,
                    Map<String, File> stagedAttachments, Map<String, String> attachmentNames) {
            this.mergedRoot = mergedRoot;
            this.stageDirectory = stageDirectory;
            this.stagedAttachments = stagedAttachments;
            this.attachmentNames = attachmentNames;
        }

        void discard() {
            deleteTree(stageDirectory);
        }
    }

    static final class PreparedCommit {
        final RestorePlan plan;
        final List<File> copiedAttachments;
        PreparedCommit(RestorePlan plan, List<File> copiedAttachments) {
            this.plan = plan;
            this.copiedAttachments = copiedAttachments;
        }
        void discard() {
            for (File file : copiedAttachments) file.delete();
            plan.discard();
        }
    }

    private StudyBackup() { }

    static void export(File filesDirectory, JSONObject root, OutputStream destination) throws Exception {
        JSONObject snapshot = new JSONObject(root.toString());
        Set<String> attachments = referencedAttachments(snapshot);
        if (attachments.size() + 2 > MAX_ENTRIES) throw new IOException("StudyFlow has too many attachments for a single backup.");
        ZipOutputStream zip = new ZipOutputStream(destination, StandardCharsets.UTF_8);
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT);
            manifest.put("version", VERSION);
            putBytes(zip, "manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
            byte[] data = snapshot.toString().getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_DATA_BYTES) throw new IOException("Study data is too large to back up safely.");
            putBytes(zip, "data.json", data);

            File canonicalDirectory = filesDirectory.getCanonicalFile();
            long total = data.length;
            for (String name : attachments) {
                validateName(name);
                File file = new File(canonicalDirectory, name).getCanonicalFile();
                if (!canonicalDirectory.equals(file.getParentFile()))
                    throw new IOException("An attachment path is invalid. The backup was not created.");
                if (!file.isFile()) throw new IOException("An attachment is missing. Re-import it before backing up.");
                long length = file.length();
                if (length < 0 || length > MAX_ATTACHMENT_BYTES || total + length > MAX_ARCHIVE_BYTES)
                    throw new IOException("Attachments exceed the backup size limit.");
                zip.putNextEntry(new ZipEntry("attachments/" + name));
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buffer = new byte[32768];
                    int read;
                    long copied = 0;
                    while ((read = in.read(buffer)) != -1) {
                        copied += read;
                        total += read;
                        if (copied > MAX_ATTACHMENT_BYTES || total > MAX_ARCHIVE_BYTES)
                            throw new IOException("Attachments exceed the backup size limit.");
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            zip.finish();
        } finally {
            zip.close();
        }
    }

    static RestorePlan prepareRestore(File filesDirectory, InputStream source, JSONObject currentRoot) throws Exception {
        File stage = new File(filesDirectory, ".studyflow-restore-" + UUID.randomUUID());
        if (!stage.mkdirs()) throw new IOException("Could not prepare temporary restore storage.");
        Map<String, File> staged = new LinkedHashMap<>();
        byte[] manifestBytes = null;
        byte[] dataBytes = null;
        long total = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(source, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) throw new IOException("The backup contains too many files.");
                String name = entry.getName();
                if (entry.isDirectory()) throw new IOException("The backup contains an unsupported folder entry.");
                if ("manifest.json".equals(name)) {
                    if (manifestBytes != null) throw new IOException("The backup has duplicate metadata.");
                    manifestBytes = readBounded(zip, 4096, MAX_ARCHIVE_BYTES);
                    total += manifestBytes.length;
                } else if ("data.json".equals(name)) {
                    if (dataBytes != null) throw new IOException("The backup has duplicate study data.");
                    dataBytes = readBounded(zip, MAX_DATA_BYTES, MAX_ARCHIVE_BYTES);
                    total += dataBytes.length;
                } else if (name.startsWith("attachments/")) {
                    String sourceName = name.substring("attachments/".length());
                    validateName(sourceName);
                    if (staged.containsKey(sourceName)) throw new IOException("The backup has duplicate attachments.");
                    File temporary = new File(stage, sourceName);
                    long copied = copyBounded(zip, temporary, MAX_ATTACHMENT_BYTES, MAX_ARCHIVE_BYTES - total);
                    total += copied;
                    staged.put(sourceName, temporary);
                } else {
                    throw new IOException("The backup contains an unsupported file path.");
                }
                if (total > MAX_ARCHIVE_BYTES) throw new IOException("The backup exceeds the size limit.");
                zip.closeEntry();
            }
        } catch (Exception e) {
            deleteTree(stage);
            throw e;
        }

        try {
            if (manifestBytes == null || dataBytes == null) throw new IOException("This is not a complete StudyFlow backup.");
            JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
            if (!FORMAT.equals(manifest.optString("format")) || manifest.optInt("version") != VERSION)
                throw new IOException("This StudyFlow backup version is not supported.");
            JSONObject incoming = new JSONObject(new String(dataBytes, StandardCharsets.UTF_8));
            JSONObject merged = merge(currentRoot, incoming);

            Set<String> currentNoteIds = ids(currentRoot.optJSONArray("notes"));
            JSONArray incomingNotes = incoming.optJSONArray("notes");
            Map<String, String> fileNames = new LinkedHashMap<>();
            JSONArray mergedNotes = merged.optJSONArray("notes");
            for (int i = 0; incomingNotes != null && i < incomingNotes.length(); i++) {
                JSONObject note = incomingNotes.optJSONObject(i);
                if (note == null || currentNoteIds.contains(note.optString("id"))) continue;
                String sourceName = note.optString("file");
                if (sourceName.isEmpty()) continue;
                validateName(sourceName);
                File stagedFile = staged.get(sourceName);
                if (stagedFile == null || !stagedFile.isFile())
                    throw new IOException("The backup is missing an attachment referenced by a note.");
                String destinationName = fileNames.get(sourceName);
                if (destinationName == null) {
                    destinationName = UUID.randomUUID().toString();
                    File destinationFile = new File(filesDirectory, destinationName);
                    while (destinationFile.exists()) {
                        destinationName = UUID.randomUUID().toString();
                        destinationFile = new File(filesDirectory, destinationName);
                    }
                    fileNames.put(sourceName, destinationName);
                }
                for (int j = 0; j < mergedNotes.length(); j++) {
                    JSONObject mergedNote = mergedNotes.optJSONObject(j);
                    if (mergedNote != null && note.optString("id").equals(mergedNote.optString("id"))) {
                        mergedNote.put("file", destinationName);
                        break;
                    }
                }
            }
            validateRelationships(merged);
            Map<String, File> referencedStage = new LinkedHashMap<>();
            for (String sourceName : fileNames.keySet()) referencedStage.put(sourceName, staged.get(sourceName));
            return new RestorePlan(merged, stage, referencedStage, fileNames);
        } catch (Exception e) {
            deleteTree(stage);
            throw e;
        }
    }

    static PreparedCommit stageCommit(File filesDirectory, RestorePlan plan) throws Exception {
        List<File> committed = new ArrayList<>();
        try {
            for (Map.Entry<String, String> mapping : plan.attachmentNames.entrySet()) {
                File staged = plan.stagedAttachments.get(mapping.getKey());
                File destination = new File(filesDirectory, mapping.getValue());
                if (staged == null || !staged.isFile() || destination.exists())
                    throw new IOException("Could not safely restore an attachment.");
                if (!staged.renameTo(destination)) {
                    copyFile(staged, destination);
                    staged.delete();
                }
                committed.add(destination);
            }
            return new PreparedCommit(plan, committed);
        } catch (Exception e) {
            for (File file : committed) file.delete();
            plan.discard();
            throw e;
        }
    }

    static void finishCommit(Store store, PreparedCommit prepared) throws Exception {
        String original = store.root.toString();
        try {
            store.root = prepared.plan.mergedRoot;
            store.save();
            prepared.plan.discard();
        } catch (Exception e) {
            try { store.root = new JSONObject(original); } catch (JSONException ignored) { }
            prepared.discard();
            throw e;
        }
    }

    private static JSONObject merge(JSONObject current, JSONObject incoming) throws Exception {
        JSONObject merged = new JSONObject(current.toString());
        for (String key : incoming.keySet()) {
            if (isCollection(key)) continue;
            if (!merged.has(key)) merged.put(key, incoming.get(key));
        }
        for (String collection : COLLECTIONS) {
            JSONArray existing = merged.optJSONArray(collection);
            if (existing == null) existing = new JSONArray();
            JSONArray source = incoming.optJSONArray(collection);
            if (source == null) source = new JSONArray();
            Set<String> known = ids(existing);
            Set<String> incomingIds = new HashSet<>();
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null) throw new IOException("The backup contains an invalid " + collection + " record.");
                String id = item.optString("id");
                if (id.isEmpty() || !incomingIds.add(id))
                    throw new IOException("The backup contains duplicate or missing record IDs.");
                if (!known.contains(id)) existing.put(new JSONObject(item.toString()));
            }
            merged.put(collection, existing);
        }
        return merged;
    }

    private static void validateRelationships(JSONObject root) throws IOException {
        Set<String> subjects = ids(root.optJSONArray("subjects"));
        Set<String> chapters = ids(root.optJSONArray("chapters"));
        for (int i = 0; i < root.optJSONArray("chapters").length(); i++) {
            JSONObject chapter = root.optJSONArray("chapters").optJSONObject(i);
            if (!subjects.contains(chapter.optString("subject")))
                throw new IOException("A chapter in this backup refers to a missing subject.");
        }
        for (String collection : new String[]{"notes", "cards", "logs"}) {
            JSONArray items = root.optJSONArray(collection);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                String chapter = item.optString("chapter");
                if (!chapter.isEmpty() && !chapters.contains(chapter))
                    throw new IOException("A " + collection + " record refers to a missing chapter.");
            }
        }
    }

    private static Set<String> referencedAttachments(JSONObject root) throws Exception {
        Set<String> names = new HashSet<>();
        JSONArray notes = root.optJSONArray("notes");
        if (notes == null) return names;
        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note == null) throw new IOException("Study data contains an invalid note.");
            String file = note.optString("file");
            if (!file.isEmpty()) {
                validateName(file);
                names.add(file);
            }
        }
        return names;
    }

    private static Set<String> ids(JSONArray array) throws IOException {
        Set<String> ids = new HashSet<>();
        if (array == null) return ids;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || item.optString("id").isEmpty())
                throw new IOException("Study data contains a record without an ID.");
            ids.add(item.optString("id"));
        }
        return ids;
    }

    private static boolean isCollection(String key) {
        for (String collection : COLLECTIONS) if (collection.equals(key)) return true;
        return false;
    }

    private static void validateName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,120}") || ".".equals(name) || "..".equals(name))
            throw new IOException("The backup contains an unsafe attachment name.");
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readBounded(InputStream in, long limit, long remaining) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + (long) read > limit || out.size() + (long) read > remaining)
                throw new IOException("The backup contains an oversized file.");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static long copyBounded(InputStream in, File file, long fileLimit, long archiveRemaining) throws IOException {
        long total = 0;
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > fileLimit || total > archiveRemaining)
                    throw new IOException("The backup contains an oversized attachment.");
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        } catch (IOException e) {
            file.delete();
            throw e;
        }
        return total;
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.getFD().sync();
        } catch (IOException e) {
            destination.delete();
            throw e;
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}