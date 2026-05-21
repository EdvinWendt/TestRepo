package com.example.testrepo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.testrepo.backend.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SupabaseHistoryService {
    interface LoadEntriesCallback {
        void onSuccess(@NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> entries);

        void onError(@NonNull String message);
    }

    interface EntryCallback {
        void onSuccess(@NonNull ReceiptHistoryStore.HistoryEntry entry);

        void onError(@NonNull String message);
    }

    interface SimpleCallback {
        void onSuccess();

        void onError(@NonNull String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private SupabaseHistoryService() {
    }

    static void loadEntries(
            @NonNull Context context,
            @NonNull LoadEntriesCallback callback
    ) {
        Context appContext = context.getApplicationContext();
        if (!isRemoteHistoryAvailable(appContext)) {
            postSuccess(callback, ReceiptHistoryStore.loadEntries(appContext));
            return;
        }

        String accessToken = AppSettings.getLoginAccessToken(appContext);
        EXECUTOR.execute(() -> {
            try {
                ArrayList<ReceiptHistoryStore.HistoryEntry> remoteEntries =
                        loadRemoteEntries(appContext, accessToken);
                if (remoteEntries.isEmpty()) {
                    ArrayList<ReceiptHistoryStore.HistoryEntry> legacyEntries =
                            ReceiptHistoryStore.loadEntries(appContext);
                    if (!legacyEntries.isEmpty()) {
                        ArrayList<ReceiptHistoryStore.HistoryEntry> migratedEntries =
                                insertEntries(appContext, accessToken, legacyEntries);
                        ReceiptHistoryStore.clearHistory(appContext);
                        postSuccess(callback, migratedEntries);
                        return;
                    }
                }
                postSuccess(callback, remoteEntries);
            } catch (SupabaseHistoryException exception) {
                ArrayList<ReceiptHistoryStore.HistoryEntry> legacyEntries =
                        ReceiptHistoryStore.loadEntries(appContext);
                if (!legacyEntries.isEmpty()) {
                    postSuccess(callback, legacyEntries);
                    return;
                }
                postError(callback, exception.getMessage());
            }
        });
    }

    static void saveEntry(
            @NonNull Context context,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull EntryCallback callback
    ) {
        Context appContext = context.getApplicationContext();
        if (!isRemoteHistoryAvailable(appContext)) {
            postError(callback, appContext.getString(R.string.history_sync_unavailable));
            return;
        }

        String accessToken = AppSettings.getLoginAccessToken(appContext);
        EXECUTOR.execute(() -> {
            try {
                ReceiptHistoryStore.HistoryEntry savedEntry =
                        insertEntry(appContext, accessToken, entry);
                postSuccess(callback, savedEntry);
            } catch (SupabaseHistoryException exception) {
                postError(callback, exception.getMessage());
            }
        });
    }

    static void saveEntry(
            @NonNull Context context,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull SimpleCallback callback
    ) {
        saveEntry(context, entry, new EntryCallback() {
            @Override
            public void onSuccess(@NonNull ReceiptHistoryStore.HistoryEntry savedEntry) {
                postSuccess(callback);
            }

            @Override
            public void onError(@NonNull String message) {
                postError(callback, message);
            }
        });
    }

    static void updateEntry(
            @NonNull Context context,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull EntryCallback callback
    ) {
        Context appContext = context.getApplicationContext();
        if (!isRemoteHistoryAvailable(appContext)) {
            postError(callback, appContext.getString(R.string.history_sync_unavailable));
            return;
        }
        if (entry.storageId.isEmpty()) {
            postError(callback, appContext.getString(R.string.history_sync_entry_missing));
            return;
        }

        String accessToken = AppSettings.getLoginAccessToken(appContext);
        EXECUTOR.execute(() -> {
            try {
                ReceiptHistoryStore.HistoryEntry updatedEntry =
                        updateRemoteEntry(appContext, accessToken, entry);
                postSuccess(callback, updatedEntry);
            } catch (SupabaseHistoryException exception) {
                postError(callback, exception.getMessage());
            }
        });
    }

    static void removeEntry(
            @NonNull Context context,
            @NonNull ReceiptHistoryStore.HistoryEntry entry,
            @NonNull SimpleCallback callback
    ) {
        Context appContext = context.getApplicationContext();
        if (!isRemoteHistoryAvailable(appContext)) {
            postError(callback, appContext.getString(R.string.history_sync_unavailable));
            return;
        }
        if (entry.storageId.isEmpty()) {
            postError(callback, appContext.getString(R.string.history_sync_entry_missing));
            return;
        }

        String accessToken = AppSettings.getLoginAccessToken(appContext);
        EXECUTOR.execute(() -> {
            try {
                deleteRemoteEntry(appContext, accessToken, entry.storageId);
                postSuccess(callback);
            } catch (SupabaseHistoryException exception) {
                postError(callback, exception.getMessage());
            }
        });
    }

    static boolean isRemoteHistoryAvailable(@NonNull Context context) {
        return SupabaseAuthService.hasAuthenticatedSession(context);
    }

    @NonNull
    private static ArrayList<ReceiptHistoryStore.HistoryEntry> loadRemoteEntries(
            @NonNull Context context,
            @NonNull String accessToken
    ) throws SupabaseHistoryException {
        String responseText = executeRequest(
                context,
                SupabaseConfig.getRestUrl()
                        + "/history_entries?select=id,payload&order=created_at.desc",
                "GET",
                null,
                accessToken,
                false
        );
        return parseEntriesResponse(responseText);
    }

    @NonNull
    private static ReceiptHistoryStore.HistoryEntry insertEntry(
            @NonNull Context context,
            @NonNull String accessToken,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) throws SupabaseHistoryException {
        ArrayList<ReceiptHistoryStore.HistoryEntry> insertedEntries = insertEntries(
                context,
                accessToken,
                java.util.Collections.singletonList(entry)
        );
        if (insertedEntries.isEmpty()) {
            throw new SupabaseHistoryException(
                    context.getString(R.string.history_sync_save_failed)
            );
        }
        return insertedEntries.get(0);
    }

    @NonNull
    private static ArrayList<ReceiptHistoryStore.HistoryEntry> insertEntries(
            @NonNull Context context,
            @NonNull String accessToken,
            @NonNull List<ReceiptHistoryStore.HistoryEntry> entries
    ) throws SupabaseHistoryException {
        JSONArray requestBody = new JSONArray();
        for (ReceiptHistoryStore.HistoryEntry entry : entries) {
            JSONObject rowObject = new JSONObject();
            try {
                rowObject.put("payload", entry.toJson());
            } catch (JSONException exception) {
                throw new SupabaseHistoryException(
                        context.getString(R.string.history_sync_save_failed)
                );
            }
            requestBody.put(rowObject);
        }

        String responseText = executeRequest(
                context,
                SupabaseConfig.getRestUrl() + "/history_entries?select=id,payload",
                "POST",
                requestBody.toString(),
                accessToken,
                true
        );
        return parseEntriesResponse(responseText);
    }

    @NonNull
    private static ReceiptHistoryStore.HistoryEntry updateRemoteEntry(
            @NonNull Context context,
            @NonNull String accessToken,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) throws SupabaseHistoryException {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("payload", entry.toJson());
        } catch (JSONException exception) {
            throw new SupabaseHistoryException(
                    context.getString(R.string.history_sync_update_failed)
            );
        }

        String responseText = executeRequest(
                context,
                SupabaseConfig.getRestUrl()
                        + "/history_entries?id=eq."
                        + encodeQueryValue(entry.storageId)
                        + "&select=id,payload",
                "PATCH",
                requestBody.toString(),
                accessToken,
                true
        );

        ArrayList<ReceiptHistoryStore.HistoryEntry> updatedEntries = parseEntriesResponse(responseText);
        if (updatedEntries.isEmpty()) {
            throw new SupabaseHistoryException(
                    context.getString(R.string.history_sync_update_failed)
            );
        }
        return updatedEntries.get(0);
    }

    private static void deleteRemoteEntry(
            @NonNull Context context,
            @NonNull String accessToken,
            @NonNull String storageId
    ) throws SupabaseHistoryException {
        executeRequest(
                context,
                SupabaseConfig.getRestUrl()
                        + "/history_entries?id=eq."
                        + encodeQueryValue(storageId),
                "DELETE",
                null,
                accessToken,
                false
        );
    }

    @NonNull
    private static ArrayList<ReceiptHistoryStore.HistoryEntry> parseEntriesResponse(
            @Nullable String responseText
    ) throws SupabaseHistoryException {
        ArrayList<ReceiptHistoryStore.HistoryEntry> entries = new ArrayList<>();
        if (responseText == null || responseText.trim().isEmpty()) {
            return entries;
        }

        try {
            Object parsedResponse = new JSONTokener(responseText).nextValue();
            if (parsedResponse instanceof JSONArray) {
                JSONArray responseArray = (JSONArray) parsedResponse;
                for (int index = 0; index < responseArray.length(); index++) {
                    JSONObject rowObject = responseArray.optJSONObject(index);
                    if (rowObject == null) {
                        continue;
                    }
                    ReceiptHistoryStore.HistoryEntry entry = parseEntryRow(rowObject);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                return entries;
            }

            if (parsedResponse instanceof JSONObject) {
                ReceiptHistoryStore.HistoryEntry entry =
                        parseEntryRow((JSONObject) parsedResponse);
                if (entry != null) {
                    entries.add(entry);
                }
                return entries;
            }
        } catch (JSONException exception) {
            throw new SupabaseHistoryException("Unable to parse history response.");
        }

        return entries;
    }

    @Nullable
    private static ReceiptHistoryStore.HistoryEntry parseEntryRow(@NonNull JSONObject rowObject) {
        String storageId = rowObject.optString("id", "").trim();
        JSONObject payloadObject = rowObject.optJSONObject("payload");
        if (payloadObject == null) {
            return null;
        }
        return ReceiptHistoryStore.HistoryEntry.fromJson(payloadObject).copyWithStorageId(storageId);
    }

    @NonNull
    private static String executeRequest(
            @NonNull Context context,
            @NonNull String requestUrl,
            @NonNull String method,
            @Nullable String requestBody,
            @NonNull String accessToken,
            boolean returnRepresentation
    ) throws SupabaseHistoryException {
        Context appContext = context.getApplicationContext();
        String currentAccessToken;
        try {
            currentAccessToken = SupabaseAuthService.resolveAccessToken(appContext, accessToken);
        } catch (SupabaseAuthService.SupabaseAuthException exception) {
            throw new SupabaseHistoryException(exception.getMessage());
        }

        try {
            return executeRequestInternal(
                    appContext,
                    requestUrl,
                    method,
                    requestBody,
                    currentAccessToken,
                    returnRepresentation
            );
        } catch (SupabaseHistoryException exception) {
            if (!SupabaseAuthService.isJwtExpiredError(exception.getMessage())) {
                throw exception;
            }

            try {
                String retryAccessToken = SupabaseAuthService.getRetryAccessToken(
                        appContext,
                        currentAccessToken
                );
                return executeRequestInternal(
                        appContext,
                        requestUrl,
                        method,
                        requestBody,
                        retryAccessToken,
                        returnRepresentation
                );
            } catch (SupabaseAuthService.SupabaseAuthException refreshException) {
                throw new SupabaseHistoryException(refreshException.getMessage());
            }
        }
    }

    @NonNull
    private static String executeRequestInternal(
            @NonNull Context context,
            @NonNull String requestUrl,
            @NonNull String method,
            @Nullable String requestBody,
            @NonNull String accessToken,
            boolean returnRepresentation
    ) throws SupabaseHistoryException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(requestUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("apikey", SupabaseConfig.getPublishableKey());
            connection.setRequestProperty("Authorization", "Bearer " + accessToken.trim());
            connection.setRequestProperty("Accept", "application/json");
            if (returnRepresentation) {
                if ("POST".equalsIgnoreCase(method)) {
                    connection.setRequestProperty(
                            "Prefer",
                            "return=representation,missing=default"
                    );
                } else {
                    connection.setRequestProperty("Prefer", "return=representation");
                }
            }

            if (requestBody != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bodyBytes);
                }
            }

            int responseCode = connection.getResponseCode();
            String responseText = readResponseBody(
                    responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream()
            );
            if (responseCode >= 200 && responseCode < 300) {
                return responseText;
            }

            throw new SupabaseHistoryException(
                    extractErrorMessage(responseText, context.getString(R.string.history_sync_error))
            );
        } catch (IOException exception) {
            throw new SupabaseHistoryException(
                    context.getString(R.string.auth_network_error)
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
    private static String extractErrorMessage(
            @Nullable String responseText,
            @NonNull String fallbackMessage
    ) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return fallbackMessage;
        }

        try {
            JSONObject responseObject = new JSONObject(responseText);
            String[] keys = new String[] {
                    "message",
                    "error_description",
                    "details",
                    "hint",
                    "error"
            };
            for (String key : keys) {
                String value = responseObject.optString(key, "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (JSONException ignored) {
            // Fall through to the default fallback.
        }
        return fallbackMessage;
    }

    @NonNull
    private static String encodeQueryValue(@NonNull String value) throws SupabaseHistoryException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            throw new SupabaseHistoryException("Unable to encode history entry id.");
        }
    }

    private static void postSuccess(
            @NonNull LoadEntriesCallback callback,
            @NonNull ArrayList<ReceiptHistoryStore.HistoryEntry> entries
    ) {
        MAIN_HANDLER.post(() -> callback.onSuccess(entries));
    }

    private static void postSuccess(
            @NonNull EntryCallback callback,
            @NonNull ReceiptHistoryStore.HistoryEntry entry
    ) {
        MAIN_HANDLER.post(() -> callback.onSuccess(entry));
    }

    private static void postSuccess(@NonNull SimpleCallback callback) {
        MAIN_HANDLER.post(callback::onSuccess);
    }

    private static void postError(
            @NonNull LoadEntriesCallback callback,
            @NonNull String message
    ) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static void postError(
            @NonNull EntryCallback callback,
            @NonNull String message
    ) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static void postError(
            @NonNull SimpleCallback callback,
            @NonNull String message
    ) {
        MAIN_HANDLER.post(() -> callback.onError(message));
    }

    private static final class SupabaseHistoryException extends Exception {
        SupabaseHistoryException(@NonNull String message) {
            super(message);
        }
    }
}
