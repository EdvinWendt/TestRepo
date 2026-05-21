package com.example.testrepo;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

public final class DeviceCapabilityHelper {
    private DeviceCapabilityHelper() {
    }

    public static boolean supportsSms(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
}
