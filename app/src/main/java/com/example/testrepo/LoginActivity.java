package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {
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

        setContentView(R.layout.activity_login);

        TextInputLayout emailInputLayout = findViewById(R.id.input_layout_login_email);
        TextInputLayout passwordInputLayout = findViewById(R.id.input_layout_login_password);
        TextInputEditText emailInputView = findViewById(R.id.edit_login_email);
        TextInputEditText passwordInputView = findViewById(R.id.edit_login_password);
        MaterialButton signInButton = findViewById(R.id.button_sign_in);
        TextView signUpView = findViewById(R.id.text_sign_up);

        signInButton.setOnClickListener(view -> attemptEnterApp(
                emailInputLayout,
                passwordInputLayout,
                emailInputView,
                passwordInputView,
                signInButton
        ));
        signUpView.setOnClickListener(
                view -> startActivity(new Intent(this, SignUpActivity.class))
        );
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

    private void attemptEnterApp(
            @NonNull TextInputLayout emailInputLayout,
            @NonNull TextInputLayout passwordInputLayout,
            @NonNull TextInputEditText emailInputView,
            @NonNull TextInputEditText passwordInputView,
            @NonNull MaterialButton signInButton
    ) {
        String email = getText(emailInputView);
        String password = getText(passwordInputView);

        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

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

        if (hasError) {
            return;
        }

        signInButton.setEnabled(false);
        signInButton.setText(R.string.login_signing_in);

        SupabaseAuthService.signIn(this, email, password, new SupabaseAuthService.Callback() {
            @Override
            public void onSuccess(@NonNull SupabaseAuthService.AuthResponse authResponse) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                signInButton.setEnabled(true);
                signInButton.setText(R.string.login_sign_in);
                AppSettings.setLoginEmail(LoginActivity.this, email);
                AppSettings.setLoginCompleted(LoginActivity.this, true);
                openMainView();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                signInButton.setEnabled(true);
                signInButton.setText(R.string.login_sign_in);
                if (message.equals(getString(R.string.login_failed_invalid_credentials))
                        || message.equalsIgnoreCase("Invalid login credentials")) {
                    passwordInputLayout.setError(getString(R.string.login_failed_invalid_credentials));
                    return;
                }

                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
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
