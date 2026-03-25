package com.example.testrepo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ReceiptHistoryStore {
    private static final String PREFERENCES_NAME = "receipt_history_preferences";
    private static final String KEY_ENTRIES = "receipt_history_entries";
    private static final String KEY_RECEIPT_NAME = "receipt_name";
    private static final String KEY_TOTAL_AMOUNT = "total_amount";
    private static final String KEY_SENT_DATE = "sent_date";
    private static final String KEY_PARTICIPANTS = "participants";
    private static final String KEY_PARTICIPANT_NAME = "name";
    private static final String KEY_PARTICIPANT_AMOUNT = "amount";

    private ReceiptHistoryStore() {
    }

    static void saveEntry(@NonNull Context context, @NonNull HistoryEntry entry) {
        ArrayList<HistoryEntry> entries = loadEntries(context);
        entries.add(0, entry);

        JSONArray serializedEntries = new JSONArray();
        for (HistoryEntry existingEntry : entries) {
            serializedEntries.put(existingEntry.toJson());
        }

        getPreferences(context)
                .edit()
                .putString(KEY_ENTRIES, serializedEntries.toString())
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
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    static final class HistoryEntry {
        final String receiptName;
        final String totalAmount;
        final String sentDate;
        final ArrayList<ParticipantShare> participants;

        HistoryEntry(
                @NonNull String receiptName,
                @NonNull String totalAmount,
                @NonNull String sentDate,
                @NonNull List<ParticipantShare> participants
        ) {
            this.receiptName = receiptName;
            this.totalAmount = totalAmount;
            this.sentDate = sentDate;
            this.participants = new ArrayList<>(participants);
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray participantArray = new JSONArray();
            for (ParticipantShare participant : participants) {
                participantArray.put(participant.toJson());
            }

            try {
                object.put(KEY_RECEIPT_NAME, receiptName);
                object.put(KEY_TOTAL_AMOUNT, totalAmount);
                object.put(KEY_SENT_DATE, sentDate);
                object.put(KEY_PARTICIPANTS, participantArray);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize history entry", exception);
            }

            return object;
        }

        @NonNull
        private static HistoryEntry fromJson(@NonNull JSONObject object) {
            JSONArray participantArray = object.optJSONArray(KEY_PARTICIPANTS);
            ArrayList<ParticipantShare> participants = new ArrayList<>();
            if (participantArray != null) {
                for (int index = 0; index < participantArray.length(); index++) {
                    JSONObject participantObject = participantArray.optJSONObject(index);
                    if (participantObject == null) {
                        continue;
                    }
                    participants.add(ParticipantShare.fromJson(participantObject));
                }
            }

            return new HistoryEntry(
                    object.optString(KEY_RECEIPT_NAME, ""),
                    object.optString(KEY_TOTAL_AMOUNT, ""),
                    object.optString(KEY_SENT_DATE, ""),
                    participants
            );
        }
    }

    static final class ParticipantShare {
        final String name;
        final String amount;

        ParticipantShare(@NonNull String name, @NonNull String amount) {
            this.name = name;
            this.amount = amount;
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put(KEY_PARTICIPANT_NAME, name);
                object.put(KEY_PARTICIPANT_AMOUNT, amount);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize participant share", exception);
            }
            return object;
        }

        @NonNull
        private static ParticipantShare fromJson(@NonNull JSONObject object) {
            return new ParticipantShare(
                    object.optString(KEY_PARTICIPANT_NAME, ""),
                    object.optString(KEY_PARTICIPANT_AMOUNT, "")
            );
        }
    }
}
