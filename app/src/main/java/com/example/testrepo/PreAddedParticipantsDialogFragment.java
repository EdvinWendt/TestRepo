package com.example.testrepo;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreAddedParticipantsDialogFragment extends DialogFragment {
    private static final String TAG = "PreAddedParticipantsDialog";
    private static final int MAX_PARTICIPANT_BUTTONS_PER_ROW = 5;

    @Nullable
    private LinearLayout participantButtonsLayout;
    private boolean showAddParticipantDialogAfterContactsPermission;
    @Nullable
    private ExecutorService backgroundExecutor;
    private final ActivityResultLauncher<String> requestContactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (showAddParticipantDialogAfterContactsPermission) {
                    showAddParticipantDialogAfterContactsPermission = false;
                    showAddParticipantDialog(isGranted);
                }
            });

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new PreAddedParticipantsDialogFragment().show(fragmentManager, TAG);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        backgroundExecutor = Executors.newSingleThreadExecutor();
        return inflater.inflate(R.layout.dialog_pre_added_participants, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        participantButtonsLayout = view.findViewById(R.id.layout_pre_added_participant_buttons);
        refreshParticipantButtons();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setWindowAnimations(R.style.TestRepo_DialogAnimation);
        }
    }

    private void refreshParticipantButtons() {
        if (participantButtonsLayout == null || getContext() == null) {
            return;
        }

        participantButtonsLayout.removeAllViews();
        ArrayList<AppSettings.PreAddedParticipant> participants =
                AppSettings.getPreAddedParticipants(requireContext());

        int buttonSize = dpToPx(52);
        int buttonSpacing = dpToPx(6);
        int rowSpacing = dpToPx(8);

        ArrayList<View> participantBadgeButtons = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            participantBadgeButtons.add(
                    createParticipantBadgeButton(
                            participants.get(index),
                            index,
                            buttonSize,
                            buttonSpacing
                    )
            );
        }
        participantBadgeButtons.add(createAddParticipantBadgeButton(buttonSize, buttonSpacing));

        LinearLayout currentRow = null;
        for (int index = 0; index < participantBadgeButtons.size(); index++) {
            if (index % MAX_PARTICIPANT_BUTTONS_PER_ROW == 0) {
                currentRow = new LinearLayout(requireContext());
                LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (index > 0) {
                    rowLayoutParams.topMargin = rowSpacing;
                }
                currentRow.setLayoutParams(rowLayoutParams);
                currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                participantButtonsLayout.addView(currentRow);
            }

            if (currentRow != null) {
                currentRow.addView(participantBadgeButtons.get(index));
            }
        }
    }

    @NonNull
    private MaterialButton createParticipantBadgeButton(
            @NonNull AppSettings.PreAddedParticipant participant,
            int participantIndex,
            int buttonSize,
            int buttonSpacing
    ) {
        MaterialButton participantButton = new MaterialButton(requireContext());
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(buttonSize, buttonSize);
        layoutParams.setMargins(buttonSpacing, 0, buttonSpacing, 0);
        participantButton.setLayoutParams(layoutParams);
        int participantColor = createParticipantColor(participantIndex);
        participantButton.setText(getParticipantInitials(participant.name));
        participantButton.setAllCaps(false);
        participantButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        participantButton.setCheckable(false);
        participantButton.setClickable(true);
        participantButton.setInsetTop(0);
        participantButton.setInsetBottom(0);
        participantButton.setMinWidth(0);
        participantButton.setMinHeight(0);
        participantButton.setMinimumWidth(0);
        participantButton.setMinimumHeight(0);
        participantButton.setPadding(0, 0, 0, 0);
        participantButton.setCornerRadius(buttonSize / 2);
        participantButton.setStrokeWidth(0);
        participantButton.setBackgroundTintList(ColorStateList.valueOf(participantColor));
        participantButton.setTextColor(getParticipantTextColor(participantColor));
        participantButton.setContentDescription(participant.name);
        participantButton.setOnClickListener(view -> showParticipantOptionsDialog(participant));
        return participantButton;
    }

    @NonNull
    private AppCompatImageButton createAddParticipantBadgeButton(int buttonSize, int buttonSpacing) {
        AppCompatImageButton addParticipantButton = new AppCompatImageButton(requireContext());
        LinearLayout.LayoutParams addButtonLayoutParams =
                new LinearLayout.LayoutParams(buttonSize, buttonSize);
        addButtonLayoutParams.setMargins(buttonSpacing, 0, buttonSpacing, 0);
        addParticipantButton.setLayoutParams(addButtonLayoutParams);
        addParticipantButton.setBackgroundColor(Color.TRANSPARENT);
        addParticipantButton.setImageResource(R.drawable.ic_add_participant_badge);
        addParticipantButton.setScaleType(ImageView.ScaleType.CENTER);
        addParticipantButton.setPadding(0, 0, 0, 0);
        addParticipantButton.setContentDescription(getString(R.string.add_participant));
        addParticipantButton.setOnClickListener(view -> openAddParticipantDialog());
        return addParticipantButton;
    }

    private void openAddParticipantDialog() {
        if (hasContactsPermission()) {
            showAddParticipantDialog(true);
        } else {
            showAddParticipantDialogAfterContactsPermission = true;
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void showAddParticipantDialog(boolean contactsPermissionGranted) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_pre_added_participant, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.layout_participant_name);
        TextInputLayout phoneLayout = dialogView.findViewById(R.id.layout_participant_phone);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_participant_name);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.input_participant_phone);
        ListView phoneContactsList = dialogView.findViewById(R.id.list_phone_contacts);
        TextView emptyContactsView = dialogView.findViewById(R.id.text_phone_contacts_empty);
        MaterialButton addParticipantButton =
                dialogView.findViewById(R.id.button_add_participant_confirm);

        ArrayList<PhoneContact> phoneContacts = new ArrayList<>();
        PhoneContactsAdapter phoneContactsAdapter = new PhoneContactsAdapter(phoneContacts);
        phoneContactsList.setAdapter(phoneContactsAdapter);
        phoneContactsList.setEmptyView(emptyContactsView);
        addParticipantButton.setEnabled(false);
        phoneContactsList.setOnItemClickListener((parent, view, position, id) -> {
            PhoneContact selectedContact = phoneContactsAdapter.getItem(position);
            if (selectedContact == null) {
                return;
            }

            nameInput.setText(selectedContact.name);
            phoneInput.setText(selectedContact.phoneNumber);
            nameLayout.setError(null);
            phoneLayout.setError(null);
        });

        TextWatcher validationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateAddParticipantButtonState(
                        nameLayout,
                        phoneLayout,
                        nameInput,
                        phoneInput,
                        addParticipantButton
                );
            }
        };
        nameInput.addTextChangedListener(validationWatcher);
        phoneInput.addTextChangedListener(validationWatcher);
        updateAddParticipantButtonState(
                nameLayout,
                phoneLayout,
                nameInput,
                phoneInput,
                addParticipantButton
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_new_participant_title)
                .setView(dialogView)
                .create();

        addParticipantButton.setOnClickListener(view -> {
            String name = getText(nameInput);
            String phoneNumber = getText(phoneInput);

            nameLayout.setError(null);
            phoneLayout.setError(null);

            boolean hasError = false;
            if (name.isEmpty()) {
                nameLayout.setError(getString(R.string.contact_name_required));
                hasError = true;
            }
            if (phoneNumber.isEmpty()) {
                phoneLayout.setError(getString(R.string.contact_phone_required));
                hasError = true;
            } else if (!isValidPhoneNumber(phoneNumber)) {
                phoneLayout.setError(getString(R.string.contact_phone_invalid));
                hasError = true;
            }
            if (hasError) {
                return;
            }

            AppSettings.PreAddedParticipant participant =
                    new AppSettings.PreAddedParticipant(name, phoneNumber);
            if (isPreAddedParticipantAlreadyAdded(participant)) {
                Toast.makeText(
                        requireContext(),
                        R.string.participant_already_added,
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            AppSettings.addPreAddedParticipant(requireContext(), participant);
            Toast.makeText(
                    requireContext(),
                    R.string.participant_added,
                    Toast.LENGTH_SHORT
            ).show();
            refreshParticipantButtons();
            dialog.dismiss();
        });
        dialog.show();

        if (!contactsPermissionGranted) {
            emptyContactsView.setText(R.string.phone_contacts_permission_required);
            return;
        }

        emptyContactsView.setText(R.string.loading_phone_contacts);
        if (backgroundExecutor == null) {
            return;
        }

        backgroundExecutor.execute(() -> {
            ArrayList<PhoneContact> availableContacts = loadPhoneContactsFromDevice();
            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || !dialog.isShowing()) {
                    return;
                }

                phoneContacts.clear();
                phoneContacts.addAll(availableContacts);
                phoneContactsAdapter.notifyDataSetChanged();
                if (availableContacts.isEmpty()) {
                    emptyContactsView.setText(R.string.no_phone_contacts);
                } else {
                    emptyContactsView.setText("");
                }
            });
        });
    }

    private void showParticipantOptionsDialog(@NonNull AppSettings.PreAddedParticipant participant) {
        String participantMessage = participant.phoneNumber.isEmpty()
                ? getString(R.string.participant_phone_unavailable)
                : participant.phoneNumber;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(participant.name)
                .setMessage(participantMessage)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.remove, (dialog, which) -> {
                    AppSettings.removePreAddedParticipant(requireContext(), participant);
                    refreshParticipantButtons();
                })
                .show();
    }

    private boolean isPreAddedParticipantAlreadyAdded(
            @NonNull AppSettings.PreAddedParticipant participant
    ) {
        String normalizedName = normalizeWhitespace(participant.name).toLowerCase(Locale.US);
        String normalizedPhoneNumber = normalizePhoneNumber(participant.phoneNumber);

        for (AppSettings.PreAddedParticipant existingParticipant
                : AppSettings.getPreAddedParticipants(requireContext())) {
            boolean sameName = existingParticipant.name.toLowerCase(Locale.US).equals(normalizedName);
            boolean samePhone = normalizePhoneNumber(existingParticipant.phoneNumber)
                    .equals(normalizedPhoneNumber);
            if ((sameName && samePhone) || samePhone) {
                return true;
            }
        }

        return false;
    }

    private int createParticipantColor(int participantIndex) {
        float hue = (participantIndex * 137.508f) % 360f;
        float[] hsv = {hue, 0.72f, 0.78f};
        return Color.HSVToColor(hsv);
    }

    private int getParticipantTextColor(int backgroundColor) {
        double brightness = (
                (Color.red(backgroundColor) * 0.299)
                        + (Color.green(backgroundColor) * 0.587)
                        + (Color.blue(backgroundColor) * 0.114)
        ) / 255d;
        return brightness > 0.65d ? Color.BLACK : Color.WHITE;
    }

    @NonNull
    private String getParticipantInitials(@NonNull String name) {
        String normalizedName = normalizeWhitespace(name);
        if (normalizedName.isEmpty()) {
            return "?";
        }

        String[] parts = normalizedName.split(" ");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        if (initials.length() == 0) {
            initials.append(Character.toUpperCase(normalizedName.charAt(0)));
        }
        if (initials.length() == 1 && normalizedName.length() > 1) {
            initials.append(Character.toUpperCase(normalizedName.charAt(1)));
        }
        return initials.toString();
    }

    private boolean isValidPhoneNumber(@NonNull String phoneNumber) {
        String trimmedPhoneNumber = normalizeWhitespace(phoneNumber);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        return !trimmedPhoneNumber.isEmpty()
                && normalizedPhoneNumber.length() >= 6
                && Patterns.PHONE.matcher(trimmedPhoneNumber).matches();
    }

    private boolean hasContactsPermission() {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private ArrayList<PhoneContact> loadPhoneContactsFromDevice() {
        ArrayList<PhoneContact> contacts = new ArrayList<>();
        Context context = getContext();
        if (context == null || !hasContactsPermission()) {
            return contacts;
        }

        Set<String> seenContacts = new HashSet<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )) {
            if (cursor == null) {
                return contacts;
            }

            int nameColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            );
            int phoneColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            );

            while (cursor.moveToNext()) {
                String name = nameColumn >= 0 ? normalizeWhitespace(cursor.getString(nameColumn)) : "";
                String phoneNumber =
                        phoneColumn >= 0 ? normalizeWhitespace(cursor.getString(phoneColumn)) : "";
                if (name.isEmpty() || phoneNumber.isEmpty()) {
                    continue;
                }

                String dedupeKey = name.toLowerCase(Locale.US)
                        + "\u001F"
                        + phoneNumber.replaceAll("[^+\\d]", "");
                if (seenContacts.add(dedupeKey)) {
                    contacts.add(new PhoneContact(name, phoneNumber));
                }
            }
        }

        contacts.sort(Comparator
                .comparing((PhoneContact contact) -> contact.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(contact -> contact.phoneNumber, String.CASE_INSENSITIVE_ORDER));
        return contacts;
    }

    private void updateAddParticipantButtonState(
            TextInputLayout nameLayout,
            TextInputLayout phoneLayout,
            TextInputEditText nameInput,
            TextInputEditText phoneInput,
            MaterialButton addParticipantButton
    ) {
        String name = getText(nameInput);
        String phoneNumber = getText(phoneInput);
        boolean phoneNumberValid = isValidPhoneNumber(phoneNumber);

        addParticipantButton.setEnabled(!name.isEmpty() && phoneNumberValid);
        nameLayout.setError(null);
        if (phoneNumber.isEmpty() || phoneNumberValid) {
            phoneLayout.setError(null);
        } else {
            phoneLayout.setError(getString(R.string.contact_phone_invalid));
        }
    }

    @NonNull
    private String getText(@NonNull TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    @NonNull
    private String normalizeWhitespace(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private String normalizePhoneNumber(@Nullable String phoneNumber) {
        return normalizeWhitespace(phoneNumber).replaceAll("[^+\\d]", "");
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
        }
    }

    private final class PhoneContactsAdapter extends android.widget.ArrayAdapter<PhoneContact> {
        PhoneContactsAdapter(ArrayList<PhoneContact> contacts) {
            super(requireContext(), R.layout.item_phone_contact, contacts);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = getLayoutInflater().inflate(R.layout.item_phone_contact, parent, false);
            }

            PhoneContact contact = getItem(position);
            TextView nameView = itemView.findViewById(R.id.text_phone_contact_name);
            TextView phoneView = itemView.findViewById(R.id.text_phone_contact_number);

            if (contact != null) {
                nameView.setText(contact.name);
                phoneView.setText(contact.phoneNumber);
            }

            return itemView;
        }
    }

    private static final class PhoneContact {
        private final String name;
        private final String phoneNumber;

        private PhoneContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
    }
}
