package com.example.testrepo;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AppSettings {
    private static final String PREFERENCES_NAME = "app_settings";
    private static final String KEY_AUTO_ROTATE_IMAGE = "auto_rotate_image";
    private static final String KEY_DARK_THEME = "dark_theme";
    private static final String KEY_ACCENT_COLOR = "accent_color";
    private static final String KEY_SPLIT_ITEMS = "split_items";
    private static final String KEY_PRE_ADDED_PARTICIPANTS = "pre_added_participants";
    private static final String KEY_FAVORITE_PHONE_CONTACTS = "favorite_phone_contacts";
    private static final String KEY_USERNAME_NICKNAME = "username_nickname";
    private static final String KEY_STARTUP_PERMISSION_PROMPT_SHOWN =
            "startup_permission_prompt_shown";
    private static final boolean DEFAULT_AUTO_ROTATE_IMAGE_ENABLED = true;
    private static final boolean DEFAULT_DARK_THEME_ENABLED = false;
    private static final boolean DEFAULT_SPLIT_ITEMS_ENABLED = false;
    @NonNull
    private static final String ACCENT_COLOR_BLUE = "blue";
    @NonNull
    private static final String ACCENT_COLOR_GREEN = "green";
    private static final String DEFAULT_USERNAME_NICKNAME = "";
    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_PHONE = "phone";

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

    public static boolean isDarkThemeEnabled(@NonNull Context context) {
        return getPreferences(context)
                .getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME_ENABLED);
    }

    public static void setDarkThemeEnabled(@NonNull Context context, boolean enabled) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_DARK_THEME, enabled)
                .apply();
        applyThemeMode(enabled);
        applyThemeToContext(context);
    }

    public static void applyTheme(@NonNull Context context) {
        applyThemeMode(isDarkThemeEnabled(context));
        applyThemeToContext(context);
    }

    @NonNull
    public static String getAccentColor(@NonNull Context context) {
        String storedValue = getPreferences(context).getString(KEY_ACCENT_COLOR, ACCENT_COLOR_BLUE);
        return normalizeAccentColor(storedValue);
    }

    public static void setAccentColor(@NonNull Context context, @Nullable String accentColor) {
        getPreferences(context)
                .edit()
                .putString(KEY_ACCENT_COLOR, normalizeAccentColor(accentColor))
                .apply();
        applyThemeToContext(context);
    }

    public static boolean isBlueAccentColor(@NonNull Context context) {
        return ACCENT_COLOR_BLUE.equals(getAccentColor(context));
    }

    public static int getAppThemeResId(@NonNull Context context) {
        return isBlueAccentColor(context)
                ? R.style.Theme_TestRepo_Blue
                : R.style.Theme_TestRepo_Green;
    }

    public static int getFullScreenDialogThemeResId(@NonNull Context context) {
        return isBlueAccentColor(context)
                ? R.style.TestRepo_FullScreenDialog_Blue
                : R.style.TestRepo_FullScreenDialog_Green;
    }

    public static boolean isSplitItemsEnabled(@NonNull Context context) {
        return getPreferences(context)
                .getBoolean(KEY_SPLIT_ITEMS, DEFAULT_SPLIT_ITEMS_ENABLED);
    }

    public static void setSplitItemsEnabled(@NonNull Context context, boolean enabled) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_SPLIT_ITEMS, enabled)
                .apply();
    }

    @NonNull
    public static String getUsernameNickname(@NonNull Context context) {
        String storedValue = getPreferences(context).getString(
                KEY_USERNAME_NICKNAME,
                DEFAULT_USERNAME_NICKNAME
        );
        return normalizeUsernameNickname(storedValue);
    }

    public static void setUsernameNickname(@NonNull Context context, @Nullable String usernameNickname) {
        getPreferences(context)
                .edit()
                .putString(KEY_USERNAME_NICKNAME, normalizeUsernameNickname(usernameNickname))
                .apply();
    }

    public static boolean isUsernameNicknameEmpty(@NonNull Context context) {
        return getUsernameNickname(context).isEmpty();
    }

    public static void clearUsernameNickname(@NonNull Context context) {
        getPreferences(context)
                .edit()
                .putString(KEY_USERNAME_NICKNAME, DEFAULT_USERNAME_NICKNAME)
                .apply();
    }

    public static boolean hasStartupPermissionPromptBeenShown(@NonNull Context context) {
        return getPreferences(context).getBoolean(KEY_STARTUP_PERMISSION_PROMPT_SHOWN, false);
    }

    public static void setStartupPermissionPromptShown(@NonNull Context context, boolean shown) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_STARTUP_PERMISSION_PROMPT_SHOWN, shown)
                .apply();
    }

    @NonNull
    public static ArrayList<PreAddedParticipant> getPreAddedParticipants(@NonNull Context context) {
        String serializedParticipants =
                getPreferences(context).getString(KEY_PRE_ADDED_PARTICIPANTS, "[]");
        ArrayList<PreAddedParticipant> participants = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        try {
            JSONArray participantsArray = new JSONArray(serializedParticipants);
            for (int index = 0; index < participantsArray.length(); index++) {
                JSONObject participantObject = participantsArray.optJSONObject(index);
                if (participantObject == null) {
                    continue;
                }

                String name = normalizeWhitespace(participantObject.optString(JSON_KEY_NAME, ""));
                String phoneNumber = normalizeWhitespace(
                        participantObject.optString(JSON_KEY_PHONE, "")
                );
                if (name.isEmpty() || phoneNumber.isEmpty()) {
                    continue;
                }

                PreAddedParticipant participant = new PreAddedParticipant(name, phoneNumber);
                if (seenKeys.add(participant.getStorageKey())) {
                    participants.add(participant);
                }
            }
        } catch (JSONException ignored) {
            // Fall back to an empty list if stored data is malformed.
        }

        return participants;
    }

    public static void addPreAddedParticipant(
            @NonNull Context context,
            @NonNull PreAddedParticipant participant
    ) {
        ArrayList<PreAddedParticipant> participants = getPreAddedParticipants(context);
        for (PreAddedParticipant existingParticipant : participants) {
            if (existingParticipant.getStorageKey().equals(participant.getStorageKey())) {
                return;
            }
        }

        participants.add(participant);
        setPreAddedParticipants(context, participants);
    }

    public static void removePreAddedParticipant(
            @NonNull Context context,
            @NonNull PreAddedParticipant participant
    ) {
        ArrayList<PreAddedParticipant> participants = getPreAddedParticipants(context);
        String storageKey = participant.getStorageKey();
        participants.removeIf(existingParticipant -> existingParticipant.getStorageKey().equals(storageKey));
        setPreAddedParticipants(context, participants);
    }

    public static boolean isFavoritePhoneContact(
            @NonNull Context context,
            @Nullable String name,
            @Nullable String phoneNumber
    ) {
        return getFavoritePhoneContactKeys(context).contains(
                buildFavoritePhoneContactKey(name, phoneNumber)
        );
    }

    public static void setFavoritePhoneContact(
            @NonNull Context context,
            @Nullable String name,
            @Nullable String phoneNumber,
            boolean favorite
    ) {
        Set<String> favoriteKeys = getFavoritePhoneContactKeys(context);
        String contactKey = buildFavoritePhoneContactKey(name, phoneNumber);
        if (contactKey.isEmpty()) {
            return;
        }

        if (favorite) {
            favoriteKeys.add(contactKey);
        } else {
            favoriteKeys.remove(contactKey);
        }

        getPreferences(context)
                .edit()
                .putStringSet(KEY_FAVORITE_PHONE_CONTACTS, new HashSet<>(favoriteKeys))
                .apply();
    }

    public static boolean isPreAddedParticipantsPreferenceKey(@Nullable String key) {
        return KEY_PRE_ADDED_PARTICIPANTS.equals(key);
    }

    public static void registerChangeListener(
            @NonNull Context context,
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        getPreferences(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterChangeListener(
            @NonNull Context context,
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        getPreferences(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static boolean isSplitItemsPreferenceKey(@Nullable String key) {
        return KEY_SPLIT_ITEMS.equals(key);
    }

    private static void applyThemeMode(boolean darkThemeEnabled) {
        int targetMode = darkThemeEnabled
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    private static void applyThemeToContext(@NonNull Context context) {
        if (context instanceof ContextThemeWrapper) {
            ((ContextThemeWrapper) context).setTheme(getAppThemeResId(context));
        }
    }

    private static void setPreAddedParticipants(
            @NonNull Context context,
            @NonNull ArrayList<PreAddedParticipant> participants
    ) {
        JSONArray participantsArray = new JSONArray();
        for (PreAddedParticipant participant : participants) {
            JSONObject participantObject = new JSONObject();
            try {
                participantObject.put(JSON_KEY_NAME, participant.name);
                participantObject.put(JSON_KEY_PHONE, participant.phoneNumber);
                participantsArray.put(participantObject);
            } catch (JSONException ignored) {
                // Skip malformed participant entries.
            }
        }

        getPreferences(context)
                .edit()
                .putString(KEY_PRE_ADDED_PARTICIPANTS, participantsArray.toString())
                .apply();
    }

    @NonNull
    private static Set<String> getFavoritePhoneContactKeys(@NonNull Context context) {
        Set<String> storedKeys = getPreferences(context).getStringSet(
                KEY_FAVORITE_PHONE_CONTACTS,
                new HashSet<>()
        );
        return storedKeys == null ? new HashSet<>() : new HashSet<>(storedKeys);
    }

    @NonNull
    private static String buildFavoritePhoneContactKey(
            @Nullable String name,
            @Nullable String phoneNumber
    ) {
        String normalizedName = normalizeWhitespace(name).toLowerCase(Locale.US);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedName.isEmpty() || normalizedPhoneNumber.isEmpty()) {
            return "";
        }
        return normalizedName + "\u001F" + normalizedPhoneNumber;
    }

    @NonNull
    private static String normalizeWhitespace(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private static String normalizePhoneNumber(@Nullable String phoneNumber) {
        return normalizeWhitespace(phoneNumber).replaceAll("[^+\\d]", "");
    }

    @NonNull
    private static String normalizeUsernameNickname(@Nullable String usernameNickname) {
        String normalizedValue = normalizeWhitespace(usernameNickname);
        if (normalizedValue.length() > 20) {
            return normalizedValue.substring(0, 20).trim();
        }
        return normalizedValue;
    }

    @NonNull
    private static String normalizeAccentColor(@Nullable String accentColor) {
        String normalizedValue = normalizeWhitespace(accentColor).toLowerCase(Locale.US);
        if (ACCENT_COLOR_GREEN.equals(normalizedValue)) {
            return ACCENT_COLOR_GREEN;
        }
        return ACCENT_COLOR_BLUE;
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static final class PreAddedParticipant {
        @NonNull
        public final String name;
        @NonNull
        public final String phoneNumber;

        public PreAddedParticipant(@NonNull String name, @NonNull String phoneNumber) {
            this.name = normalizeWhitespace(name);
            this.phoneNumber = normalizeWhitespace(phoneNumber);
        }

        @NonNull
        String getStorageKey() {
            return name.toLowerCase(Locale.US) + "\u001F" + normalizePhoneNumber(phoneNumber);
        }
    }
}
