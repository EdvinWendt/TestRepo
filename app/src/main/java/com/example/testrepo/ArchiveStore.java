package com.example.testrepo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ArchiveStore {
    private static final String PREFS_NAME = "archive_store";
    private static final String LEGACY_KEY_ARCHIVE_NAMES = "archive_names";
    private static final String KEY_ARCHIVES = "archives";
    private static final String KEY_ARCHIVE_NAME = "name";
    private static final String KEY_ARCHIVE_RECEIPTS = "receipts";

    private ArchiveStore() {
    }

    @NonNull
    public static ArrayList<String> loadArchiveNames(@NonNull Context context) {
        ArrayList<String> archiveNames = new ArrayList<>();
        for (Archive archive : loadArchives(context)) {
            if (!archive.name.isEmpty()) {
                archiveNames.add(archive.name);
            }
        }
        return archiveNames;
    }

    @NonNull
    public static ArrayList<Archive> loadArchives(@NonNull Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String rawArchives = preferences.getString(KEY_ARCHIVES, null);

        if (rawArchives == null) {
            return loadLegacyArchives(preferences);
        }

        try {
            JSONArray jsonArray = new JSONArray(rawArchives);
            ArrayList<Archive> archives = new ArrayList<>();
            for (int index = 0; index < jsonArray.length(); index++) {
                JSONObject archiveObject = jsonArray.optJSONObject(index);
                if (archiveObject == null) {
                    continue;
                }

                Archive archive = Archive.fromJson(archiveObject);
                if (!archive.name.isEmpty()) {
                    archives.add(archive);
                }
            }
            return archives;
        } catch (JSONException exception) {
            return loadLegacyArchives(preferences);
        }
    }

    @Nullable
    public static Archive loadArchiveAt(@NonNull Context context, int index) {
        ArrayList<Archive> archives = loadArchives(context);
        if (index < 0 || index >= archives.size()) {
            return null;
        }
        return archives.get(index);
    }

    public static void addArchiveName(@NonNull Context context, @NonNull String archiveName) {
        String trimmedArchiveName = archiveName.trim();
        if (trimmedArchiveName.isEmpty()) {
            return;
        }

        ArrayList<Archive> archives = loadArchives(context);
        archives.add(0, new Archive(trimmedArchiveName, new ArrayList<>()));
        saveArchives(context, archives);
    }

    public static void addReceiptToArchive(
            @NonNull Context context,
            int archiveIndex,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry
    ) {
        ArrayList<Archive> archives = loadArchives(context);
        if (archiveIndex < 0 || archiveIndex >= archives.size()) {
            return;
        }

        Archive archive = archives.get(archiveIndex);
        archive.receipts.add(0, receiptEntry);
        saveArchives(context, archives);
    }

    public static void renameArchiveAt(
            @NonNull Context context,
            int archiveIndex,
            @NonNull String archiveName
    ) {
        String trimmedArchiveName = archiveName.trim();
        if (trimmedArchiveName.isEmpty()) {
            return;
        }

        ArrayList<Archive> archives = loadArchives(context);
        if (archiveIndex < 0 || archiveIndex >= archives.size()) {
            return;
        }

        Archive archive = archives.get(archiveIndex);
        archives.set(archiveIndex, new Archive(trimmedArchiveName, archive.receipts));
        saveArchives(context, archives);
    }

    public static void removeReceiptAt(
            @NonNull Context context,
            int archiveIndex,
            int receiptIndex
    ) {
        ArrayList<Archive> archives = loadArchives(context);
        if (archiveIndex < 0 || archiveIndex >= archives.size()) {
            return;
        }

        Archive archive = archives.get(archiveIndex);
        if (receiptIndex < 0 || receiptIndex >= archive.receipts.size()) {
            return;
        }

        archive.receipts.remove(receiptIndex);
        saveArchives(context, archives);
    }

    public static void updateReceiptAt(
            @NonNull Context context,
            int archiveIndex,
            int receiptIndex,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry
    ) {
        ArrayList<Archive> archives = loadArchives(context);
        if (archiveIndex < 0 || archiveIndex >= archives.size()) {
            return;
        }

        Archive archive = archives.get(archiveIndex);
        if (receiptIndex < 0 || receiptIndex >= archive.receipts.size()) {
            return;
        }

        archive.receipts.set(receiptIndex, receiptEntry);
        saveArchives(context, archives);
    }

    public static void moveReceiptToArchive(
            @NonNull Context context,
            int sourceArchiveIndex,
            int receiptIndex,
            int targetArchiveIndex
    ) {
        if (sourceArchiveIndex == targetArchiveIndex) {
            return;
        }

        ArrayList<Archive> archives = loadArchives(context);
        if (sourceArchiveIndex < 0 || sourceArchiveIndex >= archives.size()
                || targetArchiveIndex < 0 || targetArchiveIndex >= archives.size()) {
            return;
        }

        Archive sourceArchive = archives.get(sourceArchiveIndex);
        if (receiptIndex < 0 || receiptIndex >= sourceArchive.receipts.size()) {
            return;
        }

        ReceiptHistoryStore.HistoryEntry receiptEntry = sourceArchive.receipts.remove(receiptIndex);
        archives.get(targetArchiveIndex).receipts.add(0, receiptEntry);
        saveArchives(context, archives);
    }

    public static void removeArchiveAt(@NonNull Context context, int index) {
        ArrayList<Archive> archives = loadArchives(context);
        if (index < 0 || index >= archives.size()) {
            return;
        }

        archives.remove(index);
        saveArchives(context, archives);
    }

    private static void saveArchives(
            @NonNull Context context,
            @NonNull List<Archive> archives
    ) {
        JSONArray jsonArray = new JSONArray();
        for (Archive archive : archives) {
            jsonArray.put(archive.toJson());
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ARCHIVES, jsonArray.toString())
                .remove(LEGACY_KEY_ARCHIVE_NAMES)
                .apply();
    }

    @NonNull
    private static ArrayList<Archive> loadLegacyArchives(@NonNull SharedPreferences preferences) {
        String rawArchiveNames = preferences.getString(LEGACY_KEY_ARCHIVE_NAMES, "[]");
        ArrayList<Archive> archives = new ArrayList<>();

        try {
            JSONArray jsonArray = new JSONArray(rawArchiveNames);
            for (int index = 0; index < jsonArray.length(); index++) {
                String archiveName = jsonArray.optString(index, "").trim();
                if (!archiveName.isEmpty()) {
                    archives.add(new Archive(archiveName, new ArrayList<>()));
                }
            }
        } catch (JSONException exception) {
            preferences.edit().remove(LEGACY_KEY_ARCHIVE_NAMES).apply();
        }

        return archives;
    }

    static final class Archive {
        final String name;
        final ArrayList<ReceiptHistoryStore.HistoryEntry> receipts;

        Archive(
                @NonNull String name,
                @NonNull List<ReceiptHistoryStore.HistoryEntry> receipts
        ) {
            this.name = name.trim();
            this.receipts = new ArrayList<>(receipts);
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray receiptsArray = new JSONArray();
            for (ReceiptHistoryStore.HistoryEntry receipt : receipts) {
                receiptsArray.put(receipt.toJson());
            }

            try {
                object.put(KEY_ARCHIVE_NAME, name);
                object.put(KEY_ARCHIVE_RECEIPTS, receiptsArray);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize archive", exception);
            }
            return object;
        }

        @NonNull
        private static Archive fromJson(@NonNull JSONObject object) {
            String name = object.optString(KEY_ARCHIVE_NAME, "").trim();
            JSONArray receiptsArray = object.optJSONArray(KEY_ARCHIVE_RECEIPTS);
            ArrayList<ReceiptHistoryStore.HistoryEntry> receipts = new ArrayList<>();

            if (receiptsArray != null) {
                for (int index = 0; index < receiptsArray.length(); index++) {
                    JSONObject receiptObject = receiptsArray.optJSONObject(index);
                    if (receiptObject == null) {
                        continue;
                    }
                    receipts.add(ReceiptHistoryStore.HistoryEntry.fromJson(receiptObject));
                }
            }

            return new Archive(name, receipts);
        }
    }
}
