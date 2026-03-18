package com.example.testrepo;

import android.Manifest;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewReceiptActivity extends AppCompatActivity {
    private static final int MAX_CROP_BITMAP_DIMENSION = 2048;
    private static final int UNCHECKED_PARTICIPANT_COLOR = 0xFF8A8A8A;
    private static final String DEFAULT_PARTICIPANT_NAME = "You";
    private static final String DEFAULT_PARTICIPANT_KEY = "participant_you";

    private PreviewView previewView;
    private TextView cameraStatusView;
    private MaterialButton captureButton;
    private View cropReceiptLayout;
    private ReceiptCropImageView cropImageView;
    private MaterialButton cropReceiptButton;
    private View receiptResultsLayout;
    private LinearLayout participantButtonsLayout;
    private MaterialButton addParticipantButton;
    private ListView receiptItemsList;
    private TextView receiptTotalValueView;
    private MaterialButton nextButton;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private TextRecognizer textRecognizer;
    private ExecutorService backgroundExecutor;
    private final ReceiptParser receiptParser = new ReceiptParser();
    private final ArrayList<ReceiptParser.ReceiptItem> receiptItems = new ArrayList<>();
    private final ArrayList<Participant> participants = new ArrayList<>();
    private ReceiptItemsAdapter receiptItemsAdapter;
    private boolean participantControlsVisible;
    private boolean showAddParticipantDialogAfterContactsPermission;

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
    private final ActivityResultLauncher<String[]> requestPhoneNumberPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                refreshDefaultParticipantPhoneNumber();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_receipt);

        previewView = findViewById(R.id.view_camera_preview);
        cameraStatusView = findViewById(R.id.text_camera_status);
        cropReceiptLayout = findViewById(R.id.layout_crop_receipt);
        cropImageView = findViewById(R.id.view_receipt_crop);
        cropReceiptButton = findViewById(R.id.button_crop_receipt);
        receiptResultsLayout = findViewById(R.id.layout_receipt_results);
        participantButtonsLayout = findViewById(R.id.layout_participant_buttons);
        receiptItemsList = findViewById(R.id.list_receipt_items);
        receiptTotalValueView = findViewById(R.id.text_receipt_total_value);
        nextButton = findViewById(R.id.button_next);
        MaterialButton backButton = findViewById(R.id.button_back);
        addParticipantButton = findViewById(R.id.button_add_participant);
        captureButton = findViewById(R.id.button_take_picture);

        backgroundExecutor = Executors.newSingleThreadExecutor();
        receiptItemsAdapter = new ReceiptItemsAdapter();
        receiptItemsList.setAdapter(receiptItemsAdapter);
        receiptItemsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < receiptItems.size()) {
                showEditReceiptItemDialog(receiptItems.get(position));
            }
        });
        ensureDefaultParticipant();
        refreshParticipantButtons();
        setParticipantControlsVisible(false);
        updateReceiptTotal();
        updateNextButtonState();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        refreshDefaultParticipantPhoneNumber();
        requestPhoneNumberPermissionsIfNeeded();

        backButton.setOnClickListener(view -> finish());
        addParticipantButton.setOnClickListener(view -> openAddParticipantDialog());
        nextButton.setOnClickListener(view -> showReceiptSummaryDialog());
        captureButton.setOnClickListener(view -> {
            if (hasCameraPermission()) {
                takePicture();
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });
        cropReceiptButton.setOnClickListener(view -> cropAndAnalyzeReceipt());

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

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPhoneNumberPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPhoneNumberPermissionsIfNeeded() {
        if (hasPhoneNumberPermission()) {
            return;
        }

        ArrayList<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS);
        }
        requestPhoneNumberPermissionsLauncher.launch(permissions.toArray(new String[0]));
    }

    private void openAddParticipantDialog() {
        if (hasContactsPermission()) {
            showAddParticipantDialog(true);
        } else {
            showAddParticipantDialogAfterContactsPermission = true;
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void startCamera() {
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
            onReceiptNotDetected();
            return;
        }

        textRecognizer.process(inputImage)
                .addOnSuccessListener(recognizedText -> handleInitialRecognition(imageFile, recognizedText))
                .addOnFailureListener(exception -> onReceiptNotDetected());
    }

    private void handleInitialRecognition(File imageFile, Text recognizedText) {
        ArrayList<String> lines = extractRecognizedLines(recognizedText);
        ArrayList<ReceiptParser.ReceiptItem> detectedItems = receiptParser.extractReceiptItems(lines);

        if (receiptParser.isReceiptDetected(lines, detectedItems)) {
            showCropEditor(imageFile);
        } else {
            onReceiptNotDetected();
        }
    }

    private void showCropEditor(File imageFile) {
        stopCameraPreview();
        previewView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        setParticipantControlsVisible(false);
        showStatusMessage(R.string.preparing_crop, false);

        backgroundExecutor.execute(() -> {
            try {
                Bitmap cropBitmap = loadBitmapForCropping(imageFile);
                runOnUiThread(() -> {
                    cropImageView.setImageBitmap(cropBitmap);
                    cameraStatusView.setVisibility(View.GONE);
                    cropReceiptLayout.setVisibility(View.VISIBLE);
                    cropReceiptButton.setEnabled(true);
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

    private void handleCroppedRecognition(Text recognizedText) {
        ArrayList<String> lines = extractRecognizedLines(recognizedText);
        ArrayList<ReceiptParser.ReceiptItem> detectedItems = receiptParser.extractReceiptItems(lines);

        if (receiptParser.isReceiptDetected(lines, detectedItems) && !detectedItems.isEmpty()) {
            showReceiptResults(detectedItems);
        } else {
            onCroppedReceiptNotDetected();
        }
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
        receiptItems.clear();
        receiptItems.addAll(detectedItems);
        applyDefaultParticipantSelections();
        refreshReceiptItems();

        cameraStatusView.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.VISIBLE);
        setParticipantControlsVisible(true);
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
        receiptTotalValueView.setText(receiptParser.formatAmount(totalCents));
    }

    private void updateNextButtonState() {
        if (nextButton == null) {
            return;
        }

        if (receiptItems.isEmpty()) {
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
        TextInputLayout priceInputLayout =
                dialogView.findViewById(R.id.input_layout_receipt_item_price);
        TextInputEditText priceInputView =
                dialogView.findViewById(R.id.edit_receipt_item_price);

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
                .setTitle(item.getName())
                .setView(dialogView)
                .setNegativeButton(R.string.remove, null)
                .setPositiveButton(R.string.ok, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                receiptItems.remove(item);
                refreshReceiptItems();
                dialog.dismiss();
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String enteredPrice = "";
                if (priceInputView.getText() != null) {
                    enteredPrice = priceInputView.getText().toString().trim();
                }

                Integer updatedAmountCents = receiptParser.parseEnteredPriceToCents(enteredPrice);
                if (updatedAmountCents == null) {
                    priceInputLayout.setError(getString(R.string.invalid_receipt_price));
                    return;
                }

                priceInputLayout.setError(null);
                item.setAmountCents(updatedAmountCents);
                refreshReceiptItems();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void showAddParticipantDialog(boolean contactsPermissionGranted) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_participant, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.layout_participant_name);
        TextInputLayout phoneLayout = dialogView.findViewById(R.id.layout_participant_phone);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_participant_name);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.input_participant_phone);
        ListView phoneContactsList = dialogView.findViewById(R.id.list_phone_contacts);
        TextView emptyContactsView = dialogView.findViewById(R.id.text_phone_contacts_empty);
        MaterialButton addParticipantButton =
                dialogView.findViewById(R.id.button_add_participant_confirm);

        ArrayList<PhoneContact> phoneContacts = new ArrayList<>();
        PhoneContactsAdapter phoneContactsAdapter = new PhoneContactsAdapter(phoneContacts);
        phoneContactsList.setAdapter(phoneContactsAdapter);
        phoneContactsList.setEmptyView(emptyContactsView);
        addParticipantButton.setEnabled(false);
        phoneContactsList.setOnItemClickListener((parent, view, position, id) -> {
            PhoneContact selectedContact = phoneContactsAdapter.getItem(position);
            if (selectedContact == null) {
                return;
            }

            nameInput.setText(selectedContact.name);
            phoneInput.setText(selectedContact.phoneNumber);
            nameLayout.setError(null);
            phoneLayout.setError(null);
        });

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
            }
        };
        nameInput.addTextChangedListener(validationWatcher);
        phoneInput.addTextChangedListener(validationWatcher);
        updateAddParticipantButtonState(
                nameLayout,
                phoneLayout,
                nameInput,
                phoneInput,
                addParticipantButton
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_new_participant_title)
                .setView(dialogView)
                .create();

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

        if (!contactsPermissionGranted) {
            emptyContactsView.setText(R.string.phone_contacts_permission_required);
            return;
        }

        emptyContactsView.setText(R.string.loading_phone_contacts);
        backgroundExecutor.execute(() -> {
            ArrayList<PhoneContact> availableContacts = loadPhoneContactsFromDevice();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                    return;
                }

                phoneContacts.clear();
                phoneContacts.addAll(availableContacts);
                phoneContactsAdapter.notifyDataSetChanged();
                if (availableContacts.isEmpty()) {
                    emptyContactsView.setText(R.string.no_phone_contacts);
                } else {
                    emptyContactsView.setText("");
                }
            });
        });
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
    }

    private void refreshDefaultParticipantPhoneNumber() {
        if (backgroundExecutor == null) {
            return;
        }

        backgroundExecutor.execute(() -> {
            String phoneNumber = loadDevicePhoneNumber();
            runOnUiThread(() -> updateDefaultParticipantPhoneNumber(phoneNumber));
        });
    }

    private String loadDevicePhoneNumber() {
        String phoneNumber = loadPhoneNumberFromTelephony();
        if (!phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        return loadPhoneNumberFromOwnerProfile();
    }

    private String loadPhoneNumberFromTelephony() {
        if (!hasPhoneNumberPermission()) {
            return "";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SubscriptionManager subscriptionManager = getSystemService(SubscriptionManager.class);
            if (subscriptionManager != null) {
                try {
                    java.util.List<SubscriptionInfo> subscriptions =
                            subscriptionManager.getActiveSubscriptionInfoList();
                    if (subscriptions != null) {
                        for (SubscriptionInfo subscription : subscriptions) {
                            String phoneNumber = normalizeWhitespace(
                                    subscriptionManager.getPhoneNumber(
                                            subscription.getSubscriptionId()
                                    )
                            );
                            if (!phoneNumber.isEmpty()) {
                                return phoneNumber;
                            }
                        }
                    }
                } catch (SecurityException ignored) {
                    // Fall back to TelephonyManager below.
                }
            }
        }

        TelephonyManager telephonyManager = getSystemService(TelephonyManager.class);
        if (telephonyManager == null) {
            return "";
        }

        try {
            return normalizeWhitespace(telephonyManager.getLine1Number());
        } catch (SecurityException ignored) {
            return "";
        }
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
            for (Participant participant : participants) {
                item.selectParticipant(participant.key);
            }
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
        if (addParticipantButton != null) {
            addParticipantButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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

        for (Participant participant : participants) {
            MaterialButton participantButton = new MaterialButton(this);
            LinearLayout.LayoutParams layoutParams =
                    new LinearLayout.LayoutParams(buttonSize, buttonSize);
            layoutParams.setMargins(buttonSpacing, 0, buttonSpacing, 0);
            participantButton.setLayoutParams(layoutParams);
            participantButton.setText(getParticipantBadgeLabel(participant));
            participantButton.setAllCaps(false);
            participantButton.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    getParticipantBadgeTextSizeSp(participant, false)
            );
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
            participantButton.setStrokeWidth(0);
            participantButton.setBackgroundTintList(ColorStateList.valueOf(participant.color));
            participantButton.setTextColor(getParticipantTextColor(participant.color));
            participantButton.setContentDescription(participant.name);
            participantButton.setOnClickListener(view -> showParticipantDetailsDialog(participant));
            participantButtonsLayout.addView(participantButton);
        }
    }

    private void showParticipantDetailsDialog(Participant participant) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_participant_details, null);
        TextView participantNameView = dialogView.findViewById(R.id.text_participant_detail_name);
        TextView participantPhoneView = dialogView.findViewById(R.id.text_participant_detail_phone);
        TextView participantTotalView = dialogView.findViewById(R.id.text_participant_detail_total);
        MaterialButton removeParticipantButton =
                dialogView.findViewById(R.id.button_remove_participant);

        participantNameView.setText(participant.name);
        participantPhoneView.setText(
                participant.phoneNumber.isEmpty()
                        ? getString(R.string.participant_phone_unavailable)
                        : participant.phoneNumber
        );
        participantTotalView.setText(formatCurrency(computeParticipantShareTotal(participant)));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        boolean canRemoveParticipant = !isDefaultParticipant(participant);
        removeParticipantButton.setEnabled(canRemoveParticipant);
        if (canRemoveParticipant) {
            removeParticipantButton.setOnClickListener(view -> {
                removeParticipant(participant);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void showReceiptSummaryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receipt_summary, null);
        LinearLayout summaryRowsLayout = dialogView.findViewById(R.id.layout_receipt_summary_rows);
        MaterialButton sendRequestsButton = dialogView.findViewById(R.id.button_send_requests);

        for (Participant participant : participants) {
            View rowView = getLayoutInflater().inflate(
                    R.layout.item_receipt_summary_participant,
                    summaryRowsLayout,
                    false
            );
            TextView nameView = rowView.findViewById(R.id.text_summary_participant_name);
            TextView amountView = rowView.findViewById(R.id.text_summary_participant_amount);

            nameView.setText(participant.name);
            amountView.setText(formatCurrency(computeParticipantShareTotal(participant)));
            summaryRowsLayout.addView(rowView);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.receipt_summary_title)
                .setView(dialogView)
                .create();

        sendRequestsButton.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
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

    private void removeParticipant(Participant participant) {
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
        int checkboxSize = dpToPx(36);
        int checkboxSpacing = dpToPx(4);

        for (Participant participant : participants) {
            MaterialButton selectionButton = new MaterialButton(this);
            LinearLayout.LayoutParams layoutParams =
                    new LinearLayout.LayoutParams(checkboxSize, checkboxSize);
            layoutParams.setMargins(checkboxSpacing, 0, 0, 0);
            selectionButton.setLayoutParams(layoutParams);
            selectionButton.setText(getParticipantBadgeLabel(participant));
            selectionButton.setAllCaps(false);
            selectionButton.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    getParticipantBadgeTextSizeSp(participant, true)
            );
            selectionButton.setInsetTop(0);
            selectionButton.setInsetBottom(0);
            selectionButton.setMinWidth(0);
            selectionButton.setMinHeight(0);
            selectionButton.setMinimumWidth(0);
            selectionButton.setMinimumHeight(0);
            selectionButton.setPadding(0, 0, 0, 0);
            selectionButton.setCornerRadius(dpToPx(10));
            selectionButton.setStrokeWidth(dpToPx(2));
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
            participantSelectionLayout.addView(selectionButton);
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

    private String getParticipantBadgeLabel(Participant participant) {
        if (isDefaultParticipant(participant)) {
            return DEFAULT_PARTICIPANT_NAME;
        }
        return participant.initials;
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
        cameraStatusView.setVisibility(View.GONE);
        captureButton.setEnabled(true);
        Toast.makeText(this, R.string.no_receipt_detected, Toast.LENGTH_SHORT).show();
    }

    private void onCroppedReceiptNotDetected() {
        cameraStatusView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.VISIBLE);
        cropReceiptButton.setEnabled(true);
        Toast.makeText(this, R.string.no_receipt_detected, Toast.LENGTH_SHORT).show();
    }

    private void stopCameraPreview() {
        imageCapture = null;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private Bitmap loadBitmapForCropping(File imageFile) throws IOException {
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

        return rotateBitmapIfNeeded(bitmap, imageFile);
    }

    private Bitmap rotateBitmapIfNeeded(Bitmap bitmap, File imageFile) throws IOException {
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
        if (disableCaptureButton) {
            captureButton.setEnabled(false);
        }
    }

    private void showPermissionRequired() {
        previewView.setVisibility(View.VISIBLE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        cameraStatusView.setText(R.string.camera_permission_required);
        cameraStatusView.setVisibility(View.VISIBLE);
        captureButton.setEnabled(true);
        setParticipantControlsVisible(false);
    }

    private void showCameraUnavailable() {
        previewView.setVisibility(View.VISIBLE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        setParticipantControlsVisible(false);
        showCameraStatus(R.string.camera_unavailable);
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
                itemPriceView.setText(item.getPrice());
                bindParticipantSelectionButtons(participantSelectionLayout, item);
            } else {
                participantSelectionLayout.removeAllViews();
                participantSelectionLayout.setVisibility(View.GONE);
            }

            return itemView;
        }
    }

    private final class PhoneContactsAdapter extends ArrayAdapter<PhoneContact> {
        PhoneContactsAdapter(ArrayList<PhoneContact> contacts) {
            super(NewReceiptActivity.this, R.layout.item_phone_contact, contacts);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = getLayoutInflater().inflate(R.layout.item_phone_contact, parent, false);
            }

            PhoneContact contact = getItem(position);
            TextView nameView = itemView.findViewById(R.id.text_phone_contact_name);
            TextView phoneView = itemView.findViewById(R.id.text_phone_contact_number);

            if (contact != null) {
                nameView.setText(contact.name);
                phoneView.setText(contact.phoneNumber);
            }

            return itemView;
        }
    }

    private static final class PhoneContact {
        private final String name;
        private final String phoneNumber;

        private PhoneContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
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
