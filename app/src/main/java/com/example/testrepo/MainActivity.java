package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InstallResetHelper.resetInstallScopedDataIfNeeded(this);
        setContentView(R.layout.activity_main);

        View settingsMenuButton = findViewById(R.id.button_main_actions);
        findViewById(R.id.button_new_receipt).setOnClickListener(
                view -> startActivity(new Intent(this, NewReceiptActivity.class))
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
}
