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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SupabaseAuthService {
    interface Callback {
        void onSuccess(@NonNull AuthResponse authResponse);

        void onError(@NonNull String message);
    }

    interface SimpleCallback {
        void onSuccess();

        void onError(@NonNull String message);
    }

    static final class AuthResponse {
        final boolean signedIn;
        @NonNull
        final String accessToken;
        @NonNull
        final String refreshToken;
        @NonNull
        final String displayName;
        @NonNull
        final String phoneNumber;

        AuthResponse(
                boolean signedIn,
                @Nullable String accessToken,
                @Nullable String refreshToken,
                @Nullable String displayName,
                @Nullable String phoneNumber
        ) {
            this.signedIn = signedIn;
            this.accessToken = accessToken == null ? "" : accessToken.trim();
            this.refreshToken = refreshToken == null ? "" : refreshToken.trim();
            this.displayName = displayName == null ? "" : displayName.trim();
            this.phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        }
    }

    private static final class SessionTokens {
        @NonNull
        final String accessToken;
        @NonNull
        final String refreshToken;

        SessionTokens(@NonNull String accessToken, @NonNull String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object SESSION_REFRESH_LOCK = new Object();

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
                String accessToken = extractAccessToken(responseBody);
                String refreshToken = extractRefreshToken(responseBody);
                boolean signedIn = !accessToken.isEmpty();
                if (signedIn) {
                    JSONObject userObject = extractUserObject(responseBody);
                    postSuccess(callback, new AuthResponse(
                            true,
                            accessToken,
                            refreshToken,
                            extractDisplayName(userObject),
                            extractPhoneNumber(userObject)
                    ));
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
                String accessToken = extractAccessToken(responseBody);
                String refreshToken = extractRefreshToken(responseBody);
                boolean signedIn = !accessToken.isEmpty();
                JSONObject userObject = extractUserObject(responseBody);
                if (signedIn) {
                    postSuccess(callback, new AuthResponse(
                            true,
                            accessToken,
                            refreshToken,
                            extractDisplayName(userObject),
                            extractPhoneNumber(userObject)
                    ));
                    return;
                }

                if (hasCreatedUser(responseBody)) {
                    postSuccess(callback, new AuthResponse(
                            false,
                            "",
                            "",
                            extractDisplayName(userObject),
                            extractPhoneNumber(userObject)
                    ));
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

    static void updateDisplayName(
            @NonNull Context context,
            @Nullable String accessToken,
            @NonNull String displayName,
            @NonNull SimpleCallback callback
    ) {
        updateProfile(context, accessToken, displayName, "", callback);
    }

    static void updateProfile(
            @NonNull Context context,
            @Nullable String accessToken,
            @NonNull String displayName,
            @Nullable String phoneNumber,
            @NonNull SimpleCallback callback
    ) {
        if (!isConfigured()) {
            postError(context, callback, R.string.auth_not_configured);
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                String normalizedPhoneNumber = normalizePhoneNumberForDisplay(phoneNumber);
                JSONObject metadata = new JSONObject();
                metadata.put("display_name", displayName.trim());
                if (!normalizedPhoneNumber.isEmpty()) {
                    metadata.put("phone", normalizedPhoneNumber);
                    metadata.put("phone_number", normalizedPhoneNumber);
                }

                JSONObject requestBody = new JSONObject();
                requestBody.put("data", metadata);

                executeAuthenticatedRequest(
                        context,
                        "/auth/v1/user",
                        "PUT",
                        requestBody,
                        accessToken
                );
                postSuccess(callback);
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
        return executeRequest(context, endpoint, "POST", requestBody, null);
    }

    @NonNull
    private static JSONObject executeRequest(
            @NonNull Context context,
            @NonNull String endpoint,
            @NonNull String method,
            @NonNull JSONObject requestBody,
            @Nullable String accessToken
    ) throws SupabaseAuthException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(normalizeBaseUrl(BuildConfig.SUPABASE_URL) + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY);
            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + (
                            accessToken == null || accessToken.trim().isEmpty()
                                    ? BuildConfig.SUPABASE_PUBLISHABLE_KEY
                                    : accessToken.trim()
                    )
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
    private static JSONObject executeAuthenticatedRequest(
            @NonNull Context context,
            @NonNull String endpoint,
            @NonNull String method,
            @NonNull JSONObject requestBody,
            @Nullable String accessToken
    ) throws SupabaseAuthException {
        Context appContext = context.getApplicationContext();
        String currentAccessToken = resolveAccessToken(appContext, accessToken);
        try {
            return executeRequest(appContext, endpoint, method, requestBody, currentAccessToken);
        } catch (SupabaseAuthException exception) {
            if (!isJwtExpiredError(exception.getMessage())) {
                throw exception;
            }

            String retryAccessToken = getRetryAccessToken(appContext, currentAccessToken);
            return executeRequest(appContext, endpoint, method, requestBody, retryAccessToken);
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

    static boolean hasAuthenticatedSession(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        return isConfigured()
                && (
                !AppSettings.getLoginAccessToken(appContext).isEmpty()
                        || !AppSettings.getLoginRefreshToken(appContext).isEmpty()
        );
    }

    static boolean isJwtExpiredError(@Nullable String message) {
        String normalizedMessage = normalizeToken(message).toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("jwt expired")
                || normalizedMessage.contains("token has expired")
                || normalizedMessage.contains("token is expired");
    }

    @NonNull
    static String resolveAccessToken(
            @NonNull Context context,
            @Nullable String providedAccessToken
    ) throws SupabaseAuthException {
        Context appContext = context.getApplicationContext();
        String storedAccessToken = AppSettings.getLoginAccessToken(appContext);
        if (!storedAccessToken.isEmpty()) {
            return storedAccessToken;
        }

        String normalizedProvidedAccessToken = normalizeToken(providedAccessToken);
        if (!normalizedProvidedAccessToken.isEmpty()) {
            AppSettings.setLoginAccessToken(appContext, normalizedProvidedAccessToken);
            return normalizedProvidedAccessToken;
        }

        return refreshAccessToken(appContext);
    }

    @NonNull
    static String getRetryAccessToken(
            @NonNull Context context,
            @Nullable String failedAccessToken
    ) throws SupabaseAuthException {
        Context appContext = context.getApplicationContext();
        String storedAccessToken = AppSettings.getLoginAccessToken(appContext);
        String normalizedFailedAccessToken = normalizeToken(failedAccessToken);
        if (!storedAccessToken.isEmpty() && !storedAccessToken.equals(normalizedFailedAccessToken)) {
            return storedAccessToken;
        }
        return refreshAccessToken(appContext);
    }

    @NonNull
    static String refreshAccessToken(@NonNull Context context) throws SupabaseAuthException {
        return refreshSessionBlocking(context.getApplicationContext()).accessToken;
    }

    @NonNull
    private static SessionTokens refreshSessionBlocking(@NonNull Context context)
            throws SupabaseAuthException {
        synchronized (SESSION_REFRESH_LOCK) {
            String refreshToken = AppSettings.getLoginRefreshToken(context);
            if (refreshToken.isEmpty()) {
                throw new SupabaseAuthException(context.getString(R.string.auth_session_expired));
            }

            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("refresh_token", refreshToken);
                JSONObject responseBody = executeRequest(
                        context,
                        "/auth/v1/token?grant_type=refresh_token",
                        requestBody
                );
                String accessToken = extractAccessToken(responseBody);
                if (accessToken.isEmpty()) {
                    throw new SupabaseAuthException(context.getString(R.string.auth_session_expired));
                }

                String refreshedToken = extractRefreshToken(responseBody);
                String resolvedRefreshToken = refreshedToken.isEmpty()
                        ? refreshToken
                        : refreshedToken;
                AppSettings.setLoginAccessToken(context, accessToken);
                AppSettings.setLoginRefreshToken(context, resolvedRefreshToken);
                return new SessionTokens(accessToken, resolvedRefreshToken);
            } catch (JSONException exception) {
                throw new SupabaseAuthException(context.getString(R.string.auth_generic_error));
            }
        }
    }

    @NonNull
    private static String extractAccessToken(@NonNull JSONObject responseBody) {
        String topLevelAccessToken = responseBody.optString("access_token", "").trim();
        if (!topLevelAccessToken.isEmpty()) {
            return topLevelAccessToken;
        }

        JSONObject sessionObject = responseBody.optJSONObject("session");
        if (sessionObject == null) {
            return "";
        }
        return sessionObject.optString("access_token", "").trim();
    }

    @NonNull
    private static String extractRefreshToken(@NonNull JSONObject responseBody) {
        String topLevelRefreshToken = responseBody.optString("refresh_token", "").trim();
        if (!topLevelRefreshToken.isEmpty()) {
            return topLevelRefreshToken;
        }

        JSONObject sessionObject = responseBody.optJSONObject("session");
        if (sessionObject == null) {
            return "";
        }
        return sessionObject.optString("refresh_token", "").trim();
    }

    private static boolean hasCreatedUser(@NonNull JSONObject responseBody) {
        if (responseBody.optJSONObject("user") != null) {
            return true;
        }

        // The direct GoTrue signup endpoint can also return the user object at the top level.
        return !responseBody.optString("id", "").isEmpty()
                && !responseBody.optString("email", "").isEmpty();
    }

    @Nullable
    private static JSONObject extractUserObject(@NonNull JSONObject responseBody) {
        JSONObject userObject = responseBody.optJSONObject("user");
        if (userObject != null) {
            return userObject;
        }
        return hasCreatedUser(responseBody) ? responseBody : null;
    }

    @NonNull
    private static String extractDisplayName(@Nullable JSONObject userObject) {
        if (userObject == null) {
            return "";
        }

        JSONObject userMetadata = userObject.optJSONObject("user_metadata");
        if (userMetadata == null) {
            return "";
        }

        String[] keys = new String[] {
                "display_name",
                "username",
                "full_name",
                "name"
        };
        for (String key : keys) {
            String value = userMetadata.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    @NonNull
    private static String extractPhoneNumber(@Nullable JSONObject userObject) {
        if (userObject == null) {
            return "";
        }

        JSONObject userMetadata = userObject.optJSONObject("user_metadata");
        if (userMetadata != null) {
            String[] keys = new String[] {
                    "phone",
                    "phone_number"
            };
            for (String key : keys) {
                String value = userMetadata.optString(key, "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        return userObject.optString("phone", "").trim();
    }

    @NonNull
    private static String normalizePhoneNumberForDisplay(@Nullable String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private static String normalizeToken(@Nullable String token) {
        return token == null ? "" : token.trim();
    }

    private static void postSuccess(
            @NonNull Callback callback,
            @NonNull AuthResponse authResponse
    ) {
        MAIN_HANDLER.post(() -> callback.onSuccess(authResponse));
    }

    private static void postSuccess(@NonNull SimpleCallback callback) {
        MAIN_HANDLER.post(callback::onSuccess);
    }

    private static void postError(@NonNull Callback callback, @NonNull String message) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static void postError(@NonNull SimpleCallback callback, @NonNull String message) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static void postError(
            @NonNull Context context,
            @NonNull Callback callback,
            int messageResId
    ) {
        postError(callback, context.getString(messageResId));
    }

    private static void postError(
            @NonNull Context context,
            @NonNull SimpleCallback callback,
            int messageResId
    ) {
        postError(callback, context.getString(messageResId));
    }

    static final class SupabaseAuthException extends Exception {
        SupabaseAuthException(@NonNull String message) {
            super(message);
        }
    }
}
