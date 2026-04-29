package com.example.testrepo;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import org.json.JSONException;
import org.json.JSONObject;

public class HistoryActivity extends AppCompatActivity {
    private static final int INITIAL_VISIBLE_HISTORY_COUNT = 5;
    private static final int HISTORY_PAGE_SIZE = 5;
    private static final int MAX_ITEM_PARTICIPANT_BUTTONS_PER_ROW = 4;
    private static final int UNCHECKED_PARTICIPANT_COLOR = 0xFF8A8A8A;
    private static final long HISTORY_ENTRY_LONG_PRESS_DURATION_MS = 750L;
    private static final long HISTORY_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS = 40L;
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";
    private static final String DEFAULT_PARTICIPANT_NAME = "You";
    private static final String SWISH_PAYMENT_URL_PREFIX = "swish://payment?data=";

    private final ArrayList<ReceiptHistoryStore.HistoryEntry> historyEntries = new ArrayList<>();
    private final ArrayList<ReceiptHistoryStore.HistoryEntry> visibleHistoryEntries = new ArrayList<>();
    private HistoryEntriesAdapter historyEntriesAdapter;
    private View loadMoreFooterView;
    private int visibleHistoryCount = INITIAL_VISIBLE_HISTORY_COUNT;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        setContentView(R.layout.activity_history);

        View backButton = findViewById(R.id.button_back);
        View settingsMenuButton = findViewById(R.id.button_history_actions);
        ListView historyListView = findViewById(R.id.list_history_receipts);
        loadMoreFooterView = getLayoutInflater().inflate(
                R.layout.item_history_load_more,
                historyListView,
                false
        );
        loadMoreFooterView.setOnClickListener(view -> loadMoreHistoryEntries());

        historyListView.addFooterView(loadMoreFooterView, null, false);
        historyEntriesAdapter = new HistoryEntriesAdapter();
        historyListView.setAdapter(historyEntriesAdapter);
        historyListView.setEmptyView(findViewById(R.id.text_history_empty));
        backButton.setOnClickListener(view -> finish());
        settingsMenuButton.setOnClickListener(
                view -> SettingsMenuHelper.showSettingsMenu(this, view)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistoryEntries();
    }

    private void loadHistoryEntries() {
        historyEntries.clear();
        historyEntries.addAll(ReceiptHistoryStore.loadEntries(this));
        visibleHistoryCount = INITIAL_VISIBLE_HISTORY_COUNT;
        refreshVisibleHistoryEntries();
    }

    private void loadMoreHistoryEntries() {
        if (visibleHistoryEntries.size() >= historyEntries.size()) {
            return;
        }

        visibleHistoryCount = Math.min(
                visibleHistoryCount + HISTORY_PAGE_SIZE,
                historyEntries.size()
        );
        refreshVisibleHistoryEntries();
    }

    private void refreshVisibleHistoryEntries() {
        visibleHistoryEntries.clear();
        int visibleCount = Math.min(visibleHistoryCount, historyEntries.size());
        visibleHistoryEntries.addAll(historyEntries.subList(0, visibleCount));
        historyEntriesAdapter.notifyDataSetChanged();
        updateLoadMoreVisibility();
    }

    private void updateLoadMoreVisibility() {
        if (loadMoreFooterView == null) {
            return;
        }

        boolean hasMoreEntries = visibleHistoryEntries.size() < historyEntries.size();
        loadMoreFooterView.setVisibility(hasMoreEntries ? View.VISIBLE : View.GONE);
    }

    private void showHistoryDetailsDialog(@NonNull ReceiptHistoryStore.HistoryEntry entry) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_history_receipt_details, null);
        TextView titleView = dialogView.findViewById(R.id.text_history_receipt_dialog_title);
        TextView messageView = dialogView.findViewById(R.id.text_history_receipt_dialog_message);
        AppCompatImageButton closeButton =
                dialogView.findViewById(R.id.button_close_history_receipt);
        LinearLayout participantsLayout =
                dialogView.findViewById(R.id.layout_history_receipt_participants);
        MaterialCardView itemsCard = dialogView.findViewById(R.id.card_history_receipt_items);
        LinearLayout itemsLayout = dialogView.findViewById(R.id.layout_history_receipt_items);

        titleView.setText(entry.receiptName);

        String message = entry.message == null ? "" : entry.message.trim();
        if (message.isEmpty()) {
            messageView.setVisibility(View.GONE);
        } else {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(message);
        }

        bindHistoryParticipantButtons(participantsLayout, entry);

        if (entry.items.isEmpty()) {
            itemsCard.setVisibility(View.GONE);
        } else {
            bindHistoryReceiptItems(itemsLayout, entry);
            itemsCard.setVisibility(View.VISIBLE);
        }

        Dialog dialog = new Dialog(this, AppSettings.getAppThemeResId(this));
        dialog.setContentView(dialogView);
        closeButton.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showDeleteHistoryDialog(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @Nullable Dialog historyDetailsDialog
    ) {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_history_remove_confirmation,
                null
        );
        MaterialButton noButton = dialogView.findViewById(R.id.button_history_remove_no);
        MaterialButton yesButton = dialogView.findViewById(R.id.button_history_remove_yes);

        AlertDialog confirmationDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        noButton.setOnClickListener(view -> confirmationDialog.dismiss());
        yesButton.setOnClickListener(view -> {
            confirmationDialog.dismiss();
            if (historyDetailsDialog != null) {
                historyDetailsDialog.dismiss();
            }
            removeHistoryEntry(entry);
        });

        confirmationDialog.show();
    }

    private void showHistoryEntryActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        AnchoredDropdownMenuHelper.showSingleActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                R.string.remove,
                R.drawable.ic_history_remove,
                () -> showDeleteHistoryDialog(entry, null)
        );
    }

    private void removeHistoryEntry(@NonNull ReceiptHistoryStore.HistoryEntry entry) {
        int previousVisibleCount = Math.max(
                visibleHistoryEntries.size(),
                INITIAL_VISIBLE_HISTORY_COUNT
        );
        if (!ReceiptHistoryStore.removeEntry(this, entry)) {
            Toast.makeText(this, R.string.remove_history_entry_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        historyEntries.clear();
        historyEntries.addAll(ReceiptHistoryStore.loadEntries(this));
        visibleHistoryCount = previousVisibleCount;
        refreshVisibleHistoryEntries();
    }

    private void bindHistoryParticipantButtons(
            @NonNull LinearLayout participantsLayout,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        participantsLayout.removeAllViews();
        List<ReceiptHistoryStore.ParticipantShare> participants = entry.participants;

        if (participants.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.history_no_participants);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setTextColor(resolveThemeColor(android.R.attr.textColorSecondary, 0xFF9E9E9E));
            participantsLayout.addView(emptyView);
            return;
        }

        for (int index = 0; index < participants.size(); index++) {
            ReceiptHistoryStore.ParticipantShare participant = participants.get(index);
            View rowView = LayoutInflater.from(this).inflate(
                    R.layout.item_receipt_summary_participant,
                    participantsLayout,
                    false
            );
            MaterialButton badgeButton = rowView.findViewById(R.id.button_summary_participant_badge);
            AppCompatImageView paymentStatusIconView =
                    rowView.findViewById(R.id.image_summary_participant_payment_status);
            AppCompatImageView ownerIconView =
                    rowView.findViewById(R.id.image_summary_participant_owner);
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);
            MaterialButton payNowButton =
                    rowView.findViewById(R.id.button_summary_participant_pay_now);
            View dividerView = rowView.findViewById(R.id.view_summary_participant_divider);
            boolean isOwner = isCrownedParticipant(participant);

            configureHistoryParticipantBadgeButton(badgeButton, participant, true);
            bindHistoryParticipantPaymentStatusIcon(paymentStatusIconView, participant);
            ownerIconView.setVisibility(
                    isOwner ? View.VISIBLE : View.GONE
            );
            nameView.setText(participant.name);
            amountView.setText(
                    buildHistoryParticipantTotalDisplayText(participant.amount, entry.totalAmount)
            );
            payNowButton.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            payNowButton.setEnabled(!isDefaultParticipant(participant));
            payNowButton.setOnClickListener(
                    view -> {
                        openSwishForHistoryEntry(entry, participant);
                        bindHistoryParticipantButtons(participantsLayout, entry);
                    }
            );
            dividerView.setVisibility(index == participants.size() - 1 ? View.GONE : View.VISIBLE);

            View.OnClickListener openDetailsListener =
                    view -> showHistoryParticipantDetailsDialog(participant, entry.totalAmount);
            rowView.setOnClickListener(openDetailsListener);
            badgeButton.setOnClickListener(openDetailsListener);
            participantsLayout.addView(rowView);
        }
    }

    private void configureHistoryParticipantBadgeButton(
            @NonNull MaterialButton participantButton,
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            boolean clickable
    ) {
        int buttonSize = dpToPx(52);
        ViewGroup.LayoutParams layoutParams = participantButton.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.width = buttonSize;
            layoutParams.height = buttonSize;
            participantButton.setLayoutParams(layoutParams);
        }
        participantButton.setClickable(clickable);
        participantButton.setFocusable(clickable);
        participantButton.setEnabled(true);
        participantButton.setInsetTop(0);
        participantButton.setInsetBottom(0);
        participantButton.setMinWidth(0);
        participantButton.setMinHeight(0);
        participantButton.setMinimumWidth(0);
        participantButton.setMinimumHeight(0);
        participantButton.setPadding(0, 0, 0, 0);
        participantButton.setCornerRadius(buttonSize / 2);
        applyParticipantBadgeTextStyle(participantButton, participant, false);
        participantButton.setStrokeWidth(0);
        participantButton.setBackgroundTintList(ColorStateList.valueOf(participant.color));
        participantButton.setTextColor(getParticipantTextColor(participant.color));
        participantButton.setContentDescription(participant.name);
    }

    private void bindHistoryReceiptItems(
            @NonNull LinearLayout itemsLayout,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        itemsLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        List<ReceiptHistoryStore.HistoryItem> items = entry.items;
        List<ReceiptHistoryStore.ParticipantShare> participants = entry.participants;

        for (int index = 0; index < items.size(); index++) {
            ReceiptHistoryStore.HistoryItem item = items.get(index);
            View itemView = inflater.inflate(R.layout.item_receipt_line, itemsLayout, false);
            itemView.setBackgroundColor(Color.TRANSPARENT);
            itemView.setClickable(false);
            itemView.setFocusable(false);

            TextView itemNameView = itemView.findViewById(R.id.text_receipt_item_name);
            TextView itemPriceView = itemView.findViewById(R.id.text_receipt_item_price);
            LinearLayout participantSelectionLayout =
                    itemView.findViewById(R.id.layout_receipt_item_participants);

            itemNameView.setText(item.name);
            itemPriceView.setText(item.price);
            bindHistoryItemParticipantButtons(
                    participantSelectionLayout,
                    item,
                    participants,
                    entry.totalAmount
            );
            itemsLayout.addView(itemView);
            if (index < items.size() - 1) {
                itemsLayout.addView(createHistoryItemDivider());
            }
        }
    }

    @NonNull
    private View createHistoryItemDivider() {
        View dividerView = new View(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );
        int horizontalMargin = dpToPx(16);
        layoutParams.setMargins(horizontalMargin, 0, horizontalMargin, 0);
        dividerView.setLayoutParams(layoutParams);
        dividerView.setBackgroundColor(
                resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant, 0x33FFFFFF)
        );
        return dividerView;
    }

    private void bindHistoryItemParticipantButtons(
            @NonNull LinearLayout participantSelectionLayout,
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
            @NonNull String receiptTotalAmount
    ) {
        participantSelectionLayout.removeAllViews();
        if (participants.isEmpty()) {
            participantSelectionLayout.setVisibility(View.GONE);
            return;
        }

        participantSelectionLayout.setVisibility(View.VISIBLE);
        participantSelectionLayout.setOrientation(LinearLayout.VERTICAL);
        participantSelectionLayout.setGravity(Gravity.END);
        int checkboxSize = dpToPx(36);
        int checkboxSpacing = dpToPx(4);
        int rowSpacing = dpToPx(4);

        LinearLayout currentRow = null;
        for (int index = 0; index < participants.size(); index++) {
            ReceiptHistoryStore.ParticipantShare participant = participants.get(index);
            int indexInRow = index % MAX_ITEM_PARTICIPANT_BUTTONS_PER_ROW;
            if (indexInRow == 0) {
                currentRow = new LinearLayout(this);
                LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (index > 0) {
                    rowLayoutParams.topMargin = rowSpacing;
                }
                currentRow.setLayoutParams(rowLayoutParams);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setGravity(Gravity.END);
                participantSelectionLayout.addView(currentRow);
            }

            MaterialButton selectionButton = new MaterialButton(this);
            LinearLayout.LayoutParams layoutParams =
                    new LinearLayout.LayoutParams(checkboxSize, checkboxSize);
            layoutParams.setMargins(indexInRow == 0 ? 0 : checkboxSpacing, 0, 0, 0);
            selectionButton.setLayoutParams(layoutParams);
            selectionButton.setInsetTop(0);
            selectionButton.setInsetBottom(0);
            selectionButton.setMinWidth(0);
            selectionButton.setMinHeight(0);
            selectionButton.setMinimumWidth(0);
            selectionButton.setMinimumHeight(0);
            selectionButton.setPadding(0, 0, 0, 0);
            selectionButton.setCornerRadius(dpToPx(10));
            selectionButton.setStrokeWidth(dpToPx(2));
            applyParticipantBadgeTextStyle(selectionButton, participant, true);
            selectionButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            selectionButton.setFocusable(true);
            selectionButton.setClickable(true);
            selectionButton.setCheckable(false);
            selectionButton.setContentDescription(participant.name);

            int buttonColor = item.isParticipantSelected(participant.key)
                    ? participant.color
                    : UNCHECKED_PARTICIPANT_COLOR;
            selectionButton.setStrokeColor(ColorStateList.valueOf(buttonColor));
            selectionButton.setTextColor(buttonColor);
            selectionButton.setOnClickListener(
                    view -> showHistoryParticipantDetailsDialog(participant, receiptTotalAmount)
            );

            if (currentRow != null) {
                currentRow.addView(selectionButton);
            }
        }
    }

    private void showHistoryParticipantDetailsDialog(
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull String receiptTotalAmount
    ) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_participant_details, null);
        TextView participantNameView = dialogView.findViewById(R.id.text_participant_detail_name);
        TextView participantPhoneView = dialogView.findViewById(R.id.text_participant_detail_phone);
        TextView participantTotalView = dialogView.findViewById(R.id.text_participant_detail_total);
        AppCompatImageButton crownToggleButton =
                dialogView.findViewById(R.id.button_participant_crown);
        MaterialButton removeParticipantButton =
                dialogView.findViewById(R.id.button_remove_participant);
        MaterialButton toggleParticipantItemsButton =
                dialogView.findViewById(R.id.button_toggle_participant_items);

        participantNameView.setText(participant.name);
        participantPhoneView.setText(
                normalizeWhitespace(participant.phoneNumber).isEmpty()
                        ? getString(R.string.participant_phone_unavailable)
                        : participant.phoneNumber
        );
        participantTotalView.setText(
                buildHistoryParticipantTotalDisplayText(participant.amount, receiptTotalAmount)
        );
        crownToggleButton.setVisibility(View.VISIBLE);
        crownToggleButton.setImageResource(
                isCrownedParticipant(participant) ? R.drawable.crown_true : R.drawable.crown_false
        );
        crownToggleButton.setClickable(false);
        crownToggleButton.setFocusable(false);
        crownToggleButton.setContentDescription(
                getString(
                        isCrownedParticipant(participant)
                                ? R.string.participant_crown_selected
                                : R.string.participant_crown_unselected
                )
        );
        removeParticipantButton.setVisibility(View.GONE);
        toggleParticipantItemsButton.setVisibility(View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        dialog.show();
    }

    @NonNull
    private CharSequence buildHistoryParticipantTotalDisplayText(
            @Nullable String participantAmount,
            @Nullable String receiptTotalAmount
    ) {
        String amountText = normalizeWhitespace(participantAmount);
        if (amountText.isEmpty()) {
            amountText = "0,00";
        }

        BigDecimal participantTotal = parseCurrencyAmount(amountText);
        BigDecimal receiptTotal = parseCurrencyAmount(receiptTotalAmount);
        String percentageText =
                " (" + formatParticipantSharePercentage(participantTotal, receiptTotal) + "%)";
        SpannableString displayText = new SpannableString(amountText + percentageText);
        int percentageStart = amountText.length();
        displayText.setSpan(
                new RelativeSizeSpan(0.72f),
                percentageStart,
                displayText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        displayText.setSpan(
                new ForegroundColorSpan(
                        resolveThemeColor(android.R.attr.textColorSecondary, 0xFF808080)
                ),
                percentageStart,
                displayText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return displayText;
    }

    @NonNull
    private String formatParticipantSharePercentage(
            @NonNull BigDecimal participantTotal,
            @NonNull BigDecimal receiptTotal
    ) {
        if (receiptTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return "0";
        }

        return participantTotal
                .multiply(BigDecimal.valueOf(100))
                .divide(receiptTotal, 0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    @NonNull
    private BigDecimal parseCurrencyAmount(@Nullable String amountText) {
        String normalizedAmount = normalizeWhitespace(amountText)
                .replace(" ", "")
                .replace(',', '.');
        if (normalizedAmount.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(normalizedAmount);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isDefaultParticipant(@NonNull ReceiptHistoryStore.ParticipantShare participant) {
        return DEFAULT_PARTICIPANT_KEY.equals(participant.key)
                || DEFAULT_PARTICIPANT_NAME.equalsIgnoreCase(normalizeWhitespace(participant.name));
    }

    private boolean isCrownedParticipant(
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        return participant.isCrowned;
    }

    private void bindHistoryParticipantPaymentStatusIcon(
            @NonNull AppCompatImageView paymentStatusIconView,
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        if (isCrownedParticipant(participant)) {
            paymentStatusIconView.setVisibility(View.GONE);
            return;
        }

        paymentStatusIconView.setVisibility(View.VISIBLE);
        if (participant.hasPaid) {
            paymentStatusIconView.setImageResource(R.drawable.ic_history_participant_paid);
            paymentStatusIconView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_green)
            ));
        } else {
            paymentStatusIconView.setImageResource(R.drawable.ic_history_participant_unpaid);
            paymentStatusIconView.setImageTintList(ColorStateList.valueOf(
                    resolveThemeColor(com.google.android.material.R.attr.colorError, Color.RED)
            ));
        }
    }

    @NonNull
    private BigDecimal getDefaultParticipantShareAmount(
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants
    ) {
        for (ReceiptHistoryStore.ParticipantShare participant : participants) {
            if (isDefaultParticipant(participant)) {
                return parseCurrencyAmount(participant.amount);
            }
        }
        return BigDecimal.ZERO;
    }

    private void openSwishForHistoryEntry(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ReceiptHistoryStore.ParticipantShare ownerParticipant
    ) {
        String normalizedPhoneNumber = normalizePhoneNumberForSwish(ownerParticipant.phoneNumber);
        if (normalizedPhoneNumber.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.pay_now_owner_phone_unavailable,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        BigDecimal yourShareAmount = getDefaultParticipantShareAmount(entry.participants);
        if (yourShareAmount.compareTo(BigDecimal.ZERO) <= 0) {
            Toast.makeText(this, R.string.pay_now_nothing_to_pay, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent swishIntent = new Intent(
                    Intent.ACTION_VIEW,
                    buildSwishPaymentUri(
                            normalizedPhoneNumber,
                            yourShareAmount,
                            entry.receiptName
                    )
            );
            startActivity(swishIntent);
            markDefaultParticipantAsPaid(entry);
        } catch (ActivityNotFoundException | JSONException exception) {
            Toast.makeText(this, R.string.pay_now_open_swish_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void markDefaultParticipantAsPaid(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        ReceiptHistoryStore.HistoryEntry updatedEntry = ReceiptHistoryStore.markParticipantPaid(
                this,
                entry,
                DEFAULT_PARTICIPANT_KEY,
                true
        );

        entry.participants.clear();
        entry.participants.addAll(updatedEntry.participants);
    }

    @NonNull
    private Uri buildSwishPaymentUri(
            @NonNull String phoneNumber,
            @NonNull BigDecimal amount,
            @NonNull String receiptName
    ) throws JSONException {
        JSONObject payeeObject = new JSONObject();
        payeeObject.put("value", phoneNumber);

        JSONObject amountObject = new JSONObject();
        amountObject.put(
                "value",
                amount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        );

        JSONObject paymentObject = new JSONObject();
        paymentObject.put("version", 1);
        paymentObject.put("payee", payeeObject);
        paymentObject.put("amount", amountObject);

        String normalizedMessage = normalizeWhitespace(receiptName);
        if (!normalizedMessage.isEmpty()) {
            JSONObject messageObject = new JSONObject();
            messageObject.put("value", normalizedMessage);
            paymentObject.put("message", messageObject);
        }

        return Uri.parse(SWISH_PAYMENT_URL_PREFIX + Uri.encode(paymentObject.toString()));
    }

    @NonNull
    private String normalizePhoneNumberForSwish(@Nullable String phoneNumber) {
        String trimmedPhoneNumber = normalizeWhitespace(phoneNumber);
        boolean hasInternationalPrefix = trimmedPhoneNumber.startsWith("+");
        String digitsOnlyPhoneNumber = trimmedPhoneNumber.replaceAll("\\D", "");

        if (digitsOnlyPhoneNumber.isEmpty()) {
            return "";
        }

        if (hasInternationalPrefix) {
            return "+" + digitsOnlyPhoneNumber;
        }
        if (digitsOnlyPhoneNumber.startsWith("00")) {
            return "+" + digitsOnlyPhoneNumber.substring(2);
        }
        if (digitsOnlyPhoneNumber.startsWith("46")) {
            return "+" + digitsOnlyPhoneNumber;
        }
        if (digitsOnlyPhoneNumber.startsWith("0")) {
            return "+46" + digitsOnlyPhoneNumber.substring(1);
        }
        return digitsOnlyPhoneNumber;
    }

    @NonNull
    private String getParticipantBadgeLabel(@NonNull ReceiptHistoryStore.ParticipantShare participant) {
        if (isDefaultParticipant(participant)) {
            return getDefaultParticipantBadgeLabel();
        }

        String initials = normalizeWhitespace(participant.initials);
        if (!initials.isEmpty()) {
            return initials;
        }
        return deriveInitials(participant.name);
    }

    @NonNull
    private String getDefaultParticipantBadgeLabel() {
        return DEFAULT_PARTICIPANT_NAME;
    }

    private void applyParticipantBadgeTextStyle(
            @NonNull MaterialButton badgeButton,
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            boolean compact
    ) {
        badgeButton.setText(getParticipantBadgeLabel(participant));
        badgeButton.setAllCaps(false);
        badgeButton.setGravity(Gravity.CENTER);
        badgeButton.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        badgeButton.setIncludeFontPadding(false);
        badgeButton.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                badgeButton,
                compact ? 6 : 8,
                (int) getParticipantBadgeTextSizeSp(participant, compact),
                1,
                TypedValue.COMPLEX_UNIT_SP
        );
    }

    private float getParticipantBadgeTextSizeSp(
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            boolean compact
    ) {
        String badgeLabel = getParticipantBadgeLabel(participant);
        if (badgeLabel.length() > 2) {
            return compact ? 9f : 11f;
        }
        return compact ? 11f : 13f;
    }

    private int getParticipantTextColor(int backgroundColor) {
        double brightness = (
                (Color.red(backgroundColor) * 0.299)
                        + (Color.green(backgroundColor) * 0.587)
                        + (Color.blue(backgroundColor) * 0.114)
        ) / 255d;
        return brightness > 0.65d ? Color.BLACK : Color.WHITE;
    }

    @NonNull
    private String deriveInitials(@NonNull String name) {
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

    @NonNull
    private String normalizeWhitespace(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private int resolveThemeColor(int attrResId, int fallbackColor) {
        TypedValue typedValue = new TypedValue();
        if (!getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return fallbackColor;
        }

        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(this, typedValue.resourceId);
        }

        return typedValue.data;
    }

    private int dpToPx(int valueDp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                valueDp,
                getResources().getDisplayMetrics()
        ));
    }

    private void vibrateForHistoryLongPress() {
        Vibrator vibrator = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = getSystemService(VibratorManager.class);
            if (vibratorManager != null) {
                vibrator = vibratorManager.getDefaultVibrator();
            }
        } else {
            vibrator = getSystemService(Vibrator.class);
        }

        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                    VibrationEffect.createOneShot(
                            HISTORY_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS,
                            VibrationEffect.DEFAULT_AMPLITUDE
                    )
            );
        } else {
            vibrator.vibrate(HISTORY_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS);
        }
    }

    private final class HistoryEntriesAdapter extends ArrayAdapter<ReceiptHistoryStore.HistoryEntry> {
        private HistoryEntriesAdapter() {
            super(HistoryActivity.this, R.layout.item_history_receipt, visibleHistoryEntries);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_history_receipt, parent, false);
            }

            ReceiptHistoryStore.HistoryEntry entry = getItem(position);
            TextView receiptNameView = itemView.findViewById(R.id.text_history_receipt_name);
            TextView totalAmountView = itemView.findViewById(R.id.text_history_receipt_total);
            TextView sentDateView = itemView.findViewById(R.id.text_history_receipt_date);

            if (entry != null) {
                receiptNameView.setText(entry.receiptName);
                totalAmountView.setText(entry.totalAmount);
                sentDateView.setText(entry.sentDate);
            }

            if (entry != null) {
                View historyItemView = itemView;
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnClickListener(view -> showHistoryDetailsDialog(entry));
                itemView.setOnTouchListener(new View.OnTouchListener() {
                    private final int touchSlop = ViewConfiguration
                            .get(HistoryActivity.this)
                            .getScaledTouchSlop();
                    private float downX;
                    private float downY;
                    private float downRawX;
                    private float downRawY;
                    private boolean longPressTriggered;
                    private final Runnable longPressRunnable = () -> {
                        longPressTriggered = true;
                        vibrateForHistoryLongPress();
                        showHistoryEntryActionsMenu(
                                historyItemView,
                                downRawX,
                                downRawY,
                                entry
                        );
                    };

                    @Override
                    public boolean onTouch(View view, MotionEvent event) {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                downX = event.getX();
                                downY = event.getY();
                                downRawX = event.getRawX();
                                downRawY = event.getRawY();
                                longPressTriggered = false;
                                view.postDelayed(
                                        longPressRunnable,
                                        HISTORY_ENTRY_LONG_PRESS_DURATION_MS
                                );
                                return false;
                            case MotionEvent.ACTION_MOVE:
                                if (Math.abs(event.getX() - downX) > touchSlop
                                        || Math.abs(event.getY() - downY) > touchSlop) {
                                    view.removeCallbacks(longPressRunnable);
                                }
                                return false;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                view.removeCallbacks(longPressRunnable);
                                return longPressTriggered;
                            default:
                                return false;
                        }
                    }
                });
            } else {
                itemView.setOnClickListener(null);
                itemView.setOnTouchListener(null);
            }

            return itemView;
        }
    }
}
