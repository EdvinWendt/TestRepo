package com.example.testrepo;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputFilter;
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
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final int MIN_RECEIPT_ITEM_QUANTITY = 1;
    private static final int RECEIPT_FILTER_DEFAULT = 0;
    private static final int RECEIPT_FILTER_HIGH_TO_LOW = 1;
    private static final int RECEIPT_FILTER_LOW_TO_HIGH = 2;
    private static final long MENU_ARROW_ROTATION_DURATION_MS = 180L;
    private static final long ARCHIVE_TREE_TOGGLE_DURATION_MS = 100L;
    private static final float ARCHIVE_TREE_EXPANDED_ROTATION_DEGREES = 90f;
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";
    private static final String DEFAULT_PARTICIPANT_NAME = "You";
    private static final String PAYMENT_LINK_BASE_URL = "https://edvinwendt.github.io/TestRepo/";
    @NonNull
    private String appliedThemeConfigurationKey = "";

    private final ArrayList<String> archiveNames = new ArrayList<>();
    private final ArrayList<ArchiveStore.Archive> archives = new ArrayList<>();
    private final ArrayList<ReceiptHistoryStore.HistoryEntry> standaloneReceipts = new ArrayList<>();
    private final ArrayList<ArchiveRootItem> archiveRootItems = new ArrayList<>();
    private final HashSet<String> expandedArchiveNames = new HashSet<>();
    private final ReceiptParser receiptParser = new ReceiptParser();
    private ArchiveEntriesAdapter archiveEntriesAdapter;
    @Nullable
    private ArchivedReceiptEditState pendingAddParticipantEditState;
    @Nullable
    private Runnable pendingAddParticipantRefreshRunnable;
    @Nullable
    private PopupWindow archivedReceiptSaveChangesDisabledReasonsPopup;
    @Nullable
    private PopupWindow newArchiveCreateDisabledReasonsPopup;
    @Nullable
    private PopupWindow archiveReceiptIncompletePopup;
    @Nullable
    private PopupWindow archivedReceiptItemPayerPopup;
    @Nullable
    private PopupWindow sendRequestsNoInternetPopup;
    @Nullable
    private ArchivedReceiptEditState pendingScanMoreEditState;
    @Nullable
    private Runnable pendingScanMoreRefreshRunnable;
    @Nullable
    private Runnable pendingSendRequestsAction;
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
    private final ActivityResultLauncher<String> requestSendSmsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                Runnable sendRequestsAction = pendingSendRequestsAction;
                pendingSendRequestsAction = null;
                if (isGranted && sendRequestsAction != null) {
                    sendRequestsAction.run();
                } else if (!isGranted) {
                    Toast.makeText(
                            this,
                            R.string.send_requests_permission_required,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
    private final ActivityResultLauncher<Intent> scanMoreReceiptItemsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handleScanMoreReceiptItemsResult
            );

    private static final class ArchivedReceiptEditState {
        @NonNull
        private String receiptName;
        @NonNull
        private String crownedParticipantKey;
        @NonNull
        private final ArrayList<ReceiptHistoryStore.ParticipantShare> participants;
        @NonNull
        private final ArrayList<ReceiptHistoryStore.HistoryItem> allItems;
        @NonNull
        private final ArrayList<ReceiptHistoryStore.HistoryItem> items;
        @NonNull
        private final LinkedHashMap<
                ReceiptHistoryStore.HistoryItem,
                ArrayList<ReceiptHistoryStore.HistoryItem>
                > visibleItemSources;
        private int filterMode;

        private ArchivedReceiptEditState(
                @NonNull String receiptName,
                @NonNull String crownedParticipantKey,
                @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
                @NonNull List<ReceiptHistoryStore.HistoryItem> items
        ) {
            this.receiptName = receiptName;
            this.crownedParticipantKey = crownedParticipantKey;
            this.participants = new ArrayList<>(participants);
            this.allItems = new ArrayList<>(items);
            this.items = new ArrayList<>(items);
            this.visibleItemSources = new LinkedHashMap<>();
            this.filterMode = RECEIPT_FILTER_DEFAULT;
        }
    }

    private static final class ArchiveSummaryTransfer {
        @NonNull
        private final String fromParticipantName;
        @NonNull
        private final String toParticipantName;
        @NonNull
        private final String fromParticipantKey;
        @NonNull
        private final String toParticipantKey;
        private final boolean hasPaid;
        @NonNull
        private final BigDecimal amount;

        private ArchiveSummaryTransfer(
                @NonNull String fromParticipantName,
                @NonNull String toParticipantName,
                @NonNull String fromParticipantKey,
                @NonNull String toParticipantKey,
                boolean hasPaid,
                @NonNull BigDecimal amount
        ) {
            this.fromParticipantName = fromParticipantName;
            this.toParticipantName = toParticipantName;
            this.fromParticipantKey = fromParticipantKey;
            this.toParticipantKey = toParticipantKey;
            this.hasPaid = hasPaid;
            this.amount = amount;
        }
    }

    private static final class ArchiveSummaryBalance {
        @NonNull
        private final ReceiptHistoryStore.ParticipantShare participant;
        @NonNull
        private BigDecimal amount;

        private ArchiveSummaryBalance(
                @NonNull ReceiptHistoryStore.ParticipantShare participant,
                @NonNull BigDecimal amount
        ) {
            this.participant = participant;
            this.amount = amount;
        }
    }

    private static final class ArchivedReceiptPaymentRequestTransfer {
        @NonNull
        private final ReceiptHistoryStore.ParticipantShare fromParticipant;
        @NonNull
        private final ReceiptHistoryStore.ParticipantShare toParticipant;
        @NonNull
        private final BigDecimal amount;
        @NonNull
        private final String paymentCardId;

        private ArchivedReceiptPaymentRequestTransfer(
                @NonNull ReceiptHistoryStore.ParticipantShare fromParticipant,
                @NonNull ReceiptHistoryStore.ParticipantShare toParticipant,
                @NonNull BigDecimal amount,
                @NonNull String paymentCardId
        ) {
            this.fromParticipant = fromParticipant;
            this.toParticipant = toParticipant;
            this.amount = amount;
            this.paymentCardId = paymentCardId;
        }
    }

    private static final class ParticipantPaymentRequestLine {
        @NonNull
        private final String counterpartyName;
        @NonNull
        private final BigDecimal amount;
        @Nullable
        private final String paymentUrl;

        private ParticipantPaymentRequestLine(
                @NonNull String counterpartyName,
                @NonNull BigDecimal amount,
                @Nullable String paymentUrl
        ) {
            this.counterpartyName = counterpartyName;
            this.amount = amount;
            this.paymentUrl = paymentUrl;
        }
    }

    private static final class ArchiveRootItem {
        private static final int TYPE_STANDALONE_RECEIPT = 0;
        private static final int TYPE_FOLDER = 1;

        private final int type;
        private final int sourceIndex;
        @Nullable
        private final ArchiveStore.Archive archive;
        @Nullable
        private final ReceiptHistoryStore.HistoryEntry receiptEntry;

        private ArchiveRootItem(
                int type,
                int sourceIndex,
                @Nullable ArchiveStore.Archive archive,
                @Nullable ReceiptHistoryStore.HistoryEntry receiptEntry
        ) {
            this.type = type;
            this.sourceIndex = sourceIndex;
            this.archive = archive;
            this.receiptEntry = receiptEntry;
        }

        @NonNull
        private static ArchiveRootItem forArchive(int archiveIndex, @NonNull ArchiveStore.Archive archive) {
            return new ArchiveRootItem(TYPE_FOLDER, archiveIndex, archive, null);
        }

        @NonNull
        private static ArchiveRootItem forStandaloneReceipt(
                int receiptIndex,
                @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry
        ) {
            return new ArchiveRootItem(TYPE_STANDALONE_RECEIPT, receiptIndex, null, receiptEntry);
        }
    }

    private interface OnArchiveReceiptCreatedListener {
        void onArchiveReceiptCreated(@NonNull ReceiptHistoryStore.HistoryEntry newReceiptEntry);
    }

    private interface OnArchiveReceiptSavedListener {
        void onArchiveReceiptSaved(@NonNull ReceiptHistoryStore.HistoryEntry updatedEntry);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        appliedThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        if (AuthGateHelper.redirectToLoginIfNeeded(this)) {
            return;
        }
        setContentView(R.layout.activity_archive);

        View backButton = findViewById(R.id.button_back);
        View settingsMenuButton = findViewById(R.id.button_archive_actions);
        MaterialButton addArchiveButton = findViewById(R.id.button_add_archive);
        MaterialButton newReceiptButton = findViewById(R.id.button_new_receipt);
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
        newReceiptButton.setOnClickListener(view -> showSelectFolderForNewReceiptDialog());
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
        loadArchiveNames();
    }

    @Override
    protected void onDestroy() {
        dismissArchivedReceiptSaveChangesDisabledReasonsPopup();
        dismissNewArchiveCreateDisabledReasonsPopup();
        dismissArchivedReceiptItemPayerPopup();
        dismissSendRequestsNoInternetPopup();
        super.onDestroy();
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
            backgroundExecutor = null;
        }
    }

    private void loadArchiveNames() {
        archiveNames.clear();
        archives.clear();
        standaloneReceipts.clear();
        archiveRootItems.clear();

        archives.addAll(ArchiveStore.loadArchives(this));
        standaloneReceipts.addAll(ArchiveStore.loadStandaloneReceipts(this));
        for (ArchiveStore.Archive archive : archives) {
            archiveNames.add(archive.name);
        }
        expandedArchiveNames.retainAll(archiveNames);
        for (int index = 0; index < archives.size(); index++) {
            archiveRootItems.add(ArchiveRootItem.forArchive(index, archives.get(index)));
        }
        for (int index = 0; index < standaloneReceipts.size(); index++) {
            archiveRootItems.add(ArchiveRootItem.forStandaloneReceipt(
                    index,
                    standaloneReceipts.get(index)
            ));
        }
        archiveEntriesAdapter.notifyDataSetChanged();
    }

    private void expandArchiveByIndex(int archiveIndex) {
        ArchiveStore.Archive archive = ArchiveStore.loadArchiveAt(this, archiveIndex);
        if (archive == null) {
            return;
        }

        expandedArchiveNames.add(archive.name);
        loadArchiveNames();
    }

    private boolean recreateIfThemeConfigurationChanged() {
        String currentThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        if (currentThemeConfigurationKey.equals(appliedThemeConfigurationKey)) {
            return false;
        }

        recreate();
        return true;
    }

    private void showNewArchiveDialog() {
        showNewArchiveDialog(null);
    }

    private void showNewArchiveDialog(@Nullable Runnable onArchiveCreated) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_archive, null);
        TextInputLayout archiveNameInputLayout =
                dialogView.findViewById(R.id.input_layout_archive_name);
        TextInputEditText archiveNameInput = dialogView.findViewById(R.id.input_archive_name);
        MaterialButton createButton = dialogView.findViewById(R.id.button_create_archive);
        AppCompatImageButton disabledInfoButton =
                dialogView.findViewById(R.id.button_create_archive_disabled_info);

        if (archiveNameInputLayout != null) {
            archiveNameInputLayout.setHint(getString(R.string.folder_name_label));
        }

        updateNewArchiveCreateButtonState(
                archiveNameInput,
                createButton,
                disabledInfoButton
        );
        archiveNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateNewArchiveCreateButtonState(
                        archiveNameInput,
                        createButton,
                        disabledInfoButton
                );
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.button_new_folder)
                .setView(dialogView)
                .create();
        dialog.setOnDismissListener(dialogInterface ->
                dismissNewArchiveCreateDisabledReasonsPopup()
        );

        disabledInfoButton.setOnClickListener(view -> showNewArchiveCreateDisabledReasonsPopup(
                createButton,
                buildNewArchiveDisabledReasons(getArchiveName(archiveNameInput))
        ));

        createButton.setOnClickListener(view -> {
            String archiveName = getArchiveName(archiveNameInput);
            if (archiveName.isEmpty() || archiveNameExists(archiveName)) {
                updateNewArchiveCreateButtonState(
                        archiveNameInput,
                        createButton,
                        disabledInfoButton
                );
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

    private void showSelectFolderForNewReceiptDialog() {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_location,
                null
        );
        View headerView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_header,
                null
        );
        TextView headerTitleView = headerView.findViewById(R.id.text_select_archive_header_title);
        AppCompatImageButton addArchiveButton =
                headerView.findViewById(R.id.button_select_archive_add);
        ListView locationsListView = dialogView.findViewById(R.id.list_select_archive_location);
        TextView emptyView = dialogView.findViewById(R.id.text_select_archive_location_empty);
        TextInputLayout receiptNameInputLayout =
                dialogView.findViewById(R.id.input_layout_select_archive_receipt_name);
        TextInputEditText receiptNameInput =
                dialogView.findViewById(R.id.edit_select_archive_receipt_name);
        MaterialButton createButton =
                dialogView.findViewById(R.id.button_create_selected_receipt);
        ArrayList<String> locationNames = new ArrayList<>();
        ArrayAdapter<String> locationsAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_single_choice,
                locationNames
        );

        headerTitleView.setText(R.string.select_location_title);
        emptyView.setText(R.string.select_folder_empty);
        createButton.setText(R.string.create);
        locationsListView.setAdapter(locationsAdapter);
        locationsListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        final int[] selectedArchiveIndex = {-1};
        Runnable refreshLocations = () -> {
            locationNames.clear();
            locationNames.add(getString(R.string.standalone));
            locationNames.addAll(ArchiveStore.loadArchiveNames(this));
            locationsAdapter.notifyDataSetChanged();
            emptyView.setVisibility(View.GONE);
            locationsListView.setVisibility(View.VISIBLE);

            int checkedPosition = selectedArchiveIndex[0] < 0
                    ? 0
                    : Math.min(selectedArchiveIndex[0] + 1, locationNames.size() - 1);
            locationsListView.setItemChecked(checkedPosition, true);
            updateSelectLocationCreateButtonState(receiptNameInput, createButton);
        };
        refreshLocations.run();

        receiptNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (receiptNameInputLayout != null) {
                    receiptNameInputLayout.setError(null);
                }
                updateSelectLocationCreateButtonState(receiptNameInput, createButton);
            }
        });

        locationsListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedArchiveIndex[0] = position == 0 ? -1 : position - 1;
            updateSelectLocationCreateButtonState(receiptNameInput, createButton);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(headerView)
                .setView(dialogView)
                .create();

        addArchiveButton.setOnClickListener(view -> showNewArchiveDialog(() -> {
            selectedArchiveIndex[0] = 0;
            refreshLocations.run();
            locationsListView.setItemChecked(1, true);
        }));

        createButton.setEnabled(false);
        createButton.setOnClickListener(view -> {
            String receiptName = getText(receiptNameInput);
            if (receiptName.isEmpty()) {
                updateSelectLocationCreateButtonState(receiptNameInput, createButton);
                return;
            }

            ReceiptHistoryStore.HistoryEntry newReceiptEntry =
                    createEmptyArchiveReceiptEntry(receiptName);
            dialog.dismiss();
            if (selectedArchiveIndex[0] < 0) {
                ArchiveStore.addStandaloneReceipt(this, newReceiptEntry);
                loadArchiveNames();
                return;
            }

            int archiveIndex = selectedArchiveIndex[0];
            ArchiveStore.addReceiptToArchive(this, archiveIndex, newReceiptEntry);
            expandArchiveByIndex(archiveIndex);
        });

        dialog.show();
    }

    private void updateSelectLocationCreateButtonState(
            @NonNull TextInputEditText receiptNameInput,
            @NonNull MaterialButton createButton
    ) {
        createButton.setEnabled(!getText(receiptNameInput).isEmpty());
    }

    private void updateNewArchiveCreateButtonState(
            @NonNull TextInputEditText archiveNameInput,
            @NonNull MaterialButton createButton,
            @NonNull AppCompatImageButton disabledInfoButton
    ) {
        ArrayList<String> disabledReasons =
                buildNewArchiveDisabledReasons(getArchiveName(archiveNameInput));
        boolean isEnabled = disabledReasons.isEmpty();
        createButton.setEnabled(isEnabled);
        disabledInfoButton.setVisibility(isEnabled ? View.GONE : View.VISIBLE);
        if (isEnabled) {
            dismissNewArchiveCreateDisabledReasonsPopup();
        }
    }

    @NonNull
    private ArrayList<String> buildNewArchiveDisabledReasons(@NonNull String archiveName) {
        ArrayList<String> disabledReasons = new ArrayList<>();
        if (archiveName.isEmpty()) {
            disabledReasons.add(getString(R.string.create_archive_disabled_reason_empty_name));
        }
        if (!archiveName.isEmpty() && archiveNameExists(archiveName)) {
            disabledReasons.add(getString(R.string.create_archive_disabled_reason_duplicate_name));
        }
        return disabledReasons;
    }

    private void showNewArchiveCreateDisabledReasonsPopup(
            @NonNull MaterialButton createButton,
            @NonNull ArrayList<String> disabledReasons
    ) {
        if (disabledReasons.isEmpty()) {
            return;
        }
        if (newArchiveCreateDisabledReasonsPopup != null
                && newArchiveCreateDisabledReasonsPopup.isShowing()) {
            dismissNewArchiveCreateDisabledReasonsPopup();
            return;
        }

        View popupView = getLayoutInflater().inflate(
                R.layout.popup_next_button_disabled_reasons,
                null
        );
        TextView titleView = popupView.findViewById(R.id.text_next_disabled_title);
        LinearLayout reasonsLayout = popupView.findViewById(R.id.layout_next_disabled_reasons);
        titleView.setText(R.string.create_archive_disabled_reasons_title);

        for (int index = 0; index < disabledReasons.size(); index++) {
            TextView reasonView = new TextView(this);
            reasonView.setText("\u2022 " + disabledReasons.get(index));
            TextViewCompat.setTextAppearance(
                    reasonView,
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
            );
            reasonView.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK));
            if (index < disabledReasons.size() - 1) {
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                layoutParams.bottomMargin = dpToPx(8);
                reasonView.setLayoutParams(layoutParams);
            }
            reasonsLayout.addView(reasonView);
        }

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(dpToPx(280), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dpToPx(10));
        popupWindow.setOnDismissListener(() -> {
            if (newArchiveCreateDisabledReasonsPopup == popupWindow) {
                newArchiveCreateDisabledReasonsPopup = null;
            }
        });

        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();
        int xOffset = Math.max(0, createButton.getWidth() - popupWidth);
        int yOffset = -(createButton.getHeight() + popupHeight + dpToPx(8));
        popupWindow.showAsDropDown(createButton, xOffset, yOffset);
        newArchiveCreateDisabledReasonsPopup = popupWindow;
    }

    private void dismissNewArchiveCreateDisabledReasonsPopup() {
        if (newArchiveCreateDisabledReasonsPopup == null) {
            return;
        }
        newArchiveCreateDisabledReasonsPopup.dismiss();
        newArchiveCreateDisabledReasonsPopup = null;
    }

    private boolean archiveNameExists(@NonNull String archiveName) {
        String normalizedArchiveName = archiveName.trim();
        for (String existingArchiveName : archiveNames) {
            if (existingArchiveName.trim().equalsIgnoreCase(normalizedArchiveName)) {
                return true;
            }
        }
        return false;
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
        archiveSummaryButton.setOnClickListener(
                view -> showArchiveSummaryDialog(
                        archiveIndex,
                        titleView.getText().toString(),
                        archiveReceipts
                )
        );
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
        showArchivedReceiptDetailsDialog(
                entry,
                updatedEntry -> {
                    archiveReceipts.set(receiptIndex, updatedEntry);
                    ArchiveStore.updateReceiptAt(this, archiveIndex, receiptIndex, updatedEntry);
                },
                onReceiptSaved,
                () -> removeArchiveReceipt(
                        archiveIndex,
                        receiptIndex,
                        archiveReceipts,
                        onReceiptSaved
                )
        );
    }

    private void showStandaloneReceiptDetailsDialog(int receiptIndex) {
        if (receiptIndex < 0 || receiptIndex >= standaloneReceipts.size()) {
            return;
        }

        showArchivedReceiptDetailsDialog(
                standaloneReceipts.get(receiptIndex),
                updatedEntry -> ArchiveStore.updateStandaloneReceiptAt(
                        this,
                        receiptIndex,
                        updatedEntry
                ),
                () -> {
                },
                () -> {
                    ArchiveStore.removeStandaloneReceiptAt(this, receiptIndex);
                    loadArchiveNames();
                }
        );
    }

    private void showArchivedReceiptDetailsDialog(
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull OnArchiveReceiptSavedListener onArchiveReceiptSavedListener,
            @NonNull Runnable onReceiptSaved,
            @NonNull Runnable onReceiptSent
    ) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_archive_receipt_details, null);
        TextView titleView = dialogView.findViewById(R.id.text_archive_receipt_dialog_title);
        TextView messageView = dialogView.findViewById(R.id.text_archive_receipt_dialog_message);
        AppCompatImageButton closeButton =
                dialogView.findViewById(R.id.button_close_archive_receipt);
        AppCompatImageButton editNameButton =
                dialogView.findViewById(R.id.button_edit_archive_receipt_name);
        AppCompatImageButton summaryDisabledInfoButton =
                dialogView.findViewById(R.id.button_save_archive_receipt_disabled_info);
        MaterialButton summaryButton =
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
        rebuildArchivedReceiptVisibleItems(editState);
        final Runnable[] refreshContentHolder = new Runnable[1];
        final ArchivedReceiptItemsAdapter[] itemsAdapterHolder = new ArchivedReceiptItemsAdapter[1];

        titleView.setText(editState.receiptName);

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
            ArrayList<String> disabledReasons =
                    buildArchivedReceiptSummaryDisabledReasons(editState);
            boolean summaryEnabled = disabledReasons.isEmpty();
            summaryButton.setEnabled(summaryEnabled);
            summaryDisabledInfoButton.setVisibility(
                    summaryEnabled ? View.GONE : View.VISIBLE
            );
            dismissArchivedReceiptSaveChangesDisabledReasonsPopup();
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
        scanMoreAction.setOnClickListener(view ->
                startArchivedReceiptScanMoreFlow(editState, refreshContentHolder[0])
        );
        editNameButton.setOnClickListener(view ->
                showEditArchivedReceiptNameDialog(editState, titleView, refreshContentHolder[0])
        );
        summaryDisabledInfoButton.setOnClickListener(view ->
                showArchivedReceiptSaveChangesDisabledReasonsPopup(
                        summaryButton,
                        buildArchivedReceiptSummaryDisabledReasons(editState)
                )
        );
        refreshContentHolder[0].run();

        Dialog dialog = new Dialog(this, AppSettings.getAppThemeResId(this));
        dialog.setContentView(dialogView);
        closeButton.setOnClickListener(view -> {
            ReceiptHistoryStore.HistoryEntry updatedEntry =
                    buildArchivedReceiptEntry(entry, editState);
            onArchiveReceiptSavedListener.onArchiveReceiptSaved(updatedEntry);
            loadArchiveNames();
            onReceiptSaved.run();
            dialog.dismiss();
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        });
        dialog.setOnDismissListener(dialogInterface ->
                dismissArchivedReceiptSaveChangesDisabledReasonsPopup()
        );
        summaryButton.setOnClickListener(view -> {
            ReceiptHistoryStore.HistoryEntry updatedEntry =
                    buildArchivedReceiptEntry(entry, editState);
            onArchiveReceiptSavedListener.onArchiveReceiptSaved(updatedEntry);
            loadArchiveNames();
            onReceiptSaved.run();
            showArchivedReceiptSummaryDialog(updatedEntry, onReceiptSent);
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void showArchiveSummaryDialog(
            int archiveIndex,
            @NonNull String archiveName,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_archive_summary, null);
        View closeButton = dialogView.findViewById(R.id.button_close_archive_summary);
        MaterialButton sendRequestsButton =
                dialogView.findViewById(R.id.button_archive_summary_send_requests);
        AppCompatImageButton sendRequestsNoInternetInfoButton =
                dialogView.findViewById(
                        R.id.button_archive_summary_send_requests_no_internet_info
                );
        LinearLayout transfersLayout =
                dialogView.findViewById(R.id.layout_archive_summary_transfers);
        TextView emptyView = dialogView.findViewById(R.id.text_archive_summary_empty);
        TextInputLayout requestNameInputLayout =
                dialogView.findViewById(R.id.input_layout_archive_summary_request_name);
        TextInputEditText requestNameInputView =
                dialogView.findViewById(R.id.edit_archive_summary_request_name);
        final ConnectivityManager.NetworkCallback[] networkCallbackHolder =
                new ConnectivityManager.NetworkCallback[1];

        ArrayList<ArchiveSummaryTransfer> transfers =
                buildArchiveSummaryTransfers(archiveReceipts);
        boolean hasPendingPayments = !transfers.isEmpty();
        if (transfers.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            for (ArchiveSummaryTransfer transfer : transfers) {
                View rowView = getLayoutInflater().inflate(
                        R.layout.item_archive_summary_transfer,
                        transfersLayout,
                        false
                );
                TextView directionView =
                        rowView.findViewById(R.id.text_archive_summary_transfer_direction);
                TextView amountView =
                        rowView.findViewById(R.id.text_archive_summary_transfer_amount);
                directionView.setText(getString(
                        R.string.archive_summary_transfer_direction_arrow,
                        transfer.fromParticipantName,
                        transfer.toParticipantName
                ));
                amountView.setText(getString(
                        R.string.archive_summary_transfer_amount,
                        formatCurrency(transfer.amount)
                ));
                transfersLayout.addView(rowView);
            }
        }

        requestNameInputView.setFilters(new InputFilter[]{
                createArchiveSummaryRequestNameInputFilter(),
                new InputFilter.LengthFilter(20)
        });
        requestNameInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                requestNameInputLayout.setError(null);
                updateArchivedReceiptSummarySendRequestsUi(
                        sendRequestsButton,
                        sendRequestsNoInternetInfoButton,
                        hasPendingPayments,
                        requestNameInputView
                );
            }
        });
        requestNameInputLayout.setEnabled(hasPendingPayments);
        requestNameInputView.setEnabled(hasPendingPayments);
        updateArchivedReceiptSummarySendRequestsUi(
                sendRequestsButton,
                sendRequestsNoInternetInfoButton,
                hasPendingPayments,
                requestNameInputView
        );

        Dialog dialog = new Dialog(this, AppSettings.getFullScreenDialogThemeResId(this));
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        closeButton.setOnClickListener(view -> {
            dismissSendRequestsNoInternetPopup();
            dialog.dismiss();
        });
        sendRequestsNoInternetInfoButton.setOnClickListener(
                view -> showSendRequestsNoInternetPopup(sendRequestsNoInternetInfoButton)
        );
        dialog.setOnDismissListener(dialogInterface -> {
            dismissSendRequestsNoInternetPopup();
            NetworkStateHelper.unregisterNetworkCallback(
                    ArchiveActivity.this,
                    networkCallbackHolder[0]
            );
        });
        networkCallbackHolder[0] = NetworkStateHelper.registerDefaultNetworkCallback(
                this,
                () -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                        return;
                    }

                    updateArchivedReceiptSummarySendRequestsUi(
                            sendRequestsButton,
                            sendRequestsNoInternetInfoButton,
                            hasPendingPayments,
                            requestNameInputView
                    );
                })
        );
        sendRequestsButton.setOnClickListener(view -> {
            if (!NetworkStateHelper.hasInternetConnection(this)) {
                updateArchivedReceiptSummarySendRequestsUi(
                        sendRequestsButton,
                        sendRequestsNoInternetInfoButton,
                        hasPendingPayments,
                        requestNameInputView
                );
                return;
            }

            if (!validateArchiveSummaryRequestName(requestNameInputLayout, requestNameInputView)) {
                return;
            }

            showArchiveSummarySendRequestsConfirmationDialog(
                    archiveIndex,
                    getText(requestNameInputView),
                    archiveReceipts,
                    dialog
            );
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void showArchivedReceiptSummaryDialog(
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry,
            @NonNull Runnable onReceiptSent
    ) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receipt_summary, null);
        LinearLayout transfersLayout =
                dialogView.findViewById(R.id.layout_receipt_summary_transfers);
        TextView emptyView = dialogView.findViewById(R.id.text_receipt_summary_empty);
        TextInputLayout requestNameInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_summary_receipt_name);
        TextInputEditText requestNameInputView =
                dialogView.findViewById(R.id.edit_receipt_summary_receipt_name);
        View closeButton = dialogView.findViewById(R.id.button_close_receipt_summary);
        MaterialButton sendRequestsButton = dialogView.findViewById(R.id.button_send_requests);
        AppCompatImageButton sendRequestsNoInternetInfoButton =
                dialogView.findViewById(R.id.button_send_requests_no_internet_info);
        final ConnectivityManager.NetworkCallback[] networkCallbackHolder =
                new ConnectivityManager.NetworkCallback[1];

        ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers =
                buildArchivedReceiptPaymentRequestTransfers(receiptEntry);
        boolean hasPendingPayments = !transfers.isEmpty();
        if (transfers.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            for (ArchivedReceiptPaymentRequestTransfer transfer : transfers) {
                View rowView = getLayoutInflater().inflate(
                        R.layout.item_archive_summary_transfer,
                        transfersLayout,
                        false
                );
                TextView directionView =
                        rowView.findViewById(R.id.text_archive_summary_transfer_direction);
                TextView amountView =
                        rowView.findViewById(R.id.text_archive_summary_transfer_amount);
                directionView.setText(getString(
                        R.string.receipt_summary_transfer_direction_arrow,
                        getArchiveSummaryParticipantDisplayName(transfer.fromParticipant),
                        getArchiveSummaryParticipantDisplayName(transfer.toParticipant)
                ));
                amountView.setText(getString(
                        R.string.archive_summary_transfer_amount,
                        formatCurrency(transfer.amount)
                ));
                transfersLayout.addView(rowView);
            }
        }

        requestNameInputView.setFilters(new InputFilter[]{
                createArchiveSummaryRequestNameInputFilter(),
                new InputFilter.LengthFilter(20)
        });
        requestNameInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                requestNameInputLayout.setError(null);
                updateArchivedReceiptSummarySendRequestsUi(
                        sendRequestsButton,
                        sendRequestsNoInternetInfoButton,
                        hasPendingPayments,
                        requestNameInputView
                );
            }
        });
        requestNameInputLayout.setEnabled(hasPendingPayments);
        requestNameInputView.setEnabled(hasPendingPayments);
        updateArchivedReceiptSummarySendRequestsUi(
                sendRequestsButton,
                sendRequestsNoInternetInfoButton,
                hasPendingPayments,
                requestNameInputView
        );

        Dialog dialog = new Dialog(this, AppSettings.getFullScreenDialogThemeResId(this));
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        closeButton.setOnClickListener(view -> {
            dismissSendRequestsNoInternetPopup();
            dialog.dismiss();
        });
        sendRequestsNoInternetInfoButton.setOnClickListener(
                view -> showSendRequestsNoInternetPopup(sendRequestsNoInternetInfoButton)
        );
        dialog.setOnDismissListener(dialogInterface -> {
            dismissSendRequestsNoInternetPopup();
            NetworkStateHelper.unregisterNetworkCallback(
                    ArchiveActivity.this,
                    networkCallbackHolder[0]
            );
        });
        networkCallbackHolder[0] = NetworkStateHelper.registerDefaultNetworkCallback(
                this,
                () -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                        return;
                    }

                    updateArchivedReceiptSummarySendRequestsUi(
                            sendRequestsButton,
                            sendRequestsNoInternetInfoButton,
                            hasPendingPayments,
                            requestNameInputView
                    );
                })
        );
        sendRequestsButton.setOnClickListener(view -> {
            if (!NetworkStateHelper.hasInternetConnection(this)) {
                updateArchivedReceiptSummarySendRequestsUi(
                        sendRequestsButton,
                        sendRequestsNoInternetInfoButton,
                        hasPendingPayments,
                        requestNameInputView
                );
                return;
            }

            if (!validateArchiveSummaryRequestName(requestNameInputLayout, requestNameInputView)) {
                return;
            }

            showArchivedReceiptSendRequestsConfirmationDialog(
                    dialog,
                    getText(requestNameInputView),
                    receiptEntry,
                    transfers,
                    onReceiptSent
            );
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void showArchivedReceiptSendRequestsConfirmationDialog(
            @NonNull Dialog summaryDialog,
            @NonNull String requestName,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers,
            @NonNull Runnable onReceiptSent
    ) {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_send_requests_confirmation,
                null
        );
        MaterialButton noButton = dialogView.findViewById(R.id.button_send_requests_no);
        MaterialButton yesButton = dialogView.findViewById(R.id.button_send_requests_yes);

        AlertDialog confirmationDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        noButton.setOnClickListener(view -> confirmationDialog.dismiss());
        yesButton.setOnClickListener(view -> {
            confirmationDialog.dismiss();
            summaryDialog.dismiss();
            openArchivedReceiptSendRequestsFlow(
                    requestName,
                    receiptEntry,
                    transfers,
                    onReceiptSent
            );
        });

        confirmationDialog.show();
    }

    private void showArchiveSummarySendRequestsConfirmationDialog(
            int archiveIndex,
            @NonNull String requestName,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull Dialog archiveSummaryDialog
    ) {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_send_requests_confirmation,
                null
        );
        MaterialButton noButton = dialogView.findViewById(R.id.button_send_requests_no);
        MaterialButton yesButton = dialogView.findViewById(R.id.button_send_requests_yes);

        AlertDialog confirmationDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        noButton.setOnClickListener(view -> confirmationDialog.dismiss());
        yesButton.setOnClickListener(view -> {
            confirmationDialog.dismiss();
            archiveSummaryDialog.dismiss();
            openArchiveSummarySendRequestsFlow(
                    archiveIndex,
                    requestName,
                    new ArrayList<>(archiveReceipts)
            );
        });

        confirmationDialog.show();
    }

    private void updateArchivedReceiptSummarySendRequestsUi(
            @NonNull MaterialButton sendRequestsButton,
            @NonNull AppCompatImageButton sendRequestsNoInternetInfoButton,
            boolean hasPendingPayments,
            @NonNull TextInputEditText requestNameInputView
    ) {
        boolean hasInternetConnection = NetworkStateHelper.hasInternetConnection(this);
        sendRequestsButton.setEnabled(
                hasPendingPayments
                        && isArchiveSummaryRequestNameEntered(requestNameInputView)
                        && hasInternetConnection
        );
        sendRequestsNoInternetInfoButton.setVisibility(
                hasInternetConnection ? View.GONE : View.VISIBLE
        );
        if (hasInternetConnection) {
            dismissSendRequestsNoInternetPopup();
        }
    }

    private void showSendRequestsNoInternetPopup(@NonNull View anchorView) {
        if (sendRequestsNoInternetPopup != null && sendRequestsNoInternetPopup.isShowing()) {
            dismissSendRequestsNoInternetPopup();
            return;
        }

        View popupView = getLayoutInflater().inflate(
                R.layout.popup_header_help_message,
                null
        );
        TextView messageView = popupView.findViewById(R.id.text_header_help_message);
        messageView.setText(R.string.history_no_internet);

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(dpToPx(240), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dpToPx(10));
        popupWindow.setOnDismissListener(() -> {
            if (sendRequestsNoInternetPopup == popupWindow) {
                sendRequestsNoInternetPopup = null;
            }
        });

        Rect anchorBounds = new Rect();
        anchorView.getGlobalVisibleRect(anchorBounds);
        Rect visibleFrame = new Rect();
        anchorView.getWindowVisibleDisplayFrame(visibleFrame);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();
        int popupX = clamp(
                anchorBounds.right - popupWidth,
                visibleFrame.left,
                Math.max(visibleFrame.left, visibleFrame.right - popupWidth)
        );
        int popupY = clamp(
                anchorBounds.top - popupHeight - dpToPx(8),
                visibleFrame.top,
                Math.max(visibleFrame.top, visibleFrame.bottom - popupHeight)
        );

        popupWindow.showAtLocation(
                anchorView.getRootView(),
                Gravity.TOP | Gravity.START,
                popupX,
                popupY
        );
        sendRequestsNoInternetPopup = popupWindow;
    }

    private void dismissSendRequestsNoInternetPopup() {
        if (sendRequestsNoInternetPopup == null) {
            return;
        }

        sendRequestsNoInternetPopup.dismiss();
        sendRequestsNoInternetPopup = null;
    }

    private boolean hasSendSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openArchivedReceiptSendRequestsFlow(
            @NonNull String requestName,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers,
            @NonNull Runnable onReceiptSent
    ) {
        if (!DeviceCapabilityHelper.supportsSms(this)) {
            Toast.makeText(
                    this,
                    R.string.send_requests_not_supported,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Runnable sendRequestsAction = () -> sendArchivedReceiptPaymentRequests(
                requestName,
                receiptEntry,
                transfers,
                onReceiptSent
        );
        if (!hasSendSmsPermission()) {
            pendingSendRequestsAction = sendRequestsAction;
            requestSendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        sendRequestsAction.run();
    }

    private void openArchiveSummarySendRequestsFlow(
            int archiveIndex,
            @NonNull String requestName,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        if (!DeviceCapabilityHelper.supportsSms(this)) {
            Toast.makeText(
                    this,
                    R.string.send_requests_not_supported,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers =
                buildArchiveSummaryPaymentRequestTransfers(archiveReceipts);
        Runnable sendRequestsAction = () -> sendArchiveSummaryPaymentRequests(
                archiveIndex,
                requestName,
                archiveReceipts,
                transfers
        );
        if (!hasSendSmsPermission()) {
            pendingSendRequestsAction = sendRequestsAction;
            requestSendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        sendRequestsAction.run();
    }

    private void sendArchivedReceiptPaymentRequests(
            @NonNull String requestName,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers,
            @NonNull Runnable onReceiptSent
    ) {
        ReceiptHistoryStore.HistoryEntry historyEntry =
                buildArchivedReceiptSendHistoryEntry(requestName, receiptEntry, transfers);
        SupabaseHistoryService.saveEntry(
                getApplicationContext(),
                historyEntry,
                new SupabaseHistoryService.EntryCallback() {
                    @Override
                    public void onSuccess(@NonNull ReceiptHistoryStore.HistoryEntry savedHistoryEntry) {
                        sendArchivedReceiptPaymentRequestsWithHistoryId(
                                savedHistoryEntry,
                                historyEntry.receiptName,
                                receiptEntry,
                                transfers,
                                onReceiptSent
                        );
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(
                                ArchiveActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void sendArchiveSummaryPaymentRequests(
            int archiveIndex,
            @NonNull String requestName,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers
    ) {
        ReceiptHistoryStore.HistoryEntry historyEntry =
                buildArchiveSummaryHistoryEntry(requestName, archiveReceipts);
        ArrayList<ReceiptHistoryStore.ParticipantShare> summaryParticipants =
                buildArchiveSummaryHistoryParticipants(archiveReceipts);
        SupabaseHistoryService.saveEntry(
                getApplicationContext(),
                historyEntry,
                new SupabaseHistoryService.EntryCallback() {
                    @Override
                    public void onSuccess(@NonNull ReceiptHistoryStore.HistoryEntry savedHistoryEntry) {
                        sendArchiveSummaryPaymentRequestsWithHistoryId(
                                archiveIndex,
                                savedHistoryEntry,
                                historyEntry.receiptName,
                                summaryParticipants,
                                transfers,
                                archiveReceipts
                        );
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(
                                ArchiveActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void sendArchivedReceiptPaymentRequestsWithHistoryId(
            @NonNull ReceiptHistoryStore.HistoryEntry savedHistoryEntry,
            @NonNull String receiptName,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers,
            @NonNull Runnable onReceiptSent
    ) {
        String requestId = getHistoryEntryShortId(savedHistoryEntry);
        SmsManager smsManager = SmsManager.getDefault();
        int sentCount = 0;
        int skippedCount = 0;

        for (ReceiptHistoryStore.ParticipantShare participant : receiptEntry.participants) {
            if (isDefaultParticipant(participant)) {
                continue;
            }

            String phoneNumber = normalizeWhitespace(participant.phoneNumber);
            if (!isValidPhoneNumber(phoneNumber)) {
                skippedCount++;
                continue;
            }

            String message = buildArchivedReceiptPaymentRequestMessage(
                    participant,
                    receiptName,
                    transfers,
                    requestId
            );

            try {
                ArrayList<String> messageParts = smsManager.divideMessage(message);
                if (messageParts.size() > 1) {
                    smsManager.sendMultipartTextMessage(
                            phoneNumber,
                            null,
                            messageParts,
                            null,
                            null
                    );
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                }
                sentCount++;
            } catch (IllegalArgumentException | SecurityException exception) {
                skippedCount++;
            }
        }

        if (sentCount == 0) {
            removeReceiptHistoryEntrySilently(savedHistoryEntry);
            Toast.makeText(this, R.string.send_requests_none, Toast.LENGTH_SHORT).show();
            returnToMainMenu();
            return;
        }

        onReceiptSent.run();
        int messageResId = skippedCount == 0
                ? R.string.send_requests_success
                : R.string.send_requests_partial;
        Toast.makeText(
                this,
                getString(messageResId, sentCount, skippedCount),
                Toast.LENGTH_SHORT
        ).show();
        returnToMainMenu();
    }

    private void sendArchiveSummaryPaymentRequestsWithHistoryId(
            int archiveIndex,
            @NonNull ReceiptHistoryStore.HistoryEntry savedHistoryEntry,
            @NonNull String receiptName,
            @NonNull ArrayList<ReceiptHistoryStore.ParticipantShare> summaryParticipants,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        String requestId = getHistoryEntryShortId(savedHistoryEntry);
        SmsManager smsManager = SmsManager.getDefault();
        int sentCount = 0;
        int skippedCount = 0;

        for (ReceiptHistoryStore.ParticipantShare participant : summaryParticipants) {
            if (isDefaultParticipant(participant)) {
                continue;
            }

            String phoneNumber = normalizeWhitespace(participant.phoneNumber);
            if (!isValidPhoneNumber(phoneNumber)) {
                skippedCount++;
                continue;
            }

            String message = buildArchivedReceiptPaymentRequestMessage(
                    participant,
                    receiptName,
                    transfers,
                    requestId
            );

            try {
                ArrayList<String> messageParts = smsManager.divideMessage(message);
                if (messageParts.size() > 1) {
                    smsManager.sendMultipartTextMessage(
                            phoneNumber,
                            null,
                            messageParts,
                            null,
                            null
                    );
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                }
                sentCount++;
            } catch (IllegalArgumentException | SecurityException exception) {
                skippedCount++;
            }
        }

        if (sentCount == 0) {
            removeReceiptHistoryEntrySilently(savedHistoryEntry);
            Toast.makeText(this, R.string.send_requests_none, Toast.LENGTH_SHORT).show();
            returnToMainMenu();
            return;
        }

        consumeArchiveSummaryReceipts(archiveIndex, archiveReceipts);
        int messageResId = skippedCount == 0
                ? R.string.send_requests_success
                : R.string.send_requests_partial;
        Toast.makeText(
                this,
                getString(messageResId, sentCount, skippedCount),
                Toast.LENGTH_SHORT
        ).show();
        returnToMainMenu();
    }

    @NonNull
    private String buildArchivedReceiptPaymentRequestMessage(
            @NonNull ReceiptHistoryStore.ParticipantShare participant,
            @NonNull String receiptName,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers,
            @NonNull String requestId
    ) {
        ArrayList<ParticipantPaymentRequestLine> outgoingLines = new ArrayList<>();
        ArrayList<ParticipantPaymentRequestLine> incomingLines = new ArrayList<>();

        for (ArchivedReceiptPaymentRequestTransfer transfer : transfers) {
            if (transfer.fromParticipant.key.equals(participant.key)) {
                outgoingLines.add(new ParticipantPaymentRequestLine(
                        getArchiveParticipantExternalDisplayName(transfer.toParticipant),
                        transfer.amount,
                        buildPaymentRequestUrlOrNull(
                                resolveArchiveParticipantPaymentLinkPhoneNumber(transfer.toParticipant),
                                requestId,
                                transfer.paymentCardId
                        )
                ));
            } else if (transfer.toParticipant.key.equals(participant.key)) {
                incomingLines.add(new ParticipantPaymentRequestLine(
                        getArchiveParticipantExternalDisplayName(transfer.fromParticipant),
                        transfer.amount,
                        null
                ));
            }
        }

        StringBuilder messageBuilder = new StringBuilder(getString(
                R.string.participant_payment_request_intro,
                getArchiveParticipantExternalDisplayName(participant),
                receiptName
        ));

        if (outgoingLines.isEmpty() && incomingLines.isEmpty()) {
            messageBuilder.append("\n\n")
                    .append(getString(R.string.participant_payment_request_none));
            return messageBuilder.toString();
        }

        if (!outgoingLines.isEmpty()) {
            messageBuilder.append("\n\n")
                    .append(getString(R.string.participant_payment_request_section_pay))
                    .append('\n');
            appendOutgoingParticipantPaymentRequestLines(messageBuilder, outgoingLines);
        }

        if (!incomingLines.isEmpty()) {
            messageBuilder.append("\n\n")
                    .append(getString(R.string.participant_payment_request_section_receive))
                    .append('\n');
            appendIncomingParticipantPaymentRequestLines(messageBuilder, incomingLines);
        }

        return messageBuilder.toString();
    }

    private void appendOutgoingParticipantPaymentRequestLines(
            @NonNull StringBuilder messageBuilder,
            @NonNull ArrayList<ParticipantPaymentRequestLine> requestLines
    ) {
        for (int index = 0; index < requestLines.size(); index++) {
            if (index > 0) {
                messageBuilder.append("\n\n");
            }

            ParticipantPaymentRequestLine requestLine = requestLines.get(index);
            messageBuilder.append(requestLine.counterpartyName)
                    .append(' ')
                    .append(formatCurrency(requestLine.amount))
                    .append("kr");

            if (requestLine.paymentUrl != null && !requestLine.paymentUrl.isEmpty()) {
                messageBuilder.append('\n')
                        .append(requestLine.paymentUrl);
            }
        }
    }

    private void appendIncomingParticipantPaymentRequestLines(
            @NonNull StringBuilder messageBuilder,
            @NonNull ArrayList<ParticipantPaymentRequestLine> requestLines
    ) {
        for (int index = 0; index < requestLines.size(); index++) {
            if (index > 0) {
                messageBuilder.append("\n\n");
            }

            ParticipantPaymentRequestLine requestLine = requestLines.get(index);
            messageBuilder.append(formatCurrency(requestLine.amount))
                    .append("kr from ")
                    .append(requestLine.counterpartyName);
        }
    }

    @NonNull
    private ArrayList<ArchivedReceiptPaymentRequestTransfer> buildArchivedReceiptPaymentRequestTransfers(
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry
    ) {
        ArrayList<ReceiptHistoryStore.HistoryEntry> summaryReceipts = new ArrayList<>();
        summaryReceipts.add(receiptEntry);
        ArrayList<ArchiveSummaryTransfer> summaryTransfers =
                buildArchiveSummaryTransfers(summaryReceipts);
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        for (ReceiptHistoryStore.ParticipantShare participant : receiptEntry.participants) {
            participantsByKey.put(participant.key, participant);
        }

        ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers = new ArrayList<>();
        for (ArchiveSummaryTransfer summaryTransfer : summaryTransfers) {
            ReceiptHistoryStore.ParticipantShare fromParticipant =
                    participantsByKey.get(summaryTransfer.fromParticipantKey);
            ReceiptHistoryStore.ParticipantShare toParticipant =
                    participantsByKey.get(summaryTransfer.toParticipantKey);
            if (fromParticipant == null || toParticipant == null) {
                continue;
            }

            transfers.add(new ArchivedReceiptPaymentRequestTransfer(
                    fromParticipant,
                    toParticipant,
                    summaryTransfer.amount,
                    ReceiptHistoryStore.buildPaymentCardId(transfers.size())
            ));
        }
        return transfers;
    }

    @NonNull
    private ArrayList<ArchivedReceiptPaymentRequestTransfer> buildArchiveSummaryPaymentRequestTransfers(
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        ArrayList<ArchiveSummaryTransfer> summaryTransfers =
                buildArchiveSummaryTransfers(archiveReceipts);
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        for (ReceiptHistoryStore.HistoryEntry receipt : archiveReceipts) {
            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                participantsByKey.putIfAbsent(participant.key, participant);
            }
        }

        ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers = new ArrayList<>();
        for (int index = 0; index < summaryTransfers.size(); index++) {
            ArchiveSummaryTransfer summaryTransfer = summaryTransfers.get(index);
            ReceiptHistoryStore.ParticipantShare fromParticipant =
                    participantsByKey.get(summaryTransfer.fromParticipantKey);
            ReceiptHistoryStore.ParticipantShare toParticipant =
                    participantsByKey.get(summaryTransfer.toParticipantKey);
            if (fromParticipant == null || toParticipant == null) {
                continue;
            }

            transfers.add(new ArchivedReceiptPaymentRequestTransfer(
                    fromParticipant,
                    toParticipant,
                    summaryTransfer.amount,
                    ReceiptHistoryStore.buildPaymentCardId(index)
            ));
        }
        return transfers;
    }

    @NonNull
    private ReceiptHistoryStore.HistoryEntry buildArchivedReceiptSendHistoryEntry(
            @NonNull String requestName,
            @NonNull ReceiptHistoryStore.HistoryEntry receiptEntry,
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers
    ) {
        String normalizedRequestName = normalizeWhitespace(requestName);
        return new ReceiptHistoryStore.HistoryEntry(
                normalizedRequestName.isEmpty() ? receiptEntry.receiptName : normalizedRequestName,
                receiptEntry.totalAmount,
                getCurrentArchiveHistoryDate(),
                "",
                copyArchivedReceiptHistoryParticipants(receiptEntry.participants),
                copyArchivedReceiptHistoryItems(receiptEntry.items),
                ReceiptHistoryStore.ENTRY_TYPE_RECEIPT,
                new ArrayList<>(),
                buildArchivedReceiptSendHistoryPaymentCards(transfers)
        );
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.ParticipantShare> copyArchivedReceiptHistoryParticipants(
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants
    ) {
        ArrayList<ReceiptHistoryStore.ParticipantShare> copiedParticipants = new ArrayList<>();
        for (ReceiptHistoryStore.ParticipantShare participant : participants) {
            copiedParticipants.add(new ReceiptHistoryStore.ParticipantShare(
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
        return copiedParticipants;
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.HistoryItem> copyArchivedReceiptHistoryItems(
            @NonNull List<ReceiptHistoryStore.HistoryItem> items
    ) {
        ArrayList<ReceiptHistoryStore.HistoryItem> copiedItems = new ArrayList<>();
        for (ReceiptHistoryStore.HistoryItem item : items) {
            copiedItems.add(new ReceiptHistoryStore.HistoryItem(
                    item.name,
                    item.price,
                    item.payerParticipantKey,
                    new ArrayList<>(item.selectedParticipantKeys)
            ));
        }
        return copiedItems;
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.PaymentCard> buildArchivedReceiptSendHistoryPaymentCards(
            @NonNull ArrayList<ArchivedReceiptPaymentRequestTransfer> transfers
    ) {
        ArrayList<ReceiptHistoryStore.PaymentCard> paymentCards = new ArrayList<>();
        for (ArchivedReceiptPaymentRequestTransfer transfer : transfers) {
            paymentCards.add(new ReceiptHistoryStore.PaymentCard(
                    transfer.paymentCardId,
                    formatCurrency(transfer.amount),
                    resolveArchiveParticipantPaymentLinkPhoneNumber(transfer.toParticipant),
                    false
            ));
        }
        return paymentCards;
    }

    @Nullable
    private String buildPaymentRequestUrlOrNull(
            @NonNull String phoneNumber,
            @NonNull String requestId,
            @NonNull String paymentCardId
    ) {
        if (!isValidPhoneNumber(phoneNumber)
                || requestId.isEmpty()
                || paymentCardId.isEmpty()) {
            return null;
        }
        return buildPaymentRequestUrl(requestId, paymentCardId);
    }

    @NonNull
    private String buildPaymentRequestUrl(
            @NonNull String requestId,
            @NonNull String paymentCardId
    ) {
        return android.net.Uri.parse(PAYMENT_LINK_BASE_URL)
                .buildUpon()
                .appendQueryParameter("R", requestId)
                .appendQueryParameter("PC", paymentCardId)
                .build()
                .toString();
    }

    @NonNull
    private String getHistoryEntryShortId(@NonNull ReceiptHistoryStore.HistoryEntry historyEntry) {
        String storageId = normalizeWhitespace(historyEntry.storageId);
        if (storageId.isEmpty()) {
            return "";
        }
        return storageId.substring(0, Math.min(8, storageId.length()));
    }

    private void removeReceiptHistoryEntrySilently(
            @NonNull ReceiptHistoryStore.HistoryEntry historyEntry
    ) {
        if (historyEntry.storageId.isEmpty()) {
            return;
        }

        SupabaseHistoryService.removeEntry(
                getApplicationContext(),
                historyEntry,
                new SupabaseHistoryService.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(@NonNull String message) {
                    }
                }
        );
    }

    @NonNull
    private String resolveArchiveParticipantPaymentLinkPhoneNumber(
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        return resolveHistoryPaymentCardPhoneNumber(participant);
    }

    @NonNull
    private String getArchiveParticipantExternalDisplayName(
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        if (isDefaultParticipant(participant)) {
            String username = normalizeWhitespace(AppSettings.getUsernameNickname(this));
            return username.isEmpty() ? DEFAULT_PARTICIPANT_NAME : username;
        }
        return participant.name;
    }

    private void consumeArchiveSummaryReceipts(
            int archiveIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        if (archiveIndex < 0) {
            return;
        }

        for (int receiptIndex = archiveReceipts.size() - 1; receiptIndex >= 0; receiptIndex--) {
            ArchiveStore.removeReceiptAt(this, archiveIndex, receiptIndex);
        }
        archiveReceipts.clear();

        ArchiveStore.Archive updatedArchive = ArchiveStore.loadArchiveAt(this, archiveIndex);
        if (updatedArchive == null || updatedArchive.receipts.isEmpty()) {
            ArchiveStore.removeArchiveAt(this, archiveIndex);
        }
        loadArchiveNames();
    }

    private void returnToMainMenu() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @NonNull
    private InputFilter createArchiveSummaryRequestNameInputFilter() {
        return (source, start, end, dest, dstart, dend) -> {
            if (source == null) {
                return null;
            }

            StringBuilder filteredText = new StringBuilder();
            boolean changed = false;
            for (int index = start; index < end; index++) {
                char currentChar = source.charAt(index);
                if (Character.isLetterOrDigit(currentChar) || currentChar == ' ') {
                    filteredText.append(currentChar);
                } else {
                    changed = true;
                }
            }

            return changed ? filteredText.toString() : null;
        };
    }

    private boolean validateArchiveSummaryRequestName(
            @NonNull TextInputLayout requestNameInputLayout,
            @NonNull TextInputEditText requestNameInputView
    ) {
        if (!isArchiveSummaryRequestNameEntered(requestNameInputView)) {
            requestNameInputLayout.setError(
                    getString(R.string.receipt_summary_receipt_name_required)
            );
            requestNameInputView.requestFocus();
            return false;
        }

        requestNameInputLayout.setError(null);
        return true;
    }

    private boolean isArchiveSummaryRequestNameEntered(
            @NonNull TextInputEditText requestNameInputView
    ) {
        return !getText(requestNameInputView).trim().isEmpty();
    }

    @NonNull
    private ReceiptHistoryStore.HistoryEntry buildArchiveSummaryHistoryEntry(
            @NonNull String requestName,
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        ArrayList<ArchiveSummaryTransfer> transfers = buildArchiveSummaryTransfers(archiveReceipts);
        BigDecimal combinedTotal = BigDecimal.ZERO;
        for (ReceiptHistoryStore.HistoryEntry receipt : archiveReceipts) {
            combinedTotal = combinedTotal.add(parseCurrencyAmount(receipt.totalAmount));
        }

        return new ReceiptHistoryStore.HistoryEntry(
                normalizeWhitespace(requestName).isEmpty()
                        ? getString(R.string.archive_summary)
                        : requestName.trim(),
                formatCurrency(combinedTotal),
                getCurrentArchiveHistoryDate(),
                buildArchiveSummaryHistoryMessage(transfers),
                buildArchiveSummaryHistoryParticipants(archiveReceipts),
                buildArchiveSummaryHistoryItems(transfers),
                ReceiptHistoryStore.ENTRY_TYPE_ARCHIVE_SUMMARY,
                copyArchiveSummaryHistoryReceipts(archiveReceipts),
                buildArchiveSummaryHistoryPaymentCards(transfers, archiveReceipts)
        );
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.HistoryEntry> copyArchiveSummaryHistoryReceipts(
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        ArrayList<ReceiptHistoryStore.HistoryEntry> copiedReceipts = new ArrayList<>();
        for (ReceiptHistoryStore.HistoryEntry receipt : archiveReceipts) {
            ArrayList<ReceiptHistoryStore.ParticipantShare> copiedParticipants = new ArrayList<>();
            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                copiedParticipants.add(new ReceiptHistoryStore.ParticipantShare(
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

            ArrayList<ReceiptHistoryStore.HistoryItem> copiedItems = new ArrayList<>();
            for (ReceiptHistoryStore.HistoryItem item : receipt.items) {
                copiedItems.add(new ReceiptHistoryStore.HistoryItem(
                        item.name,
                        item.price,
                        item.hasPaid,
                        item.payerParticipantKey,
                        new ArrayList<>(item.selectedParticipantKeys)
                ));
            }

            copiedReceipts.add(new ReceiptHistoryStore.HistoryEntry(
                    receipt.receiptName,
                    receipt.totalAmount,
                    receipt.sentDate,
                    receipt.message,
                    copiedParticipants,
                    copiedItems,
                    receipt.entryType,
                    new ArrayList<>(),
                    buildReceiptHistoryPaymentCards(receipt)
            ));
        }
        return copiedReceipts;
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.ParticipantShare> buildArchiveSummaryHistoryParticipants(
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        for (ReceiptHistoryStore.HistoryEntry receipt : archiveReceipts) {
            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                if (!participantsByKey.containsKey(participant.key)) {
                    participantsByKey.put(
                            participant.key,
                            new ReceiptHistoryStore.ParticipantShare(
                                    participant.key,
                                    participant.name,
                                    participant.initials,
                                    participant.color,
                                    participant.phoneNumber,
                                    participant.amount,
                                    participant.isCrowned,
                                    participant.hasPaid
                            )
                    );
                }
            }
        }
        return new ArrayList<>(participantsByKey.values());
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.HistoryItem> buildArchiveSummaryHistoryItems(
            @NonNull List<ArchiveSummaryTransfer> transfers
    ) {
        ArrayList<ReceiptHistoryStore.HistoryItem> items = new ArrayList<>();
        for (ArchiveSummaryTransfer transfer : transfers) {
            ArrayList<String> selectedParticipantKeys = new ArrayList<>();
            if (!normalizeWhitespace(transfer.fromParticipantKey).isEmpty()) {
                selectedParticipantKeys.add(transfer.fromParticipantKey);
            }
            items.add(new ReceiptHistoryStore.HistoryItem(
                    getString(
                            R.string.archive_summary_transfer_direction,
                            transfer.fromParticipantName,
                            transfer.toParticipantName
                    ),
                    formatCurrency(transfer.amount),
                    transfer.hasPaid,
                    transfer.toParticipantKey,
                    selectedParticipantKeys
            ));
        }
        return items;
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.PaymentCard> buildArchiveSummaryHistoryPaymentCards(
            @NonNull List<ArchiveSummaryTransfer> transfers,
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        for (ReceiptHistoryStore.HistoryEntry receipt : archiveReceipts) {
            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                participantsByKey.putIfAbsent(participant.key, participant);
            }
        }

        ArrayList<ReceiptHistoryStore.PaymentCard> paymentCards = new ArrayList<>();
        for (int index = 0; index < transfers.size(); index++) {
            ArchiveSummaryTransfer transfer = transfers.get(index);
            ReceiptHistoryStore.ParticipantShare senderParticipant =
                    participantsByKey.get(transfer.fromParticipantKey);
            ReceiptHistoryStore.ParticipantShare recipientParticipant =
                    participantsByKey.get(transfer.toParticipantKey);
            paymentCards.add(new ReceiptHistoryStore.PaymentCard(
                    ReceiptHistoryStore.buildPaymentCardId(index),
                    formatCurrency(transfer.amount),
                    resolveHistoryPaymentCardPhoneNumber(recipientParticipant),
                    transfer.hasPaid
            ));
        }
        return paymentCards;
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.PaymentCard> buildReceiptHistoryPaymentCards(
            @NonNull ReceiptHistoryStore.HistoryEntry receipt
    ) {
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        LinkedHashMap<String, BigDecimal> balancesByKey = new LinkedHashMap<>();
        for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
            participantsByKey.put(participant.key, participant);
            balancesByKey.put(participant.key, BigDecimal.ZERO);
        }

        ReceiptHistoryStore.ParticipantShare defaultPayer = findArchiveSummaryPayer(receipt);
        if (defaultPayer != null) {
            participantsByKey.putIfAbsent(defaultPayer.key, defaultPayer);
            balancesByKey.putIfAbsent(defaultPayer.key, BigDecimal.ZERO);
        }

        for (ReceiptHistoryStore.HistoryItem item : receipt.items) {
            ReceiptHistoryStore.ParticipantShare itemPayer =
                    findArchiveSummaryItemPayer(item, receipt, defaultPayer);
            if (itemPayer == null) {
                continue;
            }

            participantsByKey.putIfAbsent(itemPayer.key, itemPayer);
            balancesByKey.putIfAbsent(itemPayer.key, BigDecimal.ZERO);

            int selectedParticipantCount =
                    countArchivedReceiptSelectedParticipants(item, receipt.participants);
            if (selectedParticipantCount == 0) {
                continue;
            }

            BigDecimal itemAmount = parseCurrencyAmount(item.price);
            BigDecimal sharedAmount = itemAmount.divide(
                    BigDecimal.valueOf(selectedParticipantCount),
                    2,
                    RoundingMode.HALF_UP
            );

            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                if (!item.isParticipantSelected(participant.key)
                        || participant.key.equals(itemPayer.key)) {
                    continue;
                }

                balancesByKey.put(
                        itemPayer.key,
                        balancesByKey.get(itemPayer.key).add(sharedAmount)
                );
                balancesByKey.put(
                        participant.key,
                        balancesByKey.get(participant.key).subtract(sharedAmount)
                );
            }
        }

        ArrayList<ArchiveSummaryBalance> creditors = new ArrayList<>();
        ArrayList<ArchiveSummaryBalance> debtors = new ArrayList<>();
        for (String participantKey : balancesByKey.keySet()) {
            BigDecimal balance = balancesByKey.get(participantKey).setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new ArchiveSummaryBalance(
                        participantsByKey.get(participantKey),
                        balance
                ));
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new ArchiveSummaryBalance(
                        participantsByKey.get(participantKey),
                        balance.abs()
                ));
            }
        }

        ArrayList<ReceiptHistoryStore.PaymentCard> paymentCards = new ArrayList<>();
        int paymentCardIndex = 0;
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((first, second) -> second.amount.compareTo(first.amount));
            debtors.sort((first, second) -> second.amount.compareTo(first.amount));

            ArchiveSummaryBalance creditor = creditors.get(0);
            ArchiveSummaryBalance debtor = debtors.get(0);
            BigDecimal transferAmount = creditor.amount.min(debtor.amount)
                    .setScale(2, RoundingMode.HALF_UP);

            paymentCards.add(new ReceiptHistoryStore.PaymentCard(
                    ReceiptHistoryStore.buildPaymentCardId(paymentCardIndex),
                    formatCurrency(transferAmount),
                    resolveHistoryPaymentCardPhoneNumber(creditor.participant),
                    debtor.participant.hasPaid
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

        return paymentCards;
    }

    @NonNull
    private String resolveHistoryPaymentCardPhoneNumber(
            @Nullable ReceiptHistoryStore.ParticipantShare participant
    ) {
        if (participant == null) {
            return "";
        }

        String phoneNumber = normalizeWhitespace(participant.phoneNumber);
        if (!phoneNumber.isEmpty()) {
            return phoneNumber;
        }

        if (isDefaultParticipant(participant)) {
            return normalizeWhitespace(AppSettings.getLoginPhoneNumber(this));
        }

        return "";
    }

    @NonNull
    private String buildArchiveSummaryHistoryMessage(
            @NonNull List<ArchiveSummaryTransfer> transfers
    ) {
        if (transfers.isEmpty()) {
            return getString(R.string.archive_summary_empty);
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int index = 0; index < transfers.size(); index++) {
            ArchiveSummaryTransfer transfer = transfers.get(index);
            if (index > 0) {
                messageBuilder.append("\n");
            }
            messageBuilder.append(getString(
                    R.string.history_archive_summary_transfer_line,
                    transfer.fromParticipantName,
                    transfer.toParticipantName,
                    formatCurrency(transfer.amount)
            ));
        }
        return messageBuilder.toString();
    }

    @NonNull
    private ArrayList<ArchiveSummaryTransfer> buildArchiveSummaryTransfers(
            @NonNull List<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        LinkedHashMap<String, ReceiptHistoryStore.ParticipantShare> participantsByKey =
                new LinkedHashMap<>();
        LinkedHashMap<String, BigDecimal> balancesByKey = new LinkedHashMap<>();

        for (ReceiptHistoryStore.HistoryEntry receipt : archiveReceipts) {
            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                participantsByKey.putIfAbsent(participant.key, participant);
                balancesByKey.putIfAbsent(participant.key, BigDecimal.ZERO);
            }

            ReceiptHistoryStore.ParticipantShare defaultPayer = findArchiveSummaryPayer(receipt);
            if (defaultPayer != null) {
                participantsByKey.putIfAbsent(defaultPayer.key, defaultPayer);
                balancesByKey.putIfAbsent(defaultPayer.key, BigDecimal.ZERO);
            }

            for (ReceiptHistoryStore.HistoryItem item : receipt.items) {
                ReceiptHistoryStore.ParticipantShare itemPayer =
                        findArchiveSummaryItemPayer(item, receipt, defaultPayer);
                if (itemPayer == null) {
                    continue;
                }

                participantsByKey.putIfAbsent(itemPayer.key, itemPayer);
                balancesByKey.putIfAbsent(itemPayer.key, BigDecimal.ZERO);

                int selectedParticipantCount =
                        countArchivedReceiptSelectedParticipants(item, receipt.participants);
                if (selectedParticipantCount == 0) {
                    continue;
                }

                BigDecimal itemAmount = parseCurrencyAmount(item.price);
                BigDecimal sharedAmount = itemAmount.divide(
                        BigDecimal.valueOf(selectedParticipantCount),
                        2,
                        RoundingMode.HALF_UP
                );

                for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                    if (!item.isParticipantSelected(participant.key)
                            || participant.key.equals(itemPayer.key)) {
                        continue;
                    }

                    balancesByKey.put(
                            itemPayer.key,
                            balancesByKey.get(itemPayer.key).add(sharedAmount)
                    );
                    balancesByKey.put(
                            participant.key,
                            balancesByKey.get(participant.key).subtract(sharedAmount)
                    );
                }
            }
        }

        ArrayList<ArchiveSummaryBalance> creditors = new ArrayList<>();
        ArrayList<ArchiveSummaryBalance> debtors = new ArrayList<>();
        for (String participantKey : balancesByKey.keySet()) {
            BigDecimal balance = balancesByKey.get(participantKey).setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new ArchiveSummaryBalance(
                        participantsByKey.get(participantKey),
                        balance
                ));
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new ArchiveSummaryBalance(
                        participantsByKey.get(participantKey),
                        balance.abs()
                ));
            }
        }

        ArrayList<ArchiveSummaryTransfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((first, second) -> second.amount.compareTo(first.amount));
            debtors.sort((first, second) -> second.amount.compareTo(first.amount));

            ArchiveSummaryBalance creditor = creditors.get(0);
            ArchiveSummaryBalance debtor = debtors.get(0);
            BigDecimal transferAmount = creditor.amount.min(debtor.amount)
                    .setScale(2, RoundingMode.HALF_UP);

            transfers.add(new ArchiveSummaryTransfer(
                    getArchiveSummaryParticipantDisplayName(debtor.participant),
                    getArchiveSummaryParticipantDisplayName(creditor.participant),
                    debtor.participant.key,
                    creditor.participant.key,
                    false,
                    transferAmount
            ));

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

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findArchiveSummaryPayer(
            @NonNull ReceiptHistoryStore.HistoryEntry receipt
    ) {
        for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
            if (participant.isCrowned) {
                return participant;
            }
        }
        for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
            if (isDefaultParticipant(participant)) {
                return participant;
            }
        }
        if (receipt.participants.isEmpty()) {
            return null;
        }
        return receipt.participants.get(0);
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findArchiveSummaryItemPayer(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ReceiptHistoryStore.HistoryEntry receipt,
            @Nullable ReceiptHistoryStore.ParticipantShare defaultPayer
    ) {
        String payerParticipantKey = normalizeWhitespace(item.payerParticipantKey);
        if (!payerParticipantKey.isEmpty()) {
            for (ReceiptHistoryStore.ParticipantShare participant : receipt.participants) {
                if (participant.key.equals(payerParticipantKey)) {
                    return participant;
                }
            }
        }
        return defaultPayer;
    }

    @NonNull
    private String getArchiveSummaryParticipantDisplayName(
            @NonNull ReceiptHistoryStore.ParticipantShare participant
    ) {
        return isDefaultParticipant(participant)
                ? DEFAULT_PARTICIPANT_NAME
                : normalizeWhitespace(participant.name);
    }

    private void showEditArchivedReceiptNameDialog(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull TextView titleView,
            @NonNull Runnable refreshReceiptDetails
    ) {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_new_archive_receipt,
                null
        );
        TextInputEditText receiptNameInput = dialogView.findViewById(R.id.input_receipt_name);
        MaterialButton applyButton = dialogView.findViewById(R.id.button_create_receipt);

        receiptNameInput.setText(editState.receiptName);
        if (receiptNameInput.getText() != null) {
            receiptNameInput.setSelection(receiptNameInput.getText().length());
        }
        applyButton.setText(R.string.apply);
        applyButton.setEnabled(false);

        receiptNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String updatedReceiptName = getText(receiptNameInput);
                applyButton.setEnabled(
                        !updatedReceiptName.isEmpty()
                                && !updatedReceiptName.equals(editState.receiptName)
                );
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_receipt_name_title)
                .setView(dialogView)
                .create();

        applyButton.setOnClickListener(view -> {
            String updatedReceiptName = getText(receiptNameInput);
            if (updatedReceiptName.isEmpty() || updatedReceiptName.equals(editState.receiptName)) {
                applyButton.setEnabled(false);
                return;
            }

            editState.receiptName = updatedReceiptName;
            titleView.setText(updatedReceiptName);
            refreshReceiptDetails.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showCreateArchiveReceiptDialog(
            int archiveIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull ArchiveReceiptEntriesAdapter receiptsAdapter,
            @NonNull MaterialButton archiveSummaryButton,
            @NonNull ListView receiptsListView
    ) {
        showCreateArchiveReceiptDialog(archiveIndex, newReceiptEntry -> {
            archiveReceipts.add(0, newReceiptEntry);
            receiptsAdapter.notifyDataSetChanged();
            archiveSummaryButton.setEnabled(true);
            receiptsListView.post(() -> receiptsListView.smoothScrollToPosition(0));
        });
    }

    private void showCreateArchiveReceiptDialog(
            int archiveIndex,
            @Nullable OnArchiveReceiptCreatedListener onArchiveReceiptCreatedListener
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
            loadArchiveNames();
            dialog.dismiss();
            if (onArchiveReceiptCreatedListener != null) {
                onArchiveReceiptCreatedListener.onArchiveReceiptCreated(newReceiptEntry);
            }
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
        editState.visibleItemSources.clear();
        for (ReceiptHistoryStore.HistoryItem sourceItem : editState.allItems) {
            editState.items.add(sourceItem);
            ArrayList<ReceiptHistoryStore.HistoryItem> sourceItems = new ArrayList<>();
            sourceItems.add(sourceItem);
            editState.visibleItemSources.put(sourceItem, sourceItems);
        }

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

    private int getArchivedReceiptItemQuantity(@NonNull ReceiptHistoryStore.HistoryItem item) {
        String normalizedName = normalizeWhitespace(item.name);
        if (normalizedName.startsWith("(")) {
            int closeParenIndex = normalizedName.indexOf(')');
            if (closeParenIndex > 1 && closeParenIndex < normalizedName.length() - 1) {
                String quantityText = normalizedName.substring(1, closeParenIndex).trim();
                try {
                    int parsedQuantity = Integer.parseInt(quantityText);
                    if (parsedQuantity >= MIN_RECEIPT_ITEM_QUANTITY) {
                        return parsedQuantity;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return MIN_RECEIPT_ITEM_QUANTITY;
    }

    @NonNull
    private String getArchivedReceiptItemCanonicalName(@NonNull String itemName) {
        String canonicalName = normalizeWhitespace(receiptParser.getCanonicalItemName(itemName));
        return canonicalName.isEmpty() ? itemName.trim() : canonicalName;
    }

    @NonNull
    private String buildArchivedReceiptItemDisplayName(
            @NonNull String canonicalName,
            int quantity
    ) {
        int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        if (normalizedQuantity <= 1) {
            return canonicalName;
        }
        return "(" + normalizedQuantity + ") " + canonicalName;
    }

    private int getArchivedReceiptItemUnitAmountCents(
            @NonNull ReceiptHistoryStore.HistoryItem item
    ) {
        return divideArchivedReceiptAmountCents(
                parseArchivedReceiptItemPriceToCents(item.price),
                getArchivedReceiptItemQuantity(item)
        );
    }

    private int divideArchivedReceiptAmountCents(int totalAmountCents, int quantity) {
        int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        BigDecimal unitAmount = BigDecimal.valueOf(totalAmountCents, 2).divide(
                BigDecimal.valueOf(normalizedQuantity),
                2,
                RoundingMode.HALF_UP
        );
        return unitAmount.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int multiplyArchivedReceiptAmountCents(int unitAmountCents, int quantity) {
        long multipliedAmount =
                (long) unitAmountCents * Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        if (multipliedAmount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (multipliedAmount < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) multipliedAmount;
    }

    @NonNull
    private ReceiptHistoryStore.HistoryItem createArchivedReceiptHistoryItem(
            @NonNull String itemName,
            int unitAmountCents,
            int quantity,
            boolean hasPaid,
            @NonNull String payerParticipantKey,
            @NonNull List<String> selectedParticipantKeys
    ) {
        String canonicalName = getArchivedReceiptItemCanonicalName(itemName);
        int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        return new ReceiptHistoryStore.HistoryItem(
                buildArchivedReceiptItemDisplayName(canonicalName, normalizedQuantity),
                receiptParser.formatAmount(
                        multiplyArchivedReceiptAmountCents(unitAmountCents, normalizedQuantity)
                ),
                hasPaid,
                payerParticipantKey,
                selectedParticipantKeys
        );
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
        TextInputEditText quantityInputView =
                dialogView.findViewById(R.id.edit_receipt_item_quantity);
        MaterialCardView payerSelectorView =
                dialogView.findViewById(R.id.button_receipt_item_payer_selector);
        AppCompatImageView payerValueSwatchView =
                dialogView.findViewById(R.id.image_receipt_item_payer_value_swatch);
        TextView payerValueView =
                dialogView.findViewById(R.id.text_receipt_item_payer_value);
        AppCompatImageButton payerMenuButton =
                dialogView.findViewById(R.id.button_receipt_item_payer_menu);
        MaterialButton decreaseQuantityButton =
                dialogView.findViewById(R.id.button_decrease_receipt_item_quantity);
        MaterialButton increaseQuantityButton =
                dialogView.findViewById(R.id.button_increase_receipt_item_quantity);
        MaterialButton addButton =
                dialogView.findViewById(R.id.button_add_receipt_item_confirm);
        final String[] selectedPayerParticipantKeyHolder = new String[]{""};

        priceInputView.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );
        updateArchivedReceiptItemPayerSummary(
                payerValueSwatchView,
                payerValueView,
                editState,
                selectedPayerParticipantKeyHolder[0]
        );
        setupArchivedReceiptItemQuantityControls(
                quantityInputView,
                decreaseQuantityButton,
                increaseQuantityButton
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_new_item_title)
                .setView(dialogView)
                .create();

        View.OnClickListener openPayerMenuClickListener = view -> {
            hideKeyboardForFocusedView(dialogView);
            toggleArchivedReceiptItemPayerMenu(
                    payerSelectorView,
                    payerMenuButton,
                    editState,
                    selectedPayerParticipantKeyHolder[0],
                    selectedPayerParticipantKey -> {
                        selectedPayerParticipantKeyHolder[0] = selectedPayerParticipantKey;
                        updateArchivedReceiptItemPayerSummary(
                                payerValueSwatchView,
                                payerValueView,
                                editState,
                                selectedPayerParticipantKeyHolder[0]
                        );
                    }
            );
        };
        payerSelectorView.setOnClickListener(openPayerMenuClickListener);
        payerMenuButton.setOnClickListener(openPayerMenuClickListener);
        addButton.setOnClickListener(view -> {
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

            int quantity = normalizeArchivedReceiptItemQuantity(quantityInputView);
            dismissArchivedReceiptItemPayerPopup();
            addArchivedReceiptItems(
                    editState,
                    itemName,
                    amountCents,
                    selectedPayerParticipantKeyHolder[0],
                    quantity
            );
            refreshReceiptDetails.run();
            dialog.dismiss();
        });
        dialog.setOnDismissListener(dialogInterface -> dismissArchivedReceiptItemPayerPopup());
        dialog.show();
    }

    private void setupArchivedReceiptItemQuantityControls(
            @NonNull TextInputEditText quantityInputView,
            @NonNull MaterialButton decreaseQuantityButton,
            @NonNull MaterialButton increaseQuantityButton
    ) {
        setArchivedReceiptItemQuantityValue(quantityInputView, MIN_RECEIPT_ITEM_QUANTITY);
        boolean[] isUpdatingQuantity = new boolean[]{false};
        Runnable refreshDecreaseButtonState = () -> decreaseQuantityButton.setEnabled(
                parseArchivedReceiptItemQuantity(getText(quantityInputView))
                        > MIN_RECEIPT_ITEM_QUANTITY
        );

        quantityInputView.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isUpdatingQuantity[0]) {
                    return;
                }

                String quantityText = editable == null ? "" : editable.toString().trim();
                if (quantityText.isEmpty()) {
                    refreshDecreaseButtonState.run();
                    return;
                }

                int parsedQuantity = parseArchivedReceiptItemQuantity(quantityText);
                int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, parsedQuantity);
                String normalizedQuantityText = String.valueOf(normalizedQuantity);
                if (!normalizedQuantityText.equals(quantityText)) {
                    isUpdatingQuantity[0] = true;
                    setArchivedReceiptItemQuantityValue(quantityInputView, normalizedQuantity);
                    isUpdatingQuantity[0] = false;
                }
                refreshDecreaseButtonState.run();
            }
        });
        quantityInputView.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                normalizeArchivedReceiptItemQuantity(quantityInputView);
            }
            refreshDecreaseButtonState.run();
        });
        decreaseQuantityButton.setOnClickListener(view -> {
            int quantity = normalizeArchivedReceiptItemQuantity(quantityInputView);
            setArchivedReceiptItemQuantityValue(
                    quantityInputView,
                    Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity - 1)
            );
            refreshDecreaseButtonState.run();
        });
        increaseQuantityButton.setOnClickListener(view -> {
            int quantity = Math.max(
                    MIN_RECEIPT_ITEM_QUANTITY,
                    parseArchivedReceiptItemQuantity(getText(quantityInputView))
            );
            if (quantity < Integer.MAX_VALUE) {
                quantity++;
            }
            setArchivedReceiptItemQuantityValue(quantityInputView, quantity);
            refreshDecreaseButtonState.run();
        });
        refreshDecreaseButtonState.run();
    }

    private int normalizeArchivedReceiptItemQuantity(@NonNull TextInputEditText quantityInputView) {
        int normalizedQuantity = Math.max(
                MIN_RECEIPT_ITEM_QUANTITY,
                parseArchivedReceiptItemQuantity(getText(quantityInputView))
        );
        setArchivedReceiptItemQuantityValue(quantityInputView, normalizedQuantity);
        return normalizedQuantity;
    }

    private int parseArchivedReceiptItemQuantity(@Nullable String quantityText) {
        String normalizedQuantityText = normalizeWhitespace(quantityText);
        if (normalizedQuantityText.isEmpty()) {
            return 0;
        }
        try {
            long parsedQuantity = Long.parseLong(normalizedQuantityText);
            if (parsedQuantity < MIN_RECEIPT_ITEM_QUANTITY) {
                return 0;
            }
            return parsedQuantity > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) parsedQuantity;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void setArchivedReceiptItemQuantityValue(
            @NonNull TextInputEditText quantityInputView,
            int quantity
    ) {
        String quantityText = String.valueOf(Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity));
        quantityInputView.setText(quantityText);
        if (quantityInputView.getText() != null) {
            quantityInputView.setSelection(quantityInputView.getText().length());
        }
    }

    private void addArchivedReceiptItem(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull String itemName,
            int amountCents,
            @Nullable String payerParticipantKey
    ) {
        addArchivedReceiptItems(editState, itemName, amountCents, payerParticipantKey, 1);
    }

    private void addArchivedReceiptItems(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull String itemName,
            int amountCents,
            @Nullable String payerParticipantKey,
            int quantity
    ) {
        ArrayList<String> selectedParticipantKeys = new ArrayList<>();
        for (ReceiptHistoryStore.ParticipantShare participant : editState.participants) {
            selectedParticipantKeys.add(participant.key);
        }

        int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        String normalizedPayerParticipantKey =
                normalizeArchivedReceiptItemPayerKey(editState, payerParticipantKey);
        ReceiptHistoryStore.HistoryItem item = createArchivedReceiptHistoryItem(
                itemName,
                amountCents,
                normalizedQuantity,
                false,
                normalizedPayerParticipantKey,
                new ArrayList<>(selectedParticipantKeys)
        );
        editState.allItems.add(item);
        rebuildArchivedReceiptVisibleItems(editState);
    }

    private void startArchivedReceiptScanMoreFlow(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        pendingScanMoreEditState = editState;
        pendingScanMoreRefreshRunnable = refreshReceiptDetails;
        Intent scanIntent = new Intent(this, NewReceiptActivity.class);
        scanIntent.putExtra(NewReceiptActivity.EXTRA_SCAN_ONLY_MODE, true);
        scanMoreReceiptItemsLauncher.launch(scanIntent);
    }

    private void handleScanMoreReceiptItemsResult(@NonNull ActivityResult activityResult) {
        ArchivedReceiptEditState editState = pendingScanMoreEditState;
        Runnable refreshReceiptDetails = pendingScanMoreRefreshRunnable;
        pendingScanMoreEditState = null;
        pendingScanMoreRefreshRunnable = null;

        if (activityResult.getResultCode() != RESULT_OK
                || activityResult.getData() == null
                || editState == null
                || refreshReceiptDetails == null) {
            return;
        }

        Intent resultData = activityResult.getData();
        ArrayList<String> itemNames =
                resultData.getStringArrayListExtra(
                        NewReceiptActivity.RESULT_EXTRA_SCANNED_ITEM_NAMES
                );
        ArrayList<Integer> itemAmountCents =
                resultData.getIntegerArrayListExtra(
                        NewReceiptActivity.RESULT_EXTRA_SCANNED_ITEM_AMOUNT_CENTS
                );
        if (itemNames == null
                || itemAmountCents == null
                || itemNames.size() != itemAmountCents.size()) {
            return;
        }

        for (int index = 0; index < itemNames.size(); index++) {
            String itemName = normalizeWhitespace(itemNames.get(index));
            Integer amountCents = itemAmountCents.get(index);
            if (itemName.isEmpty() || amountCents == null) {
                continue;
            }
            addArchivedReceiptItem(editState, itemName, amountCents, "");
        }
        refreshReceiptDetails.run();
    }

    private void toggleArchivedReceiptItemPayerMenu(
            @NonNull View anchorView,
            @NonNull AppCompatImageButton menuButton,
            @NonNull ArchivedReceiptEditState editState,
            @Nullable String selectedPayerParticipantKey,
            @NonNull ArchivedReceiptItemPayerSelectionListener selectionListener
    ) {
        if (archivedReceiptItemPayerPopup != null && archivedReceiptItemPayerPopup.isShowing()) {
            dismissArchivedReceiptItemPayerPopup();
            return;
        }
        showArchivedReceiptItemPayerMenu(
                anchorView,
                menuButton,
                editState,
                selectedPayerParticipantKey,
                selectionListener
        );
    }

    private void showArchivedReceiptItemPayerMenu(
            @NonNull View anchorView,
            @NonNull AppCompatImageButton menuButton,
            @NonNull ArchivedReceiptEditState editState,
            @Nullable String selectedPayerParticipantKey,
            @NonNull ArchivedReceiptItemPayerSelectionListener selectionListener
    ) {
        View popupView = getLayoutInflater().inflate(
                R.layout.popup_receipt_item_payer_menu,
                null
        );
        LinearLayout optionsLayout =
                popupView.findViewById(R.id.layout_receipt_item_payer_options);
        String normalizedSelectedPayerParticipantKey =
                normalizeArchivedReceiptItemPayerKey(editState, selectedPayerParticipantKey);

        addArchivedReceiptItemPayerOptionRow(
                optionsLayout,
                getString(R.string.filter_default),
                Color.WHITE,
                Color.BLACK,
                normalizedSelectedPayerParticipantKey.isEmpty(),
                () -> {
                    dismissArchivedReceiptItemPayerPopup();
                    selectionListener.onPayerSelected("");
                }
        );

        for (ReceiptHistoryStore.ParticipantShare participant : editState.participants) {
            addArchivedReceiptItemPayerDivider(optionsLayout);
            addArchivedReceiptItemPayerOptionRow(
                    optionsLayout,
                    participant.name,
                    participant.color,
                    null,
                    participant.key.equals(normalizedSelectedPayerParticipantKey),
                    () -> {
                        dismissArchivedReceiptItemPayerPopup();
                        selectionListener.onPayerSelected(participant.key);
                    }
            );
        }

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dpToPx(10));
        popupWindow.setOnDismissListener(() -> {
            if (archivedReceiptItemPayerPopup == popupWindow) {
                archivedReceiptItemPayerPopup = null;
            }
            setMenuExpanded(menuButton, false);
        });

        int popupWidth = popupView.getMeasuredWidth();
        int xOffset = Math.max(0, anchorView.getWidth() - popupWidth);
        popupWindow.showAsDropDown(anchorView, xOffset, dpToPx(8));
        archivedReceiptItemPayerPopup = popupWindow;
        setMenuExpanded(menuButton, true);
    }

    private void dismissArchivedReceiptItemPayerPopup() {
        if (archivedReceiptItemPayerPopup == null) {
            return;
        }
        archivedReceiptItemPayerPopup.dismiss();
        archivedReceiptItemPayerPopup = null;
    }

    private void addArchivedReceiptItemPayerOptionRow(
            @NonNull LinearLayout parentLayout,
            @NonNull String label,
            int fillColor,
            @Nullable Integer strokeColor,
            boolean selected,
            @NonNull Runnable onClick
    ) {
        View rowView = getLayoutInflater().inflate(
                R.layout.item_receipt_item_payer_option,
                parentLayout,
                false
        );
        AppCompatImageView swatchView =
                rowView.findViewById(R.id.image_receipt_item_payer_option_swatch);
        TextView labelView = rowView.findViewById(R.id.text_receipt_item_payer_option_label);

        swatchView.setBackground(
                createArchivedReceiptItemPayerSwatchDrawable(fillColor, strokeColor)
        );
        labelView.setText(label);
        rowView.setAlpha(selected ? 1f : 0.82f);
        rowView.setOnClickListener(view -> onClick.run());
        parentLayout.addView(rowView);
    }

    private void addArchivedReceiptItemPayerDivider(@NonNull LinearLayout parentLayout) {
        View dividerView = new View(this);
        dividerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        ));
        dividerView.setBackgroundColor(
                resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant, 0xFFD0D0D0)
        );
        parentLayout.addView(dividerView);
    }

    @NonNull
    private GradientDrawable createArchivedReceiptItemPayerSwatchDrawable(
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

    private void updateArchivedReceiptItemPayerSummary(
            @NonNull AppCompatImageView payerValueSwatchView,
            @NonNull TextView payerValueView,
            @NonNull ArchivedReceiptEditState editState,
            @Nullable String payerParticipantKey
    ) {
        String normalizedPayerParticipantKey =
                normalizeArchivedReceiptItemPayerKey(editState, payerParticipantKey);
        Integer strokeColor = normalizedPayerParticipantKey.isEmpty() ? Color.BLACK : null;
        int fillColor = Color.WHITE;
        if (!normalizedPayerParticipantKey.isEmpty()) {
            ReceiptHistoryStore.ParticipantShare participant =
                    findArchivedReceiptParticipantByKey(editState, normalizedPayerParticipantKey);
            if (participant != null) {
                fillColor = participant.color;
            }
        }
        payerValueSwatchView.setBackground(
                createArchivedReceiptItemPayerSwatchDrawable(fillColor, strokeColor)
        );
        payerValueView.setText(
                getArchivedReceiptItemPayerDisplayName(editState, normalizedPayerParticipantKey)
        );
    }

    @NonNull
    private String getArchivedReceiptItemPayerDisplayName(
            @NonNull ArchivedReceiptEditState editState,
            @Nullable String payerParticipantKey
    ) {
        String normalizedPayerParticipantKey =
                normalizeArchivedReceiptItemPayerKey(editState, payerParticipantKey);
        if (normalizedPayerParticipantKey.isEmpty()) {
            return getString(R.string.filter_default);
        }

        ReceiptHistoryStore.ParticipantShare participant =
                findArchivedReceiptParticipantByKey(editState, normalizedPayerParticipantKey);
        return participant == null ? getString(R.string.filter_default) : participant.name;
    }

    @NonNull
    private String normalizeArchivedReceiptItemPayerKey(
            @NonNull ArchivedReceiptEditState editState,
            @Nullable String payerParticipantKey
    ) {
        String normalizedPayerParticipantKey = normalizeWhitespace(payerParticipantKey);
        if (normalizedPayerParticipantKey.isEmpty()) {
            return "";
        }
        return findArchivedReceiptParticipantByKey(editState, normalizedPayerParticipantKey) == null
                ? ""
                : normalizedPayerParticipantKey;
    }

    @Nullable
    private ReceiptHistoryStore.ParticipantShare findArchivedReceiptParticipantByKey(
            @NonNull ArchivedReceiptEditState editState,
            @Nullable String participantKey
    ) {
        String normalizedParticipantKey = normalizeWhitespace(participantKey);
        if (normalizedParticipantKey.isEmpty()) {
            return null;
        }
        for (ReceiptHistoryStore.ParticipantShare participant : editState.participants) {
            if (participant.key.equals(normalizedParticipantKey)) {
                return participant;
            }
        }
        return null;
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
                    R.layout.item_receipt_view_participant_button,
                    participantsLayout,
                    false
            );
            MaterialButton badgeButton = rowView.findViewById(R.id.button_summary_participant_badge);
            AppCompatImageView ownerIconView =
                    rowView.findViewById(R.id.image_summary_participant_owner);
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);

            configureArchivedReceiptSummaryParticipantBadgeButton(badgeButton, participant);
            ownerIconView.setVisibility(
                    isCrownedParticipant(participant, editState) ? View.VISIBLE : View.GONE
            );
            nameView.setText(participant.name);
            amountView.setText(buildArchivedReceiptParticipantTotalDisplayText(
                    computeArchivedReceiptParticipantShareTotal(participant, editState),
                    receiptTotalAmount
            ));

            LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index < participants.size() - 1) {
                rowLayoutParams.bottomMargin = dpToPx(8);
            }
            rowView.setLayoutParams(rowLayoutParams);

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
            AppCompatImageView payerSwatchView =
                    itemView.findViewById(R.id.image_receipt_item_payer_swatch);
            TextView itemNameView = itemView.findViewById(R.id.text_receipt_item_name);
            TextView itemPriceView = itemView.findViewById(R.id.text_receipt_item_price);
            LinearLayout participantSelectionLayout =
                    itemView.findViewById(R.id.layout_receipt_item_participants);

            if (item != null) {
                String payerParticipantKey =
                        normalizeArchivedReceiptItemPayerKey(editState, item.payerParticipantKey);
                if (payerParticipantKey.isEmpty()) {
                    payerSwatchView.setVisibility(View.GONE);
                    payerSwatchView.setBackground(null);
                } else {
                    ReceiptHistoryStore.ParticipantShare payerParticipant =
                            findArchivedReceiptParticipantByKey(editState, payerParticipantKey);
                    if (payerParticipant == null) {
                        payerSwatchView.setVisibility(View.GONE);
                        payerSwatchView.setBackground(null);
                    } else {
                        payerSwatchView.setVisibility(View.VISIBLE);
                        payerSwatchView.setBackground(
                                createArchivedReceiptItemPayerSwatchDrawable(
                                        payerParticipant.color,
                                        null
                                )
                        );
                    }
                }
                itemNameView.setText(item.name);
                itemPriceView.setText(
                        getString(R.string.archive_summary_transfer_amount, item.price)
                );
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
                itemView.setOnClickListener(view -> showEditArchivedReceiptItemDialog(
                        item,
                        editState,
                        refreshReceiptDetailsHolder[0]
                ));
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
                payerSwatchView.setVisibility(View.GONE);
                payerSwatchView.setBackground(null);
                itemNameView.setText("");
                itemPriceView.setText("");
                participantSelectionLayout.removeAllViews();
                participantSelectionLayout.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
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
        List<ReceiptHistoryStore.HistoryItem> sourceItems =
                getArchivedReceiptVisibleSourceItems(editState, item);
        editState.allItems.removeAll(sourceItems);
        rebuildArchivedReceiptVisibleItems(editState);
        refreshReceiptDetails.run();
    }

    @NonNull
    private List<ReceiptHistoryStore.HistoryItem> getArchivedReceiptVisibleSourceItems(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull ReceiptHistoryStore.HistoryItem item
    ) {
        ArrayList<ReceiptHistoryStore.HistoryItem> sourceItems =
                editState.visibleItemSources.get(item);
        if (sourceItems != null && !sourceItems.isEmpty()) {
            return sourceItems;
        }

        ArrayList<ReceiptHistoryStore.HistoryItem> fallbackItems = new ArrayList<>();
        fallbackItems.add(item);
        return fallbackItems;
    }

    private void showEditArchivedReceiptItemDialog(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        List<ReceiptHistoryStore.HistoryItem> sourceItems =
                getArchivedReceiptVisibleSourceItems(editState, item);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_receipt_item, null);
        TextInputLayout nameInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_item_name);
        TextInputLayout priceInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_item_price);
        TextInputEditText nameInputView =
                dialogView.findViewById(R.id.edit_receipt_item_name);
        TextInputEditText priceInputView =
                dialogView.findViewById(R.id.edit_receipt_item_price);
        TextInputEditText quantityInputView =
                dialogView.findViewById(R.id.edit_receipt_item_quantity);
        MaterialCardView payerSelectorView =
                dialogView.findViewById(R.id.button_receipt_item_payer_selector);
        AppCompatImageView payerValueSwatchView =
                dialogView.findViewById(R.id.image_receipt_item_payer_value_swatch);
        TextView payerValueView =
                dialogView.findViewById(R.id.text_receipt_item_payer_value);
        AppCompatImageButton payerMenuButton =
                dialogView.findViewById(R.id.button_receipt_item_payer_menu);
        MaterialButton decreaseQuantityButton =
                dialogView.findViewById(R.id.button_decrease_receipt_item_quantity);
        MaterialButton increaseQuantityButton =
                dialogView.findViewById(R.id.button_increase_receipt_item_quantity);
        MaterialButton removeButton =
                dialogView.findViewById(R.id.button_remove_receipt_item);
        MaterialButton splitCombineButton =
                dialogView.findViewById(R.id.button_split_combine_receipt_item);

        String normalizedOriginalName = receiptParser.getCanonicalItemName(item.name);
        final String originalName = normalizedOriginalName.trim().isEmpty()
                ? item.name
                : normalizedOriginalName;
        int originalAmountCents = parseArchivedReceiptItemPriceToCents(sourceItems.get(0).price);
        int originalQuantity = getArchivedReceiptItemQuantity(item);
        int originalUnitAmountCents = getArchivedReceiptItemUnitAmountCents(item);
        String originalPayerParticipantKey =
                normalizeArchivedReceiptItemPayerKey(editState, item.payerParticipantKey);
        final String[] selectedPayerParticipantKeyHolder =
                new String[]{originalPayerParticipantKey};

        nameInputView.setText(originalName);
        if (nameInputView.getText() != null) {
            nameInputView.setSelection(nameInputView.getText().length());
        }
        priceInputView.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );
        priceInputView.setText(receiptParser.formatAmount(originalUnitAmountCents));
        if (priceInputView.getText() != null) {
            priceInputView.setSelection(priceInputView.getText().length());
        }
        setupArchivedReceiptItemQuantityControls(
                quantityInputView,
                decreaseQuantityButton,
                increaseQuantityButton
        );
        setArchivedReceiptItemQuantityValue(quantityInputView, originalQuantity);
        updateArchivedReceiptItemPayerSummary(
                payerValueSwatchView,
                payerValueView,
                editState,
                selectedPayerParticipantKeyHolder[0]
        );
        Runnable refreshStructureButtonState = () -> updateArchivedReceiptItemStructureButton(
                splitCombineButton,
                item,
                editState,
                nameInputView,
                priceInputView,
                quantityInputView,
                selectedPayerParticipantKeyHolder[0]
        );
        refreshStructureButtonState.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_receipt_item_title)
                .setView(dialogView)
                .create();
        boolean[] itemRemoved = new boolean[]{false};
        boolean[] structureActionApplied = new boolean[]{false};

        View.OnClickListener openPayerMenuClickListener = view -> {
            hideKeyboardForFocusedView(dialogView);
            toggleArchivedReceiptItemPayerMenu(
                    payerSelectorView,
                    payerMenuButton,
                    editState,
                    selectedPayerParticipantKeyHolder[0],
                    selectedPayerParticipantKey -> {
                        selectedPayerParticipantKeyHolder[0] = selectedPayerParticipantKey;
                        updateArchivedReceiptItemPayerSummary(
                                payerValueSwatchView,
                                payerValueView,
                                editState,
                                selectedPayerParticipantKeyHolder[0]
                        );
                        refreshStructureButtonState.run();
                    }
            );
        };
        payerSelectorView.setOnClickListener(openPayerMenuClickListener);
        payerMenuButton.setOnClickListener(openPayerMenuClickListener);
        TextWatcher structureButtonTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshStructureButtonState.run();
            }
        };
        nameInputView.addTextChangedListener(structureButtonTextWatcher);
        priceInputView.addTextChangedListener(structureButtonTextWatcher);
        quantityInputView.addTextChangedListener(structureButtonTextWatcher);

        removeButton.setOnClickListener(view -> {
            itemRemoved[0] = true;
            dismissArchivedReceiptItemPayerPopup();
            removeArchivedReceiptItem(item, editState, refreshReceiptDetails);
            dialog.dismiss();
        });
        splitCombineButton.setOnClickListener(view -> {
            if (applyArchivedReceiptItemStructureAction(
                    item,
                    editState,
                    nameInputLayout,
                    priceInputLayout,
                    nameInputView,
                    priceInputView,
                    quantityInputView,
                    selectedPayerParticipantKeyHolder[0],
                    refreshReceiptDetails
            )) {
                structureActionApplied[0] = true;
                dismissArchivedReceiptItemPayerPopup();
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(dialogInterface -> {
            dismissArchivedReceiptItemPayerPopup();
            if (itemRemoved[0] || structureActionApplied[0]) {
                return;
            }
            commitEditedArchivedReceiptItemIfValid(
                    item,
                    sourceItems,
                    editState,
                    originalName,
                    originalAmountCents,
                    originalQuantity,
                    originalUnitAmountCents,
                    originalPayerParticipantKey,
                    selectedPayerParticipantKeyHolder[0],
                    nameInputLayout,
                    priceInputLayout,
                    nameInputView,
                    priceInputView,
                    quantityInputView,
                    refreshReceiptDetails
            );
        });
        dialog.show();
    }

    private void commitEditedArchivedReceiptItemIfValid(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull List<ReceiptHistoryStore.HistoryItem> sourceItems,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull String originalName,
            int originalAmountCents,
            int originalQuantity,
            int originalUnitAmountCents,
            @NonNull String originalPayerParticipantKey,
            @Nullable String selectedPayerParticipantKey,
            @NonNull TextInputLayout nameInputLayout,
            @NonNull TextInputLayout priceInputLayout,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            @NonNull Runnable refreshReceiptDetails
    ) {
        String itemName = getText(nameInputView);
        String enteredPrice = getText(priceInputView);
        int updatedQuantity = normalizeArchivedReceiptItemQuantity(quantityInputView);

        nameInputLayout.setError(null);
        priceInputLayout.setError(null);

        if (itemName.isEmpty()) {
            return;
        }

        Integer updatedUnitAmountCents = receiptParser.parseEnteredPriceToCents(enteredPrice);
        if (updatedUnitAmountCents == null) {
            return;
        }

        String normalizedSelectedPayerParticipantKey =
                normalizeArchivedReceiptItemPayerKey(editState, selectedPayerParticipantKey);
        if (itemName.equals(originalName)
                && updatedUnitAmountCents == originalUnitAmountCents
                && updatedQuantity == originalQuantity
                && originalPayerParticipantKey.equals(normalizedSelectedPayerParticipantKey)) {
            return;
        }

        ArrayList<ReceiptHistoryStore.HistoryItem> updatedItems = new ArrayList<>();
        updatedItems.add(createArchivedReceiptHistoryItem(
                itemName,
                updatedUnitAmountCents,
                updatedQuantity,
                item.hasPaid,
                normalizedSelectedPayerParticipantKey,
                new ArrayList<>(item.selectedParticipantKeys)
        ));
        replaceArchivedReceiptItems(editState, sourceItems, updatedItems);
        refreshReceiptDetails.run();
    }

    private void replaceArchivedReceiptItems(
            @NonNull ArchivedReceiptEditState editState,
            @NonNull List<ReceiptHistoryStore.HistoryItem> originalItems,
            @NonNull List<ReceiptHistoryStore.HistoryItem> updatedItems
    ) {
        if (originalItems.isEmpty()) {
            return;
        }

        int sourceIndex = editState.allItems.indexOf(originalItems.get(0));
        if (sourceIndex < 0) {
            return;
        }

        editState.allItems.removeAll(originalItems);
        editState.allItems.addAll(sourceIndex, updatedItems);
        rebuildArchivedReceiptVisibleItems(editState);
    }

    private void updateArchivedReceiptItemStructureButton(
            @NonNull MaterialButton structureButton,
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            @Nullable String selectedPayerParticipantKey
    ) {
        int quantity = Math.max(
                MIN_RECEIPT_ITEM_QUANTITY,
                parseArchivedReceiptItemQuantity(getText(quantityInputView))
        );
        if (quantity > 1) {
            structureButton.setText(R.string.split);
            structureButton.setIconResource(R.drawable.ic_edit_receipt_item_split);
            structureButton.setEnabled(canApplyArchivedReceiptItemStructureAction(
                    item,
                    editState,
                    nameInputView,
                    priceInputView,
                    quantityInputView,
                    selectedPayerParticipantKey
            ));
            return;
        }

        structureButton.setText(R.string.combine);
        structureButton.setIconResource(R.drawable.ic_edit_receipt_item_combine);
        structureButton.setEnabled(canCombineArchivedReceiptItem(
                item,
                editState,
                nameInputView,
                priceInputView,
                quantityInputView,
                selectedPayerParticipantKey
        ));
    }

    private boolean canApplyArchivedReceiptItemStructureAction(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            @Nullable String selectedPayerParticipantKey
    ) {
        return buildEditedArchivedReceiptItemFromInputs(
                item,
                editState,
                null,
                null,
                nameInputView,
                priceInputView,
                quantityInputView,
                selectedPayerParticipantKey
        ) != null;
    }

    private boolean canCombineArchivedReceiptItem(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            @Nullable String selectedPayerParticipantKey
    ) {
        ReceiptHistoryStore.HistoryItem updatedItem = buildEditedArchivedReceiptItemFromInputs(
                item,
                editState,
                null,
                null,
                nameInputView,
                priceInputView,
                quantityInputView,
                selectedPayerParticipantKey
        );
        if (updatedItem == null || getArchivedReceiptItemQuantity(updatedItem) != MIN_RECEIPT_ITEM_QUANTITY) {
            return false;
        }
        return !getArchivedReceiptItemsToCombine(item, updatedItem, editState).isEmpty();
    }

    private boolean applyArchivedReceiptItemStructureAction(
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull TextInputLayout nameInputLayout,
            @NonNull TextInputLayout priceInputLayout,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            @Nullable String selectedPayerParticipantKey,
            @NonNull Runnable refreshReceiptDetails
    ) {
        ReceiptHistoryStore.HistoryItem updatedItem = buildEditedArchivedReceiptItemFromInputs(
                item,
                editState,
                nameInputLayout,
                priceInputLayout,
                nameInputView,
                priceInputView,
                quantityInputView,
                selectedPayerParticipantKey
        );
        if (updatedItem == null) {
            return false;
        }

        if (getArchivedReceiptItemQuantity(updatedItem) > MIN_RECEIPT_ITEM_QUANTITY) {
            splitArchivedReceiptItem(item, updatedItem, editState);
            refreshReceiptDetails.run();
            return true;
        }

        ArrayList<ReceiptHistoryStore.HistoryItem> itemsToCombine =
                getArchivedReceiptItemsToCombine(item, updatedItem, editState);
        if (itemsToCombine.isEmpty()) {
            return false;
        }

        combineArchivedReceiptItems(item, updatedItem, itemsToCombine, editState);
        refreshReceiptDetails.run();
        return true;
    }

    @Nullable
    private ReceiptHistoryStore.HistoryItem buildEditedArchivedReceiptItemFromInputs(
            @NonNull ReceiptHistoryStore.HistoryItem originalItem,
            @NonNull ArchivedReceiptEditState editState,
            @Nullable TextInputLayout nameInputLayout,
            @Nullable TextInputLayout priceInputLayout,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            @Nullable String selectedPayerParticipantKey
    ) {
        if (nameInputLayout != null) {
            nameInputLayout.setError(null);
        }
        if (priceInputLayout != null) {
            priceInputLayout.setError(null);
        }

        String itemName = getText(nameInputView);
        if (itemName.isEmpty()) {
            if (nameInputLayout != null) {
                nameInputLayout.setError(getString(R.string.receipt_item_name_required));
            }
            return null;
        }

        Integer updatedUnitAmountCents =
                receiptParser.parseEnteredPriceToCents(getText(priceInputView));
        if (updatedUnitAmountCents == null) {
            if (priceInputLayout != null) {
                priceInputLayout.setError(getString(R.string.invalid_receipt_price));
            }
            return null;
        }

        int updatedQuantity = Math.max(
                MIN_RECEIPT_ITEM_QUANTITY,
                parseArchivedReceiptItemQuantity(getText(quantityInputView))
        );
        return createArchivedReceiptHistoryItem(
                itemName,
                updatedUnitAmountCents,
                updatedQuantity,
                originalItem.hasPaid,
                normalizeArchivedReceiptItemPayerKey(editState, selectedPayerParticipantKey),
                new ArrayList<>(originalItem.selectedParticipantKeys)
        );
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.HistoryItem> getArchivedReceiptItemsToCombine(
            @NonNull ReceiptHistoryStore.HistoryItem originalItem,
            @NonNull ReceiptHistoryStore.HistoryItem updatedItem,
            @NonNull ArchivedReceiptEditState editState
    ) {
        ArrayList<ReceiptHistoryStore.HistoryItem> itemsToCombine = new ArrayList<>();
        for (ReceiptHistoryStore.HistoryItem candidateItem : editState.items) {
            if (candidateItem == originalItem) {
                continue;
            }
            if (areArchivedReceiptItemsCompatibleForCombine(updatedItem, candidateItem)) {
                itemsToCombine.add(candidateItem);
            }
        }
        return itemsToCombine;
    }

    private boolean areArchivedReceiptItemsCompatibleForCombine(
            @NonNull ReceiptHistoryStore.HistoryItem anchorItem,
            @NonNull ReceiptHistoryStore.HistoryItem candidateItem
    ) {
        if (!getArchivedReceiptItemCanonicalName(anchorItem.name).equalsIgnoreCase(
                getArchivedReceiptItemCanonicalName(candidateItem.name)
        )) {
            return false;
        }
        if (getArchivedReceiptItemUnitAmountCents(anchorItem) != getArchivedReceiptItemUnitAmountCents(candidateItem)) {
            return false;
        }
        if (!normalizeWhitespace(anchorItem.payerParticipantKey).equals(
                normalizeWhitespace(candidateItem.payerParticipantKey)
        )) {
            return false;
        }
        if (anchorItem.hasPaid != candidateItem.hasPaid) {
            return false;
        }
        return anchorItem.selectedParticipantKeys.equals(candidateItem.selectedParticipantKeys);
    }

    private void splitArchivedReceiptItem(
            @NonNull ReceiptHistoryStore.HistoryItem originalItem,
            @NonNull ReceiptHistoryStore.HistoryItem updatedItem,
            @NonNull ArchivedReceiptEditState editState
    ) {
        int itemIndex = editState.allItems.indexOf(originalItem);
        if (itemIndex < 0) {
            return;
        }

        String canonicalName = getArchivedReceiptItemCanonicalName(updatedItem.name);
        int quantity = getArchivedReceiptItemQuantity(updatedItem);
        int unitAmountCents = getArchivedReceiptItemUnitAmountCents(updatedItem);
        ArrayList<ReceiptHistoryStore.HistoryItem> splitItems = new ArrayList<>(quantity);
        for (int index = 0; index < quantity; index++) {
            splitItems.add(createArchivedReceiptHistoryItem(
                    canonicalName,
                    unitAmountCents,
                    MIN_RECEIPT_ITEM_QUANTITY,
                    updatedItem.hasPaid,
                    updatedItem.payerParticipantKey,
                    new ArrayList<>(updatedItem.selectedParticipantKeys)
            ));
        }

        editState.allItems.remove(itemIndex);
        editState.allItems.addAll(itemIndex, splitItems);
        rebuildArchivedReceiptVisibleItems(editState);
    }

    private void combineArchivedReceiptItems(
            @NonNull ReceiptHistoryStore.HistoryItem originalItem,
            @NonNull ReceiptHistoryStore.HistoryItem updatedItem,
            @NonNull List<ReceiptHistoryStore.HistoryItem> itemsToCombine,
            @NonNull ArchivedReceiptEditState editState
    ) {
        int itemIndex = editState.allItems.indexOf(originalItem);
        if (itemIndex < 0) {
            return;
        }

        int combinedAmountCents = parseArchivedReceiptItemPriceToCents(updatedItem.price);
        int combinedQuantity = getArchivedReceiptItemQuantity(updatedItem);
        for (ReceiptHistoryStore.HistoryItem candidateItem : itemsToCombine) {
            combinedAmountCents += parseArchivedReceiptItemPriceToCents(candidateItem.price);
            combinedQuantity += getArchivedReceiptItemQuantity(candidateItem);
        }

        ReceiptHistoryStore.HistoryItem combinedItem = createArchivedReceiptHistoryItem(
                updatedItem.name,
                divideArchivedReceiptAmountCents(combinedAmountCents, combinedQuantity),
                combinedQuantity,
                updatedItem.hasPaid,
                updatedItem.payerParticipantKey,
                new ArrayList<>(updatedItem.selectedParticipantKeys)
        );

        editState.allItems.removeAll(itemsToCombine);
        int refreshedIndex = editState.allItems.indexOf(originalItem);
        if (refreshedIndex >= 0) {
            editState.allItems.remove(refreshedIndex);
        } else {
            refreshedIndex = Math.min(itemIndex, editState.allItems.size());
        }
        editState.allItems.add(refreshedIndex, combinedItem);
        rebuildArchivedReceiptVisibleItems(editState);
    }

    private int parseArchivedReceiptItemPriceToCents(@Nullable String priceText) {
        Integer parsedAmount = receiptParser.parseEnteredPriceToCents(normalizeWhitespace(priceText));
        if (parsedAmount != null) {
            return parsedAmount;
        }
        return parseCurrencyAmount(priceText)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private void bindArchivedReceiptItemParticipantButtons(
            @NonNull LinearLayout participantSelectionLayout,
            @NonNull ReceiptHistoryStore.HistoryItem item,
            @NonNull List<ReceiptHistoryStore.ParticipantShare> participants,
            @NonNull ArchivedReceiptEditState editState,
            @NonNull Runnable refreshReceiptDetails
    ) {
        List<ReceiptHistoryStore.HistoryItem> sourceItems =
                getArchivedReceiptVisibleSourceItems(editState, item);
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
                setArchivedReceiptParticipantSelection(
                        sourceItems,
                        item,
                        participant.key,
                        !item.isParticipantSelected(participant.key)
                );
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

    private void setArchivedReceiptParticipantSelection(
            @NonNull List<ReceiptHistoryStore.HistoryItem> sourceItems,
            @NonNull ReceiptHistoryStore.HistoryItem visibleItem,
            @NonNull String participantKey,
            boolean selected
    ) {
        if (selected) {
            visibleItem.selectedParticipantKeys.add(participantKey);
        } else {
            visibleItem.selectedParticipantKeys.remove(participantKey);
        }

        for (ReceiptHistoryStore.HistoryItem sourceItem : sourceItems) {
            if (selected) {
                if (!sourceItem.selectedParticipantKeys.contains(participantKey)) {
                    sourceItem.selectedParticipantKeys.add(participantKey);
                }
            } else {
                sourceItem.selectedParticipantKeys.remove(participantKey);
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
        TextView payerLabelView =
                dialogView.findViewById(R.id.text_participant_detail_payer_label);
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
        payerLabelView.setVisibility(View.VISIBLE);
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        boolean canRemoveParticipant = !isDefaultParticipant(participant);
        LinearLayout.LayoutParams removeButtonLayoutParams =
                (LinearLayout.LayoutParams) removeParticipantButton.getLayoutParams();
        removeButtonLayoutParams.setMarginEnd(0);
        removeParticipantButton.setLayoutParams(removeButtonLayoutParams);
        removeParticipantButton.setIconResource(R.drawable.ic_receipt_participant_remove);
        removeParticipantButton.setIconTint(ColorStateList.valueOf(
                removeParticipantButton.getCurrentTextColor()
        ));
        removeParticipantButton.setIconPadding(dpToPx(8));
        removeParticipantButton.setEnabled(canRemoveParticipant);
        if (canRemoveParticipant) {
            removeParticipantButton.setOnClickListener(view -> {
                removeArchivedReceiptParticipant(participant, editState, refreshReceiptDetails);
                dialog.dismiss();
            });
        }
        toggleParticipantItemsButton.setVisibility(View.GONE);
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
        return formatCurrency(participantAmount) + "kr";
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

    private void hideKeyboardForFocusedView(@NonNull View fallbackView) {
        View focusedView = fallbackView.findFocus();
        if (focusedView == null) {
            return;
        }

        fallbackView.requestFocus();

        InputMethodManager inputMethodManager =
                ContextCompat.getSystemService(this, InputMethodManager.class);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }

        focusedView.clearFocus();
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

    private interface ArchivedReceiptItemPayerSelectionListener {
        void onPayerSelected(@NonNull String payerParticipantKey);
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
                    item.payerParticipantKey,
                    new ArrayList<>(item.selectedParticipantKeys)
            ));
        }

        return new ArchivedReceiptEditState(
                entry.receiptName,
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
                    item.payerParticipantKey,
                    new ArrayList<>(item.selectedParticipantKeys)
            ));
        }

        return new ReceiptHistoryStore.HistoryEntry(
                editState.receiptName,
                formatCurrency(computeArchivedReceiptItemsTotal(editState)),
                originalEntry.sentDate,
                originalEntry.message,
                updatedParticipants,
                copiedItems
        );
    }

    private boolean hasArchivedReceiptChanges(
            @NonNull ReceiptHistoryStore.HistoryEntry originalEntry,
            @NonNull ArchivedReceiptEditState editState
    ) {
        if (!originalEntry.receiptName.equals(editState.receiptName)
                || !getArchivedReceiptOwnerKey(originalEntry).equals(editState.crownedParticipantKey)
                || originalEntry.participants.size() != editState.participants.size()
                || originalEntry.items.size() != editState.allItems.size()) {
            return true;
        }

        for (int index = 0; index < originalEntry.participants.size(); index++) {
            ReceiptHistoryStore.ParticipantShare originalParticipant =
                    originalEntry.participants.get(index);
            ReceiptHistoryStore.ParticipantShare editedParticipant =
                    editState.participants.get(index);
            if (!originalParticipant.key.equals(editedParticipant.key)
                    || !originalParticipant.name.equals(editedParticipant.name)
                    || !originalParticipant.initials.equals(editedParticipant.initials)
                    || originalParticipant.color != editedParticipant.color
                    || !originalParticipant.phoneNumber.equals(editedParticipant.phoneNumber)
                    || originalParticipant.hasPaid != editedParticipant.hasPaid) {
                return true;
            }
        }

        for (int index = 0; index < originalEntry.items.size(); index++) {
            ReceiptHistoryStore.HistoryItem originalItem = originalEntry.items.get(index);
            ReceiptHistoryStore.HistoryItem editedItem = editState.allItems.get(index);
            if (!originalItem.name.equals(editedItem.name)
                    || !originalItem.price.equals(editedItem.price)
                    || originalItem.selectedParticipantKeys.size()
                    != editedItem.selectedParticipantKeys.size()) {
                return true;
            }

            for (int selectedIndex = 0;
                 selectedIndex < originalItem.selectedParticipantKeys.size();
                 selectedIndex++) {
                if (!originalItem.selectedParticipantKeys.get(selectedIndex)
                        .equals(editedItem.selectedParticipantKeys.get(selectedIndex))) {
                    return true;
                }
            }
        }

        return false;
    }

    @NonNull
    private ArrayList<String> buildArchivedReceiptSaveChangesDisabledReasons(
            @NonNull ReceiptHistoryStore.HistoryEntry originalEntry,
            @NonNull ArchivedReceiptEditState editState
    ) {
        ArrayList<String> disabledReasons = new ArrayList<>();
        if (!hasArchivedReceiptChanges(originalEntry, editState)) {
            disabledReasons.add(getString(R.string.save_changes_disabled_reason_no_changes));
        }
        if (!hasArchivedReceiptSelections(editState)) {
            disabledReasons.add(
                    getString(R.string.next_disabled_reason_missing_participant_selection)
            );
        }
        return disabledReasons;
    }

    @NonNull
    private ArrayList<String> buildArchivedReceiptSummaryDisabledReasons(
            @NonNull ArchivedReceiptEditState editState
    ) {
        ArrayList<String> disabledReasons = new ArrayList<>();
        if (editState.allItems.isEmpty()) {
            disabledReasons.add(getString(R.string.next_disabled_reason_no_items));
        }
        if (editState.participants.size() <= 1) {
            disabledReasons.add(getString(R.string.next_disabled_reason_not_enough_participants));
        }
        if (!hasArchivedReceiptSelections(editState)) {
            disabledReasons.add(
                    getString(R.string.next_disabled_reason_missing_participant_selection)
            );
        }
        return disabledReasons;
    }

    private void showArchivedReceiptSaveChangesDisabledReasonsPopup(
            @NonNull MaterialButton saveChangesButton,
            @NonNull ArrayList<String> disabledReasons
    ) {
        if (disabledReasons.isEmpty()) {
            return;
        }
        if (archivedReceiptSaveChangesDisabledReasonsPopup != null
                && archivedReceiptSaveChangesDisabledReasonsPopup.isShowing()) {
            dismissArchivedReceiptSaveChangesDisabledReasonsPopup();
            return;
        }

        View popupView = getLayoutInflater().inflate(
                R.layout.popup_next_button_disabled_reasons,
                null
        );
        TextView titleView = popupView.findViewById(R.id.text_next_disabled_title);
        LinearLayout reasonsLayout = popupView.findViewById(R.id.layout_next_disabled_reasons);
        titleView.setText(R.string.next_disabled_reasons);

        for (int index = 0; index < disabledReasons.size(); index++) {
            TextView reasonView = new TextView(this);
            reasonView.setText("\u2022 " + disabledReasons.get(index));
            TextViewCompat.setTextAppearance(
                    reasonView,
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
            );
            reasonView.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK));
            if (index < disabledReasons.size() - 1) {
                reasonView.setPadding(0, 0, 0, dpToPx(8));
            }
            reasonsLayout.addView(reasonView);
        }

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dpToPx(10));
        popupWindow.setOnDismissListener(() -> {
            if (archivedReceiptSaveChangesDisabledReasonsPopup == popupWindow) {
                archivedReceiptSaveChangesDisabledReasonsPopup = null;
            }
        });

        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();
        int xOffset = Math.max(0, saveChangesButton.getWidth() - popupWidth);
        int yOffset = -(saveChangesButton.getHeight() + popupHeight + dpToPx(8));
        popupWindow.showAsDropDown(saveChangesButton, xOffset, yOffset);
        archivedReceiptSaveChangesDisabledReasonsPopup = popupWindow;
    }

    private void dismissArchivedReceiptSaveChangesDisabledReasonsPopup() {
        if (archivedReceiptSaveChangesDisabledReasonsPopup == null) {
            return;
        }
        archivedReceiptSaveChangesDisabledReasonsPopup.dismiss();
        archivedReceiptSaveChangesDisabledReasonsPopup = null;
    }

    private void bindArchiveReceiptIncompleteStatusIcon(
            @NonNull View itemView,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            boolean insideFolder
    ) {
        AppCompatImageView statusIconView = itemView.findViewById(R.id.image_history_receipt_status);
        if (statusIconView == null) {
            return;
        }

        boolean summaryDisabled = isArchivedReceiptSummaryDisabled(entry);
        if (!summaryDisabled) {
            statusIconView.setVisibility(View.GONE);
            statusIconView.setOnClickListener(null);
            statusIconView.setClickable(false);
            statusIconView.setFocusable(false);
            return;
        }

        statusIconView.setVisibility(View.VISIBLE);
        statusIconView.setImageResource(R.drawable.ic_warning_outline);
        statusIconView.setImageTintList(ColorStateList.valueOf(
                resolveThemeColor(
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        Color.GRAY
                )
        ));
        statusIconView.setContentDescription(getString(
                insideFolder
                        ? R.string.archive_receipt_incomplete_folder
                        : R.string.archive_receipt_incomplete
        ));
        ViewGroup.LayoutParams layoutParams = statusIconView.getLayoutParams();
        if (layoutParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frameLayoutParams =
                    (FrameLayout.LayoutParams) layoutParams;
            frameLayoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            frameLayoutParams.topMargin = 0;
            frameLayoutParams.bottomMargin = 0;
            frameLayoutParams.setMarginEnd(dpToPx(12));
            statusIconView.setLayoutParams(frameLayoutParams);
        }
        statusIconView.setClickable(true);
        statusIconView.setFocusable(true);
        statusIconView.setOnClickListener(
                view -> showArchiveReceiptIncompletePopup(view, insideFolder)
        );
    }

    private void showArchiveReceiptIncompletePopup(
            @NonNull View anchorView,
            boolean insideFolder
    ) {
        if (archiveReceiptIncompletePopup != null && archiveReceiptIncompletePopup.isShowing()) {
            archiveReceiptIncompletePopup.dismiss();
        }

        View popupView = getLayoutInflater().inflate(
                R.layout.popup_header_help_message,
                null
        );
        TextView messageView = popupView.findViewById(R.id.text_header_help_message);
        messageView.setText(
                insideFolder
                        ? R.string.archive_receipt_incomplete_folder
                        : R.string.archive_receipt_incomplete
        );

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(dpToPx(240), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dpToPx(10));
        popupWindow.setOnDismissListener(() -> {
            if (archiveReceiptIncompletePopup == popupWindow) {
                archiveReceiptIncompletePopup = null;
            }
        });

        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();
        int xOffset = anchorView.getWidth() - popupWidth;
        int yOffset = -(anchorView.getHeight() + popupHeight + dpToPx(8));
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
        archiveReceiptIncompletePopup = popupWindow;
    }

    private void dismissArchiveReceiptIncompletePopup() {
        if (archiveReceiptIncompletePopup == null) {
            return;
        }
        archiveReceiptIncompletePopup.dismiss();
        archiveReceiptIncompletePopup = null;
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
        crownButton.setImageTintList(ColorStateList.valueOf(
                resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK)
        ));
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
        selectionButton.setBackgroundTintList(ColorStateList.valueOf(
                isChecked ? participant.color : Color.TRANSPARENT
        ));
        selectionButton.setTextColor(
                isChecked ? getParticipantTextColor(participant.color) : buttonColor
        );
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

    private boolean isArchivedReceiptSummaryDisabled(
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        return !buildArchivedReceiptSummaryDisabledReasons(
                createArchivedReceiptEditState(entry)
        ).isEmpty();
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

    private int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private void setMenuExpanded(@Nullable AppCompatImageButton button, boolean expanded) {
        if (button == null) {
            return;
        }
        button.animate()
                .rotation(expanded ? 180f : 0f)
                .setDuration(MENU_ARROW_ROTATION_DURATION_MS)
                .start();
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

    private void toggleArchiveTree(@NonNull ArchiveRootItem rootItem, @NonNull View itemView) {
        ArchiveStore.Archive archive = rootItem.archive;
        if (archive == null) {
            return;
        }

        boolean expanded = expandedArchiveNames.contains(archive.name);
        if (expanded) {
            expandedArchiveNames.remove(archive.name);
        } else {
            expandedArchiveNames.add(archive.name);
        }
        bindArchiveTreeState(itemView, rootItem, !expanded, true);
    }

    private void bindArchiveTreeState(
            @NonNull View itemView,
            @NonNull ArchiveRootItem rootItem,
            boolean expanded,
            boolean animate
    ) {
        MaterialCardView treeCard = itemView.findViewById(R.id.card_archive_entry_tree);
        LinearLayout receiptsLayout = itemView.findViewById(R.id.layout_archive_entry_tree_receipts);
        TextView emptyView = itemView.findViewById(R.id.text_archive_entry_tree_empty);
        MaterialButton summaryButton = itemView.findViewById(R.id.button_archive_entry_summary);
        AppCompatImageView chevronView = itemView.findViewById(R.id.image_archive_entry_chevron);

        if (treeCard != null
                && receiptsLayout != null
                && emptyView != null
                && summaryButton != null
                && rootItem.archive != null) {
            if (expanded) {
                bindInlineArchiveReceiptViews(
                        receiptsLayout,
                        emptyView,
                        summaryButton,
                        rootItem.sourceIndex,
                        rootItem.archive.name,
                        rootItem.archive.receipts
                );
                treeCard.setVisibility(View.VISIBLE);
            } else {
                treeCard.setVisibility(View.GONE);
            }
        }

        if (chevronView == null) {
            return;
        }

        chevronView.animate().cancel();
        if (animate) {
            chevronView.animate()
                    .rotation(expanded ? ARCHIVE_TREE_EXPANDED_ROTATION_DEGREES : 0f)
                    .setDuration(ARCHIVE_TREE_TOGGLE_DURATION_MS)
                    .start();
        } else {
            chevronView.setRotation(expanded ? ARCHIVE_TREE_EXPANDED_ROTATION_DEGREES : 0f);
        }
    }

    private void bindInlineArchiveReceiptViews(
            @NonNull LinearLayout receiptsLayout,
            @NonNull TextView emptyView,
            @NonNull MaterialButton summaryButton,
            int archiveIndex,
            @NonNull String archiveName,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts
    ) {
        receiptsLayout.removeAllViews();
        if (archiveReceipts.isEmpty()) {
            receiptsLayout.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            summaryButton.setVisibility(View.GONE);
            return;
        }

        receiptsLayout.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        summaryButton.setVisibility(View.VISIBLE);
        summaryButton.setOnClickListener(
                view -> showArchiveSummaryDialog(
                        archiveIndex,
                        archiveName,
                        archiveReceipts
                )
        );
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int index = 0; index < archiveReceipts.size(); index++) {
            ReceiptHistoryStore.HistoryEntry entry = archiveReceipts.get(index);
            View rowView = inflater.inflate(R.layout.item_history_receipt, receiptsLayout, false);
            ViewGroup.LayoutParams layoutParams = rowView.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLayoutParams =
                        (ViewGroup.MarginLayoutParams) layoutParams;
                int compactVerticalMargin = dpToPx(8);
                marginLayoutParams.topMargin = compactVerticalMargin;
                marginLayoutParams.bottomMargin = compactVerticalMargin;
                rowView.setLayoutParams(marginLayoutParams);
            }
            TextView receiptNameView = rowView.findViewById(R.id.text_history_receipt_name);
            TextView totalAmountView = rowView.findViewById(R.id.text_history_receipt_total);

            receiptNameView.setText(entry.receiptName);
            totalAmountView.setText(
                    getString(R.string.archive_summary_transfer_amount, entry.totalAmount)
            );
            bindArchiveReceiptIncompleteStatusIcon(rowView, entry, true);

            final int receiptIndex = index;
            rowView.setClickable(true);
            rowView.setFocusable(true);
            rowView.setOnClickListener(view -> {
                dismissArchiveReceiptIncompletePopup();
                if (receiptIndex < 0 || receiptIndex >= archiveReceipts.size()) {
                    return;
                }

                showArchivedReceiptDetailsDialog(
                        archiveIndex,
                        receiptIndex,
                        archiveReceipts,
                        archiveReceipts.get(receiptIndex),
                        this::loadArchiveNames
                );
            });
            rowView.setOnTouchListener(new View.OnTouchListener() {
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
                            rowView,
                            downRawX,
                            downRawY,
                            archiveIndex,
                            receiptIndex,
                            archiveReceipts,
                            ArchiveActivity.this::loadArchiveNames
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
            receiptsLayout.addView(rowView);
        }
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

    private void showStandaloneReceiptActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            int receiptIndex
    ) {
        AnchoredDropdownMenuHelper.showActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                Arrays.asList(
                        new AnchoredDropdownMenuHelper.ActionItem(
                                R.string.remove,
                                R.drawable.ic_history_remove,
                                () -> showRemoveStandaloneReceiptDialog(receiptIndex)
                        ),
                        new AnchoredDropdownMenuHelper.ActionItem(
                                R.string.move,
                                R.drawable.ic_archive_move,
                                () -> showMoveStandaloneReceiptDialog(receiptIndex)
                        )
                )
        );
    }

    private void showRemoveStandaloneReceiptDialog(int receiptIndex) {
        if (receiptIndex < 0 || receiptIndex >= standaloneReceipts.size()) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_archive_remove_confirmation,
                null
        );
        TextView titleView = dialogView.findViewById(R.id.text_archive_remove_confirmation_title);
        MaterialButton noButton = dialogView.findViewById(R.id.button_archive_remove_no);
        MaterialButton yesButton = dialogView.findViewById(R.id.button_archive_remove_yes);

        titleView.setText(R.string.remove_receipt_confirmation_title);

        AlertDialog confirmationDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        noButton.setOnClickListener(view -> confirmationDialog.dismiss());
        yesButton.setOnClickListener(view -> {
            confirmationDialog.dismiss();
            ArchiveStore.removeStandaloneReceiptAt(this, receiptIndex);
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
            @NonNull Runnable onReceiptsChanged
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
                                        onReceiptsChanged
                                )
                        ),
                        new AnchoredDropdownMenuHelper.ActionItem(
                                R.string.move,
                                R.drawable.ic_archive_move,
                                () -> showMoveArchiveReceiptDialog(
                                        archiveIndex,
                                        receiptIndex,
                                        archiveReceipts,
                                        onReceiptsChanged
                                )
                        )
                )
        );
    }

    private void removeArchiveReceipt(
            int archiveIndex,
            int receiptIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull Runnable onReceiptsChanged
    ) {
        if (receiptIndex < 0 || receiptIndex >= archiveReceipts.size()) {
            return;
        }

        ArchiveStore.removeReceiptAt(this, archiveIndex, receiptIndex);
        archiveReceipts.remove(receiptIndex);
        onReceiptsChanged.run();
    }

    private void showMoveArchiveReceiptDialog(
            int sourceArchiveIndex,
            int receiptIndex,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> archiveReceipts,
            @NonNull Runnable onReceiptsChanged
    ) {
        if (receiptIndex < 0 || receiptIndex >= archiveReceipts.size()) {
            return;
        }

        ArrayList<String> locationNames = new ArrayList<>();
        final int[] currentSourceArchiveIndex = {sourceArchiveIndex};
        final int[] selectedArchiveIndex = {sourceArchiveIndex};

        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_location,
                null
        );
        View headerView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_header,
                null
        );
        TextView headerTitleView = headerView.findViewById(R.id.text_select_archive_header_title);
        AppCompatImageButton addArchiveButton =
                headerView.findViewById(R.id.button_select_archive_add);
        ListView archivesListView = dialogView.findViewById(R.id.list_select_archive_location);
        TextView emptyView = dialogView.findViewById(R.id.text_select_archive_location_empty);
        TextInputLayout receiptNameInputLayout =
                dialogView.findViewById(R.id.input_layout_select_archive_receipt_name);
        MaterialButton moveButton = dialogView.findViewById(R.id.button_create_selected_receipt);
        ArrayAdapter<String> archivesAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_single_choice,
                locationNames
        );

        headerTitleView.setText(R.string.select_location_title);
        receiptNameInputLayout.setVisibility(View.GONE);
        archivesListView.setAdapter(archivesAdapter);
        archivesListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        moveButton.setText(R.string.move);

        Runnable updateMoveButtonState = () ->
                moveButton.setEnabled(selectedArchiveIndex[0] != currentSourceArchiveIndex[0]);
        Runnable refreshDestinations = () -> {
            locationNames.clear();
            locationNames.add(getString(R.string.standalone));

            ArrayList<ArchiveStore.Archive> refreshedArchives = ArchiveStore.loadArchives(this);
            for (int index = 0; index < refreshedArchives.size(); index++) {
                locationNames.add(refreshedArchives.get(index).name);
            }

            archivesAdapter.notifyDataSetChanged();
            archivesListView.clearChoices();
            int checkedPosition = selectedArchiveIndex[0] < 0
                    ? 0
                    : Math.min(selectedArchiveIndex[0] + 1, locationNames.size() - 1);
            archivesListView.setItemChecked(checkedPosition, true);
            archivesListView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            updateMoveButtonState.run();
        };
        refreshDestinations.run();

        archivesListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedArchiveIndex[0] = position == 0 ? -1 : position - 1;
            updateMoveButtonState.run();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(headerView)
                .setView(dialogView)
                .create();

        addArchiveButton.setOnClickListener(view -> showNewArchiveDialog(() -> {
            currentSourceArchiveIndex[0] += 1;
            if (selectedArchiveIndex[0] >= 0) {
                selectedArchiveIndex[0] += 1;
            }
            refreshDestinations.run();
        }));

        updateMoveButtonState.run();
        moveButton.setOnClickListener(view -> {
            if (selectedArchiveIndex[0] == currentSourceArchiveIndex[0]) {
                updateMoveButtonState.run();
                return;
            }

            if (selectedArchiveIndex[0] < 0) {
                ArchiveStore.moveReceiptToStandalone(
                        this,
                        currentSourceArchiveIndex[0],
                        receiptIndex
                );
            } else {
                ArchiveStore.moveReceiptToArchive(
                        this,
                        currentSourceArchiveIndex[0],
                        receiptIndex,
                        selectedArchiveIndex[0]
                );
            }
            archiveReceipts.remove(receiptIndex);
            onReceiptsChanged.run();
            dialog.dismiss();
            Toast.makeText(this, R.string.receipt_moved, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showMoveStandaloneReceiptDialog(int receiptIndex) {
        if (receiptIndex < 0 || receiptIndex >= standaloneReceipts.size()) {
            return;
        }

        ArrayList<String> locationNames = new ArrayList<>();
        final int[] selectedArchiveIndex = {-1};

        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_location,
                null
        );
        View headerView = getLayoutInflater().inflate(
                R.layout.dialog_select_archive_header,
                null
        );
        TextView headerTitleView = headerView.findViewById(R.id.text_select_archive_header_title);
        AppCompatImageButton addArchiveButton =
                headerView.findViewById(R.id.button_select_archive_add);
        ListView archivesListView = dialogView.findViewById(R.id.list_select_archive_location);
        TextView emptyView = dialogView.findViewById(R.id.text_select_archive_location_empty);
        TextInputLayout receiptNameInputLayout =
                dialogView.findViewById(R.id.input_layout_select_archive_receipt_name);
        MaterialButton moveButton = dialogView.findViewById(R.id.button_create_selected_receipt);
        ArrayAdapter<String> archivesAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_single_choice,
                locationNames
        );

        headerTitleView.setText(R.string.select_location_title);
        receiptNameInputLayout.setVisibility(View.GONE);
        archivesListView.setAdapter(archivesAdapter);
        archivesListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        moveButton.setText(R.string.move);

        Runnable updateMoveButtonState = () -> moveButton.setEnabled(selectedArchiveIndex[0] >= 0);
        Runnable refreshDestinations = () -> {
            locationNames.clear();
            locationNames.add(getString(R.string.standalone));
            locationNames.addAll(ArchiveStore.loadArchiveNames(this));
            archivesAdapter.notifyDataSetChanged();
            archivesListView.clearChoices();
            int checkedPosition = selectedArchiveIndex[0] < 0
                    ? 0
                    : Math.min(selectedArchiveIndex[0] + 1, locationNames.size() - 1);
            archivesListView.setItemChecked(checkedPosition, true);
            archivesListView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            updateMoveButtonState.run();
        };
        refreshDestinations.run();

        archivesListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedArchiveIndex[0] = position == 0 ? -1 : position - 1;
            updateMoveButtonState.run();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(headerView)
                .setView(dialogView)
                .create();

        addArchiveButton.setOnClickListener(view -> showNewArchiveDialog(() -> {
            selectedArchiveIndex[0] = 0;
            refreshDestinations.run();
        }));

        updateMoveButtonState.run();
        moveButton.setOnClickListener(view -> {
            if (selectedArchiveIndex[0] < 0) {
                updateMoveButtonState.run();
                return;
            }

            ArchiveStore.moveStandaloneReceiptToArchive(this, receiptIndex, selectedArchiveIndex[0]);
            dialog.dismiss();
            expandArchiveByIndex(selectedArchiveIndex[0]);
            Toast.makeText(this, R.string.receipt_moved, Toast.LENGTH_SHORT).show();
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

    private final class ArchiveEntriesAdapter extends BaseAdapter {
        private ArchiveEntriesAdapter() {
        }

        @Override
        public int getCount() {
            return archiveRootItems.size();
        }

        @NonNull
        @Override
        public ArchiveRootItem getItem(int position) {
            return archiveRootItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).type;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ArchiveRootItem rootItem = getItem(position);
            if (rootItem.type == ArchiveRootItem.TYPE_STANDALONE_RECEIPT) {
                return getStandaloneReceiptView(rootItem, convertView, parent);
            }
            return getArchiveView(rootItem, convertView, parent);
        }

        @NonNull
        private View getArchiveView(
                @NonNull ArchiveRootItem rootItem,
                @Nullable View convertView,
                @NonNull ViewGroup parent
        ) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(ArchiveActivity.this)
                        .inflate(R.layout.item_archive_entry, parent, false);
            }

            TextView archiveNameView = itemView.findViewById(R.id.text_archive_name);
            TextView archiveTotalView = itemView.findViewById(R.id.text_archive_total);
            View headerView = itemView.findViewById(R.id.layout_archive_entry_header);
            ArchiveStore.Archive archive = rootItem.archive;
            archiveNameView.setText(archive == null ? "" : archive.name);
            archiveTotalView.setText(formatArchiveTotalAmount(archive));

            boolean expanded = archive != null && expandedArchiveNames.contains(archive.name);
            bindArchiveTreeState(itemView, rootItem, expanded, false);

            final View archiveRowView = itemView;
            final View archiveHeaderView = headerView == null ? archiveRowView : headerView;
            final int archiveIndex = rootItem.sourceIndex;
            archiveHeaderView.setClickable(true);
            archiveHeaderView.setFocusable(true);
            archiveHeaderView.setOnClickListener(view -> toggleArchiveTree(rootItem, archiveRowView));
            archiveHeaderView.setOnTouchListener(new View.OnTouchListener() {
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
                            archiveHeaderView,
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

        @NonNull
        private View getStandaloneReceiptView(
                @NonNull ArchiveRootItem rootItem,
                @Nullable View convertView,
                @NonNull ViewGroup parent
        ) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(ArchiveActivity.this)
                        .inflate(R.layout.item_history_receipt, parent, false);
            }

            ReceiptHistoryStore.HistoryEntry receiptEntry = rootItem.receiptEntry;
            TextView receiptNameView = itemView.findViewById(R.id.text_history_receipt_name);
            TextView totalAmountView = itemView.findViewById(R.id.text_history_receipt_total);

            receiptNameView.setText(receiptEntry == null ? "" : receiptEntry.receiptName);
            totalAmountView.setText(receiptEntry == null
                    ? ""
                    : getString(R.string.archive_summary_transfer_amount, receiptEntry.totalAmount));
            if (receiptEntry != null) {
                bindArchiveReceiptIncompleteStatusIcon(itemView, receiptEntry, false);
            } else {
                AppCompatImageView statusIconView =
                        itemView.findViewById(R.id.image_history_receipt_status);
                if (statusIconView != null) {
                    statusIconView.setVisibility(View.GONE);
                    statusIconView.setOnClickListener(null);
                    statusIconView.setClickable(false);
                    statusIconView.setFocusable(false);
                }
            }

            final View receiptItemView = itemView;
            final int receiptIndex = rootItem.sourceIndex;
            itemView.setClickable(true);
            itemView.setFocusable(true);
            itemView.setOnClickListener(view -> {
                dismissArchiveReceiptIncompletePopup();
                showStandaloneReceiptDetailsDialog(receiptIndex);
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
                    showStandaloneReceiptActionsMenu(
                            receiptItemView,
                            downRawX,
                            downRawY,
                            receiptIndex
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

            if (entry != null) {
                receiptNameView.setText(entry.receiptName);
                totalAmountView.setText(
                        getString(R.string.archive_summary_transfer_amount, entry.totalAmount)
                );
                bindArchiveReceiptIncompleteStatusIcon(itemView, entry, true);
            } else {
                receiptNameView.setText("");
                totalAmountView.setText("");
                AppCompatImageView statusIconView =
                        itemView.findViewById(R.id.image_history_receipt_status);
                if (statusIconView != null) {
                    statusIconView.setVisibility(View.GONE);
                    statusIconView.setOnClickListener(null);
                    statusIconView.setClickable(false);
                    statusIconView.setFocusable(false);
                }
            }

            if (entry != null) {
                View receiptItemView = itemView;
                final int receiptIndex = position;
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnClickListener(view -> {
                    dismissArchiveReceiptIncompletePopup();
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
                                () -> {
                                    loadArchiveNames();
                                    ArchiveReceiptEntriesAdapter.this.notifyDataSetChanged();
                                }
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
