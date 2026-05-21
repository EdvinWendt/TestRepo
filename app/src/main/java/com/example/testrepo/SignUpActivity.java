package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignUpActivity extends AppCompatActivity {
    @NonNull
    private String appliedThemeConfigurationKey = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        appliedThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);

        if (AppSettings.isSignedIn(this)) {
            openMainView();
            return;
        }

        setContentView(R.layout.activity_sign_up);

        AppCompatImageButton backButton = findViewById(R.id.button_sign_up_back);
        TextInputLayout emailInputLayout = findViewById(R.id.input_layout_sign_up_email);
        TextInputLayout passwordInputLayout = findViewById(R.id.input_layout_sign_up_password);
        TextInputLayout confirmPasswordInputLayout =
                findViewById(R.id.input_layout_sign_up_confirm_password);
        TextInputEditText emailInputView = findViewById(R.id.edit_sign_up_email);
        TextInputEditText passwordInputView = findViewById(R.id.edit_sign_up_password);
        TextInputEditText confirmPasswordInputView =
                findViewById(R.id.edit_sign_up_confirm_password);
        MaterialButton signUpButton = findViewById(R.id.button_sign_up);

        backButton.setOnClickListener(view -> finish());
        signUpButton.setOnClickListener(view -> attemptCreateAccount(
                emailInputLayout,
                passwordInputLayout,
                confirmPasswordInputLayout,
                emailInputView,
                passwordInputView,
                confirmPasswordInputView,
                signUpButton
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        recreateIfThemeConfigurationChanged();
    }

    private void recreateIfThemeConfigurationChanged() {
        String currentThemeConfigurationKey = AppSettings.getThemeConfigurationKey(this);
        if (currentThemeConfigurationKey.equals(appliedThemeConfigurationKey)) {
            return;
        }

        recreate();
    }

    private void attemptCreateAccount(
            @NonNull TextInputLayout emailInputLayout,
            @NonNull TextInputLayout passwordInputLayout,
            @NonNull TextInputLayout confirmPasswordInputLayout,
            @NonNull TextInputEditText emailInputView,
            @NonNull TextInputEditText passwordInputView,
            @NonNull TextInputEditText confirmPasswordInputView,
            @NonNull MaterialButton signUpButton
    ) {
        String email = getText(emailInputView);
        String password = getText(passwordInputView);
        String confirmPassword = getText(confirmPasswordInputView);

        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);

        boolean hasError = false;
        if (email.isEmpty()) {
            emailInputLayout.setError(getString(R.string.login_email_required));
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError(getString(R.string.login_email_invalid));
            hasError = true;
        }

        if (password.isEmpty()) {
            passwordInputLayout.setError(getString(R.string.login_password_required));
            hasError = true;
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.setError(getString(R.string.sign_up_confirm_password_required));
            hasError = true;
        } else if (!confirmPassword.equals(password)) {
            confirmPasswordInputLayout.setError(getString(R.string.sign_up_password_mismatch));
            hasError = true;
        }

        if (hasError) {
            return;
        }

        signUpButton.setEnabled(false);
        signUpButton.setText(R.string.sign_up_creating_account);

        SupabaseAuthService.signUp(this, email, password, new SupabaseAuthService.Callback() {
            @Override
            public void onSuccess(@NonNull SupabaseAuthService.AuthResponse authResponse) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                signUpButton.setEnabled(true);
                signUpButton.setText(R.string.login_sign_up);
                if (authResponse.signedIn) {
                    AppSettings.clearUsernameNickname(SignUpActivity.this);
                    AppSettings.setLoginAccessToken(SignUpActivity.this, authResponse.accessToken);
                    AppSettings.setLoginRefreshToken(
                            SignUpActivity.this,
                            authResponse.refreshToken
                    );
                    AppSettings.setLoginEmail(SignUpActivity.this, email);
                    AppSettings.setLoginCompleted(SignUpActivity.this, true);
                    openMainView();
                    return;
                }

                AppSettings.clearUsernameNickname(SignUpActivity.this);
                AppSettings.clearLoginAccessToken(SignUpActivity.this);
                AppSettings.clearLoginRefreshToken(SignUpActivity.this);
                startActivity(ConfirmSignUpActivity.createIntent(SignUpActivity.this, email));
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                signUpButton.setEnabled(true);
                signUpButton.setText(R.string.login_sign_up);
                String lowerCaseMessage = message.toLowerCase();
                if (lowerCaseMessage.contains("already registered")
                        || lowerCaseMessage.contains("already been registered")
                        || lowerCaseMessage.contains("user already exists")) {
                    emailInputLayout.setError(message);
                    return;
                }

                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openMainView() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @NonNull
    private String getText(@NonNull TextInputEditText inputView) {
        return inputView.getText() == null
                ? ""
                : inputView.getText().toString().trim();
    }
}
