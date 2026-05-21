package com.example.testrepo;

import android.Manifest;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.PopupMenu;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

public class NewReceiptActivity extends AppCompatActivity {
    static final String EXTRA_SCAN_ONLY_MODE = "com.example.testrepo.extra.SCAN_ONLY_MODE";
    static final String RESULT_EXTRA_SCANNED_ITEM_NAMES =
            "com.example.testrepo.result.SCANNED_ITEM_NAMES";
    static final String RESULT_EXTRA_SCANNED_ITEM_AMOUNT_CENTS =
            "com.example.testrepo.result.SCANNED_ITEM_AMOUNT_CENTS";
    private static final int MAX_CROP_BITMAP_DIMENSION = 2048;
    private static final int MAX_IMPORTED_PDF_PAGE_DIMENSION = 2800;
    private static final int MAX_ITEM_PARTICIPANT_BUTTONS_PER_ROW = 4;
    private static final int ACTIONS_MODE_HIDDEN = 0;
    private static final int ACTIONS_MODE_SETTINGS_ONLY = 1;
    private static final int ACTIONS_MODE_RECEIPT = 2;
    private static final long MENU_ARROW_ROTATION_DURATION_MS = 180L;
    private static final int RECEIPT_FILTER_DEFAULT = 0;
    private static final int RECEIPT_FILTER_HIGH_TO_LOW = 1;
    private static final int RECEIPT_FILTER_LOW_TO_HIGH = 2;
    private static final int UNCHECKED_PARTICIPANT_COLOR = 0xFF8A8A8A;
    private static final int MIN_RECEIPT_ITEM_QUANTITY = 1;
    private static final long RECEIPT_ITEM_LONG_PRESS_DURATION_MS = 750L;
    private static final long RECEIPT_ITEM_LONG_PRESS_VIBRATION_DURATION_MS = 40L;
    private static final String DEFAULT_PARTICIPANT_NAME = "You";
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String PAYMENT_LINK_BASE_URL = "https://edvinwendt.github.io/TestRepo/";
    private static final String STATE_KEY_RECEIPT_DRAFT_VISIBLE =
            "state.receipt_draft_visible";
    private static final String STATE_KEY_RECEIPT_NAME =
            "state.receipt_name";
    private static final String STATE_KEY_CROWNED_PARTICIPANT_KEY =
            "state.crowned_participant_key";
    private static final String STATE_KEY_RECEIPT_ITEMS_FILTER_MODE =
            "state.receipt_items_filter_mode";
    private static final String STATE_KEY_NEXT_RECEIPT_ITEM_SOURCE_ORDER =
            "state.next_receipt_item_source_order";
    private static final String STATE_KEY_LAST_SHARED_RECEIPT_INTENT =
            "state.last_shared_receipt_intent";
    private static final String STATE_KEY_LAST_REFRESHABLE_RECEIPT_IMAGE_PATH =
            "state.last_refreshable_receipt_image_path";
    private static final String STATE_KEY_PARTICIPANTS =
            "state.participants";
    private static final String STATE_KEY_RECEIPT_ITEMS =
            "state.receipt_items";
    private static final String STATE_KEY_TRACKED_RECEIPT_ITEMS =
            "state.tracked_receipt_items";
    private static final String STATE_KEY_PARTICIPANT_NAME =
            "state.participant_name";
    private static final String STATE_KEY_PARTICIPANT_PHONE_NUMBER =
            "state.participant_phone_number";
    private static final String STATE_KEY_PARTICIPANT_KEY =
            "state.participant_key";
    private static final String STATE_KEY_PARTICIPANT_INITIALS =
            "state.participant_initials";
    private static final String STATE_KEY_PARTICIPANT_COLOR =
            "state.participant_color";
    private static final String STATE_KEY_RECEIPT_ITEM_NAME =
            "state.receipt_item_name";
    private static final String STATE_KEY_RECEIPT_ITEM_AMOUNT_CENTS =
            "state.receipt_item_amount_cents";
    private static final String STATE_KEY_RECEIPT_ITEM_SPLIT_QUANTITY =
            "state.receipt_item_split_quantity";
    private static final String STATE_KEY_RECEIPT_ITEM_PANT_AMOUNT_CENTS =
            "state.receipt_item_pant_amount_cents";
    private static final String STATE_KEY_RECEIPT_ITEM_SOURCE_ORDER =
            "state.receipt_item_source_order";
    private static final String STATE_KEY_RECEIPT_ITEM_PAYER_PARTICIPANT_KEY =
            "state.receipt_item_payer_participant_key";
    private static final String STATE_KEY_RECEIPT_ITEM_SELECTED_PARTICIPANT_KEYS =
            "state.receipt_item_selected_participant_keys";

    private PreviewView previewView;
    private TextView cameraStatusView;
    private MaterialButton captureButton;
    private View cropReceiptLayout;
    private ReceiptCropImageView cropImageView;
    private MaterialButton cropReceiptButton;
    private View receiptResultsLayout;
    private View receiptActionButtonsLayout;
    private AppCompatImageButton backButton;
    private LinearLayout participantButtonsLayout;
    private TextView screenTitleView;
    private AppCompatImageButton receiptActionsButton;
    private ListView receiptItemsList;
    private View receiptItemsEmptyView;
    private TextView receiptTotalValueView;
    private MaterialButton nextButton;
    private AppCompatImageButton nextButtonDisabledInfoButton;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private TextRecognizer textRecognizer;
    private ExecutorService backgroundExecutor;
    private final ReceiptParser receiptParser = new ReceiptParser();
    private final ArrayList<ReceiptParser.ReceiptItem> receiptItems = new ArrayList<>();
    private final ArrayList<ReceiptParser.ReceiptItem> trackedReceiptItems = new ArrayList<>();
    private final ArrayList<Participant> participants = new ArrayList<>();
    private ReceiptItemsAdapter receiptItemsAdapter;
    private int currentScreenTitleResId = R.string.photo_screen_title;
    private int actionsMenuMode = ACTIONS_MODE_HIDDEN;
    private int receiptItemsFilterMode = RECEIPT_FILTER_DEFAULT;
    private int nextReceiptItemSourceOrder;
    private boolean participantControlsVisible;
    private boolean sendRequestsAfterSmsPermission;
    private boolean showAddParticipantDialogAfterContactsPermission;
    private boolean appendScannedItemsMode;
    private boolean pendingScanMorePermissionRequest;
    private boolean scanOnlyMode;
    @NonNull
    private String currentReceiptName = "";
    @NonNull
    private String pendingSendRequestsMessage = "";
    @NonNull
    private String crownedParticipantKey = DEFAULT_PARTICIPANT_KEY;
    @NonNull
    private final ArrayList<String> nextButtonDisabledReasons = new ArrayList<>();
    @Nullable
    private Intent lastSharedReceiptIntent;
    @Nullable
    private File lastRefreshableReceiptImageFile;
    @Nullable
    private PopupWindow nextButtonDisabledReasonsPopup;
    @Nullable
    private PopupWindow newArchiveCreateDisabledReasonsPopup;
    @Nullable
    private PopupWindow headerHelpPopup;
    @Nullable
    private PopupWindow receiptItemPayerPopup;
    @Nullable
    private PopupWindow sendRequestsNoInternetPopup;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChangeListener =
            (sharedPreferences, key) -> {
                if (AppSettings.isSplitItemsPreferenceKey(key) && !trackedReceiptItems.isEmpty()) {
                    reapplyTrackedReceiptItems();
                }
            };

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    pendingScanMorePermissionRequest = false;
                    startCamera();
                } else {
                    if (pendingScanMorePermissionRequest) {
                        pendingScanMorePermissionRequest = false;
                        appendScannedItemsMode = false;
                        Toast.makeText(
                                this,
                                R.string.camera_permission_required,
                                Toast.LENGTH_SHORT
                        ).show();
                        if (scanOnlyMode) {
                            cancelScanOnlyFlow();
                        } else {
                            showReceiptResultsUi();
                        }
                    } else {
                        showPermissionRequired();
                    }
                }
            });
    private final ActivityResultLauncher<String> requestContactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                refreshDefaultParticipantPhoneNumber();
                if (showAddParticipantDialogAfterContactsPermission) {
                    showAddParticipantDialogAfterContactsPermission = false;
                    showAddParticipantDialog(isGranted);
                }
            });
    private final ActivityResultLauncher<String> requestSendSmsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted && sendRequestsAfterSmsPermission) {
                    sendRequestsAfterSmsPermission = false;
                    sendParticipantPaymentRequests();
                } else if (!isGranted) {
                    sendRequestsAfterSmsPermission = false;
                    pendingSendRequestsMessage = "";
                    Toast.makeText(
                            this,
                            R.string.send_requests_permission_required,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
    private final ActivityResultLauncher<String> importPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), imageUri -> {
                if (imageUri != null) {
                    importPhotoToCropView(imageUri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        if (AuthGateHelper.redirectToLoginIfNeeded(this)) {
            return;
        }
        setContentView(R.layout.activity_new_receipt);
        getSupportFragmentManager().setFragmentResultListener(
                EditUsernameDialogFragment.REQUEST_KEY,
                this,
                (requestKey, result) -> maybeShowStartupPermissionPrompt(
                        result.getBoolean(
                                EditUsernameDialogFragment.RESULT_KEY_REQUIRED_USERNAME,
                                false
                        )
                )
        );

        previewView = findViewById(R.id.view_camera_preview);
        cameraStatusView = findViewById(R.id.text_camera_status);
        cropReceiptLayout = findViewById(R.id.layout_crop_receipt);
        cropImageView = findViewById(R.id.view_receipt_crop);
        cropReceiptButton = findViewById(R.id.button_crop_receipt);
        receiptResultsLayout = findViewById(R.id.layout_receipt_results);
        receiptActionButtonsLayout = findViewById(R.id.layout_receipt_action_buttons);
        participantButtonsLayout = findViewById(R.id.layout_participant_buttons);
        screenTitleView = findViewById(R.id.text_new_receipt_screen_title);
        receiptItemsList = findViewById(R.id.list_receipt_items);
        receiptItemsEmptyView = findViewById(R.id.text_receipt_items_empty);
        receiptTotalValueView = findViewById(R.id.text_receipt_total_value);
        nextButton = findViewById(R.id.button_next);
        nextButtonDisabledInfoButton = findViewById(R.id.button_next_disabled_info);
        View addReceiptItemAction = findViewById(R.id.action_add_receipt_item);
        View addParticipantAction = findViewById(R.id.action_add_participant);
        View scanMoreAction = findViewById(R.id.action_scan_more_receipt_items);
        backButton = findViewById(R.id.button_back);
        receiptActionsButton = findViewById(R.id.button_receipt_actions);
        captureButton = findViewById(R.id.button_take_picture);
        scanOnlyMode = getIntent().getBooleanExtra(EXTRA_SCAN_ONLY_MODE, false);
        currentReceiptName = getString(R.string.new_receipt_screen_title);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        receiptItemsAdapter = new ReceiptItemsAdapter();
        receiptItemsList.setAdapter(receiptItemsAdapter);
        ensureDefaultParticipant();
        applyPreAddedParticipants();
        refreshParticipantButtons();
        setParticipantControlsVisible(false);
        updateReceiptTotal();
        updateNextButtonState();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        refreshDefaultParticipantPhoneNumber();
        updateNavigationButtonForCurrentState();

        backButton.setOnClickListener(view -> handleNavigationButtonPressed());
        addReceiptItemAction.setOnClickListener(view -> showAddReceiptItemDialog());
        addParticipantAction.setOnClickListener(view -> openAddParticipantDialog());
        scanMoreAction.setOnClickListener(view -> startScanMoreFlow());
        receiptActionsButton.setOnClickListener(this::showActiveActionsMenu);
        nextButton.setOnClickListener(view -> showReceiptSummaryDialog());
        nextButtonDisabledInfoButton.setOnClickListener(
                view -> showNextButtonDisabledReasonsPopup()
        );
        captureButton.setOnClickListener(view -> {
            if (hasCameraPermission()) {
                takePicture();
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });
        cropReceiptButton.setOnClickListener(view -> cropAndAnalyzeReceipt());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleNavigationButtonPressed();
            }
        });

        if (restoreReceiptDraft(savedInstanceState)) {
            return;
        }

        if (handleSharedReceiptIntent(getIntent())) {
            return;
        }

        if (scanOnlyMode) {
            startScanMoreFlow();
            return;
        }

        showReceiptResultsUi();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        populateReceiptDraftState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedReceiptIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (AuthGateHelper.redirectToLoginIfNeeded(this)) {
            return;
        }
        promptForRequiredUsernameIfNeeded();
        AppSettings.registerChangeListener(this, settingsChangeListener);
    }

    @Override
    protected void onStop() {
        AppSettings.unregisterChangeListener(this, settingsChangeListener);
        super.onStop();
    }

    private void populateReceiptDraftState(@NonNull Bundle outState) {
        boolean receiptDraftVisible = isReceiptResultsVisible();
        outState.putBoolean(STATE_KEY_RECEIPT_DRAFT_VISIBLE, receiptDraftVisible);
        if (!receiptDraftVisible) {
            return;
        }

        syncTrackedReceiptItemsToCurrentItems();
        outState.putString(STATE_KEY_RECEIPT_NAME, currentReceiptName);
        outState.putString(STATE_KEY_CROWNED_PARTICIPANT_KEY, crownedParticipantKey);
        outState.putInt(STATE_KEY_RECEIPT_ITEMS_FILTER_MODE, receiptItemsFilterMode);
        outState.putInt(STATE_KEY_NEXT_RECEIPT_ITEM_SOURCE_ORDER, nextReceiptItemSourceOrder);
        outState.putParcelable(STATE_KEY_LAST_SHARED_RECEIPT_INTENT, lastSharedReceiptIntent);
        outState.putString(
                STATE_KEY_LAST_REFRESHABLE_RECEIPT_IMAGE_PATH,
                lastRefreshableReceiptImageFile == null
                        ? null
                        : lastRefreshableReceiptImageFile.getAbsolutePath()
        );
        outState.putParcelableArrayList(
                STATE_KEY_PARTICIPANTS,
                toParticipantStateBundles(participants)
        );
        outState.putParcelableArrayList(
                STATE_KEY_RECEIPT_ITEMS,
                toReceiptItemStateBundles(receiptItems)
        );
        outState.putParcelableArrayList(
                STATE_KEY_TRACKED_RECEIPT_ITEMS,
                toReceiptItemStateBundles(trackedReceiptItems)
        );
    }

    private boolean restoreReceiptDraft(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null
                || !savedInstanceState.getBoolean(STATE_KEY_RECEIPT_DRAFT_VISIBLE, false)) {
            return false;
        }

        participants.clear();
        participants.addAll(fromParticipantStateBundles(
                savedInstanceState.getParcelableArrayList(STATE_KEY_PARTICIPANTS)
        ));
        if (participants.isEmpty()) {
            ensureDefaultParticipant();
        }

        currentReceiptName = savedInstanceState.getString(
                STATE_KEY_RECEIPT_NAME,
                getString(R.string.new_receipt_screen_title)
        );
        crownedParticipantKey = savedInstanceState.getString(
                STATE_KEY_CROWNED_PARTICIPANT_KEY,
                DEFAULT_PARTICIPANT_KEY
        );
        receiptItemsFilterMode = savedInstanceState.getInt(
                STATE_KEY_RECEIPT_ITEMS_FILTER_MODE,
                RECEIPT_FILTER_DEFAULT
        );
        nextReceiptItemSourceOrder = savedInstanceState.getInt(
                STATE_KEY_NEXT_RECEIPT_ITEM_SOURCE_ORDER,
                0
        );
        lastSharedReceiptIntent = savedInstanceState.getParcelable(STATE_KEY_LAST_SHARED_RECEIPT_INTENT);

        String lastRefreshableReceiptImagePath = savedInstanceState.getString(
                STATE_KEY_LAST_REFRESHABLE_RECEIPT_IMAGE_PATH
        );
        lastRefreshableReceiptImageFile = lastRefreshableReceiptImagePath == null
                || lastRefreshableReceiptImagePath.trim().isEmpty()
                ? null
                : new File(lastRefreshableReceiptImagePath);
        if (lastRefreshableReceiptImageFile != null && !lastRefreshableReceiptImageFile.exists()) {
            lastRefreshableReceiptImageFile = null;
        }

        trackedReceiptItems.clear();
        trackedReceiptItems.addAll(fromReceiptItemStateBundles(
                savedInstanceState.getParcelableArrayList(STATE_KEY_TRACKED_RECEIPT_ITEMS)
        ));
        receiptItems.clear();
        receiptItems.addAll(fromReceiptItemStateBundles(
                savedInstanceState.getParcelableArrayList(STATE_KEY_RECEIPT_ITEMS)
        ));

        if (findParticipantByKey(crownedParticipantKey) == null) {
            crownedParticipantKey = DEFAULT_PARTICIPANT_KEY;
        }
        appendScannedItemsMode = false;
        pendingScanMorePermissionRequest = false;
        showAddParticipantDialogAfterContactsPermission = false;
        sendRequestsAfterSmsPermission = false;
        pendingSendRequestsMessage = "";

        showReceiptResultsUi();
        return true;
    }

    private boolean isReceiptResultsVisible() {
        return receiptResultsLayout != null && receiptResultsLayout.getVisibility() == View.VISIBLE;
    }

    @NonNull
    private ArrayList<Bundle> toParticipantStateBundles(
            @NonNull List<Participant> sourceParticipants
    ) {
        ArrayList<Bundle> participantStateBundles = new ArrayList<>();
        for (Participant participant : sourceParticipants) {
            Bundle participantState = new Bundle();
            participantState.putString(STATE_KEY_PARTICIPANT_NAME, participant.name);
            participantState.putString(
                    STATE_KEY_PARTICIPANT_PHONE_NUMBER,
                    participant.phoneNumber
            );
            participantState.putString(STATE_KEY_PARTICIPANT_KEY, participant.key);
            participantState.putString(STATE_KEY_PARTICIPANT_INITIALS, participant.initials);
            participantState.putInt(STATE_KEY_PARTICIPANT_COLOR, participant.color);
            participantStateBundles.add(participantState);
        }
        return participantStateBundles;
    }

    @NonNull
    private ArrayList<Participant> fromParticipantStateBundles(
            @Nullable ArrayList<Bundle> participantStateBundles
    ) {
        ArrayList<Participant> restoredParticipants = new ArrayList<>();
        if (participantStateBundles == null) {
            return restoredParticipants;
        }

        for (Bundle participantState : participantStateBundles) {
            if (participantState == null) {
                continue;
            }

            String participantName = participantState.getString(STATE_KEY_PARTICIPANT_NAME, "");
            String participantKey = participantState.getString(STATE_KEY_PARTICIPANT_KEY, "");
            if (normalizeWhitespace(participantName).isEmpty()
                    || normalizeWhitespace(participantKey).isEmpty()) {
                continue;
            }

            restoredParticipants.add(new Participant(
                    participantName,
                    participantState.getString(STATE_KEY_PARTICIPANT_PHONE_NUMBER, ""),
                    participantKey,
                    participantState.getString(
                            STATE_KEY_PARTICIPANT_INITIALS,
                            getParticipantInitials(participantName)
                    ),
                    participantState.getInt(
                            STATE_KEY_PARTICIPANT_COLOR,
                            createParticipantColor(restoredParticipants.size())
                    )
            ));
        }
        return restoredParticipants;
    }

    @NonNull
    private ArrayList<Bundle> toReceiptItemStateBundles(
            @NonNull List<ReceiptParser.ReceiptItem> sourceItems
    ) {
        ArrayList<Bundle> receiptItemStateBundles = new ArrayList<>();
        for (ReceiptParser.ReceiptItem item : sourceItems) {
            Bundle itemState = new Bundle();
            itemState.putString(STATE_KEY_RECEIPT_ITEM_NAME, item.getName());
            itemState.putInt(STATE_KEY_RECEIPT_ITEM_AMOUNT_CENTS, item.getAmountCents());
            itemState.putInt(STATE_KEY_RECEIPT_ITEM_SPLIT_QUANTITY, item.getSplitQuantity());
            itemState.putInt(
                    STATE_KEY_RECEIPT_ITEM_PANT_AMOUNT_CENTS,
                    item.getPantAmountCents()
            );
            itemState.putInt(STATE_KEY_RECEIPT_ITEM_SOURCE_ORDER, item.getSourceOrder());
            itemState.putString(
                    STATE_KEY_RECEIPT_ITEM_PAYER_PARTICIPANT_KEY,
                    item.getPayerParticipantKey()
            );
            itemState.putStringArrayList(
                    STATE_KEY_RECEIPT_ITEM_SELECTED_PARTICIPANT_KEYS,
                    new ArrayList<>(item.copySelectedParticipantKeys())
            );
            receiptItemStateBundles.add(itemState);
        }
        return receiptItemStateBundles;
    }

    @NonNull
    private ArrayList<ReceiptParser.ReceiptItem> fromReceiptItemStateBundles(
            @Nullable ArrayList<Bundle> receiptItemStateBundles
    ) {
        ArrayList<ReceiptParser.ReceiptItem> restoredItems = new ArrayList<>();
        if (receiptItemStateBundles == null) {
            return restoredItems;
        }

        for (Bundle itemState : receiptItemStateBundles) {
            if (itemState == null) {
                continue;
            }

            String itemName = itemState.getString(STATE_KEY_RECEIPT_ITEM_NAME, "");
            if (normalizeWhitespace(itemName).isEmpty()) {
                continue;
            }

            ReceiptParser.ReceiptItem restoredItem = new ReceiptParser.ReceiptItem(
                    itemName,
                    itemState.getInt(STATE_KEY_RECEIPT_ITEM_AMOUNT_CENTS, 0),
                    itemState.getInt(STATE_KEY_RECEIPT_ITEM_SPLIT_QUANTITY, 1),
                    itemState.getInt(STATE_KEY_RECEIPT_ITEM_PANT_AMOUNT_CENTS, 0)
            );
            restoredItem.setSourceOrder(
                    itemState.getInt(STATE_KEY_RECEIPT_ITEM_SOURCE_ORDER, 0)
            );
            restoredItem.setPayerParticipantKey(
                    itemState.getString(STATE_KEY_RECEIPT_ITEM_PAYER_PARTICIPANT_KEY)
            );
            ArrayList<String> selectedParticipantKeys = itemState.getStringArrayList(
                    STATE_KEY_RECEIPT_ITEM_SELECTED_PARTICIPANT_KEYS
            );
            if (selectedParticipantKeys != null) {
                restoredItem.selectParticipants(new HashSet<>(selectedParticipantKeys));
            }
            restoredItems.add(restoredItem);
        }
        return restoredItems;
    }

    private void startCaptureFlow() {
        if (hasCameraPermission()) {
            startCamera();
        } else {
            showPermissionRequired();
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanMoreFlow() {
        appendScannedItemsMode = true;
        updateNavigationButtonForCurrentState();
        if (hasCameraPermission()) {
            startCamera();
        } else {
            pendingScanMorePermissionRequest = true;
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void promptForRequiredUsernameIfNeeded() {
        if (AppSettings.isUsernameNicknameEmpty(this)) {
            EditUsernameDialogFragment.show(getSupportFragmentManager(), true);
        }
    }

    private void maybeShowStartupPermissionPrompt(boolean requiredUsernameFlow) {
        if (!requiredUsernameFlow || AppSettings.hasStartupPermissionPromptBeenShown(this)) {
            return;
        }

        PermissionOnboardingDialogFragment.show(getSupportFragmentManager());
    }

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSendSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openAddParticipantDialog() {
        if (hasContactsPermission()) {
            showAddParticipantDialog(true);
        } else {
            showAddParticipantDialogAfterContactsPermission = true;
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void showAbandonReceiptDialog() {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_abandon_receipt_confirmation,
                null
        );
        MaterialButton noButton = dialogView.findViewById(R.id.button_abandon_receipt_no);
        MaterialButton yesButton = dialogView.findViewById(R.id.button_abandon_receipt_yes);

        AlertDialog abandonDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        noButton.setOnClickListener(view -> abandonDialog.dismiss());
        yesButton.setOnClickListener(view -> {
            abandonDialog.dismiss();
            returnToMainMenu();
        });

        abandonDialog.show();
    }

    private void handleNavigationButtonPressed() {
        if (appendScannedItemsMode) {
            closeScanMoreOverlay();
            return;
        }

        showAbandonReceiptDialog();
    }

    private void closeScanMoreOverlay() {
        appendScannedItemsMode = false;
        pendingScanMorePermissionRequest = false;
        cameraStatusView.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        stopCameraPreview();
        if (scanOnlyMode) {
            cancelScanOnlyFlow();
            return;
        }
        showReceiptResultsUi();
    }

    private void updateNavigationButtonForCurrentState() {
        if (backButton == null) {
            return;
        }

        if (appendScannedItemsMode) {
            backButton.setImageResource(R.drawable.ic_close);
            backButton.setContentDescription(getString(R.string.close));
        } else {
            backButton.setImageResource(R.drawable.ic_back_chevron);
            backButton.setContentDescription(getString(R.string.back));
        }
    }

    private void returnToMainMenu() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void clearTextInputFocus(@Nullable TextInputEditText inputView, @Nullable View fallbackView) {
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

    private void setScreenTitle(int titleResId) {
        currentScreenTitleResId = titleResId;
        dismissHeaderHelpPopup();
        if (screenTitleView != null) {
            screenTitleView.setText(titleResId);
        }
        updateActionsButtonAppearance();
    }

    private void showActiveActionsMenu(View anchorView) {
        if (actionsMenuMode == ACTIONS_MODE_SETTINGS_ONLY) {
            if (shouldShowHeaderHelpButton()) {
                showHeaderHelpPopup(anchorView);
            } else {
                SettingsMenuHelper.showSettingsMenu(this, anchorView);
            }
            return;
        }

        if (actionsMenuMode != ACTIONS_MODE_RECEIPT) {
            return;
        }

        showReceiptActionsMenu(anchorView);
    }

    private void showPhotoActionsMenu(View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_photo_actions);
        popupMenu.setForceShowIcon(true);
        tintPopupMenuIcons(popupMenu);
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_import_photo) {
                openImportPhotoPicker();
                return true;
            }
            if (itemId == R.id.action_settings) {
                SettingsDialogFragment.show(getSupportFragmentManager());
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showReceiptActionsMenu(View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_new_receipt_actions);
        popupMenu.setForceShowIcon(true);
        tintPopupMenuIcons(popupMenu);
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_account) {
                AccountDialogFragment.show(getSupportFragmentManager());
                return true;
            }
            if (itemId == R.id.action_add_to_archive) {
                openAddToArchiveFlowFromReceiptView();
                return true;
            }
            if (itemId == R.id.action_settings) {
                SettingsDialogFragment.show(getSupportFragmentManager());
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showReceiptFiltersMenu(View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_receipt_filters);
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_receipt_filter_default) {
                applyReceiptItemsFilter(RECEIPT_FILTER_DEFAULT);
                return true;
            }
            if (itemId == R.id.action_receipt_filter_high_to_low) {
                applyReceiptItemsFilter(RECEIPT_FILTER_HIGH_TO_LOW);
                return true;
            }
            if (itemId == R.id.action_receipt_filter_low_to_high) {
                applyReceiptItemsFilter(RECEIPT_FILTER_LOW_TO_HIGH);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void applyReceiptItemsFilter(int filterMode) {
        receiptItemsFilterMode = filterMode;
        reapplyTrackedReceiptItems();
    }

    private void tintPopupMenuIcons(@NonNull PopupMenu popupMenu) {
        ColorStateList iconTint = resolvePopupMenuIconTint();
        for (int index = 0; index < popupMenu.getMenu().size(); index++) {
            Drawable icon = popupMenu.getMenu().getItem(index).getIcon();
            if (icon == null) {
                continue;
            }

            Drawable tintedIcon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTintList(tintedIcon, iconTint);
            popupMenu.getMenu().getItem(index).setIcon(tintedIcon);
        }
    }

    @NonNull
    private ColorStateList resolvePopupMenuIconTint() {
        TypedValue typedValue = new TypedValue();
        if (!getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            return ColorStateList.valueOf(Color.BLACK);
        }

        if (typedValue.resourceId != 0) {
            ColorStateList colorStateList = ContextCompat.getColorStateList(this, typedValue.resourceId);
            if (colorStateList != null) {
                return colorStateList;
            }
        }

        return ColorStateList.valueOf(typedValue.data);
    }

    private void refreshReceiptFlow() {
        if (lastSharedReceiptIntent != null) {
            importSharedReceipt(new Intent(lastSharedReceiptIntent));
            return;
        }

        if (lastRefreshableReceiptImageFile != null && lastRefreshableReceiptImageFile.exists()) {
            refreshReceiptFromImageFile(lastRefreshableReceiptImageFile);
            return;
        }

        Toast.makeText(this, R.string.refresh_unavailable, Toast.LENGTH_SHORT).show();
    }

    private void openImportPhotoPicker() {
        importPhotoLauncher.launch("image/*");
    }

    private boolean handleSharedReceiptIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return false;
        }

        lastSharedReceiptIntent = new Intent(intent);
        lastRefreshableReceiptImageFile = null;
        importSharedReceipt(intent);
        return true;
    }

    private void importSharedReceipt(@NonNull Intent intent) {
        clearCurrentReceiptResults();
        stopCameraPreview();
        previewView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        setScreenTitle(R.string.receipt_screen_title);
        showStatusMessage(R.string.importing_shared_receipt, false);

        backgroundExecutor.execute(() -> {
            SharedReceiptImportData importData;
            try {
                importData = prepareSharedReceiptImport(intent);
            } catch (IOException exception) {
                runOnUiThread(() -> handleSharedReceiptImportFailure(
                        R.string.shared_receipt_open_failed
                ));
                return;
            }

            if (importData.rows.isEmpty() && importData.imageUris.isEmpty()) {
                cleanupTemporaryFiles(importData.temporaryFiles);
                runOnUiThread(() -> handleSharedReceiptImportFailure(
                        R.string.shared_receipt_empty
                ));
                return;
            }

            runOnUiThread(() -> processSharedReceiptImport(importData));
        });
    }

    private void processSharedReceiptImport(@NonNull SharedReceiptImportData importData) {
        if (importData.imageUris.isEmpty()) {
            finishSharedReceiptImport(importData.rows, importData.temporaryFiles);
            return;
        }

        processSharedReceiptImage(importData, 0);
    }

    private void processSharedReceiptImage(
            @NonNull SharedReceiptImportData importData,
            int index
    ) {
        if (index >= importData.imageUris.size()) {
            finishSharedReceiptImport(importData.rows, importData.temporaryFiles);
            return;
        }

        InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, importData.imageUris.get(index));
        } catch (IOException exception) {
            cleanupTemporaryFiles(importData.temporaryFiles);
            handleSharedReceiptImportFailure(R.string.shared_receipt_open_failed);
            return;
        }

        textRecognizer.process(inputImage)
                .addOnSuccessListener(recognizedText -> {
                    importData.rows.addAll(extractRecognizedLines(recognizedText));
                    processSharedReceiptImage(importData, index + 1);
                })
                .addOnFailureListener(exception -> {
                    cleanupTemporaryFiles(importData.temporaryFiles);
                    handleSharedReceiptImportFailure(R.string.shared_receipt_open_failed);
                });
    }

    private void finishSharedReceiptImport(
            @NonNull ArrayList<String> importedRows,
            @NonNull ArrayList<File> temporaryFiles
    ) {
        cleanupTemporaryFiles(temporaryFiles);

        ArrayList<ReceiptParser.ReceiptItem> detectedItems =
                receiptParser.extractReceiptItems(importedRows);
        if (receiptParser.isReceiptDetected(importedRows, detectedItems) && !detectedItems.isEmpty()) {
            showReceiptResults(detectedItems);
        } else {
            handleSharedReceiptImportFailure(R.string.no_receipt_detected);
        }
    }

    private void handleSharedReceiptImportFailure(int messageResId) {
        cameraStatusView.setVisibility(View.GONE);
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
        showCameraFallbackAfterImport();
    }

    private void showCameraFallbackAfterImport() {
        if (hasCameraPermission()) {
            startCamera();
        } else {
            showPermissionRequired();
        }
    }

    private void importPhotoToCropView(@NonNull Uri imageUri) {
        clearCurrentReceiptResults();
        stopCameraPreview();
        previewView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        lastSharedReceiptIntent = null;
        lastRefreshableReceiptImageFile = null;
        setScreenTitle(R.string.crop_screen_title);
        showStatusMessage(R.string.importing_photo, false);

        backgroundExecutor.execute(() -> {
            try {
                File importedPhotoFile = createImageFile("imported_receipt_");
                copyUriToFile(imageUri, importedPhotoFile);
                runOnUiThread(() -> prepareImportedPhotoForCrop(importedPhotoFile));
            } catch (IOException exception) {
                runOnUiThread(this::handleImportedPhotoFailure);
            }
        });
    }

    private void prepareImportedPhotoForCrop(@NonNull File imageFile) {
        if (!AppSettings.isAutoRotateImageEnabled(this)) {
            showCropEditor(imageFile, 0f);
            return;
        }

        InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, Uri.fromFile(imageFile));
        } catch (IOException exception) {
            showCropEditor(imageFile, 0f);
            return;
        }

        textRecognizer.process(inputImage)
                .addOnSuccessListener(
                        recognizedText -> showCropEditor(
                                imageFile,
                                computeReceiptAlignmentRotationDegrees(recognizedText)
                        )
                )
                .addOnFailureListener(exception -> showCropEditor(imageFile, 0f));
    }

    private void handleImportedPhotoFailure() {
        cameraStatusView.setVisibility(View.GONE);
        Toast.makeText(this, R.string.import_photo_failed, Toast.LENGTH_SHORT).show();
        showCameraFallbackAfterImport();
    }

    private void copyUriToFile(@NonNull Uri sourceUri, @NonNull File outputFile) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            if (inputStream == null) {
                throw new IOException("Unable to read selected photo");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    @NonNull
    private SharedReceiptImportData prepareSharedReceiptImport(@NonNull Intent intent)
            throws IOException {
        SharedReceiptImportData importData = new SharedReceiptImportData();
        try {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.trim().isEmpty()) {
                importData.rows.addAll(splitSharedTextIntoRows(sharedText));
            }

            for (Uri sharedUri : collectSharedUris(intent)) {
                importSharedUri(intent, sharedUri, importData);
            }
            return importData;
        } catch (IOException exception) {
            cleanupTemporaryFiles(importData.temporaryFiles);
            throw exception;
        }
    }

    @NonNull
    private ArrayList<String> splitSharedTextIntoRows(@NonNull String sharedText) {
        ArrayList<String> rows = new ArrayList<>();
        String[] rawRows = sharedText.split("\\r?\\n");
        for (String rawRow : rawRows) {
            String normalizedRow = normalizeWhitespace(rawRow);
            if (!normalizedRow.isEmpty()) {
                rows.add(normalizedRow);
            }
        }
        return rows;
    }

    @NonNull
    private ArrayList<Uri> collectSharedUris(@NonNull Intent intent) {
        LinkedHashSet<Uri> uriSet = new LinkedHashSet<>();

        Uri sharedStream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (sharedStream != null) {
            uriSet.add(sharedStream);
        }

        ArrayList<Uri> sharedStreams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (sharedStreams != null) {
            uriSet.addAll(sharedStreams);
        }

        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri clipUri = clipData.getItemAt(index).getUri();
                if (clipUri != null) {
                    uriSet.add(clipUri);
                }
            }
        }

        Uri dataUri = intent.getData();
        if (dataUri != null) {
            uriSet.add(dataUri);
        }

        return new ArrayList<>(uriSet);
    }

    private void importSharedUri(
            @NonNull Intent intent,
            @NonNull Uri sharedUri,
            @NonNull SharedReceiptImportData importData
    ) throws IOException {
        String mimeType = resolveSharedMimeType(intent, sharedUri);
        if (mimeType != null && mimeType.startsWith("text/")) {
            importData.rows.addAll(readSharedTextRows(sharedUri));
            return;
        }

        if (isPdfMimeType(mimeType) || isPdfUri(sharedUri)) {
            ArrayList<File> renderedPages = renderPdfToImageFiles(sharedUri);
            importData.temporaryFiles.addAll(renderedPages);
            for (File renderedPage : renderedPages) {
                importData.imageUris.add(Uri.fromFile(renderedPage));
            }
            return;
        }

        importData.imageUris.add(sharedUri);
    }

    @Nullable
    private String resolveSharedMimeType(@NonNull Intent intent, @NonNull Uri sharedUri) {
        String mimeType = getContentResolver().getType(sharedUri);
        if (mimeType != null && !mimeType.isEmpty()) {
            return mimeType;
        }

        String displayName = resolveSharedDisplayName(sharedUri);
        if (!displayName.isEmpty()) {
            String loweredDisplayName = displayName.toLowerCase(Locale.US);
            if (loweredDisplayName.endsWith(".pdf")) {
                return MIME_TYPE_PDF;
            }
            if (loweredDisplayName.endsWith(".txt")) {
                return "text/plain";
            }
        }

        String intentType = intent.getType();
        return intentType == null || intentType.isEmpty() || "*/*".equals(intentType)
                ? null
                : intentType;
    }

    @NonNull
    private String resolveSharedDisplayName(@NonNull Uri sharedUri) {
        if ("content".equalsIgnoreCase(sharedUri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(
                    sharedUri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            )) {
                if (cursor != null
                        && cursor.moveToFirst()
                        && !cursor.isNull(0)) {
                    return cursor.getString(0);
                }
            }
        }

        String lastPathSegment = sharedUri.getLastPathSegment();
        return lastPathSegment == null ? "" : lastPathSegment;
    }

    private boolean isPdfMimeType(@Nullable String mimeType) {
        return MIME_TYPE_PDF.equalsIgnoreCase(mimeType);
    }

    private boolean isPdfUri(@NonNull Uri sharedUri) {
        String displayName = resolveSharedDisplayName(sharedUri).toLowerCase(Locale.US);
        return displayName.endsWith(".pdf");
    }

    @NonNull
    private ArrayList<String> readSharedTextRows(@NonNull Uri sharedUri) throws IOException {
        ArrayList<String> rows = new ArrayList<>();
        try (InputStream inputStream = getContentResolver().openInputStream(sharedUri)) {
            if (inputStream == null) {
                throw new IOException("Unable to read shared text");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String normalizedLine = normalizeWhitespace(line);
                    if (!normalizedLine.isEmpty()) {
                        rows.add(normalizedLine);
                    }
                }
            }
        }
        return rows;
    }

    @NonNull
    private ArrayList<File> renderPdfToImageFiles(@NonNull Uri pdfUri) throws IOException {
        ArrayList<File> renderedFiles = new ArrayList<>();
        ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(pdfUri, "r");
        if (fileDescriptor == null) {
            throw new IOException("Unable to open shared PDF");
        }

        try (ParcelFileDescriptor descriptor = fileDescriptor;
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                File outputFile = createImageFile("shared_receipt_" + pageIndex + "_");
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    Bitmap pageBitmap = renderPdfPage(page);
                    try {
                        saveBitmapAsJpeg(pageBitmap, outputFile);
                    } finally {
                        pageBitmap.recycle();
                    }
                }
                renderedFiles.add(outputFile);
            }
        } catch (IOException | RuntimeException exception) {
            cleanupTemporaryFiles(renderedFiles);
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("Unable to render shared PDF", exception);
        }

        return renderedFiles;
    }

    @NonNull
    private Bitmap renderPdfPage(@NonNull PdfRenderer.Page page) {
        float widthScale = (float) MAX_IMPORTED_PDF_PAGE_DIMENSION
                / Math.max(page.getWidth(), 1);
        float heightScale = (float) MAX_IMPORTED_PDF_PAGE_DIMENSION
                / Math.max(page.getHeight(), 1);
        float scale = Math.max(1f, Math.min(widthScale, heightScale));

        int bitmapWidth = Math.max(1, Math.round(page.getWidth() * scale));
        int bitmapHeight = Math.max(1, Math.round(page.getHeight() * scale));
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        Matrix renderMatrix = new Matrix();
        renderMatrix.setScale(
                (float) bitmapWidth / Math.max(page.getWidth(), 1),
                (float) bitmapHeight / Math.max(page.getHeight(), 1)
        );
        page.render(
                bitmap,
                null,
                renderMatrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        );
        return bitmap;
    }

    private void cleanupTemporaryFiles(@NonNull ArrayList<File> temporaryFiles) {
        for (File temporaryFile : temporaryFiles) {
            if (temporaryFile.exists()) {
                temporaryFile.delete();
            }
        }
    }

    private void startCamera() {
        setScreenTitle(R.string.photo_screen_title);
        captureButton.setEnabled(false);
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception exception) {
                showCameraUnavailable();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(getTargetRotation())
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        try {
            provider.unbindAll();
            provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
            );
        } catch (RuntimeException exception) {
            showCameraUnavailable();
            return;
        }

        previewView.setVisibility(View.VISIBLE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        cameraStatusView.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        captureButton.setEnabled(true);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
        setParticipantControlsVisible(false);
    }

    private int getTargetRotation() {
        return previewView.getDisplay() == null
                ? Surface.ROTATION_0
                : previewView.getDisplay().getRotation();
    }

    private void takePicture() {
        if (imageCapture == null) {
            showCameraUnavailable();
            return;
        }

        final File outputFile;
        try {
            outputFile = createImageFile("receipt_");
        } catch (IOException exception) {
            Toast.makeText(this, R.string.photo_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        showCameraStatus(R.string.scanning_receipt);
        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults outputFileResults
                    ) {
                        detectReceiptInImage(outputFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        cameraStatusView.setVisibility(View.GONE);
                        captureButton.setEnabled(true);
                        Toast.makeText(
                                NewReceiptActivity.this,
                                R.string.photo_save_failed,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void detectReceiptInImage(File imageFile) {
        InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, Uri.fromFile(imageFile));
        } catch (IOException exception) {
            showCropEditor(imageFile, 0f);
            return;
        }

        textRecognizer.process(inputImage)
                .addOnSuccessListener(recognizedText -> handleInitialRecognition(imageFile, recognizedText))
                .addOnFailureListener(exception -> showCropEditor(imageFile, 0f));
    }

    private void handleInitialRecognition(File imageFile, Text recognizedText) {
        float autoRotateDegrees = AppSettings.isAutoRotateImageEnabled(this)
                ? computeReceiptAlignmentRotationDegrees(recognizedText)
                : 0f;
        showCropEditor(imageFile, autoRotateDegrees);
    }

    private void showCropEditor(File imageFile, float autoRotateDegrees) {
        stopCameraPreview();
        setScreenTitle(R.string.crop_screen_title);
        previewView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        setParticipantControlsVisible(false);
        updateNavigationButtonForCurrentState();
        showStatusMessage(R.string.preparing_crop, false);

        backgroundExecutor.execute(() -> {
            try {
                Bitmap cropBitmap = loadBitmapForCropping(imageFile, autoRotateDegrees);
                runOnUiThread(() -> {
                    cropImageView.setImageBitmap(cropBitmap);
                    cameraStatusView.setVisibility(View.GONE);
                    cropReceiptLayout.setVisibility(View.VISIBLE);
                    cropReceiptButton.setEnabled(true);
                    setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
                    updateNavigationButtonForCurrentState();
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    cameraStatusView.setVisibility(View.GONE);
                    Toast.makeText(
                            NewReceiptActivity.this,
                            R.string.captured_photo_unavailable,
                            Toast.LENGTH_SHORT
                    ).show();
                    startCamera();
                });
            }
        });
    }

    private void cropAndAnalyzeReceipt() {
        Bitmap croppedBitmap = cropImageView.getCroppedBitmap();
        if (croppedBitmap == null) {
            Toast.makeText(this, R.string.no_receipt_detected, Toast.LENGTH_SHORT).show();
            return;
        }

        setScreenTitle(R.string.crop_screen_title);
        cropReceiptButton.setEnabled(false);
        cropReceiptLayout.setVisibility(View.GONE);
        showStatusMessage(R.string.scanning_cropped_receipt, false);

        backgroundExecutor.execute(() -> {
            File croppedFile = null;
            try {
                croppedFile = createImageFile("receipt_crop_");
                saveBitmapAsJpeg(croppedBitmap, croppedFile);
            } catch (IOException exception) {
                File finalCroppedFile = croppedFile;
                croppedBitmap.recycle();
                runOnUiThread(() -> {
                    cameraStatusView.setVisibility(View.GONE);
                    cropReceiptLayout.setVisibility(View.VISIBLE);
                    cropReceiptButton.setEnabled(true);
                    Toast.makeText(
                            NewReceiptActivity.this,
                            R.string.photo_save_failed,
                            Toast.LENGTH_SHORT
                    ).show();
                    if (finalCroppedFile != null && finalCroppedFile.exists()) {
                        finalCroppedFile.delete();
                    }
                });
                return;
            }

            croppedBitmap.recycle();
            File finalCroppedFile = croppedFile;
            runOnUiThread(() -> analyzeCroppedReceipt(finalCroppedFile));
        });
    }

    private void analyzeCroppedReceipt(File croppedFile) {
        lastRefreshableReceiptImageFile = croppedFile;
        lastSharedReceiptIntent = null;
        InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, Uri.fromFile(croppedFile));
        } catch (IOException exception) {
            onCroppedReceiptNotDetected();
            return;
        }

        textRecognizer.process(inputImage)
                .addOnSuccessListener(this::handleCroppedRecognition)
                .addOnFailureListener(exception -> onCroppedReceiptNotDetected());
    }

    private void refreshReceiptFromImageFile(@NonNull File imageFile) {
        clearCurrentReceiptResults();
        stopCameraPreview();
        previewView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        setScreenTitle(R.string.receipt_screen_title);
        showStatusMessage(R.string.refreshing_receipt, false);

        InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, Uri.fromFile(imageFile));
        } catch (IOException exception) {
            handleReceiptRefreshFailure(R.string.shared_receipt_open_failed);
            return;
        }

        textRecognizer.process(inputImage)
                .addOnSuccessListener(this::handleRefreshedReceiptRecognition)
                .addOnFailureListener(exception -> handleReceiptRefreshFailure(
                        R.string.shared_receipt_open_failed
                ));
    }

    private void handleRefreshedReceiptRecognition(Text recognizedText) {
        ArrayList<String> lines = extractRecognizedLines(recognizedText);
        ArrayList<ReceiptParser.ReceiptItem> detectedItems = receiptParser.extractReceiptItems(lines);

        if (receiptParser.isReceiptDetected(lines, detectedItems) && !detectedItems.isEmpty()) {
            showReceiptResults(detectedItems);
        } else {
            handleReceiptRefreshFailure(R.string.no_receipt_detected);
        }
    }

    private void handleReceiptRefreshFailure(int messageResId) {
        cameraStatusView.setVisibility(View.GONE);
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }

    private void handleCroppedRecognition(Text recognizedText) {
        ArrayList<String> lines = extractRecognizedLines(recognizedText);
        ArrayList<ReceiptParser.ReceiptItem> detectedItems = receiptParser.extractReceiptItems(lines);
        if (scanOnlyMode) {
            assignSourceOrderToReceiptItems(detectedItems);
            returnScannedReceiptItems(
                    prepareReceiptItemsForDisplay(cloneReceiptItems(detectedItems))
            );
            return;
        }
        if (appendScannedItemsMode) {
            appendScannedItemsMode = false;
            appendScannedReceiptItems(detectedItems);
            return;
        }
        showReceiptResults(detectedItems);
    }

    private void cancelScanOnlyFlow() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void returnScannedReceiptItems(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> detectedItems
    ) {
        ArrayList<String> itemNames = new ArrayList<>(detectedItems.size());
        ArrayList<Integer> itemAmountCents = new ArrayList<>(detectedItems.size());
        for (ReceiptParser.ReceiptItem item : detectedItems) {
            itemNames.add(item.getName());
            itemAmountCents.add(item.getAmountCents());
        }

        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra(RESULT_EXTRA_SCANNED_ITEM_NAMES, itemNames);
        resultIntent.putIntegerArrayListExtra(
                RESULT_EXTRA_SCANNED_ITEM_AMOUNT_CENTS,
                itemAmountCents
        );
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private ArrayList<String> extractRecognizedLines(Text recognizedText) {
        ArrayList<String> lineRows = buildRowsFromFragments(collectLineFragments(recognizedText));
        ArrayList<RowFragment> elementFragments = collectElementFragments(recognizedText);
        if (elementFragments.isEmpty()) {
            return lineRows;
        }

        ArrayList<String> elementRows = buildRowsFromFragments(elementFragments);
        return chooseBetterRecognizedRows(lineRows, elementRows);
    }

    private ArrayList<RowFragment> collectLineFragments(Text recognizedText) {
        ArrayList<RowFragment> fragments = new ArrayList<>();
        int fallbackOrder = 0;
        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String lineText = normalizeWhitespace(line.getText());
                if (!lineText.isEmpty()) {
                    Rect bounds = line.getBoundingBox();
                    fragments.add(new RowFragment(lineText, bounds, fallbackOrder++));
                }
            }
        }
        return fragments;
    }

    private ArrayList<RowFragment> collectElementFragments(Text recognizedText) {
        ArrayList<RowFragment> fragments = new ArrayList<>();
        int fallbackOrder = 0;
        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                if (line.getElements().isEmpty()) {
                    String lineText = normalizeWhitespace(line.getText());
                    if (!lineText.isEmpty()) {
                        fragments.add(new RowFragment(lineText, line.getBoundingBox(), fallbackOrder++));
                    }
                    continue;
                }

                for (Text.Element element : line.getElements()) {
                    String elementText = normalizeWhitespace(element.getText());
                    if (!elementText.isEmpty()) {
                        fragments.add(new RowFragment(
                                elementText,
                                element.getBoundingBox(),
                                fallbackOrder++
                        ));
                    }
                }
            }
        }
        return fragments;
    }

    private ArrayList<String> buildRowsFromFragments(ArrayList<RowFragment> fragments) {
        if (fragments.isEmpty()) {
            return new ArrayList<>();
        }

        Collections.sort(fragments, Comparator
                .comparingInt(RowFragment::getTop)
                .thenComparingInt(RowFragment::getLeft));

        ArrayList<RowGroup> rowGroups = new ArrayList<>();
        for (RowFragment fragment : fragments) {
            RowGroup matchingGroup = null;
            float bestDistance = Float.MAX_VALUE;
            for (RowGroup group : rowGroups) {
                float verticalDistance = group.getVerticalDistance(fragment);
                if (group.belongsToSameRow(fragment) && verticalDistance < bestDistance) {
                    matchingGroup = group;
                    bestDistance = verticalDistance;
                }
            }

            if (matchingGroup == null) {
                matchingGroup = new RowGroup();
                rowGroups.add(matchingGroup);
            }
            matchingGroup.add(fragment);
        }

        Collections.sort(rowGroups, Comparator.comparingDouble(RowGroup::getCenterY));

        ArrayList<String> rows = new ArrayList<>();
        for (RowGroup group : rowGroups) {
            String combinedText = group.toCombinedText();
            if (!combinedText.isEmpty()) {
                rows.add(combinedText);
            }
        }
        return rows;
    }

    private float computeReceiptAlignmentRotationDegrees(@NonNull Text recognizedText) {
        double weightedAngleSum = 0d;
        double totalWeight = 0d;

        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Point[] cornerPoints = line.getCornerPoints();
                if (cornerPoints == null || cornerPoints.length < 2) {
                    continue;
                }

                Point startPoint = cornerPoints[0];
                Point endPoint = cornerPoints[1];
                float deltaX = endPoint.x - startPoint.x;
                float deltaY = endPoint.y - startPoint.y;
                double width = Math.hypot(deltaX, deltaY);
                if (width < dpToPx(24)) {
                    continue;
                }

                double angleDegrees = Math.toDegrees(Math.atan2(deltaY, deltaX));
                angleDegrees = normalizeTextAngle(angleDegrees);

                weightedAngleSum += angleDegrees * width;
                totalWeight += width;
            }
        }

        if (totalWeight == 0d) {
            return 0f;
        }

        double averageAngleDegrees = weightedAngleSum / totalWeight;
        if (Math.abs(averageAngleDegrees) < 0.75d) {
            return 0f;
        }

        return (float) -averageAngleDegrees;
    }

    private double normalizeTextAngle(double angleDegrees) {
        while (angleDegrees <= -90d) {
            angleDegrees += 180d;
        }
        while (angleDegrees > 90d) {
            angleDegrees -= 180d;
        }
        return angleDegrees;
    }

    private ArrayList<String> chooseBetterRecognizedRows(
            ArrayList<String> lineRows,
            ArrayList<String> elementRows
    ) {
        ArrayList<ReceiptParser.ReceiptItem> lineItems = receiptParser.extractReceiptItems(lineRows);
        ArrayList<ReceiptParser.ReceiptItem> elementItems =
                receiptParser.extractReceiptItems(elementRows);

        if (elementItems.size() > lineItems.size()) {
            return elementRows;
        }
        if (lineItems.size() > elementItems.size()) {
            return lineRows;
        }

        int lineParseableRows = countParseableRows(lineRows);
        int elementParseableRows = countParseableRows(elementRows);
        if (elementParseableRows > lineParseableRows) {
            return elementRows;
        }
        return lineRows;
    }

    private int countParseableRows(ArrayList<String> rows) {
        int parseableRows = 0;
        for (String row : rows) {
            if (receiptParser.parseReceiptItem(row) != null) {
                parseableRows++;
            }
        }
        return parseableRows;
    }

    private void showReceiptResults(ArrayList<ReceiptParser.ReceiptItem> detectedItems) {
        appendScannedItemsMode = false;
        assignSourceOrderToReceiptItems(detectedItems);
        trackedReceiptItems.clear();
        trackedReceiptItems.addAll(cloneReceiptItems(detectedItems));
        reapplyTrackedReceiptItems();
        showReceiptResultsUi();
    }

    private void appendScannedReceiptItems(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> detectedItems
    ) {
        assignSourceOrderToAppendedReceiptItems(detectedItems);
        trackedReceiptItems.addAll(cloneReceiptItems(detectedItems));
        reapplyTrackedReceiptItems();
        showReceiptResultsUi();
    }

    @NonNull
    private ArrayList<ReceiptParser.ReceiptItem> prepareReceiptItemsForDisplay(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> detectedItems
    ) {
        ArrayList<ReceiptParser.ReceiptItem> displayItems = new ArrayList<>(detectedItems.size());
        for (ReceiptParser.ReceiptItem item : detectedItems) {
            ReceiptParser.ReceiptItem displayItem = new ReceiptParser.ReceiptItem(
                    receiptParser.getGroupedDisplayName(item),
                    item.getAmountCents(),
                    item.getSplitQuantity(),
                    item.getPantAmountCents()
            );
            displayItem.setSourceOrder(item.getSourceOrder());
            displayItem.setPayerParticipantKey(item.getPayerParticipantKey());
            displayItem.selectParticipants(item.copySelectedParticipantKeys());
            displayItems.add(displayItem);
        }
        return displayItems;
    }

    private void reapplyTrackedReceiptItems() {
        ArrayList<ReceiptParser.ReceiptItem> currentItems = cloneReceiptItems(receiptItems);
        ArrayList<ReceiptParser.ReceiptItem> displayItems =
                prepareReceiptItemsForDisplay(cloneReceiptItems(trackedReceiptItems));

        if (currentItems.isEmpty()) {
            for (ReceiptParser.ReceiptItem item : displayItems) {
                selectAllParticipantsForItem(item);
            }
        } else {
            copyParticipantSelections(currentItems, displayItems);
            copyItemPayerSelections(currentItems, displayItems);
        }

        sortReceiptItemsForDisplay(displayItems);
        receiptItems.clear();
        receiptItems.addAll(displayItems);
        refreshReceiptItems();
    }

    private void sortReceiptItemsForDisplay(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> itemsToSort
    ) {
        Comparator<ReceiptParser.ReceiptItem> sourceOrderComparator =
                Comparator.comparingInt(ReceiptParser.ReceiptItem::getSourceOrder);

        if (receiptItemsFilterMode == RECEIPT_FILTER_HIGH_TO_LOW) {
            itemsToSort.sort((left, right) -> {
                int amountComparison = Integer.compare(
                        right.getAmountCents(),
                        left.getAmountCents()
                );
                if (amountComparison != 0) {
                    return amountComparison;
                }
                return Integer.compare(left.getSourceOrder(), right.getSourceOrder());
            });
            return;
        }

        if (receiptItemsFilterMode == RECEIPT_FILTER_LOW_TO_HIGH) {
            itemsToSort.sort((left, right) -> {
                int amountComparison = Integer.compare(
                        left.getAmountCents(),
                        right.getAmountCents()
                );
                if (amountComparison != 0) {
                    return amountComparison;
                }
                return Integer.compare(left.getSourceOrder(), right.getSourceOrder());
            });
            return;
        }

        itemsToSort.sort(sourceOrderComparator);
    }

    private void assignSourceOrderToReceiptItems(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> itemsToAssign
    ) {
        nextReceiptItemSourceOrder = 0;
        for (ReceiptParser.ReceiptItem item : itemsToAssign) {
            item.setSourceOrder(nextReceiptItemSourceOrder++);
        }
    }

    private void assignSourceOrderToAppendedReceiptItems(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> itemsToAssign
    ) {
        for (ReceiptParser.ReceiptItem item : itemsToAssign) {
            item.setSourceOrder(nextReceiptItemSourceOrder++);
        }
    }

    @NonNull
    private ArrayList<ReceiptParser.ReceiptItem> cloneReceiptItems(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> sourceItems
    ) {
        ArrayList<ReceiptParser.ReceiptItem> clonedItems = new ArrayList<>(sourceItems.size());
        for (ReceiptParser.ReceiptItem sourceItem : sourceItems) {
            clonedItems.add(sourceItem.copy());
        }
        return clonedItems;
    }

    private void copyParticipantSelections(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> sourceItems,
            @NonNull ArrayList<ReceiptParser.ReceiptItem> targetItems
    ) {
        LinkedHashMap<Integer, LinkedHashSet<String>> selectedParticipantsBySourceOrder =
                new LinkedHashMap<>();

        for (ReceiptParser.ReceiptItem sourceItem : sourceItems) {
            int sourceOrder = sourceItem.getSourceOrder();
            LinkedHashSet<String> selectedParticipants =
                    selectedParticipantsBySourceOrder.get(sourceOrder);
            if (selectedParticipants == null) {
                selectedParticipants = new LinkedHashSet<>();
                selectedParticipantsBySourceOrder.put(sourceOrder, selectedParticipants);
            }
            selectedParticipants.addAll(sourceItem.copySelectedParticipantKeys());
        }

        for (ReceiptParser.ReceiptItem targetItem : targetItems) {
            Set<String> selectedParticipants =
                    selectedParticipantsBySourceOrder.get(targetItem.getSourceOrder());
            if (selectedParticipants != null) {
                targetItem.selectParticipants(selectedParticipants);
            } else {
                selectAllParticipantsForItem(targetItem);
            }
        }
    }

    private void copyItemPayerSelections(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> sourceItems,
            @NonNull ArrayList<ReceiptParser.ReceiptItem> targetItems
    ) {
        LinkedHashMap<Integer, String> payerParticipantKeysBySourceOrder = new LinkedHashMap<>();

        for (ReceiptParser.ReceiptItem sourceItem : sourceItems) {
            String payerParticipantKey =
                    normalizeReceiptItemPayerKey(sourceItem.getPayerParticipantKey());
            payerParticipantKeysBySourceOrder.put(sourceItem.getSourceOrder(), payerParticipantKey);
        }

        for (ReceiptParser.ReceiptItem targetItem : targetItems) {
            targetItem.setPayerParticipantKey(
                    payerParticipantKeysBySourceOrder.get(targetItem.getSourceOrder())
            );
        }
    }

    private void showReceiptResultsUi() {
        stopCameraPreview();
        setScreenTitle(R.string.receipt_screen_title);
        cameraStatusView.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.VISIBLE);
        setActionsMenuMode(ACTIONS_MODE_RECEIPT);
        setParticipantControlsVisible(true);
        refreshReceiptItems();
        updateNavigationButtonForCurrentState();
    }

    private void clearCurrentReceiptResults() {
        trackedReceiptItems.clear();
        receiptItems.clear();
        nextReceiptItemSourceOrder = 0;
        refreshReceiptItems();
        receiptResultsLayout.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        setActionsMenuMode(ACTIONS_MODE_HIDDEN);
        setParticipantControlsVisible(false);
    }

    private void refreshReceiptItems() {
        receiptItemsAdapter.notifyDataSetChanged();
        updateReceiptItemsEmptyState();
        receiptItemsList.post(this::updateReceiptItemsListHeight);
        updateReceiptTotal();
        updateNextButtonState();
        refreshParticipantButtons();
    }

    private void updateReceiptItemsEmptyState() {
        if (receiptItemsList == null || receiptItemsEmptyView == null) {
            return;
        }

        boolean hasItems = !receiptItems.isEmpty();
        receiptItemsList.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        receiptItemsEmptyView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
    }

    private void updateReceiptItemsListHeight() {
        if (receiptItemsList == null) {
            return;
        }

        ListAdapter adapter = receiptItemsList.getAdapter();
        if (adapter == null) {
            return;
        }

        int width = receiptItemsList.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels - dpToPx(64);
        }

        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int totalHeight = 0;
        for (int index = 0; index < adapter.getCount(); index++) {
            View itemView = adapter.getView(index, null, receiptItemsList);
            itemView.measure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            totalHeight += itemView.getMeasuredHeight();
        }

        ViewGroup.LayoutParams layoutParams = receiptItemsList.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        layoutParams.height = totalHeight
                + (receiptItemsList.getDividerHeight() * Math.max(0, adapter.getCount() - 1));
        receiptItemsList.setLayoutParams(layoutParams);
        receiptItemsList.requestLayout();
    }

    private void updateReceiptTotal() {
        int totalCents = 0;
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            totalCents += item.getAmountCents();
        }
        receiptTotalValueView.setText(
                getString(R.string.receipt_total_format, receiptParser.formatAmount(totalCents))
        );
    }

    private void updateNextButtonState() {
        if (nextButton == null) {
            return;
        }

        ArrayList<String> disabledReasons = buildNextButtonDisabledReasons();
        nextButtonDisabledReasons.clear();
        nextButtonDisabledReasons.addAll(disabledReasons);

        boolean nextButtonEnabled = disabledReasons.isEmpty();
        nextButton.setEnabled(nextButtonEnabled);
        if (nextButtonDisabledInfoButton != null) {
            nextButtonDisabledInfoButton.setVisibility(
                    nextButtonEnabled ? View.GONE : View.VISIBLE
            );
        }
        dismissNextButtonDisabledReasonsPopup();
    }

    @NonNull
    private ArrayList<String> buildNextButtonDisabledReasons() {
        ArrayList<String> disabledReasons = new ArrayList<>();
        if (receiptItems.isEmpty()) {
            disabledReasons.add(getString(R.string.next_disabled_reason_no_items));
        }
        if (participants.size() <= 1) {
            disabledReasons.add(getString(R.string.next_disabled_reason_not_enough_participants));
        }
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            if (countSelectedParticipants(item) == 0) {
                disabledReasons.add(
                        getString(R.string.next_disabled_reason_missing_participant_selection)
                );
                break;
            }
        }
        return disabledReasons;
    }

    private void showNextButtonDisabledReasonsPopup() {
        if (nextButton == null || nextButtonDisabledReasons.isEmpty()) {
            return;
        }
        if (nextButtonDisabledReasonsPopup != null && nextButtonDisabledReasonsPopup.isShowing()) {
            dismissNextButtonDisabledReasonsPopup();
            return;
        }

        View popupView = getLayoutInflater().inflate(
                R.layout.popup_next_button_disabled_reasons,
                null
        );
        LinearLayout reasonsLayout = popupView.findViewById(R.id.layout_next_disabled_reasons);

        for (int index = 0; index < nextButtonDisabledReasons.size(); index++) {
            TextView reasonView = new TextView(this);
            reasonView.setText("\u2022 " + nextButtonDisabledReasons.get(index));
            TextViewCompat.setTextAppearance(
                    reasonView,
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
            );
            reasonView.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK));
            if (index < nextButtonDisabledReasons.size() - 1) {
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
            if (nextButtonDisabledReasonsPopup == popupWindow) {
                nextButtonDisabledReasonsPopup = null;
            }
        });

        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();
        int xOffset = Math.max(0, nextButton.getWidth() - popupWidth);
        int yOffset = -(nextButton.getHeight() + popupHeight + dpToPx(8));
        popupWindow.showAsDropDown(nextButton, xOffset, yOffset);
        nextButtonDisabledReasonsPopup = popupWindow;
    }

    private void dismissNextButtonDisabledReasonsPopup() {
        if (nextButtonDisabledReasonsPopup == null) {
            return;
        }
        nextButtonDisabledReasonsPopup.dismiss();
        nextButtonDisabledReasonsPopup = null;
    }

    private void showHeaderHelpPopup(@NonNull View anchorView) {
        int messageResId = currentScreenTitleResId == R.string.crop_screen_title
                ? R.string.crop_help_message
                : R.string.photo_help_message;
        if (headerHelpPopup != null && headerHelpPopup.isShowing()) {
            dismissHeaderHelpPopup();
            return;
        }

        View popupView = getLayoutInflater().inflate(
                R.layout.popup_header_help_message,
                null
        );
        TextView messageView = popupView.findViewById(R.id.text_header_help_message);
        messageView.setText(messageResId);

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
            if (headerHelpPopup == popupWindow) {
                headerHelpPopup = null;
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
                anchorBounds.bottom + dpToPx(8),
                visibleFrame.top,
                Math.max(visibleFrame.top, visibleFrame.bottom - popupHeight)
        );

        popupWindow.showAtLocation(
                anchorView.getRootView(),
                Gravity.TOP | Gravity.START,
                popupX,
                popupY
        );
        headerHelpPopup = popupWindow;
    }

    private void dismissHeaderHelpPopup() {
        if (headerHelpPopup == null) {
            return;
        }
        headerHelpPopup.dismiss();
        headerHelpPopup = null;
    }

    private void vibrateForReceiptItemLongPress() {
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
                            RECEIPT_ITEM_LONG_PRESS_VIBRATION_DURATION_MS,
                            VibrationEffect.DEFAULT_AMPLITUDE
                    )
            );
        } else {
            vibrator.vibrate(RECEIPT_ITEM_LONG_PRESS_VIBRATION_DURATION_MS);
        }
    }

    private void showEditReceiptItemDialog(ReceiptParser.ReceiptItem item) {
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
        View payerSelectorView =
                dialogView.findViewById(R.id.button_receipt_item_payer_selector);
        AppCompatImageButton payerMenuButton =
                dialogView.findViewById(R.id.button_receipt_item_payer_menu);
        AppCompatImageView payerValueSwatchView =
                dialogView.findViewById(R.id.image_receipt_item_payer_value_swatch);
        TextView payerValueView =
                dialogView.findViewById(R.id.text_receipt_item_payer_value);
        MaterialButton decreaseQuantityButton =
                dialogView.findViewById(R.id.button_decrease_receipt_item_quantity);
        MaterialButton increaseQuantityButton =
                dialogView.findViewById(R.id.button_increase_receipt_item_quantity);
        MaterialButton removeButton =
                dialogView.findViewById(R.id.button_remove_receipt_item);
        MaterialButton splitCombineButton =
                dialogView.findViewById(R.id.button_split_combine_receipt_item);
        String normalizedOriginalName = receiptParser.getCanonicalItemName(item.getName());
        final String originalName = normalizedOriginalName.trim().isEmpty()
                ? item.getName()
                : normalizedOriginalName;
        int originalAmountCents = item.getAmountCents();
        int originalQuantity = item.getSplitQuantity();
        int originalUnitAmountCents = getReceiptItemUnitAmountCents(item);
        int originalPantAmountCents = item.getPantAmountCents();
        int originalUnitPantAmountCents = getReceiptItemUnitPantAmountCents(item);
        String originalPayerParticipantKey =
                normalizeReceiptItemPayerKey(item.getPayerParticipantKey());
        final String[] selectedPayerParticipantKeyHolder = new String[]{originalPayerParticipantKey};

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
        setupReceiptItemQuantityControls(
                quantityInputView,
                decreaseQuantityButton,
                increaseQuantityButton
        );
        setReceiptItemQuantityValue(quantityInputView, originalQuantity);
        updateReceiptItemPayerSummary(
                payerValueSwatchView,
                payerValueView,
                selectedPayerParticipantKeyHolder[0]
        );
        Runnable refreshStructureButtonState = () -> updateReceiptItemStructureButton(
                splitCombineButton,
                item,
                nameInputView,
                priceInputView,
                quantityInputView,
                originalUnitPantAmountCents,
                selectedPayerParticipantKeyHolder[0]
        );
        refreshStructureButtonState.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_receipt_item_title)
                .setView(dialogView)
                .create();
        applyDialogAnimations(dialog);
        boolean[] itemRemoved = new boolean[]{false};
        boolean[] structureActionApplied = new boolean[]{false};

        View.OnClickListener openPayerMenuClickListener = view -> {
            hideKeyboardForFocusedView(dialogView);
            toggleReceiptItemPayerMenu(
                    payerSelectorView,
                    payerMenuButton,
                    selectedPayerParticipantKeyHolder[0],
                    selectedPayerParticipantKey -> {
                        selectedPayerParticipantKeyHolder[0] = selectedPayerParticipantKey;
                        updateReceiptItemPayerSummary(
                                payerValueSwatchView,
                                payerValueView,
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
            dismissReceiptItemPayerPopup();
            removeReceiptItem(item);
            dialog.dismiss();
        });
        splitCombineButton.setOnClickListener(view -> {
            if (applyReceiptItemStructureAction(
                    item,
                    originalUnitPantAmountCents,
                    selectedPayerParticipantKeyHolder[0],
                    nameInputLayout,
                    priceInputLayout,
                    nameInputView,
                    priceInputView,
                    quantityInputView
            )) {
                structureActionApplied[0] = true;
                dismissReceiptItemPayerPopup();
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(dialogInterface -> {
            dismissReceiptItemPayerPopup();
            if (itemRemoved[0] || structureActionApplied[0]) {
                return;
            }
            commitEditedReceiptItemIfValid(
                    item,
                    originalName,
                    originalAmountCents,
                    originalQuantity,
                    originalUnitAmountCents,
                    originalPantAmountCents,
                    originalUnitPantAmountCents,
                    originalPayerParticipantKey,
                    selectedPayerParticipantKeyHolder[0],
                    nameInputLayout,
                    priceInputLayout,
                    nameInputView,
                    priceInputView,
                    quantityInputView
            );
        });
        dialog.show();
    }

    private void commitEditedReceiptItemIfValid(
            @NonNull ReceiptParser.ReceiptItem item,
            @NonNull String originalName,
            int originalAmountCents,
            int originalQuantity,
            int originalUnitAmountCents,
            int originalPantAmountCents,
            int originalUnitPantAmountCents,
            @Nullable String originalPayerParticipantKey,
            @Nullable String selectedPayerParticipantKey,
            @NonNull TextInputLayout nameInputLayout,
            @NonNull TextInputLayout priceInputLayout,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView
    ) {
        String itemName = getText(nameInputView);
        String enteredPrice = getText(priceInputView);
        int updatedQuantity = normalizeReceiptItemQuantity(quantityInputView);

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
                normalizeReceiptItemPayerKey(selectedPayerParticipantKey);
        if (itemName.equals(originalName)
                && updatedUnitAmountCents == originalUnitAmountCents
                && updatedQuantity == originalQuantity
                && areEqualNullableStrings(
                        originalPayerParticipantKey,
                        normalizedSelectedPayerParticipantKey
                )) {
            return;
        }

        boolean quantityUnchanged = updatedQuantity == originalQuantity;
        boolean unitPriceUnchanged = updatedUnitAmountCents == originalUnitAmountCents;
        int updatedAmountCents = quantityUnchanged && unitPriceUnchanged
                ? originalAmountCents
                : multiplyAmountCents(updatedUnitAmountCents, updatedQuantity);
        int updatedPantAmountCents = quantityUnchanged && unitPriceUnchanged
                ? originalPantAmountCents
                : multiplyAmountCents(originalUnitPantAmountCents, updatedQuantity);

        ReceiptParser.ReceiptItem updatedItem = new ReceiptParser.ReceiptItem(
                itemName,
                updatedAmountCents,
                updatedQuantity,
                updatedPantAmountCents
        );
        updatedItem.setSourceOrder(item.getSourceOrder());
        updatedItem.setPayerParticipantKey(normalizedSelectedPayerParticipantKey);
        updatedItem.selectParticipants(item.copySelectedParticipantKeys());
        replaceReceiptItem(item, updatedItem);
        syncTrackedReceiptItemsToCurrentItems();
        reapplyTrackedReceiptItems();
    }

    private void updateReceiptItemStructureButton(
            @NonNull MaterialButton structureButton,
            @NonNull ReceiptParser.ReceiptItem item,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            int originalUnitPantAmountCents,
            @Nullable String selectedPayerParticipantKey
    ) {
        int quantity = Math.max(
                MIN_RECEIPT_ITEM_QUANTITY,
                parseReceiptItemQuantity(getText(quantityInputView))
        );
        if (quantity > 1) {
            structureButton.setText(R.string.split);
            structureButton.setIconResource(R.drawable.ic_edit_receipt_item_split);
            structureButton.setEnabled(canApplyReceiptItemStructureAction(
                    item,
                    nameInputView,
                    priceInputView,
                    quantityInputView,
                    originalUnitPantAmountCents,
                    selectedPayerParticipantKey
            ));
            return;
        }

        structureButton.setText(R.string.combine);
        structureButton.setIconResource(R.drawable.ic_edit_receipt_item_combine);
        structureButton.setEnabled(canCombineReceiptItem(
                item,
                nameInputView,
                priceInputView,
                quantityInputView,
                originalUnitPantAmountCents,
                selectedPayerParticipantKey
        ));
    }

    private boolean canApplyReceiptItemStructureAction(
            @NonNull ReceiptParser.ReceiptItem item,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            int originalUnitPantAmountCents,
            @Nullable String selectedPayerParticipantKey
    ) {
        return buildEditedReceiptItemFromInputs(
                item,
                originalUnitPantAmountCents,
                selectedPayerParticipantKey,
                null,
                null,
                nameInputView,
                priceInputView,
                quantityInputView
        ) != null;
    }

    private boolean canCombineReceiptItem(
            @NonNull ReceiptParser.ReceiptItem item,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView,
            int originalUnitPantAmountCents,
            @Nullable String selectedPayerParticipantKey
    ) {
        ReceiptParser.ReceiptItem updatedItem = buildEditedReceiptItemFromInputs(
                item,
                originalUnitPantAmountCents,
                selectedPayerParticipantKey,
                null,
                null,
                nameInputView,
                priceInputView,
                quantityInputView
        );
        if (updatedItem == null || updatedItem.getSplitQuantity() != MIN_RECEIPT_ITEM_QUANTITY) {
            return false;
        }
        return !getReceiptItemsToCombine(item, updatedItem).isEmpty();
    }

    private boolean applyReceiptItemStructureAction(
            @NonNull ReceiptParser.ReceiptItem item,
            int originalUnitPantAmountCents,
            @Nullable String selectedPayerParticipantKey,
            @NonNull TextInputLayout nameInputLayout,
            @NonNull TextInputLayout priceInputLayout,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView
    ) {
        ReceiptParser.ReceiptItem updatedItem = buildEditedReceiptItemFromInputs(
                item,
                originalUnitPantAmountCents,
                selectedPayerParticipantKey,
                nameInputLayout,
                priceInputLayout,
                nameInputView,
                priceInputView,
                quantityInputView
        );
        if (updatedItem == null) {
            return false;
        }

        if (updatedItem.getSplitQuantity() > MIN_RECEIPT_ITEM_QUANTITY) {
            splitReceiptItem(item, updatedItem);
            return true;
        }

        ArrayList<ReceiptParser.ReceiptItem> itemsToCombine = getReceiptItemsToCombine(item, updatedItem);
        if (itemsToCombine.isEmpty()) {
            return false;
        }
        combineReceiptItems(item, updatedItem, itemsToCombine);
        return true;
    }

    @Nullable
    private ReceiptParser.ReceiptItem buildEditedReceiptItemFromInputs(
            @NonNull ReceiptParser.ReceiptItem originalItem,
            int originalUnitPantAmountCents,
            @Nullable String selectedPayerParticipantKey,
            @Nullable TextInputLayout nameInputLayout,
            @Nullable TextInputLayout priceInputLayout,
            @NonNull TextInputEditText nameInputView,
            @NonNull TextInputEditText priceInputView,
            @NonNull TextInputEditText quantityInputView
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
                parseReceiptItemQuantity(getText(quantityInputView))
        );
        int updatedAmountCents = multiplyAmountCents(updatedUnitAmountCents, updatedQuantity);
        int updatedPantAmountCents = multiplyAmountCents(
                originalUnitPantAmountCents,
                updatedQuantity
        );
        ReceiptParser.ReceiptItem updatedItem = new ReceiptParser.ReceiptItem(
                itemName,
                updatedAmountCents,
                updatedQuantity,
                updatedPantAmountCents
        );
        updatedItem.setSourceOrder(originalItem.getSourceOrder());
        updatedItem.setPayerParticipantKey(normalizeReceiptItemPayerKey(selectedPayerParticipantKey));
        updatedItem.selectParticipants(originalItem.copySelectedParticipantKeys());
        return updatedItem;
    }

    @NonNull
    private ArrayList<ReceiptParser.ReceiptItem> getReceiptItemsToCombine(
            @NonNull ReceiptParser.ReceiptItem originalItem,
            @NonNull ReceiptParser.ReceiptItem updatedItem
    ) {
        ArrayList<ReceiptParser.ReceiptItem> itemsToCombine = new ArrayList<>();
        for (ReceiptParser.ReceiptItem candidateItem : receiptItems) {
            if (candidateItem == originalItem) {
                continue;
            }
            if (areReceiptItemsCompatibleForCombine(updatedItem, candidateItem)) {
                itemsToCombine.add(candidateItem);
            }
        }
        return itemsToCombine;
    }

    private boolean areReceiptItemsCompatibleForCombine(
            @NonNull ReceiptParser.ReceiptItem anchorItem,
            @NonNull ReceiptParser.ReceiptItem candidateItem
    ) {
        if (!normalizeWhitespace(receiptParser.getCanonicalItemName(anchorItem.getName()))
                .equalsIgnoreCase(
                        normalizeWhitespace(receiptParser.getCanonicalItemName(candidateItem.getName()))
                )) {
            return false;
        }
        if (getReceiptItemUnitAmountCents(anchorItem) != getReceiptItemUnitAmountCents(candidateItem)) {
            return false;
        }
        if (getReceiptItemUnitPantAmountCents(anchorItem) != getReceiptItemUnitPantAmountCents(candidateItem)) {
            return false;
        }
        if (!areEqualNullableStrings(
                normalizeReceiptItemPayerKey(anchorItem.getPayerParticipantKey()),
                normalizeReceiptItemPayerKey(candidateItem.getPayerParticipantKey())
        )) {
            return false;
        }
        return anchorItem.copySelectedParticipantKeys().equals(
                candidateItem.copySelectedParticipantKeys()
        );
    }

    private void splitReceiptItem(
            @NonNull ReceiptParser.ReceiptItem originalItem,
            @NonNull ReceiptParser.ReceiptItem updatedItem
    ) {
        int itemIndex = receiptItems.indexOf(originalItem);
        if (itemIndex < 0) {
            return;
        }

        String canonicalName = normalizeWhitespace(receiptParser.getCanonicalItemName(updatedItem.getName()));
        if (canonicalName.isEmpty()) {
            canonicalName = updatedItem.getName().trim();
        }

        int unitAmountCents = getReceiptItemUnitAmountCents(updatedItem);
        int unitPantAmountCents = getReceiptItemUnitPantAmountCents(updatedItem);
        ArrayList<ReceiptParser.ReceiptItem> splitItems = new ArrayList<>(updatedItem.getSplitQuantity());
        for (int index = 0; index < updatedItem.getSplitQuantity(); index++) {
            ReceiptParser.ReceiptItem splitItem = new ReceiptParser.ReceiptItem(
                    canonicalName,
                    unitAmountCents,
                    MIN_RECEIPT_ITEM_QUANTITY,
                    unitPantAmountCents
            );
            splitItem.setPayerParticipantKey(updatedItem.getPayerParticipantKey());
            splitItem.selectParticipants(updatedItem.copySelectedParticipantKeys());
            splitItems.add(splitItem);
        }

        receiptItems.remove(itemIndex);
        receiptItems.addAll(itemIndex, splitItems);
        reassignReceiptItemSourceOrders();
        syncTrackedReceiptItemsToCurrentItems();
        reapplyTrackedReceiptItems();
    }

    private void combineReceiptItems(
            @NonNull ReceiptParser.ReceiptItem originalItem,
            @NonNull ReceiptParser.ReceiptItem updatedItem,
            @NonNull List<ReceiptParser.ReceiptItem> itemsToCombine
    ) {
        int itemIndex = receiptItems.indexOf(originalItem);
        if (itemIndex < 0) {
            return;
        }

        String canonicalName = normalizeWhitespace(receiptParser.getCanonicalItemName(updatedItem.getName()));
        if (canonicalName.isEmpty()) {
            canonicalName = updatedItem.getName().trim();
        }

        int combinedAmountCents = updatedItem.getAmountCents();
        int combinedQuantity = updatedItem.getSplitQuantity();
        int combinedPantAmountCents = updatedItem.getPantAmountCents();
        for (ReceiptParser.ReceiptItem candidateItem : itemsToCombine) {
            combinedAmountCents += candidateItem.getAmountCents();
            combinedQuantity += candidateItem.getSplitQuantity();
            combinedPantAmountCents += candidateItem.getPantAmountCents();
        }

        ReceiptParser.ReceiptItem combinedItem = new ReceiptParser.ReceiptItem(
                canonicalName,
                combinedAmountCents,
                combinedQuantity,
                combinedPantAmountCents
        );
        combinedItem.setPayerParticipantKey(updatedItem.getPayerParticipantKey());
        combinedItem.selectParticipants(updatedItem.copySelectedParticipantKeys());

        receiptItems.removeAll(itemsToCombine);
        int refreshedItemIndex = receiptItems.indexOf(originalItem);
        if (refreshedItemIndex < 0) {
            refreshedItemIndex = Math.min(itemIndex, receiptItems.size());
        } else {
            receiptItems.remove(refreshedItemIndex);
        }
        receiptItems.add(refreshedItemIndex, combinedItem);
        reassignReceiptItemSourceOrders();
        syncTrackedReceiptItemsToCurrentItems();
        reapplyTrackedReceiptItems();
    }

    private int getReceiptItemUnitAmountCents(@NonNull ReceiptParser.ReceiptItem item) {
        return divideAmountCents(item.getAmountCents(), item.getSplitQuantity());
    }

    private int getReceiptItemUnitPantAmountCents(@NonNull ReceiptParser.ReceiptItem item) {
        return divideAmountCents(item.getPantAmountCents(), item.getSplitQuantity());
    }

    private int divideAmountCents(int totalAmountCents, int quantity) {
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

    private int multiplyAmountCents(int unitAmountCents, int quantity) {
        long multipliedAmount = (long) unitAmountCents * Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        if (multipliedAmount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (multipliedAmount < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) multipliedAmount;
    }

    private void toggleReceiptItemPayerMenu(
            @NonNull View anchorView,
            @NonNull AppCompatImageButton menuButton,
            @Nullable String selectedPayerParticipantKey,
            @NonNull ReceiptItemPayerSelectionListener selectionListener
    ) {
        if (receiptItemPayerPopup != null && receiptItemPayerPopup.isShowing()) {
            dismissReceiptItemPayerPopup();
            return;
        }
        showReceiptItemPayerMenu(anchorView, menuButton, selectedPayerParticipantKey, selectionListener);
    }

    private void showReceiptItemPayerMenu(
            @NonNull View anchorView,
            @NonNull AppCompatImageButton menuButton,
            @Nullable String selectedPayerParticipantKey,
            @NonNull ReceiptItemPayerSelectionListener selectionListener
    ) {
        View popupView = getLayoutInflater().inflate(
                R.layout.popup_receipt_item_payer_menu,
                null
        );
        LinearLayout optionsLayout =
                popupView.findViewById(R.id.layout_receipt_item_payer_options);

        String normalizedSelectedPayerParticipantKey =
                normalizeReceiptItemPayerKey(selectedPayerParticipantKey);
        addReceiptItemPayerOptionRow(
                optionsLayout,
                getString(R.string.filter_default),
                Color.WHITE,
                Color.BLACK,
                normalizedSelectedPayerParticipantKey == null,
                () -> {
                    dismissReceiptItemPayerPopup();
                    selectionListener.onPayerSelected(null);
                }
        );

        for (int index = 0; index < participants.size(); index++) {
            Participant participant = participants.get(index);
            addReceiptItemPayerDivider(optionsLayout);
            addReceiptItemPayerOptionRow(
                    optionsLayout,
                    participant.name,
                    participant.color,
                    null,
                    participant.key.equals(normalizedSelectedPayerParticipantKey),
                    () -> {
                        dismissReceiptItemPayerPopup();
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
            if (receiptItemPayerPopup == popupWindow) {
                receiptItemPayerPopup = null;
            }
            setMenuExpanded(menuButton, false);
        });

        int popupWidth = popupView.getMeasuredWidth();
        int xOffset = Math.max(0, anchorView.getWidth() - popupWidth);
        popupWindow.showAsDropDown(anchorView, xOffset, dpToPx(8));
        receiptItemPayerPopup = popupWindow;
        setMenuExpanded(menuButton, true);
    }

    private void dismissReceiptItemPayerPopup() {
        if (receiptItemPayerPopup == null) {
            return;
        }
        receiptItemPayerPopup.dismiss();
        receiptItemPayerPopup = null;
    }

    private void addReceiptItemPayerOptionRow(
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

        swatchView.setBackground(createReceiptItemPayerSwatchDrawable(fillColor, strokeColor));
        labelView.setText(label);
        rowView.setAlpha(selected ? 1f : 0.82f);
        rowView.setOnClickListener(view -> onClick.run());
        parentLayout.addView(rowView);
    }

    private void addReceiptItemPayerDivider(@NonNull LinearLayout parentLayout) {
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
    private GradientDrawable createReceiptItemPayerSwatchDrawable(
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

    private void updateReceiptItemPayerSummary(
            @NonNull AppCompatImageView payerValueSwatchView,
            @NonNull TextView payerValueView,
            @Nullable String payerParticipantKey
    ) {
        String normalizedPayerParticipantKey = normalizeReceiptItemPayerKey(payerParticipantKey);
        Integer strokeColor = normalizedPayerParticipantKey == null ? Color.BLACK : null;
        int fillColor = Color.WHITE;
        if (normalizedPayerParticipantKey != null) {
            Participant participant = findParticipantByKey(normalizedPayerParticipantKey);
            if (participant != null) {
                fillColor = participant.color;
            }
        }
        payerValueSwatchView.setBackground(
                createReceiptItemPayerSwatchDrawable(fillColor, strokeColor)
        );
        payerValueView.setText(getReceiptItemPayerDisplayName(payerParticipantKey));
    }

    @NonNull
    private String getReceiptItemPayerDisplayName(@Nullable String payerParticipantKey) {
        String normalizedPayerParticipantKey = normalizeReceiptItemPayerKey(payerParticipantKey);
        if (normalizedPayerParticipantKey == null) {
            return getString(R.string.filter_default);
        }

        Participant participant = findParticipantByKey(normalizedPayerParticipantKey);
        if (participant == null) {
            return getString(R.string.filter_default);
        }
        return participant.name;
    }

    @Nullable
    private String normalizeReceiptItemPayerKey(@Nullable String payerParticipantKey) {
        String normalizedPayerParticipantKey = normalizeWhitespace(payerParticipantKey);
        if (normalizedPayerParticipantKey.isEmpty()) {
            return null;
        }
        return findParticipantByKey(normalizedPayerParticipantKey) == null
                ? null
                : normalizedPayerParticipantKey;
    }

    private boolean areEqualNullableStrings(@Nullable String left, @Nullable String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private void showReceiptItemActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            @NonNull ReceiptParser.ReceiptItem item
    ) {
        AnchoredDropdownMenuHelper.showSingleActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                R.string.remove,
                R.drawable.ic_history_remove,
                () -> removeReceiptItem(item)
        );
    }

    private void removeReceiptItem(@NonNull ReceiptParser.ReceiptItem item) {
        receiptItems.remove(item);
        syncTrackedReceiptItemsToCurrentItems();
        reapplyTrackedReceiptItems();
    }

    private void replaceReceiptItem(
            @NonNull ReceiptParser.ReceiptItem originalItem,
            @NonNull ReceiptParser.ReceiptItem updatedItem
    ) {
        int itemIndex = receiptItems.indexOf(originalItem);
        if (itemIndex >= 0) {
            receiptItems.set(itemIndex, updatedItem);
        }
    }

    private void showAddReceiptItemDialog() {
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
        final String[] selectedPayerParticipantKeyHolder = new String[]{null};

        priceInputView.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );
        updateReceiptItemPayerSummary(
                payerValueSwatchView,
                payerValueView,
                selectedPayerParticipantKeyHolder[0]
        );
        setupReceiptItemQuantityControls(
                quantityInputView,
                decreaseQuantityButton,
                increaseQuantityButton
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_new_item_title)
                .setView(dialogView)
                .create();
        applyDialogAnimations(dialog);

        View.OnClickListener openPayerMenuClickListener = view -> {
            hideKeyboardForFocusedView(dialogView);
            toggleReceiptItemPayerMenu(
                    payerSelectorView,
                    payerMenuButton,
                    selectedPayerParticipantKeyHolder[0],
                    selectedPayerParticipantKey -> {
                        selectedPayerParticipantKeyHolder[0] = selectedPayerParticipantKey;
                        updateReceiptItemPayerSummary(
                                payerValueSwatchView,
                                payerValueView,
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

            int quantity = normalizeReceiptItemQuantity(quantityInputView);
            dismissReceiptItemPayerPopup();
            addReceiptItems(
                    itemName,
                    amountCents,
                    selectedPayerParticipantKeyHolder[0],
                    quantity
            );
            dialog.dismiss();
        });
        dialog.setOnDismissListener(dialogInterface -> dismissReceiptItemPayerPopup());
        dialog.show();
    }

    private void setupReceiptItemQuantityControls(
            @NonNull TextInputEditText quantityInputView,
            @NonNull MaterialButton decreaseQuantityButton,
            @NonNull MaterialButton increaseQuantityButton
    ) {
        setReceiptItemQuantityValue(quantityInputView, MIN_RECEIPT_ITEM_QUANTITY);
        boolean[] isUpdatingQuantity = new boolean[]{false};
        Runnable refreshDecreaseButtonState = () -> decreaseQuantityButton.setEnabled(
                parseReceiptItemQuantity(getText(quantityInputView)) > MIN_RECEIPT_ITEM_QUANTITY
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

                int parsedQuantity = parseReceiptItemQuantity(quantityText);
                int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, parsedQuantity);
                String normalizedQuantityText = String.valueOf(normalizedQuantity);
                if (!normalizedQuantityText.equals(quantityText)) {
                    isUpdatingQuantity[0] = true;
                    setReceiptItemQuantityValue(quantityInputView, normalizedQuantity);
                    isUpdatingQuantity[0] = false;
                }
                refreshDecreaseButtonState.run();
            }
        });
        quantityInputView.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                normalizeReceiptItemQuantity(quantityInputView);
            }
            refreshDecreaseButtonState.run();
        });
        decreaseQuantityButton.setOnClickListener(view -> {
            int quantity = normalizeReceiptItemQuantity(quantityInputView);
            setReceiptItemQuantityValue(
                    quantityInputView,
                    Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity - 1)
            );
            refreshDecreaseButtonState.run();
        });
        increaseQuantityButton.setOnClickListener(view -> {
            int quantity = Math.max(
                    MIN_RECEIPT_ITEM_QUANTITY,
                    parseReceiptItemQuantity(getText(quantityInputView))
            );
            if (quantity < Integer.MAX_VALUE) {
                quantity++;
            }
            setReceiptItemQuantityValue(quantityInputView, quantity);
            refreshDecreaseButtonState.run();
        });
        refreshDecreaseButtonState.run();
    }

    private int normalizeReceiptItemQuantity(@NonNull TextInputEditText quantityInputView) {
        int normalizedQuantity = Math.max(
                MIN_RECEIPT_ITEM_QUANTITY,
                parseReceiptItemQuantity(getText(quantityInputView))
        );
        setReceiptItemQuantityValue(quantityInputView, normalizedQuantity);
        return normalizedQuantity;
    }

    private int parseReceiptItemQuantity(@Nullable String quantityText) {
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

    private void setReceiptItemQuantityValue(
            @NonNull TextInputEditText quantityInputView,
            int quantity
    ) {
        String quantityText = String.valueOf(Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity));
        quantityInputView.setText(quantityText);
        if (quantityInputView.getText() != null) {
            quantityInputView.setSelection(quantityInputView.getText().length());
        }
    }

    private void addReceiptItem(
            @NonNull String itemName,
            int amountCents,
            @Nullable String payerParticipantKey
    ) {
        addReceiptItems(itemName, amountCents, payerParticipantKey, 1);
    }

    private void addReceiptItems(
            @NonNull String itemName,
            int amountCents,
            @Nullable String payerParticipantKey,
            int quantity
    ) {
        int normalizedQuantity = Math.max(MIN_RECEIPT_ITEM_QUANTITY, quantity);
        ReceiptParser.ReceiptItem item = new ReceiptParser.ReceiptItem(
                itemName,
                multiplyAmountCents(amountCents, normalizedQuantity),
                normalizedQuantity
        );
        item.setSourceOrder(nextReceiptItemSourceOrder++);
        item.setPayerParticipantKey(normalizeReceiptItemPayerKey(payerParticipantKey));
        selectAllParticipantsForItem(item);
        receiptItems.add(item);
        syncTrackedReceiptItemsToCurrentItems();
        reapplyTrackedReceiptItems();
        showReceiptResultsUi();
    }

    private void reassignReceiptItemSourceOrders() {
        for (int index = 0; index < receiptItems.size(); index++) {
            receiptItems.get(index).setSourceOrder(index);
        }
        nextReceiptItemSourceOrder = receiptItems.size();
    }

    private void syncTrackedReceiptItemsToCurrentItems() {
        trackedReceiptItems.clear();
        ArrayList<ReceiptParser.ReceiptItem> sortedItems = cloneReceiptItems(receiptItems);
        sortedItems.sort(Comparator.comparingInt(ReceiptParser.ReceiptItem::getSourceOrder));
        int highestSourceOrder = -1;
        for (ReceiptParser.ReceiptItem item : sortedItems) {
            ReceiptParser.ReceiptItem trackedItem = item.copy();
            trackedItem.setName(receiptParser.getCanonicalItemName(trackedItem.getName()));
            trackedReceiptItems.add(trackedItem);
            highestSourceOrder = Math.max(highestSourceOrder, trackedItem.getSourceOrder());
        }
        nextReceiptItemSourceOrder = highestSourceOrder + 1;
    }

    private void applyDialogAnimations(@NonNull AlertDialog dialog) {
        if (dialog.getWindow() == null) {
            return;
        }

        dialog.getWindow().setWindowAnimations(R.style.TestRepo_DialogAnimation);
    }

    private void showAddParticipantDialog(boolean contactsPermissionGranted) {
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

            if (isParticipantAlreadyAdded(name, phoneNumber)) {
                Toast.makeText(this, R.string.participant_already_added, Toast.LENGTH_SHORT).show();
                return;
            }

            addParticipant(name, phoneNumber);
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

    private void ensureDefaultParticipant() {
        if (findParticipantByKey(DEFAULT_PARTICIPANT_KEY) != null) {
            return;
        }

        participants.add(new Participant(
                DEFAULT_PARTICIPANT_NAME,
                "",
                DEFAULT_PARTICIPANT_KEY,
                getParticipantInitials(DEFAULT_PARTICIPANT_NAME),
                createParticipantColor(participants.size())
        ));
        crownedParticipantKey = DEFAULT_PARTICIPANT_KEY;
    }

    private void applyPreAddedParticipants() {
        for (AppSettings.PreAddedParticipant preAddedParticipant
                : AppSettings.getPreAddedParticipants(this)) {
            if (isParticipantAlreadyAdded(preAddedParticipant.name, preAddedParticipant.phoneNumber)) {
                continue;
            }

            participants.add(new Participant(
                    preAddedParticipant.name,
                    preAddedParticipant.phoneNumber,
                    buildParticipantKey(preAddedParticipant.name, preAddedParticipant.phoneNumber),
                    getParticipantInitials(preAddedParticipant.name),
                    createParticipantColor(participants.size())
            ));
        }
    }

    private void refreshDefaultParticipantPhoneNumber() {
        if (backgroundExecutor == null) {
            return;
        }

        backgroundExecutor.execute(() -> {
            String phoneNumber = loadPhoneNumberFromOwnerProfile();
            runOnUiThread(() -> updateDefaultParticipantPhoneNumber(phoneNumber));
        });
    }

    private String loadPhoneNumberFromOwnerProfile() {
        if (!hasContactsPermission()) {
            return "";
        }

        Uri profileDataUri = ContactsContract.Profile.CONTENT_URI.buildUpon()
                .appendPath(ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
                .build();

        try (Cursor cursor = getContentResolver().query(
                profileDataUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.Data.MIMETYPE + " = ?",
                new String[]{ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE},
                null
        )) {
            if (cursor == null) {
                return "";
            }

            int phoneNumberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                String phoneNumber = phoneNumberColumn >= 0
                        ? normalizeWhitespace(cursor.getString(phoneNumberColumn))
                        : "";
                if (!phoneNumber.isEmpty()) {
                    return phoneNumber;
                }
            }
        }

        return "";
    }

    private void updateDefaultParticipantPhoneNumber(String phoneNumber) {
        Participant participant = findParticipantByKey(DEFAULT_PARTICIPANT_KEY);
        if (participant == null || phoneNumber.isEmpty()) {
            return;
        }

        participant.phoneNumber = phoneNumber;
    }

    @Nullable
    private Participant findParticipantByKey(String participantKey) {
        for (Participant participant : participants) {
            if (participant.key.equals(participantKey)) {
                return participant;
            }
        }
        return null;
    }

    private void addParticipant(String name, String phoneNumber) {
        Participant participant = new Participant(
                name,
                phoneNumber,
                buildParticipantKey(name, phoneNumber),
                getParticipantInitials(name),
                createParticipantColor(participants.size())
        );
        participants.add(participant);
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            item.selectParticipant(participant.key);
        }
        refreshParticipantButtons();
        refreshReceiptItems();
    }

    private void applyDefaultParticipantSelections() {
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            selectAllParticipantsForItem(item);
        }
    }

    private void selectAllParticipantsForItem(ReceiptParser.ReceiptItem item) {
        for (Participant participant : participants) {
            item.selectParticipant(participant.key);
        }
    }

    private boolean isParticipantAlreadyAdded(String name, String phoneNumber) {
        String normalizedName = normalizeWhitespace(name).toLowerCase(Locale.US);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        for (Participant participant : participants) {
            boolean sameName = participant.name.toLowerCase(Locale.US).equals(normalizedName);
            boolean samePhone = !normalizedPhoneNumber.isEmpty()
                    && normalizePhoneNumber(participant.phoneNumber).equals(normalizedPhoneNumber);
            if ((sameName && samePhone) || samePhone) {
                return true;
            }
        }
        return false;
    }

    private void setParticipantControlsVisible(boolean visible) {
        participantControlsVisible = visible;
        updateReceiptActionButtonsVisibility();
        if (visible) {
            refreshParticipantButtons();
        } else {
            updateParticipantButtonsVisibility();
        }
    }

    private void updateReceiptActionButtonsVisibility() {
        if (receiptActionButtonsLayout == null) {
            return;
        }

        receiptActionButtonsLayout.setVisibility(
                participantControlsVisible ? View.VISIBLE : View.GONE
        );
    }

    private void updateParticipantButtonsVisibility() {
        if (participantButtonsLayout == null) {
            return;
        }

        if (!participantControlsVisible || participants.isEmpty()) {
            participantButtonsLayout.setVisibility(View.GONE);
        } else {
            participantButtonsLayout.setVisibility(View.VISIBLE);
        }
    }

    private void refreshParticipantButtons() {
        participantButtonsLayout.removeAllViews();
        if (!participantControlsVisible || participants.isEmpty()) {
            updateParticipantButtonsVisibility();
            return;
        }

        updateParticipantButtonsVisibility();
        for (int index = 0; index < participants.size(); index++) {
            Participant participant = participants.get(index);
            View rowView = getLayoutInflater().inflate(
                    R.layout.item_receipt_view_participant_button,
                    participantButtonsLayout,
                    false
            );
            MaterialButton badgeButton = rowView.findViewById(R.id.button_summary_participant_badge);
            AppCompatImageView ownerIconView =
                    rowView.findViewById(R.id.image_summary_participant_owner);
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);

            configureSummaryParticipantBadgeButton(badgeButton, participant);
            ownerIconView.setVisibility(
                    isCrownedParticipant(participant) ? View.VISIBLE : View.GONE
            );
            nameView.setText(participant.name);
            amountView.setText(buildReceiptViewParticipantTotalDisplayText(participant));

            LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index < participants.size() - 1) {
                rowLayoutParams.bottomMargin = dpToPx(8);
            }
            rowView.setLayoutParams(rowLayoutParams);

            View.OnClickListener openDetailsListener =
                    view -> showParticipantDetailsDialog(participant);
            rowView.setOnClickListener(openDetailsListener);
            badgeButton.setOnClickListener(openDetailsListener);
            rowView.setOnTouchListener(
                    createReceiptParticipantLongPressTouchListener(rowView, participant)
            );
            badgeButton.setOnTouchListener(
                    createReceiptParticipantLongPressTouchListener(badgeButton, participant)
            );
            participantButtonsLayout.addView(rowView);
        }
    }

    @NonNull
    private View.OnTouchListener createReceiptParticipantLongPressTouchListener(
            @NonNull View anchorView,
            @NonNull Participant participant
    ) {
        return new View.OnTouchListener() {
            private final int touchSlop = ViewConfiguration
                    .get(NewReceiptActivity.this)
                    .getScaledTouchSlop();
            private float downX;
            private float downY;
            private float downRawX;
            private float downRawY;
            private boolean longPressTriggered;
            private final Runnable longPressRunnable = () -> {
                longPressTriggered = true;
                vibrateForReceiptItemLongPress();
                showReceiptParticipantActionsMenu(
                        anchorView,
                        downRawX,
                        downRawY,
                        participant
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
                                RECEIPT_ITEM_LONG_PRESS_DURATION_MS
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

    private void showReceiptParticipantActionsMenu(
            @NonNull View anchorView,
            float rawTouchX,
            float rawTouchY,
            @NonNull Participant participant
    ) {
        ArrayList<AnchoredDropdownMenuHelper.ActionItem> actions = new ArrayList<>();
        actions.add(new AnchoredDropdownMenuHelper.ActionItem(
                R.string.assign_payer,
                R.drawable.ic_receipt_owner_crown,
                () -> {
                    setCrownedParticipant(participant);
                    refreshParticipantButtons();
                },
                !isCrownedParticipant(participant)
        ));
        actions.add(new AnchoredDropdownMenuHelper.ActionItem(
                R.string.remove,
                R.drawable.ic_receipt_participant_remove,
                () -> removeParticipant(participant),
                !isDefaultParticipant(participant)
        ));
        AnchoredDropdownMenuHelper.showActionMenu(
                anchorView,
                rawTouchX,
                rawTouchY,
                actions
        );
    }

    private void showParticipantDetailsDialog(Participant participant) {
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
                participant.phoneNumber.isEmpty()
                        ? getString(R.string.participant_phone_unavailable)
                        : participant.phoneNumber
        );
        participantTotalView.setText(buildParticipantTotalDisplayText(participant));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        payerLabelView.setVisibility(View.VISIBLE);
        updateParticipantCrownButton(crownToggleButton, participant);
        crownToggleButton.setOnClickListener(view -> {
            if (isCrownedParticipant(participant)) {
                return;
            }

            setCrownedParticipant(participant);
            updateParticipantCrownButton(crownToggleButton, participant);
            refreshParticipantButtons();
        });

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
                removeParticipant(participant);
                dialog.dismiss();
            });
        }

        toggleParticipantItemsButton.setVisibility(View.GONE);

        dialog.show();
    }

    private void showReceiptSummaryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receipt_summary, null);
        View summaryRootView = dialogView.findViewById(R.id.layout_receipt_summary_root);
        LinearLayout transfersLayout =
                dialogView.findViewById(R.id.layout_receipt_summary_transfers);
        TextView emptyView = dialogView.findViewById(R.id.text_receipt_summary_empty);
        TextInputLayout receiptNameInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_summary_receipt_name);
        TextInputEditText receiptNameInputView =
                dialogView.findViewById(R.id.edit_receipt_summary_receipt_name);
        View closeButton = dialogView.findViewById(R.id.button_close_receipt_summary);
        MaterialButton sendRequestsButton = dialogView.findViewById(R.id.button_send_requests);
        AppCompatImageButton sendRequestsNoInternetInfoButton =
                dialogView.findViewById(R.id.button_send_requests_no_internet_info);
        final ConnectivityManager.NetworkCallback[] networkCallbackHolder =
                new ConnectivityManager.NetworkCallback[1];

        ArrayList<ReceiptSummaryTransfer> transfers = buildReceiptSummaryTransfers();
        boolean hasPendingPayments = !transfers.isEmpty();
        if (transfers.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            for (ReceiptSummaryTransfer transfer : transfers) {
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

        receiptNameInputView.setFilters(new InputFilter[]{
                createReceiptSummaryNameInputFilter(),
                new InputFilter.LengthFilter(20)
        });
        receiptNameInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                receiptNameInputLayout.setError(null);
                updateReceiptSummarySendRequestsUi(
                        sendRequestsButton,
                        sendRequestsNoInternetInfoButton,
                        hasPendingPayments,
                        receiptNameInputView
                );
            }
        });
        receiptNameInputLayout.setEnabled(hasPendingPayments);
        receiptNameInputView.setEnabled(hasPendingPayments);
        updateReceiptSummarySendRequestsUi(
                sendRequestsButton,
                sendRequestsNoInternetInfoButton,
                hasPendingPayments,
                receiptNameInputView
        );

        Dialog dialog = new Dialog(this, AppSettings.getFullScreenDialogThemeResId(this)) {
            @Override
            public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    clearSummaryTextInputFocusIfTappedOutside(
                            receiptNameInputView,
                            summaryRootView,
                            event
                    );
                }

                return super.dispatchTouchEvent(event);
            }
        };
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
                    NewReceiptActivity.this,
                    networkCallbackHolder[0]
            );
        });
        networkCallbackHolder[0] = NetworkStateHelper.registerDefaultNetworkCallback(
                this,
                () -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                        return;
                    }

                    updateReceiptSummarySendRequestsUi(
                            sendRequestsButton,
                            sendRequestsNoInternetInfoButton,
                            hasPendingPayments,
                            receiptNameInputView
                    );
                })
        );

        sendRequestsButton.setOnClickListener(view -> {
            if (!NetworkStateHelper.hasInternetConnection(this)) {
                updateReceiptSummarySendRequestsUi(
                        sendRequestsButton,
                        sendRequestsNoInternetInfoButton,
                        hasPendingPayments,
                        receiptNameInputView
                );
                return;
            }

            String receiptName = getText(receiptNameInputView);
            if (!validateReceiptSummaryName(receiptNameInputLayout, receiptNameInputView)) {
                return;
            }
            showSendRequestsConfirmationDialog(
                    dialog,
                    receiptName,
                    ""
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

    private void openAddToArchiveFlowFromReceiptView() {
        showSelectArchiveDialog(null, getCurrentReceiptName());
    }

    private void showSelectArchiveDialog(
            @Nullable Dialog summaryDialog,
            @NonNull String receiptName
    ) {
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
        receiptNameInput.setText(receiptName);
        if (receiptNameInput.getText() != null) {
            receiptNameInput.setSelection(receiptNameInput.getText().length());
        }

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

        addArchiveButton.setOnClickListener(view -> showNewArchiveDialogFromSelectLocation(() -> {
            selectedArchiveIndex[0] = 0;
            refreshLocations.run();
            locationsListView.setItemChecked(1, true);
        }));

        createButton.setEnabled(false);
        createButton.setOnClickListener(view -> {
            String selectedReceiptName = getText(receiptNameInput);
            if (selectedReceiptName.isEmpty()) {
                updateSelectLocationCreateButtonState(receiptNameInput, createButton);
                return;
            }

            setCurrentReceiptName(selectedReceiptName);
            ReceiptHistoryStore.HistoryEntry receiptEntry = buildCurrentReceiptHistoryEntry("");
            dialog.dismiss();
            if (summaryDialog != null) {
                summaryDialog.dismiss();
            }
            if (selectedArchiveIndex[0] < 0) {
                ArchiveStore.addStandaloneReceipt(this, receiptEntry);
            } else {
                ArchiveStore.addReceiptToArchive(
                        this,
                        selectedArchiveIndex[0],
                        receiptEntry
                );
            }
            Toast.makeText(this, R.string.receipt_added_to_archives, Toast.LENGTH_SHORT).show();
            returnToMainMenu();
        });

        dialog.show();
    }

    private void updateSelectLocationCreateButtonState(
            @NonNull TextInputEditText receiptNameInput,
            @NonNull MaterialButton createButton
    ) {
        createButton.setEnabled(!getText(receiptNameInput).isEmpty());
    }

    private void showNewArchiveDialogFromSelectLocation(@NonNull Runnable onArchiveCreated) {
        ArrayList<String> archiveNames = ArchiveStore.loadArchiveNames(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_archive, null);
        TextInputEditText archiveNameInput = dialogView.findViewById(R.id.input_archive_name);
        MaterialButton createButton = dialogView.findViewById(R.id.button_create_archive);
        AppCompatImageButton disabledInfoButton =
                dialogView.findViewById(R.id.button_create_archive_disabled_info);

        updateSelectArchiveCreateButtonState(
                archiveNameInput,
                createButton,
                disabledInfoButton,
                archiveNames
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
                updateSelectArchiveCreateButtonState(
                        archiveNameInput,
                        createButton,
                        disabledInfoButton,
                        archiveNames
                );
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_archive_title)
                .setView(dialogView)
                .create();
        dialog.setOnDismissListener(dialogInterface ->
                dismissNewArchiveCreateDisabledReasonsPopup()
        );

        disabledInfoButton.setOnClickListener(view -> showNewArchiveCreateDisabledReasonsPopup(
                createButton,
                buildSelectArchiveCreateDisabledReasons(
                        getText(archiveNameInput),
                        archiveNames
                )
        ));

        createButton.setOnClickListener(view -> {
            String archiveName = getText(archiveNameInput);
            if (archiveName.isEmpty() || archiveNameExists(archiveName, archiveNames)) {
                updateSelectArchiveCreateButtonState(
                        archiveNameInput,
                        createButton,
                        disabledInfoButton,
                        archiveNames
                );
                return;
            }

            ArchiveStore.addArchiveName(this, archiveName);
            onArchiveCreated.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateSelectArchiveCreateButtonState(
            @NonNull TextInputEditText archiveNameInput,
            @NonNull MaterialButton createButton,
            @NonNull AppCompatImageButton disabledInfoButton,
            @NonNull List<String> archiveNames
    ) {
        ArrayList<String> disabledReasons = buildSelectArchiveCreateDisabledReasons(
                getText(archiveNameInput),
                archiveNames
        );
        boolean isEnabled = disabledReasons.isEmpty();
        createButton.setEnabled(isEnabled);
        disabledInfoButton.setVisibility(isEnabled ? View.GONE : View.VISIBLE);
        if (isEnabled) {
            dismissNewArchiveCreateDisabledReasonsPopup();
        }
    }

    @NonNull
    private ArrayList<String> buildSelectArchiveCreateDisabledReasons(
            @NonNull String archiveName,
            @NonNull List<String> archiveNames
    ) {
        ArrayList<String> disabledReasons = new ArrayList<>();
        if (archiveName.isEmpty()) {
            disabledReasons.add(getString(R.string.create_archive_disabled_reason_empty_name));
        }
        if (!archiveName.isEmpty() && archiveNameExists(archiveName, archiveNames)) {
            disabledReasons.add(getString(R.string.create_archive_disabled_reason_duplicate_name));
        }
        return disabledReasons;
    }

    private boolean archiveNameExists(
            @NonNull String archiveName,
            @NonNull List<String> archiveNames
    ) {
        String normalizedArchiveName = archiveName.trim();
        for (String existingArchiveName : archiveNames) {
            if (existingArchiveName.trim().equalsIgnoreCase(normalizedArchiveName)) {
                return true;
            }
        }
        return false;
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

    private void configureSummaryParticipantBadgeButton(
            @NonNull MaterialButton badgeButton,
            @NonNull Participant participant
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
        applyParticipantBadgeTextStyle(badgeButton, participant, false);
        badgeButton.setStrokeWidth(0);
        badgeButton.setBackgroundTintList(ColorStateList.valueOf(participant.color));
        badgeButton.setTextColor(getParticipantTextColor(participant.color));
        badgeButton.setContentDescription(participant.name);
    }

    private void showSendRequestsConfirmationDialog(
            @NonNull Dialog summaryDialog,
            @NonNull String receiptName,
            @NonNull String customMessage
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
            setCurrentReceiptName(receiptName);
            confirmationDialog.dismiss();
            summaryDialog.dismiss();
            openSendRequestsFlow(customMessage);
        });

        confirmationDialog.show();
    }

    private void updateReceiptSummarySendRequestsUi(
            @NonNull MaterialButton sendRequestsButton,
            @NonNull AppCompatImageButton sendRequestsNoInternetInfoButton,
            boolean hasPendingPayments,
            @NonNull TextInputEditText receiptNameInputView
    ) {
        boolean hasInternetConnection = NetworkStateHelper.hasInternetConnection(this);
        sendRequestsButton.setEnabled(
                hasPendingPayments
                        && isValidReceiptSummaryName(getText(receiptNameInputView))
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

    private void openSendRequestsFlow(@NonNull String customMessage) {
        pendingSendRequestsMessage = customMessage.trim();
        if (!DeviceCapabilityHelper.supportsSms(this)) {
            pendingSendRequestsMessage = "";
            Toast.makeText(
                    this,
                    R.string.send_requests_not_supported,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (!hasSendSmsPermission()) {
            sendRequestsAfterSmsPermission = true;
            requestSendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }
        sendParticipantPaymentRequests();
    }

    @NonNull
    private InputFilter createReceiptSummaryNameInputFilter() {
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

    private boolean validateReceiptSummaryName(
            @NonNull TextInputLayout receiptNameInputLayout,
            @NonNull TextInputEditText receiptNameInputView
    ) {
        String receiptName = getText(receiptNameInputView);
        if (receiptName.isEmpty()) {
            receiptNameInputLayout.setError(getString(R.string.receipt_summary_receipt_name_required));
            receiptNameInputView.requestFocus();
            return false;
        }

        if (!isValidReceiptSummaryName(receiptName)) {
            receiptNameInputLayout.setError(getString(R.string.receipt_summary_receipt_name_invalid));
            receiptNameInputView.requestFocus();
            return false;
        }

        receiptNameInputLayout.setError(null);
        return true;
    }

    private boolean isValidReceiptSummaryName(@NonNull String receiptName) {
        String trimmedName = receiptName.trim();
        if (trimmedName.isEmpty()) {
            return false;
        }

        for (int index = 0; index < trimmedName.length(); index++) {
            char currentChar = trimmedName.charAt(index);
            if (!Character.isLetterOrDigit(currentChar) && currentChar != ' ') {
                return false;
            }
        }
        return true;
    }

    private void clearSummaryTextInputFocusIfTappedOutside(
            @NonNull TextInputEditText inputView,
            @NonNull View fallbackView,
            @NonNull MotionEvent event
    ) {
        if (!inputView.isFocused()) {
            return;
        }

        Rect inputBounds = new Rect();
        inputView.getGlobalVisibleRect(inputBounds);
        if (!inputBounds.contains((int) event.getRawX(), (int) event.getRawY())) {
            clearTextInputFocus(inputView, fallbackView);
        }
    }

    private void setCurrentReceiptName(@NonNull String receiptName) {
        currentReceiptName = receiptName;
    }

    private void setParticipantSelectionsForAllItems(
            @NonNull Participant participant,
            boolean selected
    ) {
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            if (selected) {
                item.selectParticipant(participant.key);
            } else {
                item.deselectParticipant(participant.key);
            }
        }
        syncTrackedReceiptItemsToCurrentItems();
        refreshReceiptItems();
    }

    @NonNull
    private CharSequence buildParticipantTotalDisplayText(@NonNull Participant participant) {
        BigDecimal participantTotal = computeParticipantShareTotal(participant);
        BigDecimal receiptTotal = BigDecimal.valueOf(computeReceiptTotalCents(), 2);
        String amountText = formatCurrency(participantTotal);
        String percentageText = " (" + formatParticipantSharePercentage(participantTotal, receiptTotal) + "%)";
        SpannableString displayText = new SpannableString(amountText + percentageText);
        int percentageStart = amountText.length();
        displayText.setSpan(
                new RelativeSizeSpan(0.72f),
                percentageStart,
                displayText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        displayText.setSpan(
                new ForegroundColorSpan(resolveThemeColor(android.R.attr.textColorSecondary, 0xFF808080)),
                percentageStart,
                displayText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return displayText;
    }

    @NonNull
    private CharSequence buildReceiptViewParticipantTotalDisplayText(@NonNull Participant participant) {
        BigDecimal participantTotal = computeParticipantShareTotal(participant);
        return formatCurrency(participantTotal) + "kr";
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

    private void sendParticipantPaymentRequests() {
        if (!DeviceCapabilityHelper.supportsSms(this)) {
            pendingSendRequestsMessage = "";
            Toast.makeText(
                    this,
                    R.string.send_requests_not_supported,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        String customMessage = pendingSendRequestsMessage;
        pendingSendRequestsMessage = "";
        String receiptName = getCurrentReceiptName();
        ReceiptHistoryStore.HistoryEntry historyEntry = buildCurrentReceiptHistoryEntry(customMessage);
        ArrayList<ReceiptSummaryTransfer> transfers = buildReceiptSummaryTransfers();

        saveReceiptHistoryEntry(
                historyEntry,
                new SupabaseHistoryService.EntryCallback() {
                    @Override
                    public void onSuccess(@NonNull ReceiptHistoryStore.HistoryEntry savedHistoryEntry) {
                        sendParticipantPaymentRequestsWithHistoryId(
                                savedHistoryEntry,
                                receiptName,
                                transfers
                        );
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(
                                NewReceiptActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void sendParticipantPaymentRequestsWithHistoryId(
            @NonNull ReceiptHistoryStore.HistoryEntry savedHistoryEntry,
            @NonNull String receiptName,
            @NonNull ArrayList<ReceiptSummaryTransfer> transfers
    ) {
        String requestId = getHistoryEntryShortId(savedHistoryEntry);
        SmsManager smsManager = SmsManager.getDefault();
        int sentCount = 0;
        int skippedCount = 0;

        for (Participant participant : participants) {
            if (isDefaultParticipant(participant)) {
                continue;
            }

            String phoneNumber = normalizeWhitespace(participant.phoneNumber);
            if (!isValidPhoneNumber(phoneNumber)) {
                skippedCount++;
                continue;
            }

            String message = buildParticipantPaymentRequestMessage(
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
    private String buildParticipantPaymentRequestMessage(
            @NonNull Participant participant,
            @NonNull String receiptName,
            @NonNull ArrayList<ReceiptSummaryTransfer> transfers,
            @NonNull String requestId
    ) {
        ArrayList<ParticipantPaymentRequestLine> outgoingLines = new ArrayList<>();
        ArrayList<ParticipantPaymentRequestLine> incomingLines = new ArrayList<>();

        for (ReceiptSummaryTransfer transfer : transfers) {
            if (transfer.fromParticipant.key.equals(participant.key)) {
                Participant payeeParticipant = transfer.toParticipant;
                outgoingLines.add(new ParticipantPaymentRequestLine(
                        getParticipantExternalDisplayName(payeeParticipant),
                        transfer.amount,
                        buildPaymentRequestUrlOrNull(
                                resolveParticipantPaymentLinkPhoneNumber(payeeParticipant),
                                transfer.amount,
                                receiptName,
                                requestId
                        )
                ));
            } else if (transfer.toParticipant.key.equals(participant.key)) {
                incomingLines.add(new ParticipantPaymentRequestLine(
                        getParticipantExternalDisplayName(transfer.fromParticipant),
                        transfer.amount,
                        null
                ));
            }
        }

        StringBuilder messageBuilder = new StringBuilder(getString(
                R.string.participant_payment_request_intro,
                getParticipantExternalDisplayName(participant),
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

    @Nullable
    private String buildPaymentRequestUrlOrNull(
            @NonNull String phoneNumber,
            @NonNull BigDecimal amount,
            @NonNull String message,
            @NonNull String requestId
    ) {
        if (!isValidPhoneNumber(phoneNumber)) {
            return null;
        }
        return buildPaymentRequestUrl(phoneNumber, amount, message, requestId);
    }

    @NonNull
    private String resolveParticipantPaymentLinkPhoneNumber(@NonNull Participant participant) {
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
    private Participant getReceiptOwnerParticipant() {
        Participant crownedParticipant = findParticipantByKey(crownedParticipantKey);
        if (crownedParticipant != null) {
            return crownedParticipant;
        }

        Participant defaultParticipant = findParticipantByKey(DEFAULT_PARTICIPANT_KEY);
        if (defaultParticipant != null) {
            return defaultParticipant;
        }

        if (!participants.isEmpty()) {
            return participants.get(0);
        }

        throw new IllegalStateException("No participants available when sending payment requests.");
    }

    @NonNull
    private String buildPaymentRequestUrl(
            @NonNull String phoneNumber,
            @NonNull BigDecimal amount,
            @NonNull String message,
            @NonNull String requestId
    ) {
        Uri.Builder builder = Uri.parse(PAYMENT_LINK_BASE_URL)
                .buildUpon()
                .appendQueryParameter("Phone", normalizePhoneNumber(phoneNumber))
                .appendQueryParameter("Amount", formatUrlAmount(amount))
                .appendQueryParameter("Message", message);
        if (!requestId.isEmpty()) {
            builder.appendQueryParameter("ID", requestId);
        }
        return builder.build().toString();
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
    private String formatUrlAmount(@NonNull BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    @NonNull
    private String getParticipantPhoneNumberForMessage(@NonNull Participant participant) {
        String phoneNumber = normalizeWhitespace(participant.phoneNumber);
        if (!phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        return getString(R.string.participant_phone_unavailable);
    }

    @NonNull
    private String getParticipantExternalDisplayName(@NonNull Participant participant) {
        if (isDefaultParticipant(participant)) {
            return AppSettings.getUsernameNickname(this);
        }
        return participant.name;
    }

    @NonNull
    private ArrayList<ReceiptSummaryTransfer> buildReceiptSummaryTransfers() {
        LinkedHashMap<String, Participant> participantsByKey = new LinkedHashMap<>();
        LinkedHashMap<String, BigDecimal> balancesByKey = new LinkedHashMap<>();
        for (Participant participant : participants) {
            participantsByKey.put(participant.key, participant);
            balancesByKey.put(participant.key, BigDecimal.ZERO);
        }

        for (ReceiptParser.ReceiptItem item : receiptItems) {
            Participant payer = findReceiptSummaryPayer(item);
            if (payer == null) {
                continue;
            }

            participantsByKey.putIfAbsent(payer.key, payer);
            balancesByKey.putIfAbsent(payer.key, BigDecimal.ZERO);

            int selectedParticipantCount = countSelectedParticipants(item);
            if (selectedParticipantCount == 0) {
                continue;
            }

            BigDecimal itemAmount = BigDecimal.valueOf(item.getAmountCents(), 2);
            BigDecimal sharedAmount = itemAmount.divide(
                    BigDecimal.valueOf(selectedParticipantCount),
                    2,
                    RoundingMode.HALF_UP
            );
            for (Participant participant : participants) {
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

        ArrayList<ReceiptSummaryBalance> creditors = new ArrayList<>();
        ArrayList<ReceiptSummaryBalance> debtors = new ArrayList<>();
        for (String participantKey : balancesByKey.keySet()) {
            BigDecimal balance = balancesByKey.get(participantKey).setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new ReceiptSummaryBalance(
                        participantsByKey.get(participantKey),
                        balance
                ));
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new ReceiptSummaryBalance(
                        participantsByKey.get(participantKey),
                        balance.abs()
                ));
            }
        }

        ArrayList<ReceiptSummaryTransfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort((first, second) -> second.amount.compareTo(first.amount));
            debtors.sort((first, second) -> second.amount.compareTo(first.amount));

            ReceiptSummaryBalance creditor = creditors.get(0);
            ReceiptSummaryBalance debtor = debtors.get(0);
            BigDecimal transferAmount = creditor.amount.min(debtor.amount)
                    .setScale(2, RoundingMode.HALF_UP);

            transfers.add(new ReceiptSummaryTransfer(
                    debtor.participant,
                    creditor.participant,
                    getReceiptSummaryParticipantDisplayName(debtor.participant),
                    getReceiptSummaryParticipantDisplayName(creditor.participant),
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
    private Participant findReceiptSummaryPayer(@NonNull ReceiptParser.ReceiptItem item) {
        String explicitPayerKey = normalizeReceiptItemPayerKey(item.getPayerParticipantKey());
        if (explicitPayerKey != null) {
            Participant explicitPayer = findParticipantByKey(explicitPayerKey);
            if (explicitPayer != null) {
                return explicitPayer;
            }
        }
        return getReceiptOwnerParticipant();
    }

    @NonNull
    private String getReceiptSummaryParticipantDisplayName(@NonNull Participant participant) {
        return isDefaultParticipant(participant)
                ? DEFAULT_PARTICIPANT_NAME
                : normalizeWhitespace(participant.name);
    }

    private BigDecimal computeParticipantShareTotal(Participant participant) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            if (!item.isParticipantSelected(participant.key)) {
                continue;
            }

            int selectedParticipantCount = countSelectedParticipants(item);
            if (selectedParticipantCount == 0) {
                continue;
            }

            BigDecimal itemAmount = BigDecimal.valueOf(item.getAmountCents(), 2);
            BigDecimal sharedAmount = itemAmount.divide(
                    BigDecimal.valueOf(selectedParticipantCount),
                    2,
                    RoundingMode.HALF_UP
            );
            total = total.add(sharedAmount);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private int countSelectedParticipants(ReceiptParser.ReceiptItem item) {
        int count = 0;
        for (Participant participant : participants) {
            if (item.isParticipantSelected(participant.key)) {
                count++;
            }
        }
        return count;
    }

    private String formatCurrency(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.ParticipantShare> buildHistoryParticipants() {
        ArrayList<ReceiptHistoryStore.ParticipantShare> participantShares = new ArrayList<>();
        for (Participant participant : participants) {
            BigDecimal participantTotal = computeParticipantShareTotal(participant);
            participantShares.add(new ReceiptHistoryStore.ParticipantShare(
                    participant.key,
                    participant.name,
                    participant.initials,
                    participant.color,
                    participant.phoneNumber,
                    formatCurrency(participantTotal),
                    isCrownedParticipant(participant),
                    participantTotal.compareTo(BigDecimal.ZERO) <= 0
            ));
        }
        return participantShares;
    }

    @NonNull
    private ArrayList<ReceiptHistoryStore.HistoryItem> buildHistoryItems() {
        ArrayList<ReceiptHistoryStore.HistoryItem> historyItems = new ArrayList<>();
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            historyItems.add(new ReceiptHistoryStore.HistoryItem(
                    item.getName(),
                    item.getDisplayPrice(),
                    normalizeWhitespace(
                            item.getPayerParticipantKey() == null
                                    ? ""
                                    : item.getPayerParticipantKey()
                    ),
                    new ArrayList<>(item.copySelectedParticipantKeys())
            ));
        }
        return historyItems;
    }

    @NonNull
    private ReceiptHistoryStore.HistoryEntry buildCurrentReceiptHistoryEntry(
            @NonNull String customMessage
    ) {
        return new ReceiptHistoryStore.HistoryEntry(
                getCurrentReceiptName(),
                receiptParser.formatAmount(computeReceiptTotalCents()),
                getCurrentHistoryDate(),
                customMessage,
                buildHistoryParticipants(),
                buildHistoryItems()
        );
    }

    private void saveReceiptHistoryEntry(
            @NonNull ReceiptHistoryStore.HistoryEntry historyEntry,
            @NonNull SupabaseHistoryService.EntryCallback callback
    ) {
        SupabaseHistoryService.saveEntry(
                getApplicationContext(),
                historyEntry,
                callback
        );
    }

    @NonNull
    private String getCurrentReceiptName() {
        String title = normalizeWhitespace(currentReceiptName);
        return title.isEmpty() ? getString(R.string.new_receipt_screen_title) : title;
    }

    private int computeReceiptTotalCents() {
        int totalCents = 0;
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            totalCents += item.getAmountCents();
        }
        return totalCents;
    }

    @NonNull
    private String getCurrentHistoryDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US));
    }

    private void removeParticipant(Participant participant) {
        if (isCrownedParticipant(participant)) {
            crownedParticipantKey = DEFAULT_PARTICIPANT_KEY;
        }
        participants.remove(participant);
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            item.deselectParticipant(participant.key);
            if (participant.key.equals(item.getPayerParticipantKey())) {
                item.setPayerParticipantKey(null);
            }
        }
        refreshParticipantButtons();
        refreshReceiptItems();
    }

    private void bindParticipantSelectionButtons(
            LinearLayout participantSelectionLayout,
            ReceiptParser.ReceiptItem item
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
            Participant participant = participants.get(index);
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
            selectionButton.setFocusable(false);
            selectionButton.setFocusableInTouchMode(false);
            selectionButton.setCheckable(false);
            selectionButton.setContentDescription(participant.name);

            boolean isChecked = item.isParticipantSelected(participant.key);
            updateParticipantSelectionButtonStyle(selectionButton, participant, isChecked);
            selectionButton.setOnClickListener(view -> {
                item.toggleParticipantSelection(participant.key);
                updateParticipantSelectionButtonStyle(
                        selectionButton,
                        participant,
                        item.isParticipantSelected(participant.key)
                );
                refreshParticipantButtons();
                updateNextButtonState();
            });
            if (currentRow != null) {
                currentRow.addView(selectionButton);
            }
        }
    }

    private void updateParticipantSelectionButtonStyle(
            MaterialButton selectionButton,
            Participant participant,
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

    private int createParticipantColor(int participantIndex) {
        float hue = (participantIndex * 137.508f) % 360f;
        float[] hsv = {hue, 0.72f, 0.78f};
        return Color.HSVToColor(hsv);
    }

    private int getParticipantTextColor(int backgroundColor) {
        double brightness = (
                (Color.red(backgroundColor) * 0.299)
                        + (Color.green(backgroundColor) * 0.587)
                        + (Color.blue(backgroundColor) * 0.114)
        ) / 255d;
        return brightness > 0.65d ? Color.BLACK : Color.WHITE;
    }

    private boolean isDefaultParticipant(Participant participant) {
        return DEFAULT_PARTICIPANT_KEY.equals(participant.key);
    }

    private boolean isCrownedParticipant(@NonNull Participant participant) {
        return participant.key.equals(crownedParticipantKey);
    }

    private void setCrownedParticipant(@NonNull Participant participant) {
        crownedParticipantKey = participant.key;
    }

    private void updateParticipantCrownButton(
            @NonNull AppCompatImageButton crownButton,
            @NonNull Participant participant
    ) {
        boolean isSelected = isCrownedParticipant(participant);
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

    private String getParticipantBadgeLabel(Participant participant) {
        if (isDefaultParticipant(participant)) {
            return getDefaultParticipantBadgeLabel();
        }
        return participant.initials;
    }

    @NonNull
    private String getDefaultParticipantBadgeLabel() {
        return DEFAULT_PARTICIPANT_NAME;
    }

    private void applyParticipantBadgeTextStyle(
            @NonNull MaterialButton badgeButton,
            @NonNull Participant participant,
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

    private float getParticipantBadgeTextSizeSp(Participant participant, boolean compact) {
        String badgeLabel = getParticipantBadgeLabel(participant);
        if (badgeLabel.length() > 2) {
            return compact ? 9f : 11f;
        }
        return compact ? 11f : 13f;
    }

    private String getParticipantInitials(String name) {
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

    private String buildParticipantKey(String name, String phoneNumber) {
        return normalizeWhitespace(name).toLowerCase(Locale.US)
                + "\u001F"
                + normalizePhoneNumber(phoneNumber);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

    private void onReceiptNotDetected() {
        setScreenTitle(R.string.photo_screen_title);
        cameraStatusView.setVisibility(View.GONE);
        captureButton.setEnabled(true);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
        updateNavigationButtonForCurrentState();
        Toast.makeText(this, R.string.no_receipt_detected, Toast.LENGTH_SHORT).show();
    }

    private void onCroppedReceiptNotDetected() {
        setScreenTitle(R.string.crop_screen_title);
        cameraStatusView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.VISIBLE);
        cropReceiptButton.setEnabled(true);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
        updateNavigationButtonForCurrentState();
        Toast.makeText(this, R.string.no_receipt_detected, Toast.LENGTH_SHORT).show();
    }

    private void stopCameraPreview() {
        imageCapture = null;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private Bitmap loadBitmapForCropping(File imageFile, float autoRotateDegrees) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), boundsOptions);

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                boundsOptions,
                MAX_CROP_BITMAP_DIMENSION,
                MAX_CROP_BITMAP_DIMENSION
        );

        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), decodeOptions);
        if (bitmap == null) {
            throw new IOException("Bitmap decode failed");
        }

        return rotateBitmapIfNeeded(bitmap, imageFile, autoRotateDegrees);
    }

    private Bitmap rotateBitmapIfNeeded(Bitmap bitmap, File imageFile, float autoRotateDegrees)
            throws IOException {
        ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        int orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
        );

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            default:
                break;
        }

        if (Math.abs(autoRotateDegrees) >= 0.75f) {
            matrix.postRotate(autoRotateDegrees);
        }

        if (matrix.isIdentity()) {
            return bitmap;
        }

        Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    private int calculateInSampleSize(
            BitmapFactory.Options options,
            int requestedWidth,
            int requestedHeight
    ) {
        int inSampleSize = 1;
        int height = options.outHeight;
        int width = options.outWidth;

        while (height / inSampleSize > requestedHeight || width / inSampleSize > requestedWidth) {
            inSampleSize *= 2;
        }

        return Math.max(1, inSampleSize);
    }

    private void saveBitmapAsJpeg(Bitmap bitmap, File outputFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                throw new IOException("Bitmap compression failed");
            }
            outputStream.flush();
        }
    }

    private File createImageFile(String prefix) throws IOException {
        File picturesDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDirectory == null) {
            picturesDirectory = getFilesDir();
        }

        if (!picturesDirectory.exists() && !picturesDirectory.mkdirs()) {
            throw new IOException("Unable to create pictures directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis());
        return new File(picturesDirectory, prefix + timestamp + ".jpg");
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String getText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return normalizeWhitespace(phoneNumber).replaceAll("[^+\\d]", "");
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        String trimmedPhoneNumber = normalizeWhitespace(phoneNumber);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        return !trimmedPhoneNumber.isEmpty()
                && normalizedPhoneNumber.length() >= 6
                && Patterns.PHONE.matcher(trimmedPhoneNumber).matches();
    }

    private void updateAddParticipantButtonState(
            TextInputLayout nameLayout,
            TextInputLayout phoneLayout,
            TextInputEditText nameInput,
            TextInputEditText phoneInput,
            MaterialButton addParticipantButton
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

    private static final class SharedReceiptImportData {
        private final ArrayList<String> rows = new ArrayList<>();
        private final ArrayList<Uri> imageUris = new ArrayList<>();
        private final ArrayList<File> temporaryFiles = new ArrayList<>();
    }

    private static final class RowFragment {
        private final String text;
        @Nullable
        private final Rect bounds;
        private final int fallbackOrder;

        private RowFragment(String text, @Nullable Rect bounds, int fallbackOrder) {
            this.text = text;
            this.bounds = bounds;
            this.fallbackOrder = fallbackOrder;
        }

        private float getCenterY() {
            return bounds == null ? fallbackOrder * 1000f : bounds.exactCenterY();
        }

        private float getHeight() {
            return bounds == null ? 0f : bounds.height();
        }

        private boolean hasBounds() {
            return bounds != null;
        }

        private int getTop() {
            return bounds == null ? fallbackOrder * 1000 : bounds.top;
        }

        private int getBottom() {
            return bounds == null ? fallbackOrder * 1000 : bounds.bottom;
        }

        private int getLeft() {
            return bounds == null ? fallbackOrder : bounds.left;
        }
    }

    private static final class RowGroup {
        private final ArrayList<RowFragment> fragments = new ArrayList<>();
        private float averageCenterY;
        private float averageHeight;
        private int top;
        private int bottom;

        private boolean belongsToSameRow(RowFragment fragment) {
            if (fragments.isEmpty()) {
                return true;
            }

            float centerThreshold = Math.max(
                    10f,
                    Math.min(averageHeight, Math.max(fragment.getHeight(), 1f)) * 0.45f
            );
            if (Math.abs(fragment.getCenterY() - averageCenterY) > centerThreshold) {
                return false;
            }

            if (!fragment.hasBounds() || fragments.isEmpty()) {
                return true;
            }

            float allowedGap = Math.max(3f, Math.min(averageHeight, fragment.getHeight()) * 0.12f);
            return getVerticalDistance(fragment) <= allowedGap;
        }

        private float getVerticalDistance(RowFragment fragment) {
            if (fragments.isEmpty() || !fragment.hasBounds()) {
                return 0f;
            }

            int overlap = Math.min(bottom, fragment.getBottom()) - Math.max(top, fragment.getTop());
            if (overlap >= 0) {
                return 0f;
            }
            return -overlap;
        }

        private float getCenterY() {
            return averageCenterY;
        }

        private void add(RowFragment fragment) {
            fragments.add(fragment);

            float totalCenterY = 0f;
            float totalHeight = 0f;
            for (RowFragment existingFragment : fragments) {
                totalCenterY += existingFragment.getCenterY();
                totalHeight += existingFragment.getHeight();
            }
            averageCenterY = totalCenterY / fragments.size();
            averageHeight = totalHeight / fragments.size();

            if (fragments.size() == 1 || !fragment.hasBounds()) {
                top = fragment.getTop();
                bottom = fragment.getBottom();
            } else {
                top = Math.min(top, fragment.getTop());
                bottom = Math.max(bottom, fragment.getBottom());
            }
        }

        private String toCombinedText() {
            Collections.sort(fragments, Comparator.comparingInt(RowFragment::getLeft));

            StringBuilder builder = new StringBuilder();
            for (RowFragment fragment : fragments) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(fragment.text);
            }
            return builder.toString().trim().replaceAll("\\s+", " ");
        }
    }

    private void showCameraStatus(int stringResId) {
        showStatusMessage(stringResId, true);
    }

    private void showStatusMessage(int stringResId, boolean disableCaptureButton) {
        cameraStatusView.setText(stringResId);
        cameraStatusView.setVisibility(View.VISIBLE);
        cameraStatusView.bringToFront();
        setActionsMenuMode(ACTIONS_MODE_HIDDEN);
        if (disableCaptureButton) {
            captureButton.setEnabled(false);
        }
    }

    private void showPermissionRequired() {
        setScreenTitle(R.string.photo_screen_title);
        previewView.setVisibility(View.VISIBLE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        cameraStatusView.setText(R.string.camera_permission_required);
        cameraStatusView.setVisibility(View.VISIBLE);
        captureButton.setEnabled(true);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
        setParticipantControlsVisible(false);
        updateNavigationButtonForCurrentState();
    }

    private void showCameraUnavailable() {
        setScreenTitle(R.string.photo_screen_title);
        previewView.setVisibility(View.VISIBLE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        setParticipantControlsVisible(false);
        showCameraStatus(R.string.camera_unavailable);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
        updateNavigationButtonForCurrentState();
    }

    private void setActionsMenuMode(int mode) {
        actionsMenuMode = mode;
        updateActionsButtonAppearance();
    }

    private void updateActionsButtonAppearance() {
        if (receiptActionsButton == null) {
            return;
        }

        if (actionsMenuMode == ACTIONS_MODE_HIDDEN) {
            dismissHeaderHelpPopup();
            receiptActionsButton.setVisibility(View.INVISIBLE);
            return;
        }

        boolean showHeaderHelpButton = shouldShowHeaderHelpButton();
        if (!showHeaderHelpButton) {
            dismissHeaderHelpPopup();
        }

        receiptActionsButton.setVisibility(View.VISIBLE);
        receiptActionsButton.setImageResource(
                showHeaderHelpButton
                        ? R.drawable.ic_receipt_help_question
                        : R.drawable.ic_more_vert
        );
        receiptActionsButton.setContentDescription(getString(
                showHeaderHelpButton
                        ? R.string.help
                        : (actionsMenuMode == ACTIONS_MODE_RECEIPT
                        ? R.string.receipt_actions
                        : R.string.more_options)
        ));
    }

    private boolean shouldShowHeaderHelpButton() {
        return actionsMenuMode == ACTIONS_MODE_SETTINGS_ONLY
                && (currentScreenTitleResId == R.string.photo_screen_title
                || currentScreenTitleResId == R.string.crop_screen_title);
    }

    private int clamp(int value, int minValue, int maxValue) {
        return Math.min(Math.max(value, minValue), maxValue);
    }

    @Override
    protected void onDestroy() {
        dismissNextButtonDisabledReasonsPopup();
        dismissNewArchiveCreateDisabledReasonsPopup();
        dismissHeaderHelpPopup();
        dismissReceiptItemPayerPopup();
        dismissSendRequestsNoInternetPopup();
        super.onDestroy();
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
        }
    }

    private final class ReceiptItemsAdapter extends ArrayAdapter<ReceiptParser.ReceiptItem> {
        ReceiptItemsAdapter() {
            super(NewReceiptActivity.this, R.layout.item_receipt_line, receiptItems);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = getLayoutInflater().inflate(R.layout.item_receipt_line, parent, false);
            }

            ReceiptParser.ReceiptItem item = getItem(position);
            AppCompatImageView payerSwatchView =
                    itemView.findViewById(R.id.image_receipt_item_payer_swatch);
            TextView itemNameView = itemView.findViewById(R.id.text_receipt_item_name);
            TextView itemPriceView = itemView.findViewById(R.id.text_receipt_item_price);
            LinearLayout participantSelectionLayout =
                    itemView.findViewById(R.id.layout_receipt_item_participants);

            if (item != null) {
                View receiptItemView = itemView;
                String payerParticipantKey =
                        normalizeReceiptItemPayerKey(item.getPayerParticipantKey());
                if (payerParticipantKey == null) {
                    payerSwatchView.setVisibility(View.GONE);
                    payerSwatchView.setBackground(null);
                } else {
                    Participant payerParticipant = findParticipantByKey(payerParticipantKey);
                    if (payerParticipant == null) {
                        payerSwatchView.setVisibility(View.GONE);
                        payerSwatchView.setBackground(null);
                    } else {
                        payerSwatchView.setVisibility(View.VISIBLE);
                        payerSwatchView.setBackground(
                                createReceiptItemPayerSwatchDrawable(
                                        payerParticipant.color,
                                        null
                                )
                        );
                    }
                }
                itemNameView.setText(item.getName());
                itemPriceView.setText(
                        getString(R.string.archive_summary_transfer_amount, item.getDisplayPrice())
                );
                bindParticipantSelectionButtons(participantSelectionLayout, item);
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnClickListener(view -> showEditReceiptItemDialog(item));
                itemView.setOnTouchListener(new View.OnTouchListener() {
                    private final int touchSlop = ViewConfiguration
                            .get(NewReceiptActivity.this)
                            .getScaledTouchSlop();
                    private float downX;
                    private float downY;
                    private float downRawX;
                    private float downRawY;
                    private boolean longPressTriggered;
                    private final Runnable longPressRunnable = () -> {
                        longPressTriggered = true;
                        vibrateForReceiptItemLongPress();
                        showReceiptItemActionsMenu(
                                receiptItemView,
                                downRawX,
                                downRawY,
                                item
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
                                        RECEIPT_ITEM_LONG_PRESS_DURATION_MS
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
                participantSelectionLayout.removeAllViews();
                participantSelectionLayout.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                itemView.setOnTouchListener(null);
            }

            return itemView;
        }
    }

    private final class PhoneContactsAdapter extends ArrayAdapter<PhoneContactsListItem> {
        private static final int VIEW_TYPE_SECTION = 0;
        private static final int VIEW_TYPE_CONTACT = 1;
        @Nullable
        private Runnable onFavoritesChanged;
        @Nullable
        private OnPhoneContactClickListener onContactClicked;

        PhoneContactsAdapter(ArrayList<PhoneContactsListItem> contacts) {
            super(NewReceiptActivity.this, R.layout.item_phone_contact, contacts);
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

        @Nullable
        private PhoneContact getContact(int position) {
            PhoneContactsListItem item = getItem(position);
            return item == null ? null : item.contact;
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
        badgeButton.setText(getParticipantInitials(contact.name));
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

    private static final class ReceiptSummaryTransfer {
        @NonNull
        private final Participant fromParticipant;
        @NonNull
        private final Participant toParticipant;
        @NonNull
        private final String fromParticipantName;
        @NonNull
        private final String toParticipantName;
        @NonNull
        private final BigDecimal amount;

        private ReceiptSummaryTransfer(
                @NonNull Participant fromParticipant,
                @NonNull Participant toParticipant,
                @NonNull String fromParticipantName,
                @NonNull String toParticipantName,
                @NonNull BigDecimal amount
        ) {
            this.fromParticipant = fromParticipant;
            this.toParticipant = toParticipant;
            this.fromParticipantName = fromParticipantName;
            this.toParticipantName = toParticipantName;
            this.amount = amount;
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

    private static final class ReceiptSummaryBalance {
        @NonNull
        private final Participant participant;
        @NonNull
        private BigDecimal amount;

        private ReceiptSummaryBalance(
                @NonNull Participant participant,
                @NonNull BigDecimal amount
        ) {
            this.participant = participant;
            this.amount = amount;
        }
    }

    private interface OnPhoneContactClickListener {
        void onPhoneContactClicked(@NonNull PhoneContact contact);
    }

    private static final class Participant {
        private final String name;
        private String phoneNumber;
        private final String key;
        private final String initials;
        private final int color;

        private Participant(String name, String phoneNumber, String key, String initials, int color) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.key = key;
            this.initials = initials;
            this.color = color;
        }
    }

    private interface ReceiptItemPayerSelectionListener {
        void onPayerSelected(@Nullable String participantKey);
    }
}
