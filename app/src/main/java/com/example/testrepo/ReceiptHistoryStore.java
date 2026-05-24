package com.example.testrepo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String KEY_PAYMENT_CARDS = "payment_cards";
    private static final String KEY_PAYMENT_CARD_ID = "id";
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
    private static final String KEY_PAYMENT_CARD_HAS_PAID = "has_paid";
    private static final String KEY_PAYMENT_CARD_RECIPIENT_PHONE = "recipient_phone";

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
        ArrayList<PaymentCard> updatedPaymentCards =
                copyPaymentCardsWithParticipantPaidStatus(sourceEntry, participantKey, hasPaid);

        return new HistoryEntry(
                sourceEntry.receiptName,
                sourceEntry.totalAmount,
                sourceEntry.sentDate,
                sourceEntry.message,
                updatedParticipants,
                sourceEntry.items,
                sourceEntry.entryType,
                sourceEntry.archivedReceipts,
                updatedPaymentCards,
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
        int updatedItemIndex = -1;
        for (HistoryItem item : sourceEntry.items) {
            if (!itemUpdated && item.matches(targetItem)) {
                updatedItems.add(item.copyWithPaidStatus(hasPaid));
                itemUpdated = true;
                updatedItemIndex = updatedItems.size() - 1;
            } else {
                updatedItems.add(item);
            }
        }
        ArrayList<PaymentCard> updatedPaymentCards = new ArrayList<>();
        for (int index = 0; index < sourceEntry.paymentCards.size(); index++) {
            PaymentCard paymentCard = sourceEntry.paymentCards.get(index);
            if (index == updatedItemIndex) {
                updatedPaymentCards.add(paymentCard.copyWithPaidStatus(hasPaid));
            } else {
                updatedPaymentCards.add(paymentCard);
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
                updatedPaymentCards,
                sourceEntry.storageId
        );
    }

    @NonNull
    static HistoryEntry withPaymentCardPaidStatus(
            @NonNull HistoryEntry sourceEntry,
            @NonNull String paymentCardId,
            boolean hasPaid
    ) {
        ArrayList<PaymentCard> updatedPaymentCards = new ArrayList<>();
        boolean updatedAnyCard = false;
        for (PaymentCard paymentCard : sourceEntry.paymentCards) {
            if (!updatedAnyCard && paymentCard.id.equals(paymentCardId)) {
                updatedPaymentCards.add(paymentCard.copyWithPaidStatus(hasPaid));
                updatedAnyCard = true;
            } else {
                updatedPaymentCards.add(paymentCard);
            }
        }

        if (!updatedAnyCard) {
            return sourceEntry;
        }

        ArrayList<ParticipantShare> updatedParticipants =
                HistoryEntry.applyPaymentCardPaidStatusesToParticipants(
                        sourceEntry.participants,
                        sourceEntry.items,
                        updatedPaymentCards,
                        sourceEntry.entryType
                );
        ArrayList<HistoryItem> updatedItems =
                HistoryEntry.applyPaymentCardPaidStatusesToItems(
                        sourceEntry.items,
                        updatedPaymentCards,
                        sourceEntry.entryType
                );

        return new HistoryEntry(
                sourceEntry.receiptName,
                sourceEntry.totalAmount,
                sourceEntry.sentDate,
                sourceEntry.message,
                updatedParticipants,
                updatedItems,
                sourceEntry.entryType,
                sourceEntry.archivedReceipts,
                updatedPaymentCards,
                sourceEntry.storageId
        );
    }

    @NonNull
    private static ArrayList<PaymentCard> copyPaymentCardsWithParticipantPaidStatus(
            @NonNull HistoryEntry sourceEntry,
            @NonNull String participantKey,
            boolean hasPaid
    ) {
        ArrayList<String> debtorKeys = resolvePaymentCardDebtorKeys(sourceEntry);
        ArrayList<PaymentCard> updatedPaymentCards = new ArrayList<>();
        for (int index = 0; index < sourceEntry.paymentCards.size(); index++) {
            PaymentCard paymentCard = sourceEntry.paymentCards.get(index);
            boolean shouldUpdate = index < debtorKeys.size()
                    && participantKey.equals(debtorKeys.get(index));
            updatedPaymentCards.add(
                    shouldUpdate ? paymentCard.copyWithPaidStatus(hasPaid) : paymentCard
            );
        }
        return updatedPaymentCards;
    }

    @NonNull
    private static ArrayList<String> resolvePaymentCardDebtorKeys(
            @NonNull HistoryEntry sourceEntry
    ) {
        return ENTRY_TYPE_ARCHIVE_SUMMARY.equals(sourceEntry.entryType)
                ? buildArchiveSummaryPaymentCardDebtorKeys(sourceEntry.items)
                : buildReceiptPaymentCardDebtorKeys(sourceEntry.participants, sourceEntry.items);
    }

    @NonNull
    private static ArrayList<String> buildArchiveSummaryPaymentCardDebtorKeys(
            @NonNull List<HistoryItem> items
    ) {
        ArrayList<String> debtorKeys = new ArrayList<>();
        for (HistoryItem item : items) {
            debtorKeys.add(item.selectedParticipantKeys.isEmpty()
                    ? ""
                    : item.selectedParticipantKeys.get(0));
        }
        return debtorKeys;
    }

    @NonNull
    private static ArrayList<String> buildReceiptPaymentCardDebtorKeys(
            @NonNull List<ParticipantShare> participants,
            @NonNull List<HistoryItem> items
    ) {
        LinkedHashMap<String, ParticipantShare> participantsByKey = new LinkedHashMap<>();
        LinkedHashMap<String, BigDecimal> balancesByKey = new LinkedHashMap<>();
        for (ParticipantShare participant : participants) {
            participantsByKey.put(participant.key, participant);
            balancesByKey.put(participant.key, BigDecimal.ZERO);
        }

        for (HistoryItem item : items) {
            ParticipantShare payer = findReceiptPaymentCardPayer(participants, item);
            if (payer == null) {
                continue;
            }

            participantsByKey.putIfAbsent(payer.key, payer);
            balancesByKey.putIfAbsent(payer.key, BigDecimal.ZERO);

            int selectedParticipantCount = countSelectedParticipants(item, participants);
            if (selectedParticipantCount == 0) {
                continue;
            }

            BigDecimal itemAmount = parseCurrencyAmount(item.price);
            BigDecimal sharedAmount = itemAmount.divide(
                    BigDecimal.valueOf(selectedParticipantCount),
                    2,
                    RoundingMode.HALF_UP
            );
            for (ParticipantShare participant : participants) {
                if (!item.isParticipantSelected(participant.key)
                        || participant.key.equals(payer.key)) {
                    continue;
                }

                balancesByKey.put(
                        payer.key,
                        balancesByKey.get(payer.key).add(sharedAmount)
                );
                balancesByKey.put(
                        participant.key,
                        balancesByKey.get(participant.key).subtract(sharedAmount)
                );
            }
        }

        ArrayList<TransferBalance> creditors = new ArrayList<>();
        ArrayList<TransferBalance> debtors = new ArrayList<>();
        for (String participantKey : balancesByKey.keySet()) {
            BigDecimal balance = balancesByKey.get(participantKey).setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new TransferBalance(participantsByKey.get(participantKey), balance));
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new TransferBalance(
                        participantsByKey.get(participantKey),
                        balance.abs()
                ));
            }
        }

        ArrayList<String> debtorKeys = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((first, second) -> second.amount.compareTo(first.amount));
            debtors.sort((first, second) -> second.amount.compareTo(first.amount));

            TransferBalance creditor = creditors.get(0);
            TransferBalance debtor = debtors.get(0);
            BigDecimal transferAmount = creditor.amount.min(debtor.amount)
                    .setScale(2, RoundingMode.HALF_UP);
            debtorKeys.add(debtor.participant.key);

            creditor.amount = creditor.amount.subtract(transferAmount);
            debtor.amount = debtor.amount.subtract(transferAmount);

            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) {
                creditors.remove(0);
            }
            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) {
                debtors.remove(0);
            }
        }

        return debtorKeys;
    }

    @Nullable
    private static ParticipantShare findReceiptPaymentCardPayer(
            @NonNull List<ParticipantShare> participants,
            @NonNull HistoryItem item
    ) {
        String payerParticipantKey = item.payerParticipantKey.trim();
        if (!payerParticipantKey.isEmpty()) {
            for (ParticipantShare participant : participants) {
                if (participant.key.equals(payerParticipantKey)) {
                    return participant;
                }
            }
        }

        for (ParticipantShare participant : participants) {
            if (participant.isCrowned) {
                return participant;
            }
        }

        for (ParticipantShare participant : participants) {
            if (participant.key.startsWith("participant_you")) {
                return participant;
            }
        }

        return participants.isEmpty() ? null : participants.get(0);
    }

    private static int countSelectedParticipants(
            @NonNull HistoryItem item,
            @NonNull List<ParticipantShare> participants
    ) {
        int count = 0;
        for (ParticipantShare participant : participants) {
            if (item.isParticipantSelected(participant.key)) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    private static BigDecimal parseCurrencyAmount(@NonNull String amountText) {
        String normalizedAmount = amountText.trim().replace("kr", "").replace(",", ".");
        if (normalizedAmount.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        try {
            return new BigDecimal(normalizedAmount).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
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
        final ArrayList<PaymentCard> paymentCards;

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
                @NonNull List<HistoryEntry> archivedReceipts,
                @NonNull List<PaymentCard> paymentCards
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
                    paymentCards,
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
            this(
                    receiptName,
                    totalAmount,
                    sentDate,
                    message,
                    participants,
                    items,
                    entryType,
                    archivedReceipts,
                    new ArrayList<>(),
                    storageId
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
                @NonNull List<PaymentCard> paymentCards,
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
            this.paymentCards = new ArrayList<>(paymentCards);
        }

        @NonNull
        JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray participantArray = new JSONArray();
            JSONArray itemArray = new JSONArray();
            JSONArray archivedReceiptsArray = new JSONArray();
            JSONArray paymentCardsArray = new JSONArray();
            for (ParticipantShare participant : participants) {
                participantArray.put(participant.toJson());
            }
            for (HistoryItem item : items) {
                itemArray.put(item.toJson());
            }
            for (HistoryEntry archivedReceipt : archivedReceipts) {
                archivedReceiptsArray.put(archivedReceipt.toJson());
            }
            for (PaymentCard paymentCard : paymentCards) {
                paymentCardsArray.put(paymentCard.toJson());
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
                object.put(KEY_PAYMENT_CARDS, paymentCardsArray);
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

            JSONArray paymentCardsArray = object.optJSONArray(KEY_PAYMENT_CARDS);
            ArrayList<PaymentCard> paymentCards = new ArrayList<>();
            if (paymentCardsArray != null) {
                for (int index = 0; index < paymentCardsArray.length(); index++) {
                    JSONObject paymentCardObject = paymentCardsArray.optJSONObject(index);
                    if (paymentCardObject == null) {
                        continue;
                    }
                    paymentCards.add(PaymentCard.fromJson(paymentCardObject, index));
                }
            }
            ArrayList<ParticipantShare> resolvedParticipants =
                    applyPaymentCardPaidStatusesToParticipants(
                            participants,
                            items,
                            paymentCards,
                            object.optString(KEY_ENTRY_TYPE, ENTRY_TYPE_RECEIPT)
                    );
            ArrayList<HistoryItem> resolvedItems =
                    applyPaymentCardPaidStatusesToItems(
                            items,
                            paymentCards,
                            object.optString(KEY_ENTRY_TYPE, ENTRY_TYPE_RECEIPT)
                    );

            return new HistoryEntry(
                    object.optString(KEY_RECEIPT_NAME, ""),
                    object.optString(KEY_TOTAL_AMOUNT, ""),
                    object.optString(KEY_SENT_DATE, ""),
                    object.optString(KEY_MESSAGE, ""),
                    resolvedParticipants,
                    resolvedItems,
                    object.optString(KEY_ENTRY_TYPE, ENTRY_TYPE_RECEIPT),
                    archivedReceipts,
                    paymentCards,
                    ""
            );
        }

        @NonNull
        private static ArrayList<ParticipantShare> applyPaymentCardPaidStatusesToParticipants(
                @NonNull List<ParticipantShare> participants,
                @NonNull List<HistoryItem> items,
                @NonNull List<PaymentCard> paymentCards,
                @NonNull String entryType
        ) {
            if (paymentCards.isEmpty()) {
                return new ArrayList<>(participants);
            }

            LinkedHashMap<String, Boolean> hasCardsByParticipantKey = new LinkedHashMap<>();
            LinkedHashMap<String, Boolean> allCardsPaidByParticipantKey = new LinkedHashMap<>();
            ArrayList<String> debtorKeys = ENTRY_TYPE_ARCHIVE_SUMMARY.equals(entryType)
                    ? ReceiptHistoryStore.buildArchiveSummaryPaymentCardDebtorKeys(items)
                    : ReceiptHistoryStore.buildReceiptPaymentCardDebtorKeys(participants, items);
            for (int index = 0; index < paymentCards.size() && index < debtorKeys.size(); index++) {
                String participantKey = debtorKeys.get(index);
                if (participantKey.isEmpty()) {
                    continue;
                }

                PaymentCard paymentCard = paymentCards.get(index);
                hasCardsByParticipantKey.put(participantKey, true);
                boolean currentAllPaid = Boolean.TRUE.equals(
                        allCardsPaidByParticipantKey.getOrDefault(participantKey, true)
                );
                allCardsPaidByParticipantKey.put(participantKey, currentAllPaid && paymentCard.hasPaid);
            }

            ArrayList<ParticipantShare> resolvedParticipants = new ArrayList<>();
            for (ParticipantShare participant : participants) {
                if (hasCardsByParticipantKey.containsKey(participant.key)) {
                    resolvedParticipants.add(participant.copyWithPaidStatus(
                            Boolean.TRUE.equals(allCardsPaidByParticipantKey.get(participant.key))
                    ));
                } else {
                    resolvedParticipants.add(participant);
                }
            }
            return resolvedParticipants;
        }

        @NonNull
        private static ArrayList<HistoryItem> applyPaymentCardPaidStatusesToItems(
                @NonNull List<HistoryItem> items,
                @NonNull List<PaymentCard> paymentCards,
                @NonNull String entryType
        ) {
            if (!ENTRY_TYPE_ARCHIVE_SUMMARY.equals(entryType) || paymentCards.isEmpty()) {
                return new ArrayList<>(items);
            }

            ArrayList<HistoryItem> resolvedItems = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                HistoryItem item = items.get(index);
                if (index < paymentCards.size()) {
                    resolvedItems.add(item.copyWithPaidStatus(paymentCards.get(index).hasPaid));
                } else {
                    resolvedItems.add(item);
                }
            }
            return resolvedItems;
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
                paymentCards,
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
                    || archivedReceipts.size() != other.archivedReceipts.size()
                    || paymentCards.size() != other.paymentCards.size()) {
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

            for (int index = 0; index < paymentCards.size(); index++) {
                if (!paymentCards.get(index).matches(other.paymentCards.get(index))) {
                    return false;
                }
            }

            return true;
        }
    }

    static final class PaymentCard {
        final String id;
        final String amount;
        final String recipientPhoneNumber;
        final boolean hasPaid;

        PaymentCard(
                @NonNull String id,
                @NonNull String amount,
                @NonNull String recipientPhoneNumber,
                boolean hasPaid
        ) {
            this.id = id;
            this.amount = amount;
            this.recipientPhoneNumber = recipientPhoneNumber;
            this.hasPaid = hasPaid;
        }

        @NonNull
        private JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put(KEY_PAYMENT_CARD_ID, id);
                object.put(KEY_PARTICIPANT_AMOUNT, amount);
                object.put(KEY_PAYMENT_CARD_HAS_PAID, hasPaid);
                object.put(KEY_PAYMENT_CARD_RECIPIENT_PHONE, recipientPhoneNumber);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize payment card", exception);
            }
            return object;
        }

        @NonNull
        private static PaymentCard fromJson(@NonNull JSONObject object, int index) {
            return new PaymentCard(
                    object.optString(KEY_PAYMENT_CARD_ID, buildPaymentCardId(index)),
                    object.optString(KEY_PARTICIPANT_AMOUNT, ""),
                    object.optString(KEY_PAYMENT_CARD_RECIPIENT_PHONE, ""),
                    object.optBoolean(KEY_PAYMENT_CARD_HAS_PAID, false)
            );
        }

        @NonNull
        PaymentCard copyWithPaidStatus(boolean hasPaid) {
            return new PaymentCard(
                    id,
                    amount,
                    recipientPhoneNumber,
                    hasPaid
            );
        }

        private boolean matches(@NonNull PaymentCard other) {
            return id.equals(other.id)
                    && amount.equals(other.amount)
                    && hasPaid == other.hasPaid
                    && recipientPhoneNumber.equals(other.recipientPhoneNumber);
        }
    }

    @NonNull
    static String buildPaymentCardId(int index) {
        return String.format(Locale.US, "%04d", index + 1);
    }

    private static final class TransferBalance {
        @NonNull
        private final ParticipantShare participant;
        @NonNull
        private BigDecimal amount;

        private TransferBalance(
                @NonNull ParticipantShare participant,
                @NonNull BigDecimal amount
        ) {
            this.participant = participant;
            this.amount = amount;
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
