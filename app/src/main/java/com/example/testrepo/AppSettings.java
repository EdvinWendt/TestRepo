package com.example.testrepo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public final class AppSettings {
    private static final String PREFERENCES_NAME = "app_settings";
    private static final String KEY_AUTO_ROTATE_IMAGE = "auto_rotate_image";
    private static final boolean DEFAULT_AUTO_ROTATE_IMAGE_ENABLED = true;

    private AppSettings() {
    }

    public static boolean isAutoRotateImageEnabled(@NonNull Context context) {
        return getPreferences(context)
                .getBoolean(KEY_AUTO_ROTATE_IMAGE, DEFAULT_AUTO_ROTATE_IMAGE_ENABLED);
    }

    public static void setAutoRotateImageEnabled(@NonNull Context context, boolean enabled) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_AUTO_ROTATE_IMAGE, enabled)
                .apply();
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
