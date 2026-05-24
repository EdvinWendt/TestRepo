package com.example.testrepo;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;

import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import org.json.JSONException;
import org.json.JSONObject;

public class HistoryActivity extends AppCompatActivity {
    private static final int RECEIPT_PAYMENT_STATUS_NONE = 0;
    private static final int RECEIPT_PAYMENT_STATUS_PARTIAL = 1;
    private static final int RECEIPT_PAYMENT_STATUS_ALL = 2;
    private static final int INITIAL_VISIBLE_HISTORY_COUNT = 5;
    private static final int HISTORY_PAGE_SIZE = 5;
    private static final int MAX_ITEM_PARTICIPANT_BUTTONS_PER_ROW = 4;
    private static final int UNCHECKED_PARTICIPANT_COLOR = 0xFF8A8A8A;
    private static final long HISTORY_ENTRY_LONG_PRESS_DURATION_MS = 750L;
    private static final long HISTORY_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS = 40L;
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";
    private static final String DEFAULT_PARTICIPANT_NAME = "You";
    private static final String SWISH_PAYMENT_URL_PREFIX = "swish://payment?data=";
    private static final int HISTORY_LIST_ITEM_TYPE_SECTION = 0;
    private static final int HISTORY_LIST_ITEM_TYPE_ENTRY = 1;
    @NonNull
    private String appliedThemeConfigurationKey = "";

    private final ArrayList<ReceiptHistoryStore.HistoryEntry> historyEntries = new ArrayList<>();
    private final ArrayList<ReceiptHistoryStore.HistoryEntry> visibleHistoryEntries = new ArrayList<>();
    private final ArrayList<HistoryListItem> visibleHistoryListItems = new ArrayList<>();
    private HistoryEntriesAdapter historyEntriesAdapter;
    private View loadMoreFooterView;
    @Nullable
    private SwipeRefreshLayout historySwipeRefreshLayout;
    @Nullable
    private ListView historyListView;
    @Nullable
    private View historyEmptyStateView;
    @Nullable
    private AppCompatImageView historyEmptyIconView;
    @Nullable
    private TextView historyEmptyTextView;
    @Nullable
    private CircularProgressIndicator historyLoadingIndicatorView;
    private boolean showingOfflineEmptyState;
    private boolean loadingHistoryEntries;
    private int visibleHistoryCount = INITIAL_VISIBLE_HISTORY_COUNT;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        appliedThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        if (AuthGateHelper.redirectToLoginIfNeeded(this)) {
            return;
        }
        setContentView(R.layout.activity_history);

        View backButton = findViewById(R.id.button_back);
        View settingsMenuButton = findViewById(R.id.button_history_actions);
        historySwipeRefreshLayout = findViewById(R.id.swipe_refresh_history);
        historyListView = findViewById(R.id.list_history_receipts);
        loadMoreFooterView = getLayoutInflater().inflate(
                R.layout.item_history_load_more,
                historyListView,
                false
        );
        loadMoreFooterView.setOnClickListener(view -> loadMoreHistoryEntries());

        historyListView.addFooterView(loadMoreFooterView, null, false);
        historyEntriesAdapter = new HistoryEntriesAdapter();
        historyListView.setAdapter(historyEntriesAdapter);
        historyEmptyStateView = findViewById(R.id.layout_history_empty_state);
        historyEmptyIconView = findViewById(R.id.image_history_empty);
        historyEmptyTextView = findViewById(R.id.text_history_empty);
        historyLoadingIndicatorView = findViewById(R.id.progress_history_loading);
        historyListView.setEmptyView(historyEmptyStateView);
        if (historySwipeRefreshLayout != null) {
            historySwipeRefreshLayout.setOnRefreshListener(this::refreshHistoryEntries);
            historySwipeRefreshLayout.setOnChildScrollUpCallback((parent, child) ->
                    historyListView != null && historyListView.canScrollVertically(-1)
            );
        }
        showDefaultEmptyState();
        backButton.setOnClickListener(view -> finish());
        settingsMenuButton.setOnClickListener(
                view -> SettingsMenuHelper.showSettingsMenu(this, view)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AuthGateHelper.redirectToLoginIfNeeded(this)) {
            return;
        }
        if (recreateIfThemeConfigurationChanged()) {
            return;
        }
        loadHistoryEntries();
    }

    private boolean recreateIfThemeConfigurationChanged() {
        String currentThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        if (currentThemeConfigurationKey.equals(appliedThemeConfigurationKey)) {
            return false;
        }

        recreate();
        return true;
    }

    private void loadHistoryEntries() {
        loadHistoryEntries(INITIAL_VISIBLE_HISTORY_COUNT, false);
    }

    private void loadHistoryEntries(int requestedVisibleCount) {
        loadHistoryEntries(requestedVisibleCount, false);
    }

    private void refreshHistoryEntries() {
        if (loadingHistoryEntries) {
            if (historySwipeRefreshLayout != null) {
                historySwipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        loadHistoryEntries(visibleHistoryCount, true);
    }

    private void loadHistoryEntries(int requestedVisibleCount, boolean isSwipeRefresh) {
        int safeRequestedVisibleCount = Math.max(INITIAL_VISIBLE_HISTORY_COUNT, requestedVisibleCount);
        showHistoryLoadingState(isSwipeRefresh);
        if (!hasInternetConnection()) {
            hideHistoryLoadingState();
            showOfflineEmptyState();
            historyEntries.clear();
            visibleHistoryCount = INITIAL_VISIBLE_HISTORY_COUNT;
            refreshVisibleHistoryEntries();
            return;
        }

        showDefaultEmptyState();
        SupabaseHistoryService.loadEntries(this, new SupabaseHistoryService.LoadEntriesCallback() {
            @Override
            public void onSuccess(@NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> entries) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                hideHistoryLoadingState();
                showDefaultEmptyState();
                historyEntries.clear();
                historyEntries.addAll(entries);
                visibleHistoryCount = safeRequestedVisibleCount;
                refreshVisibleHistoryEntries();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                hideHistoryLoadingState();
                if (isOfflineHistoryError(message)) {
                    showOfflineEmptyState();
                } else {
                    showDefaultEmptyState();
                    Toast.makeText(HistoryActivity.this, message, Toast.LENGTH_SHORT).show();
                }
                historyEntries.clear();
                visibleHistoryCount = INITIAL_VISIBLE_HISTORY_COUNT;
                refreshVisibleHistoryEntries();
            }
        });
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
        rebuildVisibleHistoryListItems();
        historyEntriesAdapter.notifyDataSetChanged();
        updateLoadMoreVisibility();
    }

    private void rebuildVisibleHistoryListItems() {
        visibleHistoryListItems.clear();
        String previousSectionDate = null;
        for (ReceiptHistoryStore.HistoryEntry entry : visibleHistoryEntries) {
            String sectionDate = getHistorySectionDate(entry);
            if (!sectionDate.equals(previousSectionDate)) {
                visibleHistoryListItems.add(HistoryListItem.createSection(sectionDate));
                previousSectionDate = sectionDate;
            }
            visibleHistoryListItems.add(HistoryListItem.createEntry(entry));
        }
    }

    @NonNull
    private String getHistorySectionDate(@NonNull ReceiptHistoryStore.HistoryEntry entry) {
        String sentDate = entry.sentDate == null ? "" : entry.sentDate.trim();
        return sentDate.isEmpty() ? getString(R.string.history_unknown_date) : sentDate;
    }

    private void updateLoadMoreVisibility() {
        if (loadMoreFooterView == null) {
            return;
        }

        if (loadingHistoryEntries) {
            loadMoreFooterView.setVisibility(View.GONE);
            return;
        }

        boolean hasMoreEntries = visibleHistoryEntries.size() < historyEntries.size();
        loadMoreFooterView.setVisibility(hasMoreEntries ? View.VISIBLE : View.GONE);
    }

    private void showDefaultEmptyState() {
        showingOfflineEmptyState = false;
        updateEmptyStateViews();
    }

    private void showOfflineEmptyState() {
        showingOfflineEmptyState = true;
        updateEmptyStateViews();
    }

    private void updateEmptyStateViews() {
        if (historyEmptyStateView == null
                || historyEmptyIconView == null
                || historyEmptyTextView == null) {
            return;
        }

        if (loadingHistoryEntries) {
            historyEmptyIconView.setVisibility(View.GONE);
            historyEmptyTextView.setVisibility(View.GONE);
            return;
        }

        historyEmptyIconView.setVisibility(showingOfflineEmptyState ? View.VISIBLE : View.GONE);
        historyEmptyTextView.setVisibility(View.VISIBLE);
        historyEmptyTextView.setText(
                showingOfflineEmptyState
                        ? R.string.history_no_internet
                        : R.string.history_empty
        );
    }

    private boolean hasInternetConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        return networkCapabilities != null
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private boolean isOfflineHistoryError(@NonNull String message) {
        String normalizedMessage = message.trim();
        return normalizedMessage.equals(getString(R.string.auth_network_error))
                || normalizedMessage.equals(getString(R.string.history_no_internet));
    }

    private void showHistoryLoadingState(boolean isSwipeRefresh) {
        loadingHistoryEntries = true;
        if (historySwipeRefreshLayout != null) {
            historySwipeRefreshLayout.setRefreshing(isSwipeRefresh);
        }
        if (historyLoadingIndicatorView != null) {
            historyLoadingIndicatorView.setVisibility(isSwipeRefresh ? View.GONE : View.VISIBLE);
        }
        updateEmptyStateViews();
        updateLoadMoreVisibility();
    }

    private void hideHistoryLoadingState() {
        loadingHistoryEntries = false;
        if (historySwipeRefreshLayout != null) {
            historySwipeRefreshLayout.setRefreshing(false);
        }
        if (historyLoadingIndicatorView != null) {
            historyLoadingIndicatorView.setVisibility(View.GONE);
        }
        updateEmptyStateViews();
        updateLoadMoreVisibility();
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
        LinearLayout archiveSummaryTransfersLayout =
                dialogView.findViewById(R.id.layout_history_archive_summary_transfers);
        LinearLayout archiveReceiptSectionsLayout =
                dialogView.findViewById(R.id.layout_history_archive_receipt_sections);
        View toggleReceiptButtonContainer =
                dialogView.findViewById(R.id.layout_toggle_history_receipt_button);
        MaterialButton toggleReceiptButton =
                dialogView.findViewById(R.id.button_toggle_history_receipt);
        AppCompatImageView toggleReceiptIconView =
                dialogView.findViewById(R.id.image_toggle_history_receipt_icon);
        MaterialCardView itemsCard = dialogView.findViewById(R.id.card_history_receipt_items);
        View participantsDividerView =
                dialogView.findViewById(R.id.view_history_receipt_participants_divider);
        LinearLayout itemsLayout = dialogView.findViewById(R.id.layout_history_receipt_items);

        titleView.setText(entry.receiptName);

        String message = entry.message == null ? "" : entry.message.trim();
        if (entry.isArchiveSummary()) {
            ArrayList<ArchiveSummaryHistoryTransfer> transfers =
                    buildArchiveSummaryHistoryTransfers(entry);
            if (transfers.isEmpty()) {
                if (message.isEmpty()) {
                    messageView.setVisibility(View.GONE);
                } else {
                    messageView.setVisibility(View.VISIBLE);
                    messageView.setText(message);
                }
                archiveSummaryTransfersLayout.setVisibility(View.GONE);
            } else {
                messageView.setVisibility(View.GONE);
                bindArchiveSummaryHistoryTransfers(entry, archiveSummaryTransfersLayout, transfers);
                archiveSummaryTransfersLayout.setVisibility(View.VISIBLE);
            }
            bindArchiveSummaryReceiptSections(archiveReceiptSectionsLayout, entry.archivedReceipts);
            participantsLayout.setVisibility(View.GONE);
            participantsDividerView.setVisibility(View.GONE);
            toggleReceiptButtonContainer.setVisibility(View.GONE);
            itemsCard.setVisibility(View.GONE);
        } else {
            ArrayList<ReceiptHistoryTransfer> transfers =
                    buildReceiptHistoryTransfers(entry);
            if (transfers.isEmpty()) {
                archiveSummaryTransfersLayout.setVisibility(View.GONE);
            } else {
                bindReceiptHistoryTransfers(archiveSummaryTransfersLayout, entry, transfers);
                archiveSummaryTransfersLayout.setVisibility(View.VISIBLE);
            }
            archiveReceiptSectionsLayout.setVisibility(View.GONE);
            archiveReceiptSectionsLayout.removeAllViews();
            if (message.isEmpty()) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setVisibility(View.VISIBLE);
                messageView.setText(message);
            }
            participantsLayout.setVisibility(View.VISIBLE);
            bindHistoryParticipantButtons(participantsLayout, entry);
            boolean hasParticipants = !entry.participants.isEmpty();
            boolean hasItems = !entry.items.isEmpty();
            boolean hasReceiptContent = hasParticipants || hasItems;
            participantsDividerView.setVisibility(
                    hasParticipants && hasItems ? View.VISIBLE : View.GONE
            );
            itemsLayout.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            if (hasItems) {
                bindHistoryReceiptItems(itemsLayout, entry);
            }
            if (hasReceiptContent) {
                toggleReceiptButtonContainer.setVisibility(View.VISIBLE);
                itemsCard.setVisibility(View.GONE);
                updateHistoryReceiptToggleButton(toggleReceiptButton, toggleReceiptIconView, false);
                toggleReceiptButton.setOnClickListener(view -> {
                    boolean shouldShowReceipt = itemsCard.getVisibility() != View.VISIBLE;
                    itemsCard.setVisibility(shouldShowReceipt ? View.VISIBLE : View.GONE);
                    updateHistoryReceiptToggleButton(
                            toggleReceiptButton,
                            toggleReceiptIconView,
                            shouldShowReceipt
                    );
                });
            } else {
                toggleReceiptButtonContainer.setVisibility(View.GONE);
                toggleReceiptButton.setOnClickListener(null);
                itemsCard.setVisibility(View.GONE);
            }
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

    private void updateHistoryReceiptToggleButton(
            @NonNull MaterialButton toggleReceiptButton,
            @NonNull AppCompatImageView toggleReceiptIconView,
            boolean isShowingReceipt
    ) {
        toggleReceiptButton.setText(
                isShowingReceipt ? R.string.hide_receipt : R.string.show_receipt
        );
        toggleReceiptIconView.setImageResource(
                isShowingReceipt
                        ? R.drawable.ic_history_hide_receipt
                        : R.drawable.ic_history_show_receipt
        );
    }

    private void bindReceiptHistoryTransfers(
            @NonNull LinearLayout transfersLayout,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull List<ReceiptHistoryTransfer> transfers
    ) {
        transfersLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int defaultEndPadding = dpToPx(56);
        int expandedEndPadding = dpToPx(112);
        for (ReceiptHistoryTransfer transfer : transfers) {
            View rowView = inflater.inflate(
                    R.layout.item_history_receipt_transfer,
                    transfersLayout,
                    false
            );
            View contentLayout = rowView.findViewById(R.id.layout_archive_summary_transfer_content);
            TextView directionView =
                    rowView.findViewById(R.id.text_archive_summary_transfer_direction);
            TextView amountView =
                    rowView.findViewById(R.id.text_archive_summary_transfer_amount);
            AppCompatImageView statusIconView =
                    rowView.findViewById(R.id.image_archive_summary_transfer_status);
            MaterialButton payNowButton =
                    rowView.findViewById(R.id.button_archive_summary_transfer_pay_now);
            directionView.setText(formatHistoryTransferDirectionForHistoryDisplay(transfer.direction));
            amountView.setText(transfer.amount);
            bindHistoryTransferStatus(statusIconView, amountView, transfer.hasPaid);
            if (contentLayout != null) {
                contentLayout.setPadding(
                        contentLayout.getPaddingLeft(),
                        contentLayout.getPaddingTop(),
                        transfer.canPayNow ? expandedEndPadding : defaultEndPadding,
                        contentLayout.getPaddingBottom()
                );
            }
            if (transfer.canPayNow) {
                payNowButton.setVisibility(View.VISIBLE);
                payNowButton.setOnClickListener(
                        view -> openSwishForReceiptHistoryTransfer(
                                entry,
                                transfer,
                                () -> {
                                    bindReceiptHistoryTransfers(
                                            transfersLayout,
                                            entry,
                                            buildReceiptHistoryTransfers(entry)
                                    );
                                    historyEntriesAdapter.notifyDataSetChanged();
                                }
                        )
                );
            } else {
                payNowButton.setVisibility(View.GONE);
                payNowButton.setOnClickListener(null);
            }
            transfersLayout.addView(rowView);
        }
    }

    private void bindHistoryTransferStatus(
            @NonNull AppCompatImageView statusIconView,
            @NonNull TextView amountView,
            boolean hasPaid
    ) {
        int statusColor = getHistoryTransferStatusColor(hasPaid);
        bindArchiveSummaryHistoryTransferStatusIcon(statusIconView, hasPaid);
        amountView.setTextColor(statusColor);
    }

    @NonNull
    private ArrayList<ReceiptHistoryTransfer> buildReceiptHistoryTransfers(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        ArrayList<ReceiptHistoryTransfer> reconstructedTransfers =
                buildReconstructedReceiptHistoryTransfers(entry);
        if (!entry.paymentCards.isEmpty()) {
            return buildReceiptHistoryTransfersFromPaymentCards(entry, reconstructedTransfers);
        }
        return reconstructedTransfers;
    }

    @NonNull
    private ArrayList<ReceiptHistoryTransfer> buildReconstructedReceiptHistoryTransfers(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        LinkedHashMap<String, BigDecimal> balancesByKey = new LinkedHashMap<>();
        for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
            participantsByKey.put(participant.key, participant);
            balancesByKey.put(participant.key, BigDecimal.ZERO);
        }

        for (ReceiptHistoryStore.HistoryItem item : entry.items) {
            ReceiptHistoryStore.ParticipantShare payer = findReceiptHistoryPayer(entry, item);
            if (payer == null) {
                continue;
            }

            participantsByKey.putIfAbsent(payer.key, payer);
            balancesByKey.putIfAbsent(payer.key, BigDecimal.ZERO);

            int selectedParticipantCount = countSelectedHistoryParticipants(item, entry.participants);
            if (selectedParticipantCount == 0) {
                continue;
            }

            BigDecimal itemAmount = parseCurrencyAmount(item.price);
            BigDecimal sharedAmount = itemAmount.divide(
                    BigDecimal.valueOf(selectedParticipantCount),
                    2,
                    RoundingMode.HALF_UP
            );
            for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
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

        ArrayList<HistoryTransferBalance> creditors = new ArrayList<>();
        ArrayList<HistoryTransferBalance> debtors = new ArrayList<>();
        for (String participantKey : balancesByKey.keySet()) {
            BigDecimal balance = balancesByKey.get(participantKey).setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new HistoryTransferBalance(
                        participantsByKey.get(participantKey),
                        balance
                ));
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new HistoryTransferBalance(
                        participantsByKey.get(participantKey),
                        balance.abs()
                ));
            }
        }

        ArrayList<ReceiptHistoryTransfer> transfers = new ArrayList<>();
        int paymentCardIndex = 0;
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((first, second) -> second.amount.compareTo(first.amount));
            debtors.sort((first, second) -> second.amount.compareTo(first.amount));

            HistoryTransferBalance creditor = creditors.get(0);
            HistoryTransferBalance debtor = debtors.get(0);
            BigDecimal transferAmount = creditor.amount.min(debtor.amount)
                    .setScale(2, RoundingMode.HALF_UP);
            ReceiptHistoryStore.PaymentCard paymentCard =
                    paymentCardIndex < entry.paymentCards.size()
                            ? entry.paymentCards.get(paymentCardIndex)
                            : null;
            String recipientPhoneNumber = paymentCard != null
                    ? paymentCard.recipientPhoneNumber
                    : creditor.participant.phoneNumber;
            boolean hasPaid = paymentCard != null
                    ? paymentCard.hasPaid
                    : debtor.participant.hasPaid;
            String paymentCardId = paymentCard != null ? paymentCard.id : "";

            transfers.add(new ReceiptHistoryTransfer(
                    getString(
                            R.string.history_receipt_transfer_direction_arrow,
                            getHistoryParticipantDisplayName(debtor.participant),
                            getHistoryParticipantDisplayName(creditor.participant)
                    ),
                    getString(
                            R.string.archive_summary_transfer_amount,
                            formatHistoryTransferAmount(transferAmount)
                    ),
                    hasPaid,
                    isDefaultParticipant(debtor.participant)
                            && !hasPaid
                            && !normalizeWhitespace(recipientPhoneNumber).isEmpty(),
                    debtor.participant.key,
                    paymentCardId,
                    recipientPhoneNumber,
                    transferAmount
            ));
            paymentCardIndex++;

            creditor.amount = creditor.amount.subtract(transferAmount);
            debtor.amount = debtor.amount.subtract(transferAmount);

            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) {
                creditors.remove(0);
            }
            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) {
                debtors.remove(0);
            }
        }

        return transfers;
    }

    @NonNull
    private ArrayList<ReceiptHistoryTransfer> buildReceiptHistoryTransfersFromPaymentCards(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ArrayList<ReceiptHistoryTransfer> reconstructedTransfers
    ) {
        ArrayList<ReceiptHistoryTransfer> transfers = new ArrayList<>();
        ReceiptHistoryStore.ParticipantShare defaultParticipant =
                findDefaultHistoryParticipant(entry.participants);

        for (int index = 0; index < entry.paymentCards.size(); index++) {
            ReceiptHistoryStore.PaymentCard paymentCard = entry.paymentCards.get(index);
            ReceiptHistoryTransfer reconstructedTransfer =
                    index < reconstructedTransfers.size()
                            ? reconstructedTransfers.get(index)
                            : null;
            ReceiptHistoryStore.ParticipantShare debtorParticipant =
                    reconstructedTransfer == null
                            ? null
                            : findHistoryParticipantByKey(
                                    entry.participants,
                                    reconstructedTransfer.debtorParticipantKey
                            );
            ReceiptHistoryStore.ParticipantShare recipientParticipant =
                    findHistoryParticipantByPhoneNumber(
                            entry.participants,
                            paymentCard.recipientPhoneNumber
                    );
            if (debtorParticipant == null
                    && defaultParticipant != null
                    && (recipientParticipant == null
                    || !recipientParticipant.key.equals(defaultParticipant.key))) {
                debtorParticipant = defaultParticipant;
            }

            String debtorName = debtorParticipant != null
                    ? getHistoryParticipantDisplayName(debtorParticipant)
                    : DEFAULT_PARTICIPANT_NAME;
            String recipientName = getHistoryPaymentCardRecipientDisplayName(
                    recipientParticipant,
                    paymentCard.recipientPhoneNumber
            );
            String amountText = normalizeWhitespace(paymentCard.amount).isEmpty()
                    ? reconstructedTransfer == null
                            ? "0,00"
                            : formatHistoryTransferAmount(reconstructedTransfer.amountValue)
                    : paymentCard.amount;
            BigDecimal amountValue = parseCurrencyAmount(amountText);

            transfers.add(new ReceiptHistoryTransfer(
                    getString(
                            R.string.history_receipt_transfer_direction_arrow,
                            debtorName,
                            recipientName
                    ),
                    getString(
                            R.string.archive_summary_transfer_amount,
                            amountText
                    ),
                    paymentCard.hasPaid,
                    debtorParticipant != null
                            && isDefaultParticipant(debtorParticipant)
                            && !paymentCard.hasPaid
                            && !normalizeWhitespace(paymentCard.recipientPhoneNumber).isEmpty(),
                    debtorParticipant == null ? "" : debtorParticipant.key,
                    paymentCard.id,
                    paymentCard.recipientPhoneNumber,
                    amountValue
            ));
        }

        return transfers;
    }

    private void openSwishForReceiptHistoryTransfer(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ReceiptHistoryTransfer transfer,
            @NonNull Runnable onMarkedPaid
    ) {
        String normalizedPhoneNumber =
                normalizePhoneNumberForSwish(transfer.recipientPhoneNumber);
        if (normalizedPhoneNumber.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.pay_now_owner_phone_unavailable,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (transfer.amountValue.compareTo(BigDecimal.ZERO) <= 0) {
            Toast.makeText(this, R.string.pay_now_nothing_to_pay, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent swishIntent = new Intent(
                    Intent.ACTION_VIEW,
                    buildSwishPaymentUri(
                            normalizedPhoneNumber,
                            transfer.amountValue,
                            entry.receiptName
                    )
            );
            startActivity(swishIntent);
            markReceiptHistoryTransferAsPaid(entry, transfer, onMarkedPaid);
        } catch (ActivityNotFoundException | JSONException exception) {
            Toast.makeText(this, R.string.pay_now_open_swish_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void bindArchiveSummaryHistoryTransfers(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull LinearLayout transfersLayout,
            @NonNull List<ArchiveSummaryHistoryTransfer> transfers
    ) {
        transfersLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int defaultEndPadding = dpToPx(56);
        int expandedEndPadding = dpToPx(112);
        for (ArchiveSummaryHistoryTransfer transfer : transfers) {
            View rowView = inflater.inflate(
                    R.layout.item_history_receipt_transfer,
                    transfersLayout,
                    false
            );
            View contentLayout = rowView.findViewById(R.id.layout_archive_summary_transfer_content);
            TextView directionView =
                    rowView.findViewById(R.id.text_archive_summary_transfer_direction);
            TextView amountView =
                    rowView.findViewById(R.id.text_archive_summary_transfer_amount);
            AppCompatImageView statusIconView =
                    rowView.findViewById(R.id.image_archive_summary_transfer_status);
            MaterialButton payNowButton =
                    rowView.findViewById(R.id.button_archive_summary_transfer_pay_now);
            directionView.setText(formatHistoryTransferDirectionForHistoryDisplay(transfer.direction));
            amountView.setText(transfer.amount);
            bindHistoryTransferStatus(statusIconView, amountView, transfer.hasPaid);
            if (contentLayout != null) {
                contentLayout.setPadding(
                        contentLayout.getPaddingLeft(),
                        contentLayout.getPaddingTop(),
                        transfer.canPayNow ? expandedEndPadding : defaultEndPadding,
                        contentLayout.getPaddingBottom()
                );
            }
            if (transfer.canPayNow) {
                payNowButton.setVisibility(View.VISIBLE);
                payNowButton.setOnClickListener(
                        view -> openSwishForArchiveHistoryTransfer(
                                entry,
                                transfer,
                                () -> {
                                    bindArchiveSummaryHistoryTransfers(
                                            entry,
                                            transfersLayout,
                                            buildArchiveSummaryHistoryTransfers(entry)
                                    );
                                    historyEntriesAdapter.notifyDataSetChanged();
                                }
                        )
                );
            } else {
                payNowButton.setVisibility(View.GONE);
                payNowButton.setOnClickListener(null);
            }
            transfersLayout.addView(rowView);
        }
    }

    private void bindArchiveSummaryReceiptSections(
            @NonNull LinearLayout sectionsLayout,
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archivedReceipts
    ) {
        sectionsLayout.removeAllViews();
        if (archivedReceipts.isEmpty()) {
            sectionsLayout.setVisibility(View.GONE);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReceiptHistoryStore.HistoryEntry archivedReceipt : archivedReceipts) {
            View sectionView = inflater.inflate(
                    R.layout.item_history_archive_receipt_section,
                    sectionsLayout,
                    false
            );
            MaterialButton toggleButton = sectionView.findViewById(
                    R.id.button_history_archive_receipt_section_toggle
            );
            AppCompatImageView toggleIconView = sectionView.findViewById(
                    R.id.image_history_archive_receipt_section_toggle_icon
            );
            MaterialCardView receiptCard = sectionView.findViewById(
                    R.id.card_history_archive_receipt_section
            );
            LinearLayout participantsLayout = sectionView.findViewById(
                    R.id.layout_history_archive_receipt_section_participants
            );
            View dividerView = sectionView.findViewById(
                    R.id.view_history_archive_receipt_section_divider
            );
            LinearLayout itemsLayout = sectionView.findViewById(
                    R.id.layout_history_archive_receipt_section_items
            );

            toggleButton.setText(archivedReceipt.receiptName);

            boolean hasParticipants = !archivedReceipt.participants.isEmpty();
            boolean hasItems = !archivedReceipt.items.isEmpty();
            boolean hasReceiptContent = hasParticipants || hasItems;

            participantsLayout.setVisibility(hasParticipants ? View.VISIBLE : View.GONE);
            if (hasParticipants) {
                bindHistoryParticipantButtons(participantsLayout, archivedReceipt);
            } else {
                participantsLayout.removeAllViews();
            }

            dividerView.setVisibility(hasParticipants && hasItems ? View.VISIBLE : View.GONE);

            itemsLayout.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            if (hasItems) {
                bindHistoryReceiptItems(itemsLayout, archivedReceipt);
            } else {
                itemsLayout.removeAllViews();
            }

            receiptCard.setVisibility(View.GONE);
            toggleButton.setEnabled(hasReceiptContent);
            updateArchiveHistoryReceiptSectionToggleIcon(toggleIconView, false);
            toggleButton.setOnClickListener(view -> {
                if (!hasReceiptContent) {
                    return;
                }
                boolean shouldShowReceipt = receiptCard.getVisibility() != View.VISIBLE;
                receiptCard.setVisibility(shouldShowReceipt ? View.VISIBLE : View.GONE);
                updateArchiveHistoryReceiptSectionToggleIcon(
                        toggleIconView,
                        shouldShowReceipt
                );
            });

            sectionsLayout.addView(sectionView);
        }

        sectionsLayout.setVisibility(View.VISIBLE);
    }

    private void updateArchiveHistoryReceiptSectionToggleIcon(
            @NonNull AppCompatImageView toggleIconView,
            boolean isShowingReceipt
    ) {
        toggleIconView.setImageResource(
                isShowingReceipt
                        ? R.drawable.ic_history_hide_receipt
                        : R.drawable.ic_history_show_receipt
        );
    }

    @NonNull
    private ArrayList<ArchiveSummaryHistoryTransfer> buildArchiveSummaryHistoryTransfers(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        if (!entry.items.isEmpty()) {
            ArrayList<ArchiveSummaryHistoryTransfer> transfers = new ArrayList<>();
            for (ReceiptHistoryStore.HistoryItem item : entry.items) {
                String direction = normalizeWhitespace(item.name);
                if (direction.isEmpty()) {
                    continue;
                }
                String amount = getString(R.string.archive_summary_transfer_amount, item.price);
                ReceiptHistoryStore.ParticipantShare debtorParticipant =
                        item.selectedParticipantKeys.isEmpty()
                                ? null
                                : findHistoryParticipantByKey(
                                        entry.participants,
                                        item.selectedParticipantKeys.get(0)
                                );
                ReceiptHistoryStore.ParticipantShare creditorParticipant =
                        findHistoryParticipantByKey(entry.participants, item.payerParticipantKey);
                boolean canPayNow = debtorParticipant != null
                        && creditorParticipant != null
                        && isDefaultParticipant(debtorParticipant)
                        && !item.hasPaid
                        && !normalizeWhitespace(creditorParticipant.phoneNumber).isEmpty();
                String recipientPhoneNumber =
                        creditorParticipant == null ? "" : creditorParticipant.phoneNumber;
                transfers.add(new ArchiveSummaryHistoryTransfer(
                        direction,
                        amount,
                        item.hasPaid,
                        canPayNow,
                        recipientPhoneNumber,
                        parseCurrencyAmount(item.price),
                        item
                ));
            }
            return transfers;
        }

        return parseArchiveSummaryHistoryTransfers(entry.message);
    }

    private void bindArchiveSummaryHistoryTransferStatusIcon(
            @NonNull AppCompatImageView statusIconView,
            boolean hasPaid
    ) {
        statusIconView.setVisibility(View.VISIBLE);
        if (hasPaid) {
            statusIconView.setImageResource(R.drawable.ic_history_transfer_paid);
            statusIconView.setImageTintList(
                    ColorStateList.valueOf(getHistoryTransferStatusColor(true))
            );
        } else {
            statusIconView.setImageResource(R.drawable.ic_history_transfer_unpaid);
            statusIconView.setImageTintList(
                    ColorStateList.valueOf(getHistoryTransferStatusColor(false))
            );
        }
    }

    @NonNull
    private String formatHistoryTransferDirectionForDisplay(@Nullable String direction) {
        String normalizedDirection = normalizeWhitespace(direction);
        if (normalizedDirection.isEmpty()) {
            return "";
        }
        return normalizedDirection.replace(" pays ", " → ");
    }

    private int getHistoryTransferStatusColor(boolean hasPaid) {
        return ContextCompat.getColor(
                this,
                hasPaid ? R.color.brand_green : R.color.brand_red
        );
    }

    @NonNull
    private String formatHistoryTransferDirectionForHistoryDisplay(@Nullable String direction) {
        String normalizedDirection = normalizeWhitespace(direction);
        if (normalizedDirection.isEmpty()) {
            return "";
        }
        return normalizedDirection.replace(" pays ", " \u2192 ");
    }

    @NonNull
    private ArrayList<ArchiveSummaryHistoryTransfer> parseArchiveSummaryHistoryTransfers(
            @Nullable String message
    ) {
        ArrayList<ArchiveSummaryHistoryTransfer> transfers = new ArrayList<>();
        String normalizedMessage = normalizeWhitespace(message);
        if (normalizedMessage.isEmpty()) {
            return transfers;
        }

        String[] lines = message.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            int separatorIndex = trimmedLine.lastIndexOf(" - ");
            if (separatorIndex <= 0 || separatorIndex >= trimmedLine.length() - 3) {
                continue;
            }

            String direction = trimmedLine.substring(0, separatorIndex).trim();
            String amount = trimmedLine.substring(separatorIndex + 3).trim();
            if (direction.isEmpty() || amount.isEmpty()) {
                continue;
            }

            transfers.add(new ArchiveSummaryHistoryTransfer(
                    direction,
                    amount,
                    false,
                    false,
                    "",
                    parseCurrencyAmount(amount.replace("kr", "").trim()),
                    null
            ));
        }
        return transfers;
    }

    private void openSwishForArchiveHistoryTransfer(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ArchiveSummaryHistoryTransfer transfer,
            @NonNull Runnable onMarkedPaid
    ) {
        String normalizedPhoneNumber =
                normalizePhoneNumberForSwish(transfer.recipientPhoneNumber);
        if (normalizedPhoneNumber.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.pay_now_owner_phone_unavailable,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (transfer.amountValue.compareTo(BigDecimal.ZERO) <= 0) {
            Toast.makeText(this, R.string.pay_now_nothing_to_pay, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent swishIntent = new Intent(
                    Intent.ACTION_VIEW,
                    buildSwishPaymentUri(
                            normalizedPhoneNumber,
                            transfer.amountValue,
                            entry.receiptName
                    )
            );
            startActivity(swishIntent);
            markArchiveHistoryTransferAsPaid(entry, transfer, onMarkedPaid);
        } catch (ActivityNotFoundException | JSONException exception) {
            Toast.makeText(this, R.string.pay_now_open_swish_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void markReceiptHistoryTransferAsPaid(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ReceiptHistoryTransfer transfer,
            @NonNull Runnable onPersisted
    ) {
        if (!normalizeWhitespace(transfer.paymentCardId).isEmpty()) {
            if (transfer.hasPaid) {
                return;
            }
            ReceiptHistoryStore.HistoryEntry updatedEntry =
                    ReceiptHistoryStore.withPaymentCardPaidStatus(
                            entry,
                            transfer.paymentCardId,
                            true
                    );
            persistUpdatedHistoryEntry(entry, updatedEntry, onPersisted);
            return;
        }

        if (normalizeWhitespace(transfer.debtorParticipantKey).isEmpty() || transfer.hasPaid) {
            return;
        }
        ReceiptHistoryStore.HistoryEntry updatedEntry = ReceiptHistoryStore.withParticipantPaidStatus(
                entry,
                transfer.debtorParticipantKey,
                true
        );
        persistUpdatedHistoryEntry(entry, updatedEntry, onPersisted);
    }

    private void markArchiveHistoryTransferAsPaid(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ArchiveSummaryHistoryTransfer transfer,
            @NonNull Runnable onPersisted
    ) {
        if (transfer.sourceItem == null || transfer.hasPaid) {
            return;
        }
        ReceiptHistoryStore.HistoryEntry updatedEntry = ReceiptHistoryStore.withHistoryItemPaidStatus(
                entry,
                transfer.sourceItem,
                true
        );
        persistUpdatedHistoryEntry(entry, updatedEntry, onPersisted);
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
        SupabaseHistoryService.removeEntry(this, entry, new SupabaseHistoryService.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                loadHistoryEntries(previousVisibleCount);
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                Toast.makeText(HistoryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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
                    R.layout.item_receipt_view_participant_button,
                    participantsLayout,
                    false
            );
            MaterialButton badgeButton = rowView.findViewById(R.id.button_summary_participant_badge);
            AppCompatImageView ownerIconView =
                    rowView.findViewById(R.id.image_summary_participant_owner);
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);
            boolean isOwner = isCrownedParticipant(participant);

            configureHistoryParticipantBadgeButton(badgeButton, participant, true);
            ownerIconView.setVisibility(
                    isOwner ? View.VISIBLE : View.GONE
            );
            nameView.setText(participant.name);
            amountView.setText(buildHistoryParticipantAmountDisplayText(participant.amount));

            LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index < participants.size() - 1) {
                rowLayoutParams.bottomMargin = dpToPx(8);
            }
            rowView.setLayoutParams(rowLayoutParams);

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

            AppCompatImageView payerSwatchView =
                    itemView.findViewById(R.id.image_receipt_item_payer_swatch);
            TextView itemNameView = itemView.findViewById(R.id.text_receipt_item_name);
            TextView itemPriceView = itemView.findViewById(R.id.text_receipt_item_price);
            LinearLayout participantSelectionLayout =
                    itemView.findViewById(R.id.layout_receipt_item_participants);

            bindHistoryItemPayerSwatch(payerSwatchView, item, participants);
            itemNameView.setText(item.name);
            itemPriceView.setText(getString(R.string.archive_summary_transfer_amount, item.price));
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

    private void bindHistoryItemPayerSwatch(
            @NonNull AppCompatImageView payerSwatchView,
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants
    ) {
        String payerParticipantKey =
                normalizeHistoryItemPayerKey(item.payerParticipantKey, participants);
        if (payerParticipantKey.isEmpty()) {
            payerSwatchView.setVisibility(View.GONE);
            payerSwatchView.setBackground(null);
            return;
        }

        ReceiptHistoryStore.ParticipantShare payerParticipant =
                findHistoryParticipantByKey(participants, payerParticipantKey);
        if (payerParticipant == null) {
            payerSwatchView.setVisibility(View.GONE);
            payerSwatchView.setBackground(null);
            return;
        }

        payerSwatchView.setVisibility(View.VISIBLE);
        payerSwatchView.setBackground(
                createHistoryItemPayerSwatchDrawable(payerParticipant.color, null)
        );
    }

    @NonNull
    private GradientDrawable createHistoryItemPayerSwatchDrawable(
            int fillColor,
            @Nullable Integer strokeColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        if (strokeColor != null) {
            drawable.setStroke(dpToPx(1), strokeColor);
        }
        return drawable;
    }

    @NonNull
    private String normalizeHistoryItemPayerKey(
            @Nullable String payerParticipantKey,
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants
    ) {
        String normalizedPayerParticipantKey = normalizeWhitespace(payerParticipantKey);
        if (normalizedPayerParticipantKey.isEmpty()) {
            return "";
        }
        return findHistoryParticipantByKey(participants, normalizedPayerParticipantKey) == null
                ? ""
                : normalizedPayerParticipantKey;
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findHistoryParticipantByKey(
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
            @Nullable String participantKey
    ) {
        String normalizedParticipantKey = normalizeWhitespace(participantKey);
        if (normalizedParticipantKey.isEmpty()) {
            return null;
        }
        for (ReceiptHistoryStore.ParticipantShare participant : participants) {
            if (participant.key.equals(normalizedParticipantKey)) {
                return participant;
            }
        }
        return null;
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findHistoryParticipantByPhoneNumber(
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
            @Nullable String phoneNumber
    ) {
        String normalizedPhoneNumber = normalizePhoneNumberForSwish(phoneNumber);
        if (normalizedPhoneNumber.isEmpty()) {
            return null;
        }

        for (ReceiptHistoryStore.ParticipantShare participant : participants) {
            String participantPhoneNumber = isDefaultParticipant(participant)
                    ? AppSettings.getLoginPhoneNumber(this)
                    : participant.phoneNumber;
            if (normalizedPhoneNumber.equals(
                    normalizePhoneNumberForSwish(participantPhoneNumber)
            )) {
                return participant;
            }
        }
        return null;
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findDefaultHistoryParticipant(
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants
    ) {
        for (ReceiptHistoryStore.ParticipantShare participant : participants) {
            if (isDefaultParticipant(participant)) {
                return participant;
            }
        }
        return null;
    }

    @NonNull
    private String getHistoryPaymentCardRecipientDisplayName(
            @Nullable ReceiptHistoryStore.ParticipantShare participant,
            @Nullable String phoneNumber
    ) {
        if (participant != null) {
            return getHistoryParticipantDisplayName(participant);
        }

        String normalizedPhoneNumber = normalizeWhitespace(phoneNumber);
        if (!normalizedPhoneNumber.isEmpty()) {
            return normalizedPhoneNumber;
        }

        return getString(R.string.participant_phone_unavailable);
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
            selectionButton.setCornerRadius(checkboxSize / 2);
            selectionButton.setStrokeWidth(dpToPx(2));
            applyParticipantBadgeTextStyle(selectionButton, participant, true);
            selectionButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            selectionButton.setFocusable(true);
            selectionButton.setClickable(true);
            selectionButton.setCheckable(false);
            selectionButton.setContentDescription(participant.name);

            boolean isChecked = item.isParticipantSelected(participant.key);
            int buttonColor = isChecked
                    ? participant.color
                    : UNCHECKED_PARTICIPANT_COLOR;
            selectionButton.setStrokeColor(ColorStateList.valueOf(buttonColor));
            selectionButton.setBackgroundTintList(ColorStateList.valueOf(
                    isChecked ? participant.color : Color.TRANSPARENT
            ));
            selectionButton.setTextColor(
                    isChecked ? getParticipantTextColor(participant.color) : buttonColor
            );
            selectionButton.setFocusable(false);
            selectionButton.setClickable(false);
            selectionButton.setOnClickListener(null);

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
    private String buildHistoryParticipantAmountDisplayText(@Nullable String participantAmount) {
        String amountText = normalizeWhitespace(participantAmount);
        return amountText.isEmpty() ? "0,00kr" : amountText + "kr";
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

    private int countSelectedHistoryParticipants(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants
    ) {
        int count = 0;
        for (ReceiptHistoryStore.ParticipantShare participant : participants) {
            if (item.isParticipantSelected(participant.key)) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findReceiptHistoryPayer(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull ReceiptHistoryStore.HistoryItem item
    ) {
        String explicitPayerKey =
                normalizeHistoryItemPayerKey(item.payerParticipantKey, entry.participants);
        if (!explicitPayerKey.isEmpty()) {
            ReceiptHistoryStore.ParticipantShare explicitPayer =
                    findHistoryParticipantByKey(entry.participants, explicitPayerKey);
            if (explicitPayer != null) {
                return explicitPayer;
            }
        }
        return findHistoryReceiptOwnerParticipant(entry);
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findHistoryReceiptOwnerParticipant(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
            if (isCrownedParticipant(participant)) {
                return participant;
            }
        }
        for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
            if (isDefaultParticipant(participant)) {
                return participant;
            }
        }
        return entry.participants.isEmpty() ? null : entry.participants.get(0);
    }

    @NonNull
    private String getHistoryParticipantDisplayName(
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        return isDefaultParticipant(participant)
                ? DEFAULT_PARTICIPANT_NAME
                : normalizeWhitespace(participant.name);
    }

    @NonNull
    private String formatHistoryTransferAmount(@NonNull BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',');
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

    private void bindHistoryReceiptStatusIcon(
            @NonNull AppCompatImageView statusIconView,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        statusIconView.setVisibility(View.VISIBLE);
        int paymentStatus = entry.isArchiveSummary()
                ? getArchiveHistoryPaymentStatus(entry)
                : getHistoryReceiptPaymentStatus(entry);
        if (paymentStatus == RECEIPT_PAYMENT_STATUS_ALL) {
            statusIconView.setImageResource(R.drawable.ic_history_receipt_paid);
            statusIconView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_green)
            ));
            return;
        }
        if (paymentStatus == RECEIPT_PAYMENT_STATUS_PARTIAL) {
            statusIconView.setImageResource(R.drawable.ic_history_receipt_partial);
            statusIconView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_orange)
            ));
            return;
        }
        statusIconView.setImageResource(R.drawable.ic_history_receipt_unpaid);
        statusIconView.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.brand_red)
        ));
    }

    private int getArchiveHistoryPaymentStatus(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        ArrayList<ArchiveSummaryHistoryTransfer> transfers =
                buildArchiveSummaryHistoryTransfers(entry);
        if (transfers.isEmpty()) {
            return RECEIPT_PAYMENT_STATUS_ALL;
        }

        int paidTransferCount = 0;
        for (ArchiveSummaryHistoryTransfer transfer : transfers) {
            if (transfer.hasPaid) {
                paidTransferCount++;
            }
        }

        if (paidTransferCount == transfers.size()) {
            return RECEIPT_PAYMENT_STATUS_ALL;
        }
        if (paidTransferCount > 0) {
            return RECEIPT_PAYMENT_STATUS_PARTIAL;
        }
        return RECEIPT_PAYMENT_STATUS_NONE;
    }

    private int getHistoryReceiptPaymentStatus(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        ArrayList<ReceiptHistoryTransfer> transfers = buildReceiptHistoryTransfers(entry);
        if (transfers.isEmpty()) {
            return RECEIPT_PAYMENT_STATUS_ALL;
        }

        int paidTransferCount = 0;
        for (ReceiptHistoryTransfer transfer : transfers) {
            if (transfer.hasPaid) {
                paidTransferCount++;
            }
        }

        if (paidTransferCount == transfers.size()) {
            return RECEIPT_PAYMENT_STATUS_ALL;
        }
        if (paidTransferCount > 0) {
            return RECEIPT_PAYMENT_STATUS_PARTIAL;
        }
        return RECEIPT_PAYMENT_STATUS_NONE;
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
        ReceiptHistoryStore.HistoryEntry updatedEntry = ReceiptHistoryStore.withParticipantPaidStatus(
                entry,
                DEFAULT_PARTICIPANT_KEY,
                true
        );
        persistUpdatedHistoryEntry(entry, updatedEntry);
    }

    private void persistUpdatedHistoryEntry(
            @NonNull ReceiptHistoryStore.HistoryEntry targetEntry,
            @NonNull ReceiptHistoryStore.HistoryEntry updatedEntry
    ) {
        persistUpdatedHistoryEntry(targetEntry, updatedEntry, null);
    }

    private void persistUpdatedHistoryEntry(
            @NonNull ReceiptHistoryStore.HistoryEntry targetEntry,
            @NonNull ReceiptHistoryStore.HistoryEntry updatedEntry,
            @Nullable Runnable onSaved
    ) {
        SupabaseHistoryService.updateEntry(this, updatedEntry, new SupabaseHistoryService.EntryCallback() {
            @Override
            public void onSuccess(@NonNull ReceiptHistoryStore.HistoryEntry savedEntry) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                copyHistoryEntryMutableState(targetEntry, savedEntry);
                historyEntriesAdapter.notifyDataSetChanged();
                if (onSaved != null) {
                    onSaved.run();
                }
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                Toast.makeText(HistoryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void copyHistoryEntryMutableState(
            @NonNull ReceiptHistoryStore.HistoryEntry targetEntry,
            @NonNull ReceiptHistoryStore.HistoryEntry sourceEntry
    ) {
        targetEntry.participants.clear();
        targetEntry.participants.addAll(sourceEntry.participants);
        targetEntry.items.clear();
        targetEntry.items.addAll(sourceEntry.items);
        targetEntry.archivedReceipts.clear();
        targetEntry.archivedReceipts.addAll(sourceEntry.archivedReceipts);
        targetEntry.paymentCards.clear();
        targetEntry.paymentCards.addAll(sourceEntry.paymentCards);
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

    private final class HistoryEntriesAdapter extends ArrayAdapter<HistoryListItem> {
        private HistoryEntriesAdapter() {
            super(HistoryActivity.this, R.layout.item_history_receipt, visibleHistoryListItems);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            HistoryListItem item = getItem(position);
            if (item == null) {
                return HISTORY_LIST_ITEM_TYPE_ENTRY;
            }
            return item.viewType;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            HistoryListItem item = getItem(position);
            return item != null && item.viewType == HISTORY_LIST_ITEM_TYPE_ENTRY;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            HistoryListItem item = getItem(position);
            if (item == null) {
                return new View(getContext());
            }

            if (item.viewType == HISTORY_LIST_ITEM_TYPE_SECTION) {
                View sectionView = convertView;
                if (sectionView == null) {
                    sectionView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_history_date_section, parent, false);
                }

                TextView sectionDateView =
                        sectionView.findViewById(R.id.text_history_section_date);
                sectionDateView.setText(item.sectionDate);
                sectionView.setOnClickListener(null);
                sectionView.setOnTouchListener(null);
                sectionView.setClickable(false);
                sectionView.setFocusable(false);
                return sectionView;
            }

            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_history_receipt, parent, false);
            }

            ReceiptHistoryStore.HistoryEntry entry = item.entry;
            TextView receiptNameView = itemView.findViewById(R.id.text_history_receipt_name);
            TextView totalAmountView = itemView.findViewById(R.id.text_history_receipt_total);
            AppCompatImageView typeIconView =
                    itemView.findViewById(R.id.image_history_receipt_type);
            AppCompatImageView statusIconView =
                    itemView.findViewById(R.id.image_history_receipt_status);

            if (entry != null) {
                receiptNameView.setText(entry.receiptName);
                totalAmountView.setText(
                        getString(R.string.archive_summary_transfer_amount, entry.totalAmount)
                );
                if (entry.isArchiveSummary()) {
                    typeIconView.setImageResource(R.drawable.ic_archive_entry_folder);
                } else {
                    typeIconView.setImageResource(R.drawable.ic_history_receipt_bill);
                }
                bindHistoryReceiptStatusIcon(statusIconView, entry);
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

    private static final class HistoryListItem {
        final int viewType;
        @Nullable
        final String sectionDate;
        @Nullable
        final ReceiptHistoryStore.HistoryEntry entry;

        private HistoryListItem(
                int viewType,
                @Nullable String sectionDate,
                @Nullable ReceiptHistoryStore.HistoryEntry entry
        ) {
            this.viewType = viewType;
            this.sectionDate = sectionDate;
            this.entry = entry;
        }

        @NonNull
        private static HistoryListItem createSection(@NonNull String sectionDate) {
            return new HistoryListItem(HISTORY_LIST_ITEM_TYPE_SECTION, sectionDate, null);
        }

        @NonNull
        private static HistoryListItem createEntry(@NonNull ReceiptHistoryStore.HistoryEntry entry) {
            return new HistoryListItem(HISTORY_LIST_ITEM_TYPE_ENTRY, null, entry);
        }
    }

    private static final class ArchiveSummaryHistoryTransfer {
        @NonNull
        private final String direction;
        @NonNull
        private final String amount;
        private final boolean hasPaid;
        private final boolean canPayNow;
        @NonNull
        private final String recipientPhoneNumber;
        @NonNull
        private final BigDecimal amountValue;
        @Nullable
        private final ReceiptHistoryStore.HistoryItem sourceItem;

        private ArchiveSummaryHistoryTransfer(
                @NonNull String direction,
                @NonNull String amount,
                boolean hasPaid,
                boolean canPayNow,
                @NonNull String recipientPhoneNumber,
                @NonNull BigDecimal amountValue,
                @Nullable ReceiptHistoryStore.HistoryItem sourceItem
        ) {
            this.direction = direction;
            this.amount = amount;
            this.hasPaid = hasPaid;
            this.canPayNow = canPayNow;
            this.recipientPhoneNumber = recipientPhoneNumber;
            this.amountValue = amountValue;
            this.sourceItem = sourceItem;
        }
    }

    private static final class ReceiptHistoryTransfer {
        @NonNull
        private final String direction;
        @NonNull
        private final String amount;
        private final boolean hasPaid;
        private final boolean canPayNow;
        @NonNull
        private final String debtorParticipantKey;
        @NonNull
        private final String paymentCardId;
        @NonNull
        private final String recipientPhoneNumber;
        @NonNull
        private final BigDecimal amountValue;

        private ReceiptHistoryTransfer(
                @NonNull String direction,
                @NonNull String amount,
                boolean hasPaid,
                boolean canPayNow,
                @NonNull String debtorParticipantKey,
                @NonNull String paymentCardId,
                @NonNull String recipientPhoneNumber,
                @NonNull BigDecimal amountValue
        ) {
            this.direction = direction;
            this.amount = amount;
            this.hasPaid = hasPaid;
            this.canPayNow = canPayNow;
            this.debtorParticipantKey = debtorParticipantKey;
            this.paymentCardId = paymentCardId;
            this.recipientPhoneNumber = recipientPhoneNumber;
            this.amountValue = amountValue;
        }
    }

    private static final class HistoryTransferBalance {
        @NonNull
        private final ReceiptHistoryStore.ParticipantShare participant;
        @NonNull
        private BigDecimal amount;

        private HistoryTransferBalance(
                @NonNull ReceiptHistoryStore.ParticipantShare participant,
                @NonNull BigDecimal amount
        ) {
            this.participant = participant;
            this.amount = amount;
        }
    }
}
