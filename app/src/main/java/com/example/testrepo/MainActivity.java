package com.example.testrepo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
    private static final String EXTRA_SHOW_WELCOME_SETUP = "main.show_welcome_setup";
    private static final String EXTRA_LOGIN_ACCESS_TOKEN = "main.login_access_token";
    private static final String STATE_HAS_HANDLED_WELCOME_SETUP = "main.has_handled_welcome_setup";
    @NonNull
    private String appliedThemeConfigurationKey = "";
    private boolean shouldShowWelcomeSetupAfterLogin;
    private boolean hasHandledWelcomeSetupPrompt;
    @NonNull
    private String loginAccessToken = "";
    private boolean pendingWelcomePhoneLookupAfterPermission;
    private boolean welcomeSetupPhoneLookupInProgress;
    @NonNull
    private String welcomeSetupPhoneNumber = "";
    @Nullable
    private AlertDialog welcomeSetupDialog;
    @Nullable
    private MaterialButton welcomeSetupGetPhoneNumberButton;
    @Nullable
    private MaterialButton welcomeSetupGetStartedButton;
    @Nullable
    private TextInputEditText welcomeSetupUsernameInputView;
    @Nullable
    private TextView welcomeSetupManagePermissionsView;
    private final ActivityResultLauncher<String> requestContactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!pendingWelcomePhoneLookupAfterPermission) {
                    return;
                }

                pendingWelcomePhoneLookupAfterPermission = false;
                if (!isGranted || welcomeSetupGetPhoneNumberButton == null) {
                    refreshWelcomeSetupDialogState();
                    Toast.makeText(
                            this,
                            R.string.welcome_setup_phone_permission_required,
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                refreshWelcomeSetupDialogState();
                loadWelcomeSetupPhoneNumber(welcomeSetupGetPhoneNumberButton);
            });

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return createIntent(context, false, null);
    }

    @NonNull
    public static Intent createIntent(
            @NonNull Context context,
            boolean showWelcomeSetup,
            @Nullable String loginAccessToken
    ) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_SHOW_WELCOME_SETUP, showWelcomeSetup);
        intent.putExtra(EXTRA_LOGIN_ACCESS_TOKEN, loginAccessToken == null ? "" : loginAccessToken);
        return intent;
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
        setContentView(R.layout.activity_main);
        shouldShowWelcomeSetupAfterLogin =
                getIntent().getBooleanExtra(EXTRA_SHOW_WELCOME_SETUP, false);
        loginAccessToken = getIntent().getStringExtra(EXTRA_LOGIN_ACCESS_TOKEN);
        if (loginAccessToken == null) {
            loginAccessToken = "";
        }
        hasHandledWelcomeSetupPrompt = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_HAS_HANDLED_WELCOME_SETUP, false);

        View settingsMenuButton = findViewById(R.id.button_main_actions);
        findViewById(R.id.button_new_receipt).setOnClickListener(
                view -> startActivity(new Intent(this, NewReceiptActivity.class))
        );
        findViewById(R.id.button_archive).setOnClickListener(
                view -> startActivity(new Intent(this, ArchiveActivity.class))
        );
        findViewById(R.id.button_history).setOnClickListener(
                view -> startActivity(new Intent(this, HistoryActivity.class))
        );
        settingsMenuButton.setOnClickListener(
                view -> SettingsMenuHelper.showSettingsMenu(this, view)
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        maybeShowWelcomeSetupDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AuthGateHelper.redirectToLoginIfNeeded(this)) {
            return;
        }
        recreateIfThemeConfigurationChanged();
        refreshWelcomeSetupDialogState();
    }

    private void recreateIfThemeConfigurationChanged() {
        String currentThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        if (currentThemeConfigurationKey.equals(appliedThemeConfigurationKey)) {
            return;
        }

        recreate();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HAS_HANDLED_WELCOME_SETUP, hasHandledWelcomeSetupPrompt);
    }

    @Override
    protected void onDestroy() {
        if (welcomeSetupDialog != null) {
            welcomeSetupDialog.dismiss();
            welcomeSetupDialog = null;
        }
        welcomeSetupGetPhoneNumberButton = null;
        welcomeSetupGetStartedButton = null;
        welcomeSetupUsernameInputView = null;
        welcomeSetupManagePermissionsView = null;
        welcomeSetupPhoneLookupInProgress = false;
        super.onDestroy();
    }

    private void maybeShowWelcomeSetupDialog() {
        if (!shouldShowWelcomeSetupAfterLogin
                || hasHandledWelcomeSetupPrompt
                || !isWelcomeSetupRequired()) {
            return;
        }

        hasHandledWelcomeSetupPrompt = true;
        findViewById(android.R.id.content).post(this::showWelcomeSetupDialog);
    }

    private boolean isWelcomeSetupRequired() {
        return !AppSettings.isValidUsernameNickname(AppSettings.getUsernameNickname(this))
                || !AppSettings.isValidPhoneNumber(AppSettings.getLoginPhoneNumber(this));
    }

    private void showWelcomeSetupDialog() {
        if (isFinishing() || isDestroyed() || welcomeSetupDialog != null) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_welcome_setup_account, null);
        TextInputLayout usernameInputLayout =
                dialogView.findViewById(R.id.input_layout_welcome_setup_username);
        TextInputEditText usernameInputView =
                dialogView.findViewById(R.id.input_welcome_setup_username);
        MaterialButton getPhoneNumberButton =
                dialogView.findViewById(R.id.button_welcome_setup_get_phone_number);
        MaterialButton getStartedButton =
                dialogView.findViewById(R.id.button_welcome_setup_get_started);
        TextView managePermissionsView =
                dialogView.findViewById(R.id.text_welcome_setup_manage_permissions);
        welcomeSetupPhoneNumber = AppSettings.isValidPhoneNumber(AppSettings.getLoginPhoneNumber(this))
                ? AppSettings.getLoginPhoneNumber(this)
                : "";
        welcomeSetupGetPhoneNumberButton = getPhoneNumberButton;
        welcomeSetupGetStartedButton = getStartedButton;
        welcomeSetupUsernameInputView = usernameInputView;
        welcomeSetupManagePermissionsView = managePermissionsView;
        welcomeSetupPhoneLookupInProgress = false;

        usernameInputView.setText(AppSettings.getUsernameNickname(this));
        if (usernameInputView.getText() != null) {
            usernameInputView.setSelection(usernameInputView.getText().length());
        }
        usernameInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                usernameInputLayout.setError(null);
                refreshWelcomeSetupDialogState();
            }
        });
        getPhoneNumberButton.setOnClickListener(view -> handleGetPhoneNumberClicked(getPhoneNumberButton));
        managePermissionsView.setOnClickListener(view -> openAppPermissions());
        refreshWelcomeSetupDialogState();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        getStartedButton.setOnClickListener(view -> {
            String username = getEditableText(usernameInputView);
            if (!AppSettings.isValidUsernameNickname(username)) {
                usernameInputLayout.setError(getString(R.string.welcome_setup_username_required));
                return;
            }

            getStartedButton.setEnabled(false);
            SupabaseAuthService.updateProfile(
                    this,
                    loginAccessToken,
                    username,
                    welcomeSetupPhoneNumber,
                    new SupabaseAuthService.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            AppSettings.setUsernameNickname(MainActivity.this, username);
                            if (AppSettings.isValidPhoneNumber(welcomeSetupPhoneNumber)) {
                                AppSettings.setLoginPhoneNumber(
                                        MainActivity.this,
                                        welcomeSetupPhoneNumber
                                );
                            }
                            if (welcomeSetupDialog != null) {
                                welcomeSetupDialog.dismiss();
                                welcomeSetupDialog = null;
                            }
                            maybeShowStartupPermissionPrompt(true);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            refreshWelcomeSetupDialogState();
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        dialog.setOnDismissListener(dialogInterface -> {
            welcomeSetupDialog = null;
            welcomeSetupGetPhoneNumberButton = null;
            welcomeSetupGetStartedButton = null;
            welcomeSetupUsernameInputView = null;
            welcomeSetupManagePermissionsView = null;
            pendingWelcomePhoneLookupAfterPermission = false;
            welcomeSetupPhoneLookupInProgress = false;
        });
        dialog.show();
        welcomeSetupDialog = dialog;
    }

    @NonNull
    private String getEditableText(@NonNull TextInputEditText inputView) {
        Editable editable = inputView.getText();
        return editable == null ? "" : editable.toString();
    }

    private void handleGetPhoneNumberClicked(@NonNull MaterialButton getPhoneNumberButton) {
        if (!hasContactsPermission()) {
            pendingWelcomePhoneLookupAfterPermission = true;
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            return;
        }

        loadWelcomeSetupPhoneNumber(getPhoneNumberButton);
    }

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void loadWelcomeSetupPhoneNumber(@NonNull MaterialButton getPhoneNumberButton) {
        welcomeSetupPhoneLookupInProgress = true;
        refreshWelcomeSetupDialogState();
        new Thread(() -> {
            String phoneNumber = loadPhoneNumberFromOwnerProfile();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                welcomeSetupPhoneLookupInProgress = false;
                if (!AppSettings.isValidPhoneNumber(phoneNumber)) {
                    refreshWelcomeSetupDialogState();
                    Toast.makeText(
                            this,
                            R.string.welcome_setup_phone_number_unavailable,
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                welcomeSetupPhoneNumber = phoneNumber;
                refreshWelcomeSetupDialogState();
            });
        }).start();
    }

    @NonNull
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

    private void updateWelcomeSetupPhoneButtonText(
            @NonNull MaterialButton getPhoneNumberButton,
            @Nullable String phoneNumber
    ) {
        if (AppSettings.isValidPhoneNumber(phoneNumber)) {
            getPhoneNumberButton.setText(normalizeWhitespace(phoneNumber));
        } else {
            getPhoneNumberButton.setText(R.string.get_phone_number);
        }
    }

    private void refreshWelcomeSetupDialogState() {
        if (welcomeSetupGetPhoneNumberButton == null
                || welcomeSetupGetStartedButton == null
                || welcomeSetupUsernameInputView == null
                || welcomeSetupManagePermissionsView == null) {
            return;
        }

        boolean hasValidPhoneNumber = AppSettings.isValidPhoneNumber(welcomeSetupPhoneNumber);
        boolean hasValidUsername = AppSettings.isValidUsernameNickname(
                getEditableText(welcomeSetupUsernameInputView)
        );
        updateWelcomeSetupPhoneButtonText(welcomeSetupGetPhoneNumberButton, welcomeSetupPhoneNumber);
        welcomeSetupGetPhoneNumberButton.setEnabled(
                !hasValidPhoneNumber && !welcomeSetupPhoneLookupInProgress
        );
        welcomeSetupManagePermissionsView.setVisibility(
                hasContactsPermission() ? View.GONE : View.VISIBLE
        );
        welcomeSetupGetStartedButton.setEnabled(
                hasValidUsername && hasValidPhoneNumber && !welcomeSetupPhoneLookupInProgress
        );
    }

    @NonNull
    private String normalizeWhitespace(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void maybeShowStartupPermissionPrompt(boolean requiredUsernameFlow) {
        if (!requiredUsernameFlow || AppSettings.hasStartupPermissionPromptBeenShown(this)) {
            return;
        }

        PermissionOnboardingDialogFragment.show(getSupportFragmentManager());
    }

    private void openAppPermissions() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }
}
