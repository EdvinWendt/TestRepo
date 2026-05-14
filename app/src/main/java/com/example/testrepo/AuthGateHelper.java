package com.example.testrepo;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

final class AuthGateHelper {
    private AuthGateHelper() {
    }

    static boolean redirectToLoginIfNeeded(@NonNull AppCompatActivity activity) {
        if (AppSettings.isSignedIn(activity)) {
            return false;
        }

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}
