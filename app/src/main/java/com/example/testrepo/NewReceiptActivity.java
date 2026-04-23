package com.example.testrepo;

import android.Manifest;
import android.content.ClipData;
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
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
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
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
    private static final int MAX_CROP_BITMAP_DIMENSION = 2048;
    private static final int MAX_IMPORTED_PDF_PAGE_DIMENSION = 2800;
    private static final int MAX_PARTICIPANT_BUTTONS_PER_ROW = 5;
    private static final int MAX_ITEM_PARTICIPANT_BUTTONS_PER_ROW = 4;
    private static final int ACTIONS_MODE_HIDDEN = 0;
    private static final int ACTIONS_MODE_SETTINGS_ONLY = 1;
    private static final int ACTIONS_MODE_RECEIPT = 2;
    private static final int RECEIPT_FILTER_DEFAULT = 0;
    private static final int RECEIPT_FILTER_HIGH_TO_LOW = 1;
    private static final int RECEIPT_FILTER_LOW_TO_HIGH = 2;
    private static final int UNCHECKED_PARTICIPANT_COLOR = 0xFF8A8A8A;
    private static final String DEFAULT_PARTICIPANT_NAME = "You";
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String PAYMENT_LINK_BASE_URL = "https://edvinwendt.github.io/TestRepo/";

    private PreviewView previewView;
    private TextView cameraStatusView;
    private MaterialButton captureButton;
    private View cropReceiptLayout;
    private ReceiptCropImageView cropImageView;
    private MaterialButton cropReceiptButton;
    private View receiptResultsLayout;
    private LinearLayout participantButtonsLayout;
    private TextView screenTitleView;
    private View receiptActionsButton;
    private ListView receiptItemsList;
    private TextView receiptTotalValueView;
    private MaterialButton nextButton;
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
    @NonNull
    private String currentReceiptName = "";
    @NonNull
    private String pendingSendRequestsMessage = "";
    @NonNull
    private String crownedParticipantKey = DEFAULT_PARTICIPANT_KEY;
    @Nullable
    private Intent lastSharedReceiptIntent;
    @Nullable
    private File lastRefreshableReceiptImageFile;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChangeListener =
            (sharedPreferences, key) -> {
                if (AppSettings.isSplitItemsPreferenceKey(key) && !trackedReceiptItems.isEmpty()) {
                    reapplyTrackedReceiptItems();
                }
            };

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    showPermissionRequired();
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
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
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
        participantButtonsLayout = findViewById(R.id.layout_participant_buttons);
        screenTitleView = findViewById(R.id.text_new_receipt_screen_title);
        receiptItemsList = findViewById(R.id.list_receipt_items);
        receiptTotalValueView = findViewById(R.id.text_receipt_total_value);
        nextButton = findViewById(R.id.button_next);
        View addReceiptItemAction = findViewById(R.id.action_add_receipt_item);
        TextView addReceiptItemText = findViewById(R.id.text_add_receipt_item);
        View receiptFiltersAction = findViewById(R.id.action_receipt_filters);
        TextView receiptFiltersText = findViewById(R.id.text_receipt_filters);
        View backButton = findViewById(R.id.button_back);
        receiptActionsButton = findViewById(R.id.button_receipt_actions);
        captureButton = findViewById(R.id.button_take_picture);
        currentReceiptName = getString(R.string.new_receipt_screen_title);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        receiptItemsAdapter = new ReceiptItemsAdapter();
        receiptItemsList.setAdapter(receiptItemsAdapter);
        receiptItemsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < receiptItems.size()) {
                showEditReceiptItemDialog(receiptItems.get(position));
            }
        });
        ensureDefaultParticipant();
        applyPreAddedParticipants();
        refreshParticipantButtons();
        setParticipantControlsVisible(false);
        updateReceiptTotal();
        updateNextButtonState();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        refreshDefaultParticipantPhoneNumber();

        addReceiptItemText.setPaintFlags(
                addReceiptItemText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
        );
        receiptFiltersText.setPaintFlags(
                receiptFiltersText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
        );
        backButton.setOnClickListener(view -> showAbandonReceiptDialog());
        addReceiptItemAction.setOnClickListener(view -> showAddReceiptItemDialog());
        receiptFiltersAction.setOnClickListener(this::showReceiptFiltersMenu);
        receiptActionsButton.setOnClickListener(this::showActiveActionsMenu);
        nextButton.setOnClickListener(view -> showReceiptSummaryDialog());
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
                showAbandonReceiptDialog();
            }
        });

        if (handleSharedReceiptIntent(getIntent())) {
            return;
        }

        startCaptureFlow();
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
        promptForRequiredUsernameIfNeeded();
        AppSettings.registerChangeListener(this, settingsChangeListener);
    }

    @Override
    protected void onStop() {
        AppSettings.unregisterChangeListener(this, settingsChangeListener);
        super.onStop();
    }

    private void startCaptureFlow() {
        if (hasCameraPermission()) {
            startCamera();
        } else {
            showPermissionRequired();
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.abandon_receipt_title)
                .setMessage(R.string.abandon_receipt_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> returnToMainMenu())
                .setNegativeButton(R.string.no, null)
                .show();
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

    private void setScreenTitle(int titleResId) {
        currentScreenTitleResId = titleResId;
        if (screenTitleView != null) {
            screenTitleView.setText(titleResId);
        }
    }

    private void showActiveActionsMenu(View anchorView) {
        if (actionsMenuMode == ACTIONS_MODE_SETTINGS_ONLY) {
            if (currentScreenTitleResId == R.string.photo_screen_title) {
                showPhotoActionsMenu(anchorView);
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
            if (itemId == R.id.action_refresh_receipt) {
                refreshReceiptFlow();
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
        showReceiptResults(detectedItems);
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
        assignSourceOrderToReceiptItems(detectedItems);
        trackedReceiptItems.clear();
        trackedReceiptItems.addAll(cloneReceiptItems(detectedItems));
        reapplyTrackedReceiptItems();
        showReceiptResultsUi();
    }

    @NonNull
    private ArrayList<ReceiptParser.ReceiptItem> prepareReceiptItemsForDisplay(
            @NonNull ArrayList<ReceiptParser.ReceiptItem> detectedItems
    ) {
        if (AppSettings.isSplitItemsEnabled(this)) {
            return receiptParser.expandDiscreteQuantityItems(detectedItems);
        }

        LinkedHashMap<String, ReceiptParser.ReceiptItem> groupedItems = new LinkedHashMap<>();
        for (ReceiptParser.ReceiptItem item : detectedItems) {
            String itemName = normalizeWhitespace(receiptParser.getCanonicalItemName(item.getName()));
            if (itemName.isEmpty()) {
                itemName = item.getName().trim();
            }

            String groupingKey = itemName.toLowerCase(Locale.US);
            ReceiptParser.ReceiptItem groupedItem = groupedItems.get(groupingKey);
            if (groupedItem == null) {
                ReceiptParser.ReceiptItem newGroupedItem = new ReceiptParser.ReceiptItem(
                        itemName,
                        item.getAmountCents(),
                        item.getSplitQuantity(),
                        item.getPantAmountCents()
                );
                newGroupedItem.setSourceOrder(item.getSourceOrder());
                groupedItems.put(
                        groupingKey,
                        newGroupedItem
                );
                continue;
            }

            groupedItems.put(
                    groupingKey,
                    new ReceiptParser.ReceiptItem(
                            itemName,
                            groupedItem.getAmountCents() + item.getAmountCents(),
                            groupedItem.getSplitQuantity() + item.getSplitQuantity(),
                            groupedItem.getPantAmountCents() + item.getPantAmountCents()
                    )
            );
        }

        ArrayList<ReceiptParser.ReceiptItem> displayItems = new ArrayList<>(groupedItems.size());
        for (ReceiptParser.ReceiptItem groupedItem : groupedItems.values()) {
            ReceiptParser.ReceiptItem displayItem = new ReceiptParser.ReceiptItem(
                    receiptParser.getGroupedDisplayName(groupedItem),
                    groupedItem.getAmountCents(),
                    groupedItem.getSplitQuantity(),
                    groupedItem.getPantAmountCents()
            );
            displayItem.setSourceOrder(groupedItem.getSourceOrder());
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
        LinkedHashMap<String, LinkedHashSet<String>> selectedParticipantsByItemKey =
                new LinkedHashMap<>();

        for (ReceiptParser.ReceiptItem sourceItem : sourceItems) {
            String itemKey = getReceiptItemGroupingKey(sourceItem.getName());
            LinkedHashSet<String> selectedParticipants = selectedParticipantsByItemKey.get(itemKey);
            if (selectedParticipants == null) {
                selectedParticipants = new LinkedHashSet<>();
                selectedParticipantsByItemKey.put(itemKey, selectedParticipants);
            }
            selectedParticipants.addAll(sourceItem.copySelectedParticipantKeys());
        }

        for (ReceiptParser.ReceiptItem targetItem : targetItems) {
            Set<String> selectedParticipants =
                    selectedParticipantsByItemKey.get(getReceiptItemGroupingKey(targetItem.getName()));
            if (selectedParticipants != null) {
                targetItem.selectParticipants(selectedParticipants);
            }
        }
    }

    @NonNull
    private String getReceiptItemGroupingKey(@NonNull String itemName) {
        return normalizeWhitespace(receiptParser.getCanonicalItemName(itemName))
                .toLowerCase(Locale.US);
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
        updateReceiptTotal();
        updateNextButtonState();
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

        if (receiptItems.isEmpty() || participants.size() <= 1) {
            nextButton.setEnabled(false);
            return;
        }

        for (ReceiptParser.ReceiptItem item : receiptItems) {
            if (countSelectedParticipants(item) == 0) {
                nextButton.setEnabled(false);
                return;
            }
        }

        nextButton.setEnabled(true);
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

        nameInputView.setText(item.getName());
        if (nameInputView.getText() != null) {
            nameInputView.setSelection(nameInputView.getText().length());
        }
        priceInputView.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );
        priceInputView.setText(item.getPrice());
        if (priceInputView.getText() != null) {
            priceInputView.setSelection(priceInputView.getText().length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_receipt_item_title)
                .setView(dialogView)
                .setNegativeButton(R.string.remove, null)
                .setPositiveButton(R.string.ok, null)
                .create();
        applyDialogAnimations(dialog);

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                receiptItems.remove(item);
                syncTrackedReceiptItemsToCurrentItems();
                reapplyTrackedReceiptItems();
                dialog.dismiss();
            });

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

                Integer updatedAmountCents = receiptParser.parseEnteredPriceToCents(enteredPrice);
                if (updatedAmountCents == null) {
                    priceInputLayout.setError(getString(R.string.invalid_receipt_price));
                    hasError = true;
                }

                if (hasError || updatedAmountCents == null) {
                    return;
                }

                item.setName(itemName);
                item.setAmountCents(updatedAmountCents);
                syncTrackedReceiptItemsToCurrentItems();
                reapplyTrackedReceiptItems();
                dialog.dismiss();
            });
        });
        dialog.show();
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
        applyDialogAnimations(dialog);

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

                    addReceiptItem(itemName, amountCents);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void addReceiptItem(@NonNull String itemName, int amountCents) {
        ReceiptParser.ReceiptItem item = new ReceiptParser.ReceiptItem(itemName, amountCents);
        item.setSourceOrder(nextReceiptItemSourceOrder++);
        selectAllParticipantsForItem(item);
        receiptItems.add(item);
        syncTrackedReceiptItemsToCurrentItems();
        reapplyTrackedReceiptItems();
        showReceiptResultsUi();
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

        Dialog dialog = new Dialog(this, R.style.TestRepo_FullScreenDialog);
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
        if (visible) {
            refreshParticipantButtons();
        } else {
            updateParticipantButtonsVisibility();
        }
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
        int buttonSize = dpToPx(52);
        int buttonSpacing = dpToPx(6);
        int rowSpacing = dpToPx(8);

        ArrayList<View> participantBadgeButtons = new ArrayList<>();
        for (Participant participant : participants) {
            participantBadgeButtons.add(
                    createParticipantBadgeButton(participant, buttonSize, buttonSpacing)
            );
        }
        participantBadgeButtons.add(createAddParticipantBadgeButton(buttonSize, buttonSpacing));

        LinearLayout currentRow = null;
        for (int index = 0; index < participantBadgeButtons.size(); index++) {
            if (index % MAX_PARTICIPANT_BUTTONS_PER_ROW == 0) {
                currentRow = new LinearLayout(this);
                LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (index > 0) {
                    rowLayoutParams.topMargin = rowSpacing;
                }
                currentRow.setLayoutParams(rowLayoutParams);
                currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                participantButtonsLayout.addView(currentRow);
            }

            if (currentRow != null) {
                currentRow.addView(participantBadgeButtons.get(index));
            }
        }
    }

    @NonNull
    private MaterialButton createParticipantBadgeButton(
            @NonNull Participant participant,
            int buttonSize,
            int buttonSpacing
    ) {
        MaterialButton participantButton = new MaterialButton(this);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(buttonSize, buttonSize);
        layoutParams.setMargins(buttonSpacing, 0, buttonSpacing, 0);
        participantButton.setLayoutParams(layoutParams);
        participantButton.setCheckable(false);
        participantButton.setClickable(true);
        participantButton.setInsetTop(0);
        participantButton.setInsetBottom(0);
        participantButton.setMinWidth(0);
        participantButton.setMinHeight(0);
        participantButton.setMinimumWidth(0);
        participantButton.setMinimumHeight(0);
        participantButton.setPadding(0, 0, 0, 0);
        participantButton.setCornerRadius(buttonSize / 2);
        applyParticipantBadgeTextStyle(participantButton, participant, false);
        if (isCrownedParticipant(participant)) {
            participantButton.setStrokeWidth(dpToPx(2));
            participantButton.setStrokeColor(ColorStateList.valueOf(Color.WHITE));
        } else {
            participantButton.setStrokeWidth(0);
        }
        participantButton.setBackgroundTintList(ColorStateList.valueOf(participant.color));
        participantButton.setTextColor(getParticipantTextColor(participant.color));
        participantButton.setContentDescription(participant.name);
        participantButton.setOnClickListener(view -> showParticipantDetailsDialog(participant));
        return participantButton;
    }

    @NonNull
    private AppCompatImageButton createAddParticipantBadgeButton(int buttonSize, int buttonSpacing) {
        AppCompatImageButton addParticipantButton = new AppCompatImageButton(this);
        LinearLayout.LayoutParams addButtonLayoutParams =
                new LinearLayout.LayoutParams(buttonSize, buttonSize);
        addButtonLayoutParams.setMargins(buttonSpacing, 0, buttonSpacing, 0);
        addParticipantButton.setLayoutParams(addButtonLayoutParams);
        addParticipantButton.setBackgroundColor(Color.TRANSPARENT);
        addParticipantButton.setImageResource(R.drawable.ic_add_participant_badge);
        addParticipantButton.setScaleType(ImageView.ScaleType.CENTER);
        addParticipantButton.setPadding(0, 0, 0, 0);
        addParticipantButton.setContentDescription(getString(R.string.add_participant));
        addParticipantButton.setOnClickListener(view -> openAddParticipantDialog());
        return addParticipantButton;
    }

    private void showParticipantDetailsDialog(Participant participant) {
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
                participant.phoneNumber.isEmpty()
                        ? getString(R.string.participant_phone_unavailable)
                        : participant.phoneNumber
        );
        participantTotalView.setText(buildParticipantTotalDisplayText(participant));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

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
        removeParticipantButton.setEnabled(canRemoveParticipant);
        if (canRemoveParticipant) {
            removeParticipantButton.setOnClickListener(view -> {
                removeParticipant(participant);
                dialog.dismiss();
            });
        }

        toggleParticipantItemsButton.setText(R.string.clear);
        toggleParticipantItemsButton.setOnClickListener(view -> {
            boolean shouldClear = getString(R.string.clear).contentEquals(
                    toggleParticipantItemsButton.getText()
            );
            setParticipantSelectionsForAllItems(participant, !shouldClear);
            participantTotalView.setText(buildParticipantTotalDisplayText(participant));
            toggleParticipantItemsButton.setText(shouldClear ? R.string.fill : R.string.clear);
        });

        dialog.show();
    }

    private void showReceiptSummaryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receipt_summary, null);
        View summaryRootView = dialogView.findViewById(R.id.layout_receipt_summary_root);
        LinearLayout summaryRowsLayout = dialogView.findViewById(R.id.layout_receipt_summary_rows);
        TextInputLayout receiptNameInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_summary_receipt_name);
        TextInputEditText receiptNameInputView =
                dialogView.findViewById(R.id.edit_receipt_summary_receipt_name);
        View closeButton = dialogView.findViewById(R.id.button_close_receipt_summary);
        MaterialButton sendRequestsButton = dialogView.findViewById(R.id.button_send_requests);

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
                sendRequestsButton.setEnabled(
                        isValidReceiptSummaryName(getText(receiptNameInputView))
                );
            }
        });
        sendRequestsButton.setEnabled(isValidReceiptSummaryName(getText(receiptNameInputView)));

        for (int index = 0; index < participants.size(); index++) {
            Participant participant = participants.get(index);
            View rowView = getLayoutInflater().inflate(
                    R.layout.item_receipt_summary_participant,
                    summaryRowsLayout,
                    false
            );
            MaterialButton badgeButton = rowView.findViewById(R.id.button_summary_participant_badge);
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);
            View dividerView = rowView.findViewById(R.id.view_summary_participant_divider);

            configureSummaryParticipantBadgeButton(badgeButton, participant);
            nameView.setText(participant.name);
            amountView.setText(buildParticipantTotalDisplayText(participant));
            dividerView.setVisibility(index == participants.size() - 1 ? View.GONE : View.VISIBLE);
            summaryRowsLayout.addView(rowView);
        }

        Dialog dialog = new Dialog(this, R.style.TestRepo_FullScreenDialog) {
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
        closeButton.setOnClickListener(view -> dialog.dismiss());

        sendRequestsButton.setOnClickListener(view -> {
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
        if (isCrownedParticipant(participant)) {
            badgeButton.setStrokeWidth(dpToPx(2));
            badgeButton.setStrokeColor(ColorStateList.valueOf(Color.WHITE));
        } else {
            badgeButton.setStrokeWidth(0);
        }
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

    private void openSendRequestsFlow(@NonNull String customMessage) {
        pendingSendRequestsMessage = customMessage.trim();
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
        String customMessage = pendingSendRequestsMessage;
        pendingSendRequestsMessage = "";
        SmsManager smsManager = SmsManager.getDefault();
        int sentCount = 0;
        int skippedCount = 0;
        String receiptName = getCurrentReceiptName();
        Participant ownerParticipant = getReceiptOwnerParticipant();
        String ownerPhoneNumber = getParticipantPhoneNumberForMessage(ownerParticipant);
        ArrayList<ReceiptHistoryStore.ParticipantShare> historyParticipants =
                buildHistoryParticipants();
        ArrayList<ReceiptHistoryStore.HistoryItem> historyItems = buildHistoryItems();
        String ownerMessage = buildOwnerPaymentRequestMessage(
                ownerParticipant,
                receiptName
        );

        for (Participant participant : participants) {
            if (isDefaultParticipant(participant)) {
                continue;
            }

            BigDecimal participantTotal = computeParticipantShareTotal(participant);
            String phoneNumber = normalizeWhitespace(participant.phoneNumber);
            boolean isOwner = participant.key.equals(ownerParticipant.key);
            if (!isValidPhoneNumber(phoneNumber)
                    || (!isOwner && participantTotal.compareTo(BigDecimal.ZERO) <= 0)) {
                skippedCount++;
                continue;
            }

            String message = isOwner
                    ? ownerMessage
                    : buildNonOwnerPaymentRequestMessage(
                            participant,
                            participantTotal,
                            receiptName,
                            ownerParticipant,
                            ownerPhoneNumber
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
            Toast.makeText(this, R.string.send_requests_none, Toast.LENGTH_SHORT).show();
            returnToMainMenu();
            return;
        }

        saveReceiptHistoryEntry(historyParticipants, historyItems, customMessage);

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
    private String buildOwnerPaymentRequestMessage(
            @NonNull Participant ownerParticipant,
            @NonNull String receiptName
    ) {
        StringBuilder participantLines = new StringBuilder();
        for (Participant participant : participants) {
            if (participantLines.length() > 0) {
                participantLines.append('\n');
            }
            participantLines.append(
                    getString(
                            R.string.participant_payment_request_owner_line,
                            getParticipantExternalDisplayName(participant),
                            formatCurrency(computeParticipantShareTotal(participant))
                    )
            );
        }

        String message = getString(
                R.string.participant_payment_request_owner_message,
                getParticipantExternalDisplayName(ownerParticipant),
                receiptName,
                participantLines.toString()
        );
        return message;
    }

    @NonNull
    private String buildNonOwnerPaymentRequestMessage(
            @NonNull Participant participant,
            @NonNull BigDecimal participantTotal,
            @NonNull String receiptName,
            @NonNull Participant ownerParticipant,
            @NonNull String ownerPhoneNumber
    ) {
        return getString(
                R.string.participant_payment_request_non_owner_message,
                getParticipantExternalDisplayName(participant),
                receiptName,
                formatCurrency(participantTotal),
                getParticipantExternalDisplayName(ownerParticipant),
                ownerPhoneNumber,
                buildPaymentRequestUrl(ownerPhoneNumber, participantTotal, receiptName)
        );
    }

    @NonNull
    private String buildPaymentRequestUrl(
            @NonNull String phoneNumber,
            @NonNull BigDecimal amount,
            @NonNull String message
    ) {
        return Uri.parse(PAYMENT_LINK_BASE_URL)
                .buildUpon()
                .appendQueryParameter("Phone", normalizePhoneNumber(phoneNumber))
                .appendQueryParameter("Amount", formatUrlAmount(amount))
                .appendQueryParameter("Message", message)
                .build()
                .toString();
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
                    isCrownedParticipant(participant)
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
                    new ArrayList<>(item.copySelectedParticipantKeys())
            ));
        }
        return historyItems;
    }

    private void saveReceiptHistoryEntry(
            @NonNull ArrayList<ReceiptHistoryStore.ParticipantShare> participantShares,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryItem> historyItems,
            @NonNull String customMessage
    ) {
        ReceiptHistoryStore.saveEntry(
                this,
                new ReceiptHistoryStore.HistoryEntry(
                        getCurrentReceiptName(),
                        receiptParser.formatAmount(computeReceiptTotalCents()),
                        getCurrentHistoryDate(),
                        customMessage,
                        participantShares,
                        historyItems
                )
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
            selectionButton.setCornerRadius(dpToPx(10));
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
        selectionButton.setTextColor(buttonColor);
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

    private void onReceiptNotDetected() {
        setScreenTitle(R.string.photo_screen_title);
        cameraStatusView.setVisibility(View.GONE);
        captureButton.setEnabled(true);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
        Toast.makeText(this, R.string.no_receipt_detected, Toast.LENGTH_SHORT).show();
    }

    private void onCroppedReceiptNotDetected() {
        setScreenTitle(R.string.crop_screen_title);
        cameraStatusView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.VISIBLE);
        cropReceiptButton.setEnabled(true);
        setActionsMenuMode(ACTIONS_MODE_SETTINGS_ONLY);
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
    }

    private void setActionsMenuMode(int mode) {
        actionsMenuMode = mode;
        if (receiptActionsButton == null) {
            return;
        }

        receiptActionsButton.setVisibility(mode == ACTIONS_MODE_HIDDEN ? View.INVISIBLE : View.VISIBLE);
        receiptActionsButton.setContentDescription(getString(
                mode == ACTIONS_MODE_RECEIPT ? R.string.receipt_actions : R.string.more_options
        ));
    }

    @Override
    protected void onDestroy() {
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
            TextView itemNameView = itemView.findViewById(R.id.text_receipt_item_name);
            TextView itemPriceView = itemView.findViewById(R.id.text_receipt_item_price);
            LinearLayout participantSelectionLayout =
                    itemView.findViewById(R.id.layout_receipt_item_participants);

            if (item != null) {
                itemNameView.setText(item.getName());
                itemPriceView.setText(item.getDisplayPrice());
                bindParticipantSelectionButtons(participantSelectionLayout, item);
            } else {
                participantSelectionLayout.removeAllViews();
                participantSelectionLayout.setVisibility(View.GONE);
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
}
