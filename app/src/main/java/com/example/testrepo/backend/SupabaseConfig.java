package com.example.testrepo.backend;

import androidx.annotation.NonNull;

import com.example.testrepo.BuildConfig;

public final class SupabaseConfig {
    private SupabaseConfig() {
    }

    @NonNull
    public static String getProjectUrl() {
        return BuildConfig.SUPABASE_URL.trim();
    }

    @NonNull
    public static String getPublishableKey() {
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim();
    }

    public static boolean isConfigured() {
        return !getProjectUrl().isEmpty() && !getPublishableKey().isEmpty();
    }

    @NonNull
    public static String getRestUrl() {
        return getProjectUrl() + "/rest/v1";
    }

    @NonNull
    public static String getRpcUrl(@NonNull String rpcName) {
        return getRestUrl() + "/rpc/" + rpcName;
    }

    @NonNull
    public static String getAuthUrl() {
        return getProjectUrl() + "/auth/v1";
    }
}
