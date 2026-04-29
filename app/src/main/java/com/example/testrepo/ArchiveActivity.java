package com.example.testrepo;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArchiveActivity extends AppCompatActivity {
    private static final long ARCHIVE_ENTRY_LONG_PRESS_DURATION_MS = 750L;
    private static final long ARCHIVE_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS = 40L;
    private static final int MAX_PARTICIPANT_BUTTONS_PER_ROW = 5;
    private static final int MAX_ITEM_PARTICIPANT_BUTTONS_PER_ROW = 4;
    private static final int UNCHECKED_PARTICIPANT_COLOR = 0xFF8A8A8A;
    private static final int RECEIPT_FILTER_DEFAULT = 0;
    private static final int RECEIPT_FILTER_HIGH_TO_LOW = 1;
    private static final int RECEIPT_FILTER_LOW_TO_HIGH = 2;
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";
    private static final String DEFAULT_PARTICIPANT_NAME = "You";

    private final ArrayList<String> archiveNames = new ArrayList<>();
    private final ReceiptParser receiptParser = new ReceiptParser();
    private ArchiveEntriesAdapter archiveEntriesAdapter;
    @Nullable
    private ArchivedReceiptEditState pendingAddParticipantEditState;
    @Nullable
    private Runnable pendingAddParticipantRefreshRunnable;
    private boolean showAddParticipantDialogAfterContactsPermission;
    @Nullable
    private ExecutorService backgroundExecutor;
    private final ActivityResultLauncher<String> requestContactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!showAddParticipantDialogAfterContactsPermission
                        || pendingAddParticipantEditState == null
                        || pendingAddParticipantRefreshRunnable == null) {
                    return;
                }

                showAddParticipantDialogAfterContactsPermission = false;
                showArchivedReceiptAddParticipantDialog(
                        pendingAddParticipantEditState,
                        pendingAddParticipantRefreshRunnable,
                        isGranted
                );
            });

    private static final class ArchivedReceiptEditState {
        @NonNull
        private String crownedParticipantKey;
        @NonNull
        private final ArrayList<ReceiptHistoryStore.ParticipantShare> participants;
        @NonNull
        private final ArrayList<ReceiptHistoryStore.HistoryItem> allItems;
        @NonNull
        private final ArrayList<ReceiptHistoryStore.HistoryItem> items;
        private int filterMode;

        private ArchivedReceiptEditState(
                @NonNull String crownedParticipantKey,
                @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
                @NonNull List<ReceiptHistoryStore.HistoryItem> items
        ) {
            this.crownedParticipantKey = crownedParticipantKey;
            this.participants = new ArrayList<>(participants);
            this.allItems = new ArrayList<>(items);
            this.items = new ArrayList<>(items);
            this.filterMode = RECEIPT_FILTER_DEFAULT;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        setContentView(R.layout.activity_archive);

        View backButton = findViewById(R.id.button_back);
        View settingsMenuButton = findViewById(R.id.button_archive_actions);
        MaterialButton addArchiveButton = findViewById(R.id.button_add_archive);
        ListView archiveListView = findViewById(R.id.list_archive_receipts);
        backgroundExecutor = Executors.newSingleThreadExecutor();

        archiveEntriesAdapter = new ArchiveEntriesAdapter();
        archiveListView.setAdapter(archiveEntriesAdapter);
        archiveListView.setEmptyView(findViewById(R.id.text_archive_empty));
        backButton.setOnClickListener(view -> finish());
        settingsMenuButton.setOnClickListener(
                view -> SettingsMenuHelper.showSettingsMenu(this, view)
        );
        addArchiveButton.setOnClickListener(view -> showNewArchiveDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadArchiveNames();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
            backgroundExecutor = null;
        }
    }

    private void loadArchiveNames() {
        archiveNames.clear();
        archiveNames.addAll(ArchiveStore.loadArchiveNames(this));
        archiveEntriesAdapter.notifyDataSetChanged();
    }

    private void showNewArchiveDialog() {
        showNewArchiveDialog(null);
    }

    private void showNewArchiveDialog(@Nullable Runnable onArchiveCreated) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_archive, null);
        TextInputEditText archiveNameInput = dialogView.findViewById(R.id.input_archive_name);
        MaterialButton createButton = dialogView.findViewById(R.id.button_create_archive);

        createButton.setEnabled(false);
        archiveNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                createButton.setEnabled(!getArchiveName(archiveNameInput).isEmpty());
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_archive_title)
                .setView(dialogView)
                .create();

        createButton.setOnClickListener(view -> {
            String archiveName = getArchiveName(archiveNameInput);
            if (archiveName.isEmpty()) {
                createButton.setEnabled(false);
                return;
            }

            ArchiveStore.addArchiveName(this, archiveName);
            loadArchiveNames();
            if (onArchiveCreated != null) {
                onArchiveCreated.run();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showEditArchiveNameDialog(
            int archiveIndex,
            @NonNull String currentArchiveName,
            @NonNull Runnable onArchiveRenamed
    ) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_archive, null);
        TextInputEditText archiveNameInput = dialogView.findViewById(R.id.input_archive_name);
        MaterialButton applyButton = dialogView.findViewById(R.id.button_create_archive);

        archiveNameInput.setText(currentArchiveName);
        if (archiveNameInput.getText() != null) {
            archiveNameInput.setSelection(archiveNameInput.getText().length());
        }
        applyButton.setText(R.string.apply);
        applyButton.setEnabled(false);

        archiveNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String updatedArchiveName = getArchiveName(archiveNameInput);
                applyButton.setEnabled(
                        !updatedArchiveName.isEmpty()
                                && !updatedArchiveName.equals(currentArchiveName.trim())
                );
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_archive_name_title)
                .setView(dialogView)
                .create();

        applyButton.setOnClickListener(view -> {
            String updatedArchiveName = getArchiveName(archiveNameInput);
            if (updatedArchiveName.isEmpty()
                    || updatedArchiveName.equals(currentArchiveName.trim())) {
                applyButton.setEnabled(false);
                return;
            }

            ArchiveStore.renameArchiveAt(this, archiveIndex, updatedArchiveName);
            loadArchiveNames();
            onArchiveRenamed.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    @NonNull
    private String getArchiveName(@NonNull TextInputEditText archiveNameInput) {
        Editable editable = archiveNameInput.getText();
        if (editable == null) {
            return "";
        }
        return editable.toString().trim();
    }

    @NonNull
    private String getText(@NonNull TextInputEditText inputView) {
        Editable editable = inputView.getText();
        return editable == null ? "" : editable.toString().trim();
    }

    private void showArchiveDetailsDialog(int archiveIndex) {
        ArchiveStore.Archive archive = ArchiveStore.loadArchiveAt(this, archiveIndex);
        if (archive == null) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_archive_details, null);
        TextView titleView = dialogView.findViewById(R.id.text_archive_dialog_title);
        View closeButton = dialogView.findViewById(R.id.button_close_archive_details);
        AppCompatImageButton editButton =
                dialogView.findViewById(R.id.button_edit_archive_details);
        ListView receiptsListView = dialogView.findViewById(R.id.list_archive_receipt_entries);
        TextView emptyView = dialogView.findViewById(R.id.text_archive_receipt_entries_empty);
        MaterialButton newReceiptButton =
                dialogView.findViewById(R.id.button_archive_new_receipt);
        MaterialButton archiveSummaryButton =
                dialogView.findViewById(R.id.button_archive_send_requests);
        ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts = archive.receipts;
        ArchiveReceiptEntriesAdapter receiptsAdapter =
                new ArchiveReceiptEntriesAdapter(archiveIndex, archiveReceipts);

        titleView.setText(archive.name);
        archiveSummaryButton.setEnabled(!archiveReceipts.isEmpty());

        receiptsListView.setAdapter(receiptsAdapter);
        receiptsListView.setEmptyView(emptyView);
        newReceiptButton.setOnClickListener(view -> showCreateArchiveReceiptDialog(
                archiveIndex,
                archiveReceipts,
                receiptsAdapter,
                archiveSummaryButton,
                receiptsListView
        ));

        Dialog dialog = new Dialog(this, AppSettings.getFullScreenDialogThemeResId(this));
        dialog.setContentView(dialogView);
        closeButton.setOnClickListener(view -> dialog.dismiss());
        editButton.setOnClickListener(view -> showEditArchiveNameDialog(
                archiveIndex,
                titleView.getText().toString(),
                () -> {
                    ArchiveStore.Archive updatedArchive =
                            ArchiveStore.loadArchiveAt(this, archiveIndex);
                    if (updatedArchive != null) {
                        titleView.setText(updatedArchive.name);
                    }
                }
        ));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void showArchivedReceiptDetailsDialog(
            int archiveIndex,
            int receiptIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull Runnable onReceiptSaved
    ) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_archive_receipt_details, null);
        TextView titleView = dialogView.findViewById(R.id.text_archive_receipt_dialog_title);
        TextView messageView = dialogView.findViewById(R.id.text_archive_receipt_dialog_message);
        AppCompatImageButton closeButton =
                dialogView.findViewById(R.id.button_close_archive_receipt);
        MaterialButton saveChangesButton =
                dialogView.findViewById(R.id.button_save_archive_receipt_changes);
        View addParticipantAction =
                dialogView.findViewById(R.id.action_archive_add_participant);
        View addReceiptItemAction = dialogView.findViewById(R.id.action_archive_add_receipt_item);
        View scanMoreAction =
                dialogView.findViewById(R.id.action_archive_scan_more_receipt_items);
        LinearLayout participantsLayout =
                dialogView.findViewById(R.id.layout_archive_receipt_participant_buttons);
        ListView itemsListView = dialogView.findViewById(R.id.list_archive_receipt_items);
        TextView itemsEmptyView =
                dialogView.findViewById(R.id.text_archive_receipt_items_empty);
        TextView totalValueView = dialogView.findViewById(R.id.text_archive_receipt_total_value);
        ArchivedReceiptEditState editState = createArchivedReceiptEditState(entry);
        final Runnable[] refreshContentHolder = new Runnable[1];
        final ArchivedReceiptItemsAdapter[] itemsAdapterHolder = new ArchivedReceiptItemsAdapter[1];

        titleView.setText(entry.receiptName);

        String message = entry.message == null ? "" : entry.message.trim();
        if (message.isEmpty()) {
            messageView.setVisibility(View.GONE);
        } else {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(message);
        }

        refreshContentHolder[0] = () -> {
            String receiptTotalAmount = formatCurrency(computeArchivedReceiptItemsTotal(editState));
            bindArchivedReceiptParticipantButtons(
                    participantsLayout,
                    receiptTotalAmount,
                    editState,
                    refreshContentHolder[0]
            );
            updateArchivedReceiptItemsEmptyState(itemsListView, itemsEmptyView, editState);
            updateArchivedReceiptTotal(totalValueView, editState);
            saveChangesButton.setEnabled(hasArchivedReceiptSelections(editState));
            if (itemsAdapterHolder[0] != null) {
                itemsAdapterHolder[0].notifyDataSetChanged();
            }
            itemsListView.post(() -> updateArchivedReceiptItemsListHeight(itemsListView));
        };
        itemsAdapterHolder[0] = new ArchivedReceiptItemsAdapter(editState, refreshContentHolder);
        itemsListView.setAdapter(itemsAdapterHolder[0]);
        addParticipantAction.setOnClickListener(
                view -> openArchivedReceiptAddParticipantDialog(editState, refreshContentHolder[0])
        );
        addReceiptItemAction.setOnClickListener(view -> showAddArchivedReceiptItemDialog(
                editState,
                refreshContentHolder[0]
        ));
        scanMoreAction.setOnClickListener(view -> Toast.makeText(
                this,
                R.string.archive_scan_more_unavailable,
                Toast.LENGTH_SHORT
        ).show());
        refreshContentHolder[0].run();

        Dialog dialog = new Dialog(this, AppSettings.getAppThemeResId(this));
        dialog.setContentView(dialogView);
        closeButton.setOnClickListener(view -> dialog.dismiss());
        saveChangesButton.setOnClickListener(view -> {
            ReceiptHistoryStore.HistoryEntry updatedEntry =
                    buildArchivedReceiptEntry(entry, editState);
            archiveReceipts.set(receiptIndex, updatedEntry);
            ArchiveStore.updateReceiptAt(this, archiveIndex, receiptIndex, updatedEntry);
            onReceiptSaved.run();
            dialog.dismiss();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void showCreateArchiveReceiptDialog(
            int archiveIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ArchiveReceiptEntriesAdapter receiptsAdapter,
            @NonNull MaterialButton archiveSummaryButton,
            @NonNull ListView receiptsListView
    ) {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_new_archive_receipt,
                null
        );
        TextInputEditText receiptNameInput = dialogView.findViewById(R.id.input_receipt_name);
        MaterialButton createButton = dialogView.findViewById(R.id.button_create_receipt);

        createButton.setEnabled(false);
        receiptNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                createButton.setEnabled(!getText(receiptNameInput).isEmpty());
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.button_new_receipt)
                .setView(dialogView)
                .create();

        createButton.setOnClickListener(view -> {
            String receiptName = getText(receiptNameInput);
            if (receiptName.isEmpty()) {
                createButton.setEnabled(false);
                return;
            }

            ReceiptHistoryStore.HistoryEntry newReceiptEntry =
                    createEmptyArchiveReceiptEntry(receiptName);
            ArchiveStore.addReceiptToArchive(this, archiveIndex, newReceiptEntry);
            archiveReceipts.add(0, newReceiptEntry);
            receiptsAdapter.notifyDataSetChanged();
            archiveSummaryButton.setEnabled(true);
            loadArchiveNames();
            receiptsListView.post(() -> receiptsListView.smoothScrollToPosition(0));
            dialog.dismiss();
        });

        dialog.show();
    }

    @NonNull
    private ReceiptHistoryStore.HistoryEntry createEmptyArchiveReceiptEntry(
            @NonNull String receiptName
    ) {
        ArrayList<ReceiptHistoryStore.ParticipantShare> participants =
                buildEmptyArchiveReceiptParticipants();
        return new ReceiptHistoryStore.HistoryEntry(
                receiptName.trim().isEmpty()
                        ? getString(R.string.button_new_receipt)
                        : receiptName.trim(),
                receiptParser.formatAmount(0),
                getCurrentArchiveHistoryDate(),
                "",
                participants,
                new ArrayList<>()
        );
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.ParticipantShare> buildEmptyArchiveReceiptParticipants() {
        ArrayList<ReceiptHistoryStore.ParticipantShare> participants = new ArrayList<>();
        participants.add(new ReceiptHistoryStore.ParticipantShare(
                DEFAULT_PARTICIPANT_KEY,
                DEFAULT_PARTICIPANT_NAME,
                deriveInitials(DEFAULT_PARTICIPANT_NAME),
                createParticipantColor(participants.size()),
                "",
                receiptParser.formatAmount(0),
                true,
                true
        ));

        for (AppSettings.PreAddedParticipant preAddedParticipant
                : AppSettings.getPreAddedParticipants(this)) {
            participants.add(new ReceiptHistoryStore.ParticipantShare(
                    buildParticipantKey(preAddedParticipant.name, preAddedParticipant.phoneNumber),
                    preAddedParticipant.name,
                    deriveInitials(preAddedParticipant.name),
                    createParticipantColor(participants.size()),
                    preAddedParticipant.phoneNumber,
                    receiptParser.formatAmount(0),
                    false,
                    true
            ));
        }

        return participants;
    }

    @NonNull
    private String getCurrentArchiveHistoryDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US));
    }

    private int createParticipantColor(int participantIndex) {
        float hue = (participantIndex * 137.508f) % 360f;
        float[] hsv = {hue, 0.72f, 0.78f};
        return Color.HSVToColor(hsv);
    }

    @NonNull
    private String buildParticipantKey(@Nullable String name, @Nullable String phoneNumber) {
        return normalizeWhitespace(name).toLowerCase(Locale.US)
                + "\u001F"
                + normalizePhoneNumber(phoneNumber);
    }

    @NonNull
    private String normalizePhoneNumber(@Nullable String phoneNumber) {
        return normalizeWhitespace(phoneNumber).replaceAll("[^+\\d]", "");
    }

    private void showArchivedReceiptFiltersMenu(
            @NonNull View anchorView,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull ArchivedReceiptItemsAdapter adapter
    ) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_receipt_filters);
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_receipt_filter_default) {
                applyArchivedReceiptItemsFilter(RECEIPT_FILTER_DEFAULT, editState, adapter);
                return true;
            }
            if (itemId == R.id.action_receipt_filter_high_to_low) {
                applyArchivedReceiptItemsFilter(RECEIPT_FILTER_HIGH_TO_LOW, editState, adapter);
                return true;
            }
            if (itemId == R.id.action_receipt_filter_low_to_high) {
                applyArchivedReceiptItemsFilter(RECEIPT_FILTER_LOW_TO_HIGH, editState, adapter);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void applyArchivedReceiptItemsFilter(
            int filterMode,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull ArchivedReceiptItemsAdapter adapter
    ) {
        editState.filterMode = filterMode;
        rebuildArchivedReceiptVisibleItems(editState);
        adapter.notifyDataSetChanged();
    }

    private void rebuildArchivedReceiptVisibleItems(@NonNull ArchivedReceiptEditState editState) {
        editState.items.clear();
        editState.items.addAll(editState.allItems);

        if (editState.filterMode == RECEIPT_FILTER_HIGH_TO_LOW) {
            editState.items.sort((first, second) -> parseCurrencyAmount(second.price)
                    .compareTo(parseCurrencyAmount(first.price)));
            return;
        }

        if (editState.filterMode == RECEIPT_FILTER_LOW_TO_HIGH) {
            editState.items.sort(Comparator.comparing(this::getArchivedReceiptItemAmount));
        }
    }

    @NonNull
    private BigDecimal getArchivedReceiptItemAmount(
            @NonNull ReceiptHistoryStore.HistoryItem item
    ) {
        return parseCurrencyAmount(item.price);
    }

    private void showAddArchivedReceiptItemDialog(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_receipt_item, null);
        TextInputLayout nameInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_item_name);
        TextInputLayout priceInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_item_price);
        TextInputEditText nameInputView =
                dialogView.findViewById(R.id.edit_receipt_item_name);
        TextInputEditText priceInputView =
                dialogView.findViewById(R.id.edit_receipt_item_price);

        priceInputView.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_new_item_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add, null)
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String itemName = getText(nameInputView);
                    String enteredPrice = getText(priceInputView);

                    nameInputLayout.setError(null);
                    priceInputLayout.setError(null);

                    boolean hasError = false;
                    if (itemName.isEmpty()) {
                        nameInputLayout.setError(getString(R.string.receipt_item_name_required));
                        hasError = true;
                    }

                    Integer amountCents = receiptParser.parseEnteredPriceToCents(enteredPrice);
                    if (amountCents == null) {
                        priceInputLayout.setError(getString(R.string.invalid_receipt_price));
                        hasError = true;
                    }

                    if (hasError || amountCents == null) {
                        return;
                    }

                    addArchivedReceiptItem(editState, itemName, amountCents);
                    refreshReceiptDetails.run();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void addArchivedReceiptItem(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull String itemName,
            int amountCents
    ) {
        ArrayList<String> selectedParticipantKeys = new ArrayList<>();
        for (ReceiptHistoryStore.ParticipantShare participant : editState.participants) {
            selectedParticipantKeys.add(participant.key);
        }

        ReceiptHistoryStore.HistoryItem item = new ReceiptHistoryStore.HistoryItem(
                itemName,
                receiptParser.formatAmount(amountCents),
                selectedParticipantKeys
        );
        editState.allItems.add(item);
        rebuildArchivedReceiptVisibleItems(editState);
    }

    private void bindArchivedReceiptParticipantButtons(
            @NonNull LinearLayout participantsLayout,
            @NonNull String receiptTotalAmount,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        participantsLayout.removeAllViews();
        List<ReceiptHistoryStore.ParticipantShare> participants = editState.participants;

        if (participants.isEmpty()) {
            participantsLayout.setVisibility(View.GONE);
            return;
        }

        participantsLayout.setVisibility(View.VISIBLE);
        for (int index = 0; index < participants.size(); index++) {
            ReceiptHistoryStore.ParticipantShare participant = participants.get(index);
            View rowView = getLayoutInflater().inflate(
                    R.layout.item_receipt_summary_participant,
                    participantsLayout,
                    false
            );
            MaterialButton badgeButton = rowView.findViewById(R.id.button_summary_participant_badge);
            AppCompatImageView paymentStatusView =
                    rowView.findViewById(R.id.image_summary_participant_payment_status);
            AppCompatImageView ownerIconView =
                    rowView.findViewById(R.id.image_summary_participant_owner);
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);
            MaterialButton payNowButton =
                    rowView.findViewById(R.id.button_summary_participant_pay_now);
            View dividerView = rowView.findViewById(R.id.view_summary_participant_divider);

            configureArchivedReceiptSummaryParticipantBadgeButton(badgeButton, participant);
            paymentStatusView.setVisibility(View.GONE);
            ownerIconView.setVisibility(
                    isCrownedParticipant(participant, editState) ? View.VISIBLE : View.GONE
            );
            nameView.setText(participant.name);
            amountView.setText(buildArchivedReceiptParticipantTotalDisplayText(
                    computeArchivedReceiptParticipantShareTotal(participant, editState),
                    receiptTotalAmount
            ));
            payNowButton.setVisibility(View.GONE);
            dividerView.setVisibility(index == participants.size() - 1 ? View.GONE : View.VISIBLE);

            View.OnClickListener openDetailsListener =
                    view -> showArchivedReceiptParticipantDetailsDialog(
                            participant,
                            receiptTotalAmount,
                            editState,
                            refreshReceiptDetails
                    );
            rowView.setOnClickListener(openDetailsListener);
            badgeButton.setOnClickListener(openDetailsListener);
            rowView.setOnTouchListener(
                    createArchivedReceiptParticipantLongPressTouchListener(
                            rowView,
                            participant,
                            editState,
                            refreshReceiptDetails
                    )
            );
            badgeButton.setOnTouchListener(
                    createArchivedReceiptParticipantLongPressTouchListener(
                            badgeButton,
                            participant,
                            editState,
                            refreshReceiptDetails
                    )
            );
            participantsLayout.addView(rowView);
        }
    }

    private void configureArchivedReceiptSummaryParticipantBadgeButton(
            @NonNull MaterialButton badgeButton,
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        int buttonSize = dpToPx(52);
        ViewGroup.LayoutParams layoutParams = badgeButton.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.width = buttonSize;
            layoutParams.height = buttonSize;
            badgeButton.setLayoutParams(layoutParams);
        }
        badgeButton.setCheckable(false);
        badgeButton.setClickable(false);
        badgeButton.setFocusable(false);
        badgeButton.setInsetTop(0);
        badgeButton.setInsetBottom(0);
        badgeButton.setMinWidth(0);
        badgeButton.setMinHeight(0);
        badgeButton.setMinimumWidth(0);
        badgeButton.setMinimumHeight(0);
        badgeButton.setPadding(0, 0, 0, 0);
        badgeButton.setCornerRadius(buttonSize / 2);
        applyArchivedReceiptParticipantBadgeTextStyle(badgeButton, participant, false);
        badgeButton.setStrokeWidth(0);
        badgeButton.setBackgroundTintList(ColorStateList.valueOf(participant.color));
        badgeButton.setTextColor(getParticipantTextColor(participant.color));
        badgeButton.setContentDescription(participant.name);
    }

    @NonNull
    private View.OnTouchListener createArchivedReceiptParticipantLongPressTouchListener(
            @NonNull View anchorView,
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        return new View.OnTouchListener() {
            private final int touchSlop = ViewConfiguration
                    .get(ArchiveActivity.this)
                    .getScaledTouchSlop();
            private float downX;
            private float downY;
            private float downRawX;
            private float downRawY;
            private boolean longPressTriggered;
            private final Runnable longPressRunnable = () -> {
                longPressTriggered = true;
                vibrateForArchiveLongPress();
                showArchivedReceiptParticipantActionsMenu(
                        anchorView,
                        downRawX,
                        downRawY,
                        participant,
                        editState,
                        refreshReceiptDetails
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
                                ARCHIVE_ENTRY_LONG_PRESS_DURATION_MS
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
        };
    }

    private void showArchivedReceiptParticipantActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        ArrayList<AnchoredDropdownMenuHelper.ActionItem> actions = new ArrayList<>();
        actions.add(new AnchoredDropdownMenuHelper.ActionItem(
                R.string.assign_payer,
                R.drawable.ic_receipt_owner_crown,
                () -> {
                    editState.crownedParticipantKey = participant.key;
                    refreshReceiptDetails.run();
                },
                !isCrownedParticipant(participant, editState)
        ));
        actions.add(new AnchoredDropdownMenuHelper.ActionItem(
                R.string.remove,
                R.drawable.ic_receipt_participant_remove,
                () -> removeArchivedReceiptParticipant(participant, editState, refreshReceiptDetails),
                !isDefaultParticipant(participant)
        ));
        AnchoredDropdownMenuHelper.showActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                actions
        );
    }

    private void removeArchivedReceiptParticipant(
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        if (isCrownedParticipant(participant, editState)) {
            editState.crownedParticipantKey = DEFAULT_PARTICIPANT_KEY;
        }
        editState.participants.remove(participant);
        for (ReceiptHistoryStore.HistoryItem item : editState.allItems) {
            item.selectedParticipantKeys.remove(participant.key);
        }
        refreshReceiptDetails.run();
    }

    private final class ArchivedReceiptItemsAdapter
            extends ArrayAdapter<ReceiptHistoryStore.HistoryItem> {
        @NonNull
        private final ArchivedReceiptEditState editState;
        @NonNull
        private final Runnable[] refreshReceiptDetailsHolder;

        private ArchivedReceiptItemsAdapter(
                @NonNull ArchivedReceiptEditState editState,
                @NonNull Runnable[] refreshReceiptDetailsHolder
        ) {
            super(ArchiveActivity.this, R.layout.item_receipt_line, editState.items);
            this.editState = editState;
            this.refreshReceiptDetailsHolder = refreshReceiptDetailsHolder;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_receipt_line, parent, false);
            }

            ReceiptHistoryStore.HistoryItem item = getItem(position);
            TextView itemNameView = itemView.findViewById(R.id.text_receipt_item_name);
            TextView itemPriceView = itemView.findViewById(R.id.text_receipt_item_price);
            LinearLayout participantSelectionLayout =
                    itemView.findViewById(R.id.layout_receipt_item_participants);

            if (item != null) {
                itemNameView.setText(item.name);
                itemPriceView.setText(item.price);
                bindArchivedReceiptItemParticipantButtons(
                        participantSelectionLayout,
                        item,
                        editState.participants,
                        editState,
                        refreshReceiptDetailsHolder[0]
                );
                View receiptItemView = itemView;
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnTouchListener(new View.OnTouchListener() {
                    private final int touchSlop = ViewConfiguration
                            .get(ArchiveActivity.this)
                            .getScaledTouchSlop();
                    private float downX;
                    private float downY;
                    private float downRawX;
                    private float downRawY;
                    private boolean longPressTriggered;
                    private final Runnable longPressRunnable = () -> {
                        longPressTriggered = true;
                        vibrateForArchiveLongPress();
                        showArchivedReceiptItemActionsMenu(
                                receiptItemView,
                                downRawX,
                                downRawY,
                                item,
                                editState,
                                refreshReceiptDetailsHolder[0]
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
                                        ARCHIVE_ENTRY_LONG_PRESS_DURATION_MS
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
                itemNameView.setText("");
                itemPriceView.setText("");
                participantSelectionLayout.removeAllViews();
                participantSelectionLayout.setVisibility(View.GONE);
                itemView.setOnTouchListener(null);
                itemView.setClickable(false);
                itemView.setFocusable(false);
            }

            itemView.setBackgroundColor(Color.TRANSPARENT);
            return itemView;
        }
    }

    private void showArchivedReceiptItemActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        AnchoredDropdownMenuHelper.showSingleActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                R.string.remove,
                R.drawable.ic_history_remove,
                () -> removeArchivedReceiptItem(item, editState, refreshReceiptDetails)
        );
    }

    private void removeArchivedReceiptItem(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        editState.allItems.remove(item);
        editState.items.remove(item);
        refreshReceiptDetails.run();
    }

    private void bindArchivedReceiptItemParticipantButtons(
            @NonNull LinearLayout participantSelectionLayout,
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
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
            applyArchivedReceiptParticipantBadgeTextStyle(selectionButton, participant, true);
            selectionButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            selectionButton.setFocusable(false);
            selectionButton.setFocusableInTouchMode(false);
            selectionButton.setCheckable(false);
            selectionButton.setContentDescription(participant.name);

            updateArchivedReceiptParticipantSelectionButtonStyle(
                    selectionButton,
                    participant,
                    item.isParticipantSelected(participant.key)
            );
            selectionButton.setOnClickListener(view -> {
                toggleArchivedReceiptParticipantSelection(item, participant.key);
                updateArchivedReceiptParticipantSelectionButtonStyle(
                        selectionButton,
                        participant,
                        item.isParticipantSelected(participant.key)
                );
                refreshReceiptDetails.run();
            });

            if (currentRow != null) {
                currentRow.addView(selectionButton);
            }
        }
    }

    private void showArchivedReceiptParticipantDetailsDialog(
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull String receiptTotalAmount,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
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
                buildArchivedReceiptParticipantTotalDisplayText(
                        computeArchivedReceiptParticipantShareTotal(participant, editState),
                        receiptTotalAmount
                )
        );
        crownToggleButton.setVisibility(View.VISIBLE);
        crownToggleButton.setClickable(true);
        crownToggleButton.setFocusable(true);
        updateArchivedReceiptParticipantCrownButton(crownToggleButton, participant, editState);
        crownToggleButton.setOnClickListener(view -> {
            if (isCrownedParticipant(participant, editState)) {
                return;
            }

            editState.crownedParticipantKey = participant.key;
            updateArchivedReceiptParticipantCrownButton(crownToggleButton, participant, editState);
            participantTotalView.setText(
                    buildArchivedReceiptParticipantTotalDisplayText(
                            computeArchivedReceiptParticipantShareTotal(participant, editState),
                            receiptTotalAmount
                    )
            );
            refreshReceiptDetails.run();
        });
        removeParticipantButton.setVisibility(View.GONE);
        toggleParticipantItemsButton.setVisibility(View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        dialog.show();
    }

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openArchivedReceiptAddParticipantDialog(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        if (hasContactsPermission()) {
            showArchivedReceiptAddParticipantDialog(editState, refreshReceiptDetails, true);
        } else {
            pendingAddParticipantEditState = editState;
            pendingAddParticipantRefreshRunnable = refreshReceiptDetails;
            showAddParticipantDialogAfterContactsPermission = true;
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void showArchivedReceiptAddParticipantDialog(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails,
            boolean contactsPermissionGranted
    ) {
        pendingAddParticipantEditState = editState;
        pendingAddParticipantRefreshRunnable = refreshReceiptDetails;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_participant, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.layout_participant_name);
        TextInputLayout phoneLayout = dialogView.findViewById(R.id.layout_participant_phone);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_participant_name);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.input_participant_phone);
        ListView phoneContactsList = dialogView.findViewById(R.id.list_phone_contacts);
        TextView emptyContactsView = dialogView.findViewById(R.id.text_phone_contacts_empty);
        View closeButton = dialogView.findViewById(R.id.button_close_add_participant);
        MaterialButton addParticipantButton =
                dialogView.findViewById(R.id.button_add_participant_confirm);

        ArrayList<PhoneContactsListItem> phoneContactRows = new ArrayList<>();
        ArrayList<PhoneContact> allPhoneContacts = new ArrayList<>();
        boolean[] contactsLoading = new boolean[]{contactsPermissionGranted};
        PhoneContactsAdapter phoneContactsAdapter = new PhoneContactsAdapter(phoneContactRows);
        Runnable refreshSearchUi = () -> updateAddParticipantSearchUi(
                nameLayout,
                phoneLayout,
                nameInput,
                phoneContactsAdapter,
                allPhoneContacts,
                emptyContactsView,
                contactsPermissionGranted,
                contactsLoading[0]
        );
        phoneContactsAdapter.setOnFavoritesChanged(refreshSearchUi);
        phoneContactsAdapter.setOnContactClicked(selectedContact -> {
            nameInput.setText(selectedContact.name);
            phoneInput.setText(selectedContact.phoneNumber);
            nameLayout.setError(null);
            phoneLayout.setError(null);
            if (nameInput.isFocused()) {
                hideKeyboardAndClearFocus(nameInput, dialogView);
            }
            dialogView.post(refreshSearchUi);
        });
        phoneContactsList.setAdapter(phoneContactsAdapter);
        phoneContactsList.setEmptyView(emptyContactsView);
        addParticipantButton.setEnabled(false);

        TextWatcher validationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateAddParticipantButtonState(
                        nameLayout,
                        phoneLayout,
                        nameInput,
                        phoneInput,
                        addParticipantButton
                );
                updateAddParticipantSearchUi(
                        nameLayout,
                        phoneLayout,
                        nameInput,
                        phoneContactsAdapter,
                        allPhoneContacts,
                        emptyContactsView,
                        contactsPermissionGranted,
                        contactsLoading[0]
                );
            }
        };
        nameInput.addTextChangedListener(validationWatcher);
        phoneInput.addTextChangedListener(validationWatcher);
        configureAddParticipantKeyboardBehavior(
                dialogView,
                nameLayout,
                nameInput,
                phoneInput,
                refreshSearchUi
        );
        updateAddParticipantButtonState(
                nameLayout,
                phoneLayout,
                nameInput,
                phoneInput,
                addParticipantButton
        );

        Dialog dialog = new Dialog(this, AppSettings.getFullScreenDialogThemeResId(this));
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        closeButton.setOnClickListener(view -> dialog.dismiss());

        addParticipantButton.setOnClickListener(view -> {
            String name = getText(nameInput);
            String phoneNumber = getText(phoneInput);

            nameLayout.setError(null);
            phoneLayout.setError(null);

            boolean hasError = false;
            if (name.isEmpty()) {
                nameLayout.setError(getString(R.string.contact_name_required));
                hasError = true;
            }
            if (phoneNumber.isEmpty()) {
                phoneLayout.setError(getString(R.string.contact_phone_required));
                hasError = true;
            } else if (!isValidPhoneNumber(phoneNumber)) {
                phoneLayout.setError(getString(R.string.contact_phone_invalid));
                hasError = true;
            }
            if (hasError) {
                return;
            }

            if (isArchivedReceiptParticipantAlreadyAdded(editState, name, phoneNumber)) {
                Toast.makeText(this, R.string.participant_already_added, Toast.LENGTH_SHORT).show();
                return;
            }

            addArchivedReceiptParticipant(editState, name, phoneNumber);
            refreshReceiptDetails.run();
            Toast.makeText(this, R.string.participant_added, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
        installAddParticipantKeyboardDismissWatcher(
                dialog,
                dialogView,
                nameInput,
                phoneInput,
                refreshSearchUi
        );

        if (!contactsPermissionGranted) {
            refreshSearchUi.run();
            return;
        }

        refreshSearchUi.run();
        if (backgroundExecutor == null) {
            return;
        }
        backgroundExecutor.execute(() -> {
            ArrayList<PhoneContact> availableContacts = loadPhoneContactsFromDevice();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                    return;
                }

                allPhoneContacts.clear();
                allPhoneContacts.addAll(availableContacts);
                contactsLoading[0] = false;
                refreshSearchUi.run();
            });
        });
    }

    @NonNull
    private CharSequence buildArchivedReceiptParticipantTotalDisplayText(
            @NonNull BigDecimal participantAmount,
            @Nullable String receiptTotalAmount
    ) {
        String amountText = formatCurrency(participantAmount) + "kr";

        BigDecimal receiptTotal = parseCurrencyAmount(receiptTotalAmount);
        String percentageText =
                " (" + formatParticipantSharePercentage(participantAmount, receiptTotal) + "%)";
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

    private void addArchivedReceiptParticipant(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull String name,
            @NonNull String phoneNumber
    ) {
        String participantKey = buildParticipantKey(name, phoneNumber);
        ReceiptHistoryStore.ParticipantShare participant = new ReceiptHistoryStore.ParticipantShare(
                participantKey,
                normalizeWhitespace(name),
                deriveInitials(name),
                createParticipantColor(editState.participants.size()),
                normalizeWhitespace(phoneNumber),
                receiptParser.formatAmount(0),
                false,
                editState.allItems.isEmpty()
        );
        editState.participants.add(participant);
        for (ReceiptHistoryStore.HistoryItem item : editState.allItems) {
            if (!item.selectedParticipantKeys.contains(participantKey)) {
                item.selectedParticipantKeys.add(participantKey);
            }
        }
    }

    private boolean isArchivedReceiptParticipantAlreadyAdded(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull String name,
            @NonNull String phoneNumber
    ) {
        String normalizedName = normalizeWhitespace(name).toLowerCase(Locale.US);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        for (ReceiptHistoryStore.ParticipantShare participant : editState.participants) {
            boolean sameName =
                    normalizeWhitespace(participant.name).toLowerCase(Locale.US).equals(normalizedName);
            boolean samePhone = !normalizedPhoneNumber.isEmpty()
                    && normalizePhoneNumber(participant.phoneNumber).equals(normalizedPhoneNumber);
            if ((sameName && samePhone) || samePhone) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidPhoneNumber(@Nullable String phoneNumber) {
        String trimmedPhoneNumber = normalizeWhitespace(phoneNumber);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        return !trimmedPhoneNumber.isEmpty()
                && normalizedPhoneNumber.length() >= 6
                && Patterns.PHONE.matcher(trimmedPhoneNumber).matches();
    }

    private void updateAddParticipantSearchUi(
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputLayout phoneLayout,
            @NonNull TextInputEditText nameInput,
            @NonNull PhoneContactsAdapter phoneContactsAdapter,
            @NonNull List<PhoneContact> allPhoneContacts,
            @NonNull TextView emptyContactsView,
            boolean contactsPermissionGranted,
            boolean contactsLoading
    ) {
        boolean isSearching = nameInput.isFocused();
        updateParticipantNameSearchIconVisibility(nameLayout, nameInput);
        phoneLayout.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        updateVisiblePhoneContacts(
                phoneContactsAdapter,
                allPhoneContacts,
                getText(nameInput),
                emptyContactsView,
                contactsPermissionGranted,
                contactsLoading,
                !isSearching
        );
    }

    private void updateVisiblePhoneContacts(
            @NonNull PhoneContactsAdapter phoneContactsAdapter,
            @NonNull List<PhoneContact> allPhoneContacts,
            @NonNull String query,
            @NonNull TextView emptyContactsView,
            boolean contactsPermissionGranted,
            boolean contactsLoading,
            boolean showSectionHeaders
    ) {
        if (!contactsPermissionGranted) {
            phoneContactsAdapter.clear();
            phoneContactsAdapter.notifyDataSetChanged();
            emptyContactsView.setText(R.string.phone_contacts_permission_required);
            return;
        }

        if (contactsLoading) {
            phoneContactsAdapter.clear();
            phoneContactsAdapter.notifyDataSetChanged();
            emptyContactsView.setText(R.string.loading_phone_contacts);
            return;
        }

        ArrayList<PhoneContact> filteredContacts = new ArrayList<>();
        String normalizedQuery = normalizeWhitespace(query).toLowerCase(Locale.US);
        for (PhoneContact contact : allPhoneContacts) {
            if (normalizedQuery.isEmpty()
                    || contact.name.toLowerCase(Locale.US).contains(normalizedQuery)) {
                filteredContacts.add(contact);
            }
        }

        phoneContactsAdapter.clear();
        phoneContactsAdapter.addAll(buildPhoneContactRows(filteredContacts, showSectionHeaders));
        phoneContactsAdapter.notifyDataSetChanged();

        if (allPhoneContacts.isEmpty()) {
            emptyContactsView.setText(R.string.no_phone_contacts);
        } else if (filteredContacts.isEmpty()) {
            emptyContactsView.setText(R.string.no_matching_phone_contacts);
        } else {
            emptyContactsView.setText("");
        }
    }

    @NonNull
    private ArrayList<PhoneContactsListItem> buildPhoneContactRows(
            @NonNull List<PhoneContact> contacts,
            boolean includeSections
    ) {
        ArrayList<PhoneContactsListItem> rows = new ArrayList<>();
        ArrayList<PhoneContact> favoriteContacts = new ArrayList<>();
        ArrayList<PhoneContact> remainingContacts = new ArrayList<>();
        for (PhoneContact contact : contacts) {
            if (AppSettings.isFavoritePhoneContact(this, contact.name, contact.phoneNumber)) {
                favoriteContacts.add(contact);
            } else {
                remainingContacts.add(contact);
            }
        }

        if (!favoriteContacts.isEmpty()) {
            rows.add(PhoneContactsListItem.createSection(
                    getString(R.string.phone_contacts_favorites_title)
            ));
            for (PhoneContact contact : favoriteContacts) {
                rows.add(PhoneContactsListItem.createContact(contact));
            }
        }

        if (!includeSections) {
            for (PhoneContact contact : remainingContacts) {
                rows.add(PhoneContactsListItem.createContact(contact));
            }
            return rows;
        }

        String previousSectionLabel = "";
        for (PhoneContact contact : remainingContacts) {
            String sectionLabel = getPhoneContactSectionLabel(contact.name);
            if (!sectionLabel.equals(previousSectionLabel)) {
                rows.add(PhoneContactsListItem.createSection(sectionLabel));
                previousSectionLabel = sectionLabel;
            }
            rows.add(PhoneContactsListItem.createContact(contact));
        }
        return rows;
    }

    @NonNull
    private String getPhoneContactSectionLabel(@Nullable String contactName) {
        String normalizedName = normalizeWhitespace(contactName);
        if (normalizedName.isEmpty()) {
            return "#";
        }

        String firstCharacter = normalizedName.substring(0, 1).toUpperCase(Locale.getDefault());
        char firstChar = firstCharacter.charAt(0);
        return Character.isLetter(firstChar) ? firstCharacter : "#";
    }

    private ArrayList<PhoneContact> loadPhoneContactsFromDevice() {
        ArrayList<PhoneContact> contacts = new ArrayList<>();
        if (!hasContactsPermission()) {
            return contacts;
        }

        Set<String> seenContacts = new HashSet<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )) {
            if (cursor == null) {
                return contacts;
            }

            int nameColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            );
            int phoneColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            );

            while (cursor.moveToNext()) {
                String name = nameColumn >= 0 ? normalizeWhitespace(cursor.getString(nameColumn)) : "";
                String phoneNumber =
                        phoneColumn >= 0 ? normalizeWhitespace(cursor.getString(phoneColumn)) : "";
                if (name.isEmpty() || phoneNumber.isEmpty()) {
                    continue;
                }

                String dedupeKey = name.toLowerCase(Locale.US)
                        + "\u001F"
                        + phoneNumber.replaceAll("[^+\\d]", "");
                if (seenContacts.add(dedupeKey)) {
                    contacts.add(new PhoneContact(name, phoneNumber));
                }
            }
        }

        contacts.sort(Comparator
                .comparing((PhoneContact contact) -> contact.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(contact -> contact.phoneNumber, String.CASE_INSENSITIVE_ORDER));
        return contacts;
    }

    private void updateAddParticipantButtonState(
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputLayout phoneLayout,
            @NonNull TextInputEditText nameInput,
            @NonNull TextInputEditText phoneInput,
            @NonNull MaterialButton addParticipantButton
    ) {
        String name = getText(nameInput);
        String phoneNumber = getText(phoneInput);
        boolean phoneNumberValid = isValidPhoneNumber(phoneNumber);

        addParticipantButton.setEnabled(!name.isEmpty() && phoneNumberValid);
        nameLayout.setError(null);
        if (phoneNumber.isEmpty() || phoneNumberValid) {
            phoneLayout.setError(null);
        } else {
            phoneLayout.setError(getString(R.string.contact_phone_invalid));
        }
    }

    private void configureAddParticipantKeyboardBehavior(
            @NonNull View dialogView,
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputEditText nameInput,
            @NonNull TextInputEditText phoneInput,
            @NonNull Runnable updateSearchUi
    ) {
        updateParticipantNameSearchIconVisibility(nameLayout, nameInput);
        View.OnFocusChangeListener focusChangeListener = (view, hasFocus) -> {
            updateSearchUi.run();
            if (hasFocus) {
                return;
            }

            dialogView.post(() -> {
                updateSearchUi.run();
                if (!nameInput.isFocused() && !phoneInput.isFocused()) {
                    hideKeyboardAndClearFocus((TextInputEditText) view, dialogView);
                }
            });
        };

        nameInput.setOnFocusChangeListener(focusChangeListener);
        phoneInput.setOnFocusChangeListener(focusChangeListener);

        TextView.OnEditorActionListener editorActionListener = (textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                hideKeyboardAndClearFocus((TextInputEditText) textView, dialogView);
                dialogView.post(updateSearchUi);
                return true;
            }
            return false;
        };

        nameInput.setOnEditorActionListener(editorActionListener);
        phoneInput.setOnEditorActionListener(editorActionListener);
    }

    private void updateParticipantNameSearchIconVisibility(
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputEditText nameInput
    ) {
        nameLayout.setEndIconVisible(!nameInput.isFocused());
    }

    private void installAddParticipantKeyboardDismissWatcher(
            @NonNull Dialog dialog,
            @NonNull View dialogView,
            @NonNull TextInputEditText nameInput,
            @NonNull TextInputEditText phoneInput,
            @NonNull Runnable updateSearchUi
    ) {
        Rect visibleFrame = new Rect();
        boolean[] wasKeyboardVisible = {false};
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
            dialogView.getWindowVisibleDisplayFrame(visibleFrame);
            int rootHeight = dialogView.getRootView().getHeight();
            int keyboardHeight = Math.max(0, rootHeight - visibleFrame.height());
            boolean isKeyboardVisible = keyboardHeight > dpToPx(120);

            if (wasKeyboardVisible[0] && !isKeyboardVisible) {
                TextInputEditText focusedInput = nameInput.isFocused()
                        ? nameInput
                        : phoneInput.isFocused() ? phoneInput : null;
                if (focusedInput != null) {
                    hideKeyboardAndClearFocus(focusedInput, dialogView);
                    updateSearchUi.run();
                }
            }

            wasKeyboardVisible[0] = isKeyboardVisible;
        };

        dialogView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        dialog.setOnDismissListener(dismissedDialog -> {
            ViewTreeObserver viewTreeObserver = dialogView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(layoutListener);
            }
        });
    }

    private void hideKeyboardAndClearFocus(
            @NonNull TextInputEditText inputView,
            @NonNull View fallbackView
    ) {
        clearTextInputFocus(inputView, fallbackView);
    }

    private void clearTextInputFocus(
            @Nullable TextInputEditText inputView,
            @Nullable View fallbackView
    ) {
        if (inputView == null) {
            return;
        }

        if (fallbackView != null) {
            fallbackView.requestFocus();
        }

        InputMethodManager inputMethodManager =
                ContextCompat.getSystemService(this, InputMethodManager.class);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
        }

        inputView.clearFocus();
    }

    private final class PhoneContactsAdapter extends ArrayAdapter<PhoneContactsListItem> {
        private static final int VIEW_TYPE_SECTION = 0;
        private static final int VIEW_TYPE_CONTACT = 1;
        @Nullable
        private Runnable onFavoritesChanged;
        @Nullable
        private OnPhoneContactClickListener onContactClicked;

        PhoneContactsAdapter(ArrayList<PhoneContactsListItem> contacts) {
            super(ArchiveActivity.this, R.layout.item_phone_contact, contacts);
        }

        void setOnFavoritesChanged(@Nullable Runnable onFavoritesChanged) {
            this.onFavoritesChanged = onFavoritesChanged;
        }

        void setOnContactClicked(@Nullable OnPhoneContactClickListener onContactClicked) {
            this.onContactClicked = onContactClicked;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            PhoneContactsListItem item = getItem(position);
            return item != null && item.isSection() ? VIEW_TYPE_SECTION : VIEW_TYPE_CONTACT;
        }

        @Override
        public boolean isEnabled(int position) {
            PhoneContactsListItem item = getItem(position);
            return item != null && !item.isSection();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            PhoneContactsListItem item = getItem(position);
            if (item != null && item.isSection()) {
                View sectionView = convertView;
                if (sectionView == null || getItemViewType(position) != VIEW_TYPE_SECTION) {
                    sectionView = getLayoutInflater().inflate(
                            R.layout.item_phone_contact_section,
                            parent,
                            false
                    );
                }

                TextView sectionLabelView =
                        sectionView.findViewById(R.id.text_phone_contact_section);
                sectionLabelView.setText(item.sectionLabel);
                return sectionView;
            }

            View itemView = convertView;
            if (itemView == null || getItemViewType(position) != VIEW_TYPE_CONTACT) {
                itemView = getLayoutInflater().inflate(R.layout.item_phone_contact, parent, false);
            }

            PhoneContact contact = item == null ? null : item.contact;
            MaterialButton badgeButton = itemView.findViewById(R.id.button_phone_contact_badge);
            TextView nameView = itemView.findViewById(R.id.text_phone_contact_name);
            TextView phoneView = itemView.findViewById(R.id.text_phone_contact_number);
            AppCompatImageButton favoriteButton =
                    itemView.findViewById(R.id.button_phone_contact_favorite);

            if (contact != null) {
                configurePhoneContactBadgeButton(badgeButton, contact);
                nameView.setText(contact.name);
                phoneView.setText(contact.phoneNumber);
                configurePhoneContactFavoriteButton(favoriteButton, contact);
                itemView.setOnClickListener(view -> {
                    if (onContactClicked != null) {
                        onContactClicked.onPhoneContactClicked(contact);
                    }
                });
                favoriteButton.setOnClickListener(view -> {
                    toggleFavoritePhoneContact(contact);
                    if (onFavoritesChanged != null) {
                        onFavoritesChanged.run();
                    } else {
                        notifyDataSetChanged();
                    }
                });
            }

            return itemView;
        }
    }

    private void configurePhoneContactBadgeButton(
            @NonNull MaterialButton badgeButton,
            @NonNull PhoneContact contact
    ) {
        int buttonSize = dpToPx(52);
        ViewGroup.LayoutParams layoutParams = badgeButton.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.width = buttonSize;
            layoutParams.height = buttonSize;
            badgeButton.setLayoutParams(layoutParams);
        }
        int badgeColor = createStablePhoneContactColor(contact);
        badgeButton.setText(deriveInitials(contact.name));
        badgeButton.setAllCaps(false);
        badgeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        badgeButton.setCheckable(false);
        badgeButton.setClickable(false);
        badgeButton.setFocusable(false);
        badgeButton.setInsetTop(0);
        badgeButton.setInsetBottom(0);
        badgeButton.setMinWidth(0);
        badgeButton.setMinHeight(0);
        badgeButton.setMinimumWidth(0);
        badgeButton.setMinimumHeight(0);
        badgeButton.setPadding(0, 0, 0, 0);
        badgeButton.setCornerRadius(buttonSize / 2);
        badgeButton.setStrokeWidth(0);
        badgeButton.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        badgeButton.setTextColor(getParticipantTextColor(badgeColor));
        badgeButton.setContentDescription(contact.name);
    }

    private int createStablePhoneContactColor(@NonNull PhoneContact contact) {
        String contactKey = normalizeWhitespace(contact.name).toLowerCase(Locale.US)
                + "\u001F"
                + normalizePhoneNumber(contact.phoneNumber);
        int stableIndex = (contactKey.hashCode() & 0x7fffffff) % 1024;
        return createParticipantColor(stableIndex);
    }

    private void configurePhoneContactFavoriteButton(
            @NonNull AppCompatImageButton favoriteButton,
            @NonNull PhoneContact contact
    ) {
        boolean isFavorite = AppSettings.isFavoritePhoneContact(
                this,
                contact.name,
                contact.phoneNumber
        );
        favoriteButton.setImageResource(isFavorite ? R.drawable.star_true : R.drawable.star_false);
        favoriteButton.setContentDescription(
                getString(
                        isFavorite
                                ? R.string.remove_phone_contact_favorite
                                : R.string.add_phone_contact_favorite
                )
        );
    }

    private void toggleFavoritePhoneContact(@NonNull PhoneContact contact) {
        boolean isFavorite = AppSettings.isFavoritePhoneContact(
                this,
                contact.name,
                contact.phoneNumber
        );
        AppSettings.setFavoritePhoneContact(
                this,
                contact.name,
                contact.phoneNumber,
                !isFavorite
        );
    }

    private static final class PhoneContact {
        private final String name;
        private final String phoneNumber;

        private PhoneContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
    }

    private static final class PhoneContactsListItem {
        @Nullable
        private final String sectionLabel;
        @Nullable
        private final PhoneContact contact;

        private PhoneContactsListItem(@Nullable String sectionLabel, @Nullable PhoneContact contact) {
            this.sectionLabel = sectionLabel;
            this.contact = contact;
        }

        @NonNull
        private static PhoneContactsListItem createSection(@NonNull String sectionLabel) {
            return new PhoneContactsListItem(sectionLabel, null);
        }

        @NonNull
        private static PhoneContactsListItem createContact(@NonNull PhoneContact contact) {
            return new PhoneContactsListItem(null, contact);
        }

        private boolean isSection() {
            return sectionLabel != null;
        }
    }

    private interface OnPhoneContactClickListener {
        void onPhoneContactClicked(@NonNull PhoneContact contact);
    }

    @NonNull
    private BigDecimal computeArchivedReceiptParticipantShareTotal(
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull ArchivedReceiptEditState editState
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptHistoryStore.HistoryItem item : editState.allItems) {
            if (!item.isParticipantSelected(participant.key)) {
                continue;
            }

            int selectedParticipantCount =
                    countArchivedReceiptSelectedParticipants(item, editState.participants);
            if (selectedParticipantCount == 0) {
                continue;
            }

            BigDecimal itemAmount = parseCurrencyAmount(item.price);
            BigDecimal sharedAmount = itemAmount.divide(
                    BigDecimal.valueOf(selectedParticipantCount),
                    2,
                    RoundingMode.HALF_UP
            );
            total = total.add(sharedAmount);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void updateArchivedReceiptTotal(
            @NonNull TextView totalValueView,
            @NonNull ArchivedReceiptEditState editState
    ) {
        totalValueView.setText(
                getString(
                        R.string.receipt_total_format,
                        formatCurrency(computeArchivedReceiptItemsTotal(editState))
                )
        );
    }

    private void updateArchivedReceiptItemsEmptyState(
            @NonNull ListView itemsListView,
            @NonNull TextView itemsEmptyView,
            @NonNull ArchivedReceiptEditState editState
    ) {
        boolean hasItems = !editState.items.isEmpty();
        itemsListView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        itemsEmptyView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
    }

    private void updateArchivedReceiptItemsListHeight(@NonNull ListView itemsListView) {
        ListAdapter adapter = itemsListView.getAdapter();
        if (adapter == null) {
            return;
        }

        int width = itemsListView.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels - dpToPx(64);
        }

        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int totalHeight = 0;
        for (int index = 0; index < adapter.getCount(); index++) {
            View itemView = adapter.getView(index, null, itemsListView);
            itemView.measure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            totalHeight += itemView.getMeasuredHeight();
        }

        int dividerHeight = itemsListView.getDividerHeight();
        if (adapter.getCount() > 1 && dividerHeight > 0) {
            totalHeight += dividerHeight * (adapter.getCount() - 1);
        }

        ViewGroup.LayoutParams layoutParams = itemsListView.getLayoutParams();
        if (layoutParams.height != totalHeight) {
            layoutParams.height = totalHeight;
            itemsListView.setLayoutParams(layoutParams);
        }
    }

    private int countArchivedReceiptSelectedParticipants(
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

    @NonNull
    private String formatCurrency(@NonNull BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
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
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull ArchivedReceiptEditState editState
    ) {
        return participant.key.equals(editState.crownedParticipantKey);
    }

    @NonNull
    private ArchivedReceiptEditState createArchivedReceiptEditState(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        ArrayList<ReceiptHistoryStore.ParticipantShare> participants = new ArrayList<>();
        for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
            participants.add(new ReceiptHistoryStore.ParticipantShare(
                    participant.key,
                    participant.name,
                    participant.initials,
                    participant.color,
                    participant.phoneNumber,
                    participant.amount,
                    participant.isCrowned,
                    participant.hasPaid
            ));
        }

        ArrayList<ReceiptHistoryStore.HistoryItem> items = new ArrayList<>();
        for (ReceiptHistoryStore.HistoryItem item : entry.items) {
            items.add(new ReceiptHistoryStore.HistoryItem(
                    item.name,
                    item.price,
                    new ArrayList<>(item.selectedParticipantKeys)
            ));
        }

        return new ArchivedReceiptEditState(
                getArchivedReceiptOwnerKey(entry),
                participants,
                items
        );
    }

    @NonNull
    private String getArchivedReceiptOwnerKey(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
            if (participant.isCrowned) {
                return participant.key;
            }
        }

        for (ReceiptHistoryStore.ParticipantShare participant : entry.participants) {
            if (isDefaultParticipant(participant)) {
                return participant.key;
            }
        }

        if (!entry.participants.isEmpty()) {
            return entry.participants.get(0).key;
        }
        return "";
    }

    @NonNull
    private ReceiptHistoryStore.HistoryEntry buildArchivedReceiptEntry(
            @NonNull ReceiptHistoryStore.HistoryEntry originalEntry,
            @NonNull ArchivedReceiptEditState editState
    ) {
        ArrayList<ReceiptHistoryStore.ParticipantShare> updatedParticipants = new ArrayList<>();
        for (ReceiptHistoryStore.ParticipantShare participant : editState.participants) {
            updatedParticipants.add(new ReceiptHistoryStore.ParticipantShare(
                    participant.key,
                    participant.name,
                    participant.initials,
                    participant.color,
                    participant.phoneNumber,
                    formatCurrency(computeArchivedReceiptParticipantShareTotal(participant, editState)),
                    participant.key.equals(editState.crownedParticipantKey),
                    participant.hasPaid
            ));
        }

        ArrayList<ReceiptHistoryStore.HistoryItem> copiedItems = new ArrayList<>();
        for (ReceiptHistoryStore.HistoryItem item : editState.allItems) {
            copiedItems.add(new ReceiptHistoryStore.HistoryItem(
                    item.name,
                    item.price,
                    new ArrayList<>(item.selectedParticipantKeys)
            ));
        }

        return new ReceiptHistoryStore.HistoryEntry(
                originalEntry.receiptName,
                formatCurrency(computeArchivedReceiptItemsTotal(editState)),
                originalEntry.sentDate,
                originalEntry.message,
                updatedParticipants,
                copiedItems
        );
    }

    @NonNull
    private BigDecimal computeArchivedReceiptItemsTotal(
            @NonNull ArchivedReceiptEditState editState
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptHistoryStore.HistoryItem item : editState.allItems) {
            total = total.add(parseCurrencyAmount(item.price));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void updateArchivedReceiptParticipantCrownButton(
            @NonNull AppCompatImageButton crownButton,
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull ArchivedReceiptEditState editState
    ) {
        boolean isSelected = isCrownedParticipant(participant, editState);
        crownButton.setImageResource(isSelected ? R.drawable.crown_true : R.drawable.crown_false);
        crownButton.setContentDescription(
                getString(
                        isSelected
                                ? R.string.participant_crown_selected
                                : R.string.participant_crown_unselected
                )
        );
    }

    private void updateArchivedReceiptParticipantSelectionButtonStyle(
            @NonNull MaterialButton selectionButton,
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            boolean isChecked
    ) {
        int buttonColor = isChecked ? participant.color : UNCHECKED_PARTICIPANT_COLOR;
        selectionButton.setStrokeColor(ColorStateList.valueOf(buttonColor));
        selectionButton.setTextColor(buttonColor);
    }

    private void toggleArchivedReceiptParticipantSelection(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull String participantKey
    ) {
        if (item.isParticipantSelected(participantKey)) {
            item.selectedParticipantKeys.remove(participantKey);
        } else {
            item.selectedParticipantKeys.add(participantKey);
        }
    }

    private boolean hasArchivedReceiptSelections(@NonNull ArchivedReceiptEditState editState) {
        for (ReceiptHistoryStore.HistoryItem item : editState.allItems) {
            if (countArchivedReceiptSelectedParticipants(item, editState.participants) == 0) {
                return false;
            }
        }
        return true;
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

    private void applyArchivedReceiptParticipantBadgeTextStyle(
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

    @NonNull
    private String formatArchiveTotalAmount(@Nullable ArchiveStore.Archive archive) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (archive != null) {
            for (ReceiptHistoryStore.HistoryEntry receipt : archive.receipts) {
                totalAmount = totalAmount.add(parseCurrencyAmount(receipt.totalAmount));
            }
        }

        return totalAmount
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',') + "kr";
    }

    private void showArchiveEntryActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            int archiveIndex
    ) {
        AnchoredDropdownMenuHelper.showSingleActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                R.string.remove,
                R.drawable.ic_history_remove,
                () -> showRemoveArchiveDialog(archiveIndex)
        );
    }

    private void showRemoveArchiveDialog(int archiveIndex) {
        if (archiveIndex < 0 || archiveIndex >= archiveNames.size()) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_archive_remove_confirmation,
                null
        );
        MaterialButton noButton = dialogView.findViewById(R.id.button_archive_remove_no);
        MaterialButton yesButton = dialogView.findViewById(R.id.button_archive_remove_yes);

        AlertDialog confirmationDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        noButton.setOnClickListener(view -> confirmationDialog.dismiss());
        yesButton.setOnClickListener(view -> {
            confirmationDialog.dismiss();
            ArchiveStore.removeArchiveAt(this, archiveIndex);
            loadArchiveNames();
        });

        confirmationDialog.show();
    }

    private void showArchiveReceiptActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            int archiveIndex,
            int receiptIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ArchiveReceiptEntriesAdapter adapter
    ) {
        AnchoredDropdownMenuHelper.showActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                Arrays.asList(
                        new AnchoredDropdownMenuHelper.ActionItem(
                                R.string.remove,
                                R.drawable.ic_history_remove,
                                () -> removeArchiveReceipt(
                                        archiveIndex,
                                        receiptIndex,
                                        archiveReceipts,
                                        adapter
                                )
                        ),
                        new AnchoredDropdownMenuHelper.ActionItem(
                                R.string.move,
                                R.drawable.ic_archive_move,
                                () -> showMoveArchiveReceiptDialog(
                                        archiveIndex,
                                        receiptIndex,
                                        archiveReceipts,
                                        adapter
                                )
                        )
                )
        );
    }

    private void removeArchiveReceipt(
            int archiveIndex,
            int receiptIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ArchiveReceiptEntriesAdapter adapter
    ) {
        if (receiptIndex < 0 || receiptIndex >= archiveReceipts.size()) {
            return;
        }

        ArchiveStore.removeReceiptAt(this, archiveIndex, receiptIndex);
        archiveReceipts.remove(receiptIndex);
        loadArchiveNames();
        adapter.notifyDataSetChanged();
    }

    private void showMoveArchiveReceiptDialog(
            int sourceArchiveIndex,
            int receiptIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ArchiveReceiptEntriesAdapter adapter
    ) {
        if (receiptIndex < 0 || receiptIndex >= archiveReceipts.size()) {
            return;
        }

        ArrayList<ArchiveStore.Archive> allArchives = ArchiveStore.loadArchives(this);
        ArrayList<String> destinationArchiveNames = new ArrayList<>();
        ArrayList<Integer> destinationArchiveIndexes = new ArrayList<>();
        final int[] currentSourceArchiveIndex = {sourceArchiveIndex};

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_select_archive, null);
        View headerView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_header,
                null
        );
        AppCompatImageButton addArchiveButton =
                headerView.findViewById(R.id.button_select_archive_add);
        ListView archivesListView = dialogView.findViewById(R.id.list_select_archive);
        TextView emptyView = dialogView.findViewById(R.id.text_select_archive_empty);
        MaterialButton moveButton = dialogView.findViewById(R.id.button_add_selected_archive);
        ArrayAdapter<String> archivesAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_single_choice,
                destinationArchiveNames
        );
        archivesListView.setAdapter(archivesAdapter);
        archivesListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        moveButton.setText(R.string.move);

        final int[] selectedDestinationIndex = {-1};
        Runnable refreshDestinations = () -> {
            destinationArchiveNames.clear();
            destinationArchiveIndexes.clear();

            ArrayList<ArchiveStore.Archive> refreshedArchives = ArchiveStore.loadArchives(this);
            for (int index = 0; index < refreshedArchives.size(); index++) {
                if (index == currentSourceArchiveIndex[0]) {
                    continue;
                }
                destinationArchiveNames.add(refreshedArchives.get(index).name);
                destinationArchiveIndexes.add(index);
            }

            archivesAdapter.notifyDataSetChanged();
            archivesListView.clearChoices();
            selectedDestinationIndex[0] = -1;
            moveButton.setEnabled(false);

            if (destinationArchiveNames.isEmpty()) {
                archivesListView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                emptyView.setText(R.string.move_archive_empty);
            } else {
                archivesListView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        };
        refreshDestinations.run();

        archivesListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedDestinationIndex[0] = position;
            moveButton.setEnabled(true);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(headerView)
                .setView(dialogView)
                .create();

        addArchiveButton.setOnClickListener(view -> showNewArchiveDialog(() -> {
            currentSourceArchiveIndex[0] += 1;
            refreshDestinations.run();
            if (!destinationArchiveNames.isEmpty()) {
                selectedDestinationIndex[0] = 0;
                archivesListView.setItemChecked(0, true);
                moveButton.setEnabled(true);
            }
        }));

        moveButton.setEnabled(false);
        moveButton.setOnClickListener(view -> {
            if (selectedDestinationIndex[0] < 0) {
                moveButton.setEnabled(false);
                return;
            }

            ArchiveStore.moveReceiptToArchive(
                    this,
                    currentSourceArchiveIndex[0],
                    receiptIndex,
                    destinationArchiveIndexes.get(selectedDestinationIndex[0])
            );
            archiveReceipts.remove(receiptIndex);
            loadArchiveNames();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
            Toast.makeText(this, R.string.receipt_moved_to_archive, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void vibrateForArchiveLongPress() {
        android.os.Vibrator vibrator = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.os.VibratorManager vibratorManager =
                    getSystemService(android.os.VibratorManager.class);
            if (vibratorManager != null) {
                vibrator = vibratorManager.getDefaultVibrator();
            }
        } else {
            vibrator = getSystemService(android.os.Vibrator.class);
        }

        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                            ARCHIVE_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
            );
        } else {
            vibrator.vibrate(ARCHIVE_ENTRY_LONG_PRESS_VIBRATION_DURATION_MS);
        }
    }

    private final class ArchiveEntriesAdapter extends ArrayAdapter<String> {
        private ArchiveEntriesAdapter() {
            super(ArchiveActivity.this, R.layout.item_archive_entry, archiveNames);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_archive_entry, parent, false);
            }

            TextView archiveNameView = itemView.findViewById(R.id.text_archive_name);
            TextView archiveTotalView = itemView.findViewById(R.id.text_archive_total);
            String archiveName = getItem(position);
            ArchiveStore.Archive archive = ArchiveStore.loadArchiveAt(ArchiveActivity.this, position);
            archiveNameView.setText(archiveName == null ? "" : archiveName);
            archiveTotalView.setText(formatArchiveTotalAmount(archive));

            final View archiveItemView = itemView;
            final int archiveIndex = position;
            itemView.setClickable(true);
            itemView.setFocusable(true);
            itemView.setOnClickListener(view -> showArchiveDetailsDialog(archiveIndex));
            itemView.setOnTouchListener(new View.OnTouchListener() {
                private final int touchSlop = ViewConfiguration
                        .get(ArchiveActivity.this)
                        .getScaledTouchSlop();
                private float downX;
                private float downY;
                private float downRawX;
                private float downRawY;
                private boolean longPressTriggered;
                private final Runnable longPressRunnable = () -> {
                    longPressTriggered = true;
                    vibrateForArchiveLongPress();
                    showArchiveEntryActionsMenu(
                            archiveItemView,
                            downRawX,
                            downRawY,
                            archiveIndex
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
                                    ARCHIVE_ENTRY_LONG_PRESS_DURATION_MS
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
            return itemView;
        }
    }

    private final class ArchiveReceiptEntriesAdapter
            extends ArrayAdapter<ReceiptHistoryStore.HistoryEntry> {
        private final int archiveIndex;
        private final ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts;

        private ArchiveReceiptEntriesAdapter(
                int archiveIndex,
                @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts
        ) {
            super(ArchiveActivity.this, R.layout.item_history_receipt, archiveReceipts);
            this.archiveIndex = archiveIndex;
            this.archiveReceipts = archiveReceipts;
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
            View statusIconView = itemView.findViewById(R.id.image_history_receipt_status);

            if (statusIconView != null) {
                statusIconView.setVisibility(View.GONE);
            }

            if (entry != null) {
                receiptNameView.setText(entry.receiptName);
                totalAmountView.setText(entry.totalAmount);
                sentDateView.setText(entry.sentDate);
            } else {
                receiptNameView.setText("");
                totalAmountView.setText("");
                sentDateView.setText("");
            }

            if (entry != null) {
                View receiptItemView = itemView;
                final int receiptIndex = position;
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnClickListener(view -> {
                    if (receiptIndex < 0 || receiptIndex >= archiveReceipts.size()) {
                        return;
                    }

                    showArchivedReceiptDetailsDialog(
                            archiveIndex,
                            receiptIndex,
                            archiveReceipts,
                            archiveReceipts.get(receiptIndex),
                            this::notifyDataSetChanged
                    );
                });
                itemView.setOnTouchListener(new View.OnTouchListener() {
                    private final int touchSlop = ViewConfiguration
                            .get(ArchiveActivity.this)
                            .getScaledTouchSlop();
                    private float downX;
                    private float downY;
                    private float downRawX;
                    private float downRawY;
                    private boolean longPressTriggered;
                    private final Runnable longPressRunnable = () -> {
                        longPressTriggered = true;
                        vibrateForArchiveLongPress();
                        showArchiveReceiptActionsMenu(
                                receiptItemView,
                                downRawX,
                                downRawY,
                                archiveIndex,
                                receiptIndex,
                                archiveReceipts,
                                ArchiveReceiptEntriesAdapter.this
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
                                        ARCHIVE_ENTRY_LONG_PRESS_DURATION_MS
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
                itemView.setClickable(false);
                itemView.setFocusable(false);
                itemView.setOnClickListener(null);
                itemView.setOnTouchListener(null);
            }

            return itemView;
        }
    }
}
