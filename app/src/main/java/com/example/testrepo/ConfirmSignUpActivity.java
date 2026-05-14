package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ConfirmSignUpActivity extends AppCompatActivity {
    private static final String EXTRA_EMAIL = "email";

    @NonNull
    private String appliedThemeConfigurationKey = "";

    public static Intent createIntent(
            @NonNull AppCompatActivity activity,
            @NonNull String email
    ) {
        Intent intent = new Intent(activity, ConfirmSignUpActivity.class);
        intent.putExtra(EXTRA_EMAIL, email);
        return intent;
    }

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

        setContentView(R.layout.activity_confirm_sign_up);

        TextView messageView = findViewById(R.id.text_confirm_sign_up_message);
        MaterialButton okayButton = findViewById(R.id.button_confirm_sign_up_okay);
        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (email == null) {
            email = "";
        }

        messageView.setText(getString(R.string.confirm_sign_up_message, email));
        okayButton.setOnClickListener(view -> openLoginView());
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

    private void openLoginView() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void openMainView() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
