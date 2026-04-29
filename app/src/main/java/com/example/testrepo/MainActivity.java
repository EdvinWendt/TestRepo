package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        setContentView(R.layout.activity_main);
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
        promptForRequiredUsernameIfNeeded();
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
}
