package com.example.testrepo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewReceiptActivity extends AppCompatActivity {
    private static final int MAX_CROP_BITMAP_DIMENSION = 2048;

    private PreviewView previewView;
    private TextView cameraStatusView;
    private MaterialButton captureButton;
    private View cropReceiptLayout;
    private ReceiptCropImageView cropImageView;
    private MaterialButton cropReceiptButton;
    private View receiptResultsLayout;
    private ListView receiptItemsList;
    private TextView receiptTotalValueView;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private TextRecognizer textRecognizer;
    private ExecutorService backgroundExecutor;
    private final ReceiptParser receiptParser = new ReceiptParser();
    private final ArrayList<ReceiptParser.ReceiptItem> receiptItems = new ArrayList<>();
    private ReceiptItemsAdapter receiptItemsAdapter;

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    showPermissionRequired();
                }
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
        receiptItemsList = findViewById(R.id.list_receipt_items);
        receiptTotalValueView = findViewById(R.id.text_receipt_total_value);
        MaterialButton backButton = findViewById(R.id.button_back);
        captureButton = findViewById(R.id.button_take_picture);

        backgroundExecutor = Executors.newSingleThreadExecutor();
        receiptItemsAdapter = new ReceiptItemsAdapter();
        receiptItemsList.setAdapter(receiptItemsAdapter);
        receiptItemsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < receiptItems.size()) {
                showEditReceiptItemDialog(receiptItems.get(position));
            }
        });
        updateReceiptTotal();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        backButton.setOnClickListener(view -> finish());
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
        refreshReceiptItems();

        cameraStatusView.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        cropReceiptLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.VISIBLE);
    }

    private void refreshReceiptItems() {
        receiptItemsAdapter.notifyDataSetChanged();
        updateReceiptTotal();
    }

    private void updateReceiptTotal() {
        int totalCents = 0;
        for (ReceiptParser.ReceiptItem item : receiptItems) {
            totalCents += item.getAmountCents();
        }
        receiptTotalValueView.setText(receiptParser.formatAmount(totalCents));
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
        return value.trim().replaceAll("\\s+", " ");
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
    }

    private void showCameraUnavailable() {
        previewView.setVisibility(View.VISIBLE);
        cropReceiptLayout.setVisibility(View.GONE);
        receiptResultsLayout.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
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

            if (item != null) {
                itemNameView.setText(item.getName());
                itemPriceView.setText(item.getPrice());
            }

            return itemView;
        }
    }
}
