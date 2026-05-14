package com.example.testrepo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SupabaseAuthService {
    interface Callback {
        void onSuccess(@NonNull AuthResponse authResponse);

        void onError(@NonNull String message);
    }

    static final class AuthResponse {
        final boolean signedIn;

        AuthResponse(boolean signedIn) {
            this.signedIn = signedIn;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private SupabaseAuthService() {
    }

    static void signIn(
            @NonNull Context context,
            @NonNull String email,
            @NonNull String password,
            @NonNull Callback callback
    ) {
        if (!isConfigured()) {
            postError(context, callback, R.string.auth_not_configured);
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("email", email);
                requestBody.put("password", password);

                JSONObject responseBody = executeRequest(
                        context,
                        "/auth/v1/token?grant_type=password",
                        requestBody
                );
                boolean signedIn = !responseBody.optString("access_token", "").isEmpty();
                if (signedIn) {
                    postSuccess(callback, new AuthResponse(true));
                    return;
                }

                postError(context, callback, R.string.login_failed_invalid_credentials);
            } catch (SupabaseAuthException exception) {
                postError(callback, exception.getMessage());
            } catch (JSONException exception) {
                postError(context, callback, R.string.auth_generic_error);
            }
        });
    }

    static void signUp(
            @NonNull Context context,
            @NonNull String email,
            @NonNull String password,
            @NonNull Callback callback
    ) {
        if (!isConfigured()) {
            postError(context, callback, R.string.auth_not_configured);
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("email", email);
                requestBody.put("password", password);

                JSONObject responseBody = executeRequest(context, "/auth/v1/signup", requestBody);
                boolean signedIn = hasSignedInSession(responseBody);
                if (signedIn) {
                    postSuccess(callback, new AuthResponse(true));
                    return;
                }

                if (hasCreatedUser(responseBody)) {
                    postSuccess(callback, new AuthResponse(false));
                    return;
                }

                postError(context, callback, R.string.sign_up_failed_generic);
            } catch (SupabaseAuthException exception) {
                postError(callback, exception.getMessage());
            } catch (JSONException exception) {
                postError(context, callback, R.string.auth_generic_error);
            }
        });
    }

    @NonNull
    private static JSONObject executeRequest(
            @NonNull Context context,
            @NonNull String endpoint,
            @NonNull JSONObject requestBody
    ) throws SupabaseAuthException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(normalizeBaseUrl(BuildConfig.SUPABASE_URL) + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY);
            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + BuildConfig.SUPABASE_PUBLISHABLE_KEY
            );
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            byte[] bodyBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bodyBytes);
            }

            int responseCode = connection.getResponseCode();
            String responseText = readResponseBody(
                    responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream()
            );
            JSONObject responseJson = responseText.isEmpty()
                    ? new JSONObject()
                    : new JSONObject(responseText);

            if (responseCode >= 200 && responseCode < 300) {
                return responseJson;
            }

            throw new SupabaseAuthException(extractErrorMessage(responseJson));
        } catch (IOException exception) {
            throw new SupabaseAuthException(context.getString(R.string.auth_network_error));
        } catch (JSONException exception) {
            throw new SupabaseAuthException(context.getString(R.string.auth_generic_error));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static String extractErrorMessage(@NonNull JSONObject responseJson) {
        String[] keys = new String[] {
                "msg",
                "message",
                "error_description",
                "error"
        };
        for (String key : keys) {
            String value = responseJson.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "Authentication failed.";
    }

    @NonNull
    private static String readResponseBody(@Nullable InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String normalizeBaseUrl(@Nullable String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmedValue = baseUrl.trim();
        if (trimmedValue.endsWith("/")) {
            return trimmedValue.substring(0, trimmedValue.length() - 1);
        }
        return trimmedValue;
    }

    private static boolean isConfigured() {
        return !normalizeBaseUrl(BuildConfig.SUPABASE_URL).isEmpty()
                && !BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim().isEmpty();
    }

    private static boolean hasSignedInSession(@NonNull JSONObject responseBody) {
        if (!responseBody.optString("access_token", "").isEmpty()) {
            return true;
        }

        JSONObject sessionObject = responseBody.optJSONObject("session");
        return sessionObject != null && !sessionObject.optString("access_token", "").isEmpty();
    }

    private static boolean hasCreatedUser(@NonNull JSONObject responseBody) {
        if (responseBody.optJSONObject("user") != null) {
            return true;
        }

        // The direct GoTrue signup endpoint can also return the user object at the top level.
        return !responseBody.optString("id", "").isEmpty()
                && !responseBody.optString("email", "").isEmpty();
    }

    private static void postSuccess(
            @NonNull Callback callback,
            @NonNull AuthResponse authResponse
    ) {
        MAIN_HANDLER.post(() -> callback.onSuccess(authResponse));
    }

    private static void postError(@NonNull Callback callback, @NonNull String message) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static void postError(
            @NonNull Context context,
            @NonNull Callback callback,
            int messageResId
    ) {
        postError(callback, context.getString(messageResId));
    }

    private static final class SupabaseAuthException extends Exception {
        SupabaseAuthException(@NonNull String message) {
            super(message);
        }
    }
}
