package com.example.testrepo;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

final class InstallResetHelper {
    private static final String INSTALL_MARKER_FILE_NAME = "install_reset_marker";

    private InstallResetHelper() {
    }

    static void resetInstallScopedDataIfNeeded(@NonNull Context context) {
        File markerFile = new File(context.getNoBackupFilesDir(), INSTALL_MARKER_FILE_NAME);
        if (markerFile.exists()) {
            return;
        }

        AppSettings.clearUsernameNickname(context);

        File parentDirectory = markerFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.exists()) {
            parentDirectory.mkdirs();
        }

        try {
            markerFile.createNewFile();
        } catch (IOException ignored) {
            // If marker creation fails, the next launch will attempt the same reset again.
        }
    }
}
