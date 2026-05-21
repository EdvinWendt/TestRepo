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
import java.util.Locale;
import java.util.Map;

final class ReceiptHistoryStore {
    static final String ENTRY_TYPE_RECEIPT = "receipt";
    static final String ENTRY_TYPE_ARCHIVE_SUMMARY = "archive_summary";

    private static final String PREFERENCES_NAME = "receipt_history_preferences";
    private static final String KEY_ENTRIES = "receipt_history_entries";
    private static final String KEY_ENTRY_TYPE = "entry_type";
    private static final String KEY_RECEIPT_NAME = "receipt_name";
    private static final String KEY_TOTAL_AMOUNT = "total_amount";
    private static final String KEY_SENT_DATE = "sent_date";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_PARTICIPANTS = "participants";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ARCHIVED_RECEIPTS = "archived_receipts";
    private static final String KEY_PARTICIPANT_NAME = "name";
    private static final String KEY_PARTICIPANT_AMOUNT = "amount";
    private static final String KEY_PARTICIPANT_KEY = "key";
    private static final String KEY_PARTICIPANT_INITIALS = "initials";
    private static final String KEY_PARTICIPANT_COLOR = "color";
    private static final String KEY_PARTICIPANT_PHONE = "phone";
    private static final String KEY_PARTICIPANT_IS_CROWNED = "is_crowned";
    private static final String KEY_PARTICIPANT_HAS_PAID = "has_paid";
    private static final String KEY_ITEM_NAME = "name";
    private static final String KEY_ITEM_PRICE = "price";
    private static final String KEY_ITEM_HAS_PAID = "has_paid";
    private static final String KEY_ITEM_PAYER_PARTICIPANT_KEY = "payer_participant_key";
    private static final String KEY_ITEM_SELECTED_PARTICIPANT_KEYS = "selected_participant_keys";

    private ReceiptHistoryStore() {
    }

    static void saveEntry(@NonNull Context context, @NonNull HistoryEntry entry) {
        ArrayList<HistoryEntry> entries = loadEntries(context);
        entries.add(0, entry);
        saveEntries(context, entries);
    }

    static boolean removeEntry(@NonNull Context context, @NonNull HistoryEntry targetEntry) {
        ArrayList<HistoryEntry> entries = loadEntries(context);
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).matches(targetEntry)) {
                entries.remove(index);
                saveEntries(context, entries);
                return true;
            }
        }
        return false;
    }

    @NonNull
    static HistoryEntry markParticipantPaid(
            @NonNull Context context,
            @NonNull HistoryEntry targetEntry,
            @NonNull String participantKey,
            boolean hasPaid
    ) {
        ArrayList<HistoryEntry> entries = loadEntries(context);
            for (int index = 0; index < entries.size(); index++) {
                HistoryEntry existingEntry = entries.get(index);
                if (!existingEntry.matches(targetEntry)) {
                    continue;
                }

            HistoryEntry updatedEntry = withParticipantPaidStatus(
                    existingEntry,
                    participantKey,
                    hasPaid
            );
            entries.set(index, updatedEntry);
            saveEntries(context, entries);
            return updatedEntry;
        }

        return targetEntry;
    }

    @NonNull
    static HistoryEntry markHistoryItemPaid(
            @NonNull Context context,
            @NonNull HistoryEntry targetEntry,
            @NonNull HistoryItem targetItem,
            boolean hasPaid
    ) {
        ArrayList<HistoryEntry> entries = loadEntries(context);
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                HistoryEntry existingEntry = entries.get(entryIndex);
                if (!existingEntry.matches(targetEntry)) {
                    continue;
                }

            HistoryEntry updatedEntry = withHistoryItemPaidStatus(
                    existingEntry,
                    targetItem,
                    hasPaid
            );
            entries.set(entryIndex, updatedEntry);
            saveEntries(context, entries);
            return updatedEntry;
        }

        return targetEntry;
    }

    private static void saveEntries(
            @NonNull Context context,
            @NonNull List<HistoryEntry> entries
    ) {
        JSONArray serializedEntries = new JSONArray();
        for (HistoryEntry entry : entries) {
            serializedEntries.put(entry.toJson());
        }

        getPreferences(context)
                .edit()
                .putString(KEY_ENTRIES, serializedEntries.toString())
                .apply();
    }

    static void clearHistory(@NonNull Context context) {
        getPreferences(context)
                .edit()
                .remove(KEY_ENTRIES)
                .apply();
    }

    @NonNull
    static ArrayList<HistoryEntry> loadEntries(@NonNull Context context) {
        String rawEntries = getPreferences(context).getString(KEY_ENTRIES, "[]");
        ArrayList<HistoryEntry> entries = new ArrayList<>();

        try {
            JSONArray serializedEntries = new JSONArray(rawEntries);
            for (int index = 0; index < serializedEntries.length(); index++) {
                JSONObject serializedEntry = serializedEntries.optJSONObject(index);
                if (serializedEntry == null) {
                    continue;
                }
                entries.add(HistoryEntry.fromJson(serializedEntry));
            }
        } catch (JSONException exception) {
            return new ArrayList<>();
        }

        return entries;
    }

    @NonNull
    static HistoryEntry withParticipantPaidStatus(
            @NonNull HistoryEntry sourceEntry,
            @NonNull String participantKey,
            boolean hasPaid
    ) {
        ArrayList<ParticipantShare> updatedParticipants = new ArrayList<>();
        for (ParticipantShare participant : sourceEntry.participants) {
            updatedParticipants.add(participant.key.equals(participantKey)
                    ? participant.copyWithPaidStatus(hasPaid)
                    : participant);
        }

        return new HistoryEntry(
                sourceEntry.receiptName,
                sourceEntry.totalAmount,
                sourceEntry.sentDate,
                sourceEntry.message,
                updatedParticipants,
                sourceEntry.items,
                sourceEntry.entryType,
                sourceEntry.archivedReceipts,
                sourceEntry.storageId
        );
    }

    @NonNull
    static HistoryEntry withHistoryItemPaidStatus(
            @NonNull HistoryEntry sourceEntry,
            @NonNull HistoryItem targetItem,
            boolean hasPaid
    ) {
        ArrayList<HistoryItem> updatedItems = new ArrayList<>();
        boolean itemUpdated = false;
        for (HistoryItem item : sourceEntry.items) {
            if (!itemUpdated && item.matches(targetItem)) {
                updatedItems.add(item.copyWithPaidStatus(hasPaid));
                itemUpdated = true;
            } else {
                updatedItems.add(item);
            }
        }

        return new HistoryEntry(
                sourceEntry.receiptName,
                sourceEntry.totalAmount,
                sourceEntry.sentDate,
                sourceEntry.message,
                sourceEntry.participants,
                updatedItems,
                sourceEntry.entryType,
                sourceEntry.archivedReceipts,
                sourceEntry.storageId
        );
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        SharedPreferences accountPreferences = context.getSharedPreferences(
                getPreferencesNameForCurrentUser(context),
                Context.MODE_PRIVATE
        );
        migrateLegacyPreferencesIfNeeded(context, accountPreferences);
        return accountPreferences;
    }

    @NonNull
    private static String getPreferencesNameForCurrentUser(@NonNull Context context) {
        String normalizedEmail = AppSettings.getLoginEmail(context)
                .trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9@._-]", "_");
        if (normalizedEmail.isEmpty()) {
            normalizedEmail = "signed_out";
        }
        return PREFERENCES_NAME + "_" + normalizedEmail;
    }

    private static void migrateLegacyPreferencesIfNeeded(
            @NonNull Context context,
            @NonNull SharedPreferences accountPreferences
    ) {
        if (!accountPreferences.getAll().isEmpty()) {
            return;
        }

        SharedPreferences legacyPreferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Map<String, ?> legacyEntries = legacyPreferences.getAll();
        if (legacyEntries.isEmpty()) {
            return;
        }

        SharedPreferences.Editor accountEditor = accountPreferences.edit();
        boolean copiedAnyValue = false;
        for (Map.Entry<String, ?> entry : legacyEntries.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                accountEditor.putString(entry.getKey(), (String) value);
                copiedAnyValue = true;
            }
        }

        if (!copiedAnyValue) {
            return;
        }

        accountEditor.apply();
        legacyPreferences.edit().clear().apply();
    }

    static final class HistoryEntry {
        @NonNull
        final String storageId;
        final String entryType;
        final String receiptName;
        final String totalAmount;
        final String sentDate;
        final String message;
        final ArrayList<ParticipantShare> participants;
        final ArrayList<HistoryItem> items;
        final ArrayList<HistoryEntry> archivedReceipts;

        HistoryEntry(
                @NonNull String receiptName,
                @NonNull String totalAmount,
                @NonNull String sentDate,
                @NonNull String message,
                @NonNull List<ParticipantShare> participants,
                @NonNull List<HistoryItem> items
        ) {
            this(
                    receiptName,
                    totalAmount,
                    sentDate,
                    message,
                    participants,
                    items,
                    ENTRY_TYPE_RECEIPT,
                    new ArrayList<>(),
                    ""
            );
        }

        HistoryEntry(
                @NonNull String receiptName,
                @NonNull String totalAmount,
                @NonNull String sentDate,
                @NonNull String message,
                @NonNull List<ParticipantShare> participants,
                @NonNull List<HistoryItem> items,
                @NonNull String entryType
        ) {
            this(
                    receiptName,
                    totalAmount,
                    sentDate,
                    message,
                    participants,
                    items,
                    entryType,
                    new ArrayList<>(),
                    ""
            );
        }

        HistoryEntry(
                @NonNull String receiptName,
                @NonNull String totalAmount,
                @NonNull String sentDate,
                @NonNull String message,
                @NonNull List<ParticipantShare> participants,
                @NonNull List<HistoryItem> items,
                @NonNull String entryType,
                @NonNull List<HistoryEntry> archivedReceipts
        ) {
            this(
                    receiptName,
                    totalAmount,
                    sentDate,
                    message,
                    participants,
                    items,
                    entryType,
                    archivedReceipts,
                    ""
            );
        }

        HistoryEntry(
                @NonNull String receiptName,
                @NonNull String totalAmount,
                @NonNull String sentDate,
                @NonNull String message,
                @NonNull List<ParticipantShare> participants,
                @NonNull List<HistoryItem> items,
                @NonNull String entryType,
                @NonNull List<HistoryEntry> archivedReceipts,
                @Nullable String storageId
        ) {
            this.storageId = storageId == null ? "" : storageId.trim();
            this.entryType = entryType;
            this.receiptName = receiptName;
            this.totalAmount = totalAmount;
            this.sentDate = sentDate;
            this.message = message;
            this.participants = new ArrayList<>(participants);
            this.items = new ArrayList<>(items);
            this.archivedReceipts = new ArrayList<>(archivedReceipts);
        }

        @NonNull
        JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray participantArray = new JSONArray();
            JSONArray itemArray = new JSONArray();
            JSONArray archivedReceiptsArray = new JSONArray();
            for (ParticipantShare participant : participants) {
                participantArray.put(participant.toJson());
            }
            for (HistoryItem item : items) {
                itemArray.put(item.toJson());
            }
            for (HistoryEntry archivedReceipt : archivedReceipts) {
                archivedReceiptsArray.put(archivedReceipt.toJson());
            }

            try {
                object.put(KEY_ENTRY_TYPE, entryType);
                object.put(KEY_RECEIPT_NAME, receiptName);
                object.put(KEY_TOTAL_AMOUNT, totalAmount);
                object.put(KEY_SENT_DATE, sentDate);
                object.put(KEY_MESSAGE, message);
                object.put(KEY_PARTICIPANTS, participantArray);
                object.put(KEY_ITEMS, itemArray);
                object.put(KEY_ARCHIVED_RECEIPTS, archivedReceiptsArray);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize history entry", exception);
            }

            return object;
        }

        @NonNull
        static HistoryEntry fromJson(@NonNull JSONObject object) {
            JSONArray participantArray = object.optJSONArray(KEY_PARTICIPANTS);
            ArrayList<ParticipantShare> participants = new ArrayList<>();
            if (participantArray != null) {
                for (int index = 0; index < participantArray.length(); index++) {
                    JSONObject participantObject = participantArray.optJSONObject(index);
                    if (participantObject == null) {
                        continue;
                    }
                    participants.add(ParticipantShare.fromJson(participantObject, index));
                }
            }

            JSONArray itemArray = object.optJSONArray(KEY_ITEMS);
            ArrayList<HistoryItem> items = new ArrayList<>();
            if (itemArray != null) {
                for (int index = 0; index < itemArray.length(); index++) {
                    JSONObject itemObject = itemArray.optJSONObject(index);
                    if (itemObject == null) {
                        continue;
                    }
                    items.add(HistoryItem.fromJson(itemObject));
                }
            }

            JSONArray archivedReceiptsArray = object.optJSONArray(KEY_ARCHIVED_RECEIPTS);
            ArrayList<HistoryEntry> archivedReceipts = new ArrayList<>();
            if (archivedReceiptsArray != null) {
                for (int index = 0; index < archivedReceiptsArray.length(); index++) {
                    JSONObject archivedReceiptObject = archivedReceiptsArray.optJSONObject(index);
                    if (archivedReceiptObject == null) {
                        continue;
                    }
                    archivedReceipts.add(HistoryEntry.fromJson(archivedReceiptObject));
                }
            }

            return new HistoryEntry(
                    object.optString(KEY_RECEIPT_NAME, ""),
                    object.optString(KEY_TOTAL_AMOUNT, ""),
                    object.optString(KEY_SENT_DATE, ""),
                    object.optString(KEY_MESSAGE, ""),
                    participants,
                    items,
                    object.optString(KEY_ENTRY_TYPE, ENTRY_TYPE_RECEIPT),
                    archivedReceipts,
                    ""
            );
        }

        @NonNull
        HistoryEntry copyWithStorageId(@Nullable String storageId) {
            return new HistoryEntry(
                    receiptName,
                    totalAmount,
                    sentDate,
                    message,
                    participants,
                    items,
                    entryType,
                    archivedReceipts,
                    storageId
            );
        }

        boolean isArchiveSummary() {
            return ENTRY_TYPE_ARCHIVE_SUMMARY.equals(entryType);
        }

        private boolean matches(@NonNull HistoryEntry other) {
            if (!entryType.equals(other.entryType)
                    || !receiptName.equals(other.receiptName)
                    || !totalAmount.equals(other.totalAmount)
                    || !sentDate.equals(other.sentDate)
                    || !message.equals(other.message)
                    || participants.size() != other.participants.size()
                    || items.size() != other.items.size()
                    || archivedReceipts.size() != other.archivedReceipts.size()) {
                return false;
            }

            for (int index = 0; index < participants.size(); index++) {
                if (!participants.get(index).matches(other.participants.get(index))) {
                    return false;
                }
            }

            for (int index = 0; index < items.size(); index++) {
                if (!items.get(index).matches(other.items.get(index))) {
                    return false;
                }
            }

            for (int index = 0; index < archivedReceipts.size(); index++) {
                if (!archivedReceipts.get(index).matches(other.archivedReceipts.get(index))) {
                    return false;
                }
            }

            return true;
        }
    }

    static final class ParticipantShare {
        final String key;
        final String name;
        final String initials;
        final int color;
        final String phoneNumber;
        final String amount;
        final boolean isCrowned;
        final boolean hasPaid;

        ParticipantShare(
                @NonNull String key,
                @NonNull String name,
                @NonNull String initials,
                int color,
                @NonNull String phoneNumber,
                @NonNull String amount,
                boolean isCrowned,
                boolean hasPaid
        ) {
            this.key = key;
            this.name = name;
            this.initials = initials;
            this.color = color;
            this.phoneNumber = phoneNumber;
            this.amount = amount;
            this.isCrowned = isCrowned;
            this.hasPaid = hasPaid;
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put(KEY_PARTICIPANT_KEY, key);
                object.put(KEY_PARTICIPANT_NAME, name);
                object.put(KEY_PARTICIPANT_INITIALS, initials);
                object.put(KEY_PARTICIPANT_COLOR, color);
                object.put(KEY_PARTICIPANT_PHONE, phoneNumber);
                object.put(KEY_PARTICIPANT_AMOUNT, amount);
                object.put(KEY_PARTICIPANT_IS_CROWNED, isCrowned);
                object.put(KEY_PARTICIPANT_HAS_PAID, hasPaid);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize participant share", exception);
            }
            return object;
        }

        @NonNull
        private static ParticipantShare fromJson(@NonNull JSONObject object, int fallbackIndex) {
            String name = object.optString(KEY_PARTICIPANT_NAME, "");
            return new ParticipantShare(
                    object.optString(
                            KEY_PARTICIPANT_KEY,
                            buildLegacyParticipantKey(name, fallbackIndex)
                    ),
                    name,
                    object.optString(KEY_PARTICIPANT_INITIALS, deriveInitials(name)),
                    object.has(KEY_PARTICIPANT_COLOR)
                            ? object.optInt(KEY_PARTICIPANT_COLOR)
                            : createParticipantColor(fallbackIndex),
                    object.optString(KEY_PARTICIPANT_PHONE, ""),
                    object.optString(KEY_PARTICIPANT_AMOUNT, ""),
                    object.optBoolean(KEY_PARTICIPANT_IS_CROWNED, false),
                    object.optBoolean(KEY_PARTICIPANT_HAS_PAID, false)
            );
        }

        @NonNull
        ParticipantShare copyWithPaidStatus(boolean hasPaid) {
            return new ParticipantShare(
                    key,
                    name,
                    initials,
                    color,
                    phoneNumber,
                    amount,
                    isCrowned,
                    hasPaid
            );
        }

        private boolean matches(@NonNull ParticipantShare other) {
            return key.equals(other.key)
                    && name.equals(other.name)
                    && initials.equals(other.initials)
                    && color == other.color
                    && phoneNumber.equals(other.phoneNumber)
                    && amount.equals(other.amount)
                    && isCrowned == other.isCrowned
                    && hasPaid == other.hasPaid;
        }
    }

    static final class HistoryItem {
        final String name;
        final String price;
        final boolean hasPaid;
        @NonNull
        final String payerParticipantKey;
        final ArrayList<String> selectedParticipantKeys;

        HistoryItem(
                @NonNull String name,
                @NonNull String price,
                @NonNull List<String> selectedParticipantKeys
        ) {
            this(name, price, false, "", selectedParticipantKeys);
        }

        HistoryItem(
                @NonNull String name,
                @NonNull String price,
                boolean hasPaid,
                @NonNull List<String> selectedParticipantKeys
        ) {
            this(name, price, hasPaid, "", selectedParticipantKeys);
        }

        HistoryItem(
                @NonNull String name,
                @NonNull String price,
                @NonNull String payerParticipantKey,
                @NonNull List<String> selectedParticipantKeys
        ) {
            this(name, price, false, payerParticipantKey, selectedParticipantKeys);
        }

        HistoryItem(
                @NonNull String name,
                @NonNull String price,
                boolean hasPaid,
                @NonNull String payerParticipantKey,
                @NonNull List<String> selectedParticipantKeys
        ) {
            this.name = name;
            this.price = price;
            this.hasPaid = hasPaid;
            this.payerParticipantKey = payerParticipantKey;
            this.selectedParticipantKeys = new ArrayList<>(selectedParticipantKeys);
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray selectedParticipantsArray = new JSONArray();
            for (String participantKey : selectedParticipantKeys) {
                selectedParticipantsArray.put(participantKey);
            }
            try {
                object.put(KEY_ITEM_NAME, name);
                object.put(KEY_ITEM_PRICE, price);
                object.put(KEY_ITEM_HAS_PAID, hasPaid);
                object.put(KEY_ITEM_PAYER_PARTICIPANT_KEY, payerParticipantKey);
                object.put(KEY_ITEM_SELECTED_PARTICIPANT_KEYS, selectedParticipantsArray);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize history item", exception);
            }
            return object;
        }

        @NonNull
        private static HistoryItem fromJson(@NonNull JSONObject object) {
            JSONArray selectedParticipantsArray =
                    object.optJSONArray(KEY_ITEM_SELECTED_PARTICIPANT_KEYS);
            ArrayList<String> selectedParticipantKeys = new ArrayList<>();
            if (selectedParticipantsArray != null) {
                for (int index = 0; index < selectedParticipantsArray.length(); index++) {
                    String participantKey = selectedParticipantsArray.optString(index, "");
                    if (!participantKey.isEmpty()) {
                        selectedParticipantKeys.add(participantKey);
                    }
                }
            }

            return new HistoryItem(
                    object.optString(KEY_ITEM_NAME, ""),
                    object.optString(KEY_ITEM_PRICE, ""),
                    object.optBoolean(KEY_ITEM_HAS_PAID, false),
                    object.optString(KEY_ITEM_PAYER_PARTICIPANT_KEY, ""),
                    selectedParticipantKeys
            );
        }

        boolean isParticipantSelected(@NonNull String participantKey) {
            return selectedParticipantKeys.contains(participantKey);
        }

        @NonNull
        HistoryItem copyWithPaidStatus(boolean hasPaid) {
            return new HistoryItem(
                    name,
                    price,
                    hasPaid,
                    payerParticipantKey,
                    selectedParticipantKeys
            );
        }

        private boolean matches(@NonNull HistoryItem other) {
            if (!name.equals(other.name)
                    || !price.equals(other.price)
                    || hasPaid != other.hasPaid
                    || !payerParticipantKey.equals(other.payerParticipantKey)
                    || selectedParticipantKeys.size() != other.selectedParticipantKeys.size()) {
                return false;
            }

            for (int index = 0; index < selectedParticipantKeys.size(); index++) {
                if (!selectedParticipantKeys.get(index)
                        .equals(other.selectedParticipantKeys.get(index))) {
                    return false;
                }
            }

            return true;
        }
    }

    @NonNull
    private static String deriveInitials(@NonNull String name) {
        String normalizedName = normalizeWhitespace(name);
        if (normalizedName.isEmpty()) {
            return "?";
        }

        String[] parts = normalizedName.split(" ");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        if (initials.length() == 0) {
            initials.append(Character.toUpperCase(normalizedName.charAt(0)));
        }
        if (initials.length() == 1 && normalizedName.length() > 1) {
            initials.append(Character.toUpperCase(normalizedName.charAt(1)));
        }
        return initials.toString();
    }

    private static int createParticipantColor(int participantIndex) {
        float hue = (participantIndex * 137.508f) % 360f;
        float[] hsv = {hue, 0.72f, 0.78f};
        return android.graphics.Color.HSVToColor(hsv);
    }

    @NonNull
    private static String buildLegacyParticipantKey(@NonNull String name, int index) {
        return normalizeWhitespace(name).toLowerCase()
                .replaceAll("\\s+", "_")
                + "_"
                + index;
    }

    @NonNull
    private static String normalizeWhitespace(@NonNull String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
