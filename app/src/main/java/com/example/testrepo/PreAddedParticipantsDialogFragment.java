package com.example.testrepo;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.util.List;
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
        View closeButton = dialogView.findViewById(R.id.button_close_add_participant);
        MaterialButton addParticipantButton =
                dialogView.findViewById(R.id.button_add_participant_confirm);

        ArrayList<PhoneContactsListItem> phoneContactRows = new ArrayList<>();
        ArrayList<PhoneContact> allPhoneContacts = new ArrayList<>();
        boolean[] contactsLoading = new boolean[]{contactsPermissionGranted};
        PhoneContactsAdapter phoneContactsAdapter = new PhoneContactsAdapter(phoneContactRows);
        Runnable refreshSearchUi = () -> updateAddParticipantSearchUi(
                nameLayout,
                phoneLayout,
                nameInput,
                phoneContactsAdapter,
                allPhoneContacts,
                emptyContactsView,
                contactsPermissionGranted,
                contactsLoading[0]
        );
        phoneContactsAdapter.setOnFavoritesChanged(refreshSearchUi);
        phoneContactsAdapter.setOnContactClicked(selectedContact -> {
            nameInput.setText(selectedContact.name);
            phoneInput.setText(selectedContact.phoneNumber);
            nameLayout.setError(null);
            phoneLayout.setError(null);
            if (nameInput.isFocused()) {
                hideKeyboardAndClearFocus(nameInput, dialogView);
            }
            dialogView.post(refreshSearchUi);
        });
        phoneContactsList.setAdapter(phoneContactsAdapter);
        phoneContactsList.setEmptyView(emptyContactsView);
        addParticipantButton.setEnabled(false);

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
                updateAddParticipantSearchUi(
                        nameLayout,
                        phoneLayout,
                        nameInput,
                        phoneContactsAdapter,
                        allPhoneContacts,
                        emptyContactsView,
                        contactsPermissionGranted,
                        contactsLoading[0]
                );
            }
        };
        nameInput.addTextChangedListener(validationWatcher);
        phoneInput.addTextChangedListener(validationWatcher);
        configureAddParticipantKeyboardBehavior(
                dialogView,
                nameLayout,
                nameInput,
                phoneInput,
                refreshSearchUi
        );
        updateAddParticipantButtonState(
                nameLayout,
                phoneLayout,
                nameInput,
                phoneInput,
                addParticipantButton
        );

        Dialog dialog = new Dialog(requireContext(), R.style.TestRepo_FullScreenDialog);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        closeButton.setOnClickListener(view -> dialog.dismiss());

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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
        installAddParticipantKeyboardDismissWatcher(
                dialog,
                dialogView,
                nameInput,
                phoneInput,
                refreshSearchUi
        );

        if (!contactsPermissionGranted) {
            refreshSearchUi.run();
            return;
        }

        refreshSearchUi.run();
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

                allPhoneContacts.clear();
                allPhoneContacts.addAll(availableContacts);
                contactsLoading[0] = false;
                refreshSearchUi.run();
            });
        });
    }

    private void updateAddParticipantSearchUi(
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputLayout phoneLayout,
            @NonNull TextInputEditText nameInput,
            @NonNull PhoneContactsAdapter phoneContactsAdapter,
            @NonNull List<PhoneContact> allPhoneContacts,
            @NonNull TextView emptyContactsView,
            boolean contactsPermissionGranted,
            boolean contactsLoading
    ) {
        boolean isSearching = nameInput.isFocused();
        updateParticipantNameSearchIconVisibility(nameLayout, nameInput);
        phoneLayout.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        updateVisiblePhoneContacts(
                phoneContactsAdapter,
                allPhoneContacts,
                getText(nameInput),
                emptyContactsView,
                contactsPermissionGranted,
                contactsLoading,
                !isSearching
        );
    }

    private void updateVisiblePhoneContacts(
            @NonNull PhoneContactsAdapter phoneContactsAdapter,
            @NonNull List<PhoneContact> allPhoneContacts,
            @NonNull String query,
            @NonNull TextView emptyContactsView,
            boolean contactsPermissionGranted,
            boolean contactsLoading,
            boolean showSectionHeaders
    ) {
        if (!contactsPermissionGranted) {
            phoneContactsAdapter.clear();
            phoneContactsAdapter.notifyDataSetChanged();
            emptyContactsView.setText(R.string.phone_contacts_permission_required);
            return;
        }

        if (contactsLoading) {
            phoneContactsAdapter.clear();
            phoneContactsAdapter.notifyDataSetChanged();
            emptyContactsView.setText(R.string.loading_phone_contacts);
            return;
        }

        ArrayList<PhoneContact> filteredContacts = new ArrayList<>();
        String normalizedQuery = normalizeWhitespace(query).toLowerCase(Locale.US);
        for (PhoneContact contact : allPhoneContacts) {
            if (normalizedQuery.isEmpty()
                    || contact.name.toLowerCase(Locale.US).contains(normalizedQuery)) {
                filteredContacts.add(contact);
            }
        }

        phoneContactsAdapter.clear();
        phoneContactsAdapter.addAll(buildPhoneContactRows(filteredContacts, showSectionHeaders));
        phoneContactsAdapter.notifyDataSetChanged();

        if (allPhoneContacts.isEmpty()) {
            emptyContactsView.setText(R.string.no_phone_contacts);
        } else if (filteredContacts.isEmpty()) {
            emptyContactsView.setText(R.string.no_matching_phone_contacts);
        } else {
            emptyContactsView.setText("");
        }
    }

    @NonNull
    private ArrayList<PhoneContactsListItem> buildPhoneContactRows(
            @NonNull List<PhoneContact> contacts,
            boolean includeSections
    ) {
        ArrayList<PhoneContactsListItem> rows = new ArrayList<>();
        ArrayList<PhoneContact> favoriteContacts = new ArrayList<>();
        ArrayList<PhoneContact> remainingContacts = new ArrayList<>();
        for (PhoneContact contact : contacts) {
            if (AppSettings.isFavoritePhoneContact(
                    requireContext(),
                    contact.name,
                    contact.phoneNumber
            )) {
                favoriteContacts.add(contact);
            } else {
                remainingContacts.add(contact);
            }
        }

        if (!favoriteContacts.isEmpty()) {
            rows.add(PhoneContactsListItem.createSection(
                    getString(R.string.phone_contacts_favorites_title)
            ));
            for (PhoneContact contact : favoriteContacts) {
                rows.add(PhoneContactsListItem.createContact(contact));
            }
        }

        if (!includeSections) {
            for (PhoneContact contact : remainingContacts) {
                rows.add(PhoneContactsListItem.createContact(contact));
            }
            return rows;
        }

        String previousSectionLabel = "";
        for (PhoneContact contact : remainingContacts) {
            String sectionLabel = getPhoneContactSectionLabel(contact.name);
            if (!sectionLabel.equals(previousSectionLabel)) {
                rows.add(PhoneContactsListItem.createSection(sectionLabel));
                previousSectionLabel = sectionLabel;
            }
            rows.add(PhoneContactsListItem.createContact(contact));
        }
        return rows;
    }

    @NonNull
    private String getPhoneContactSectionLabel(@Nullable String contactName) {
        String normalizedName = normalizeWhitespace(contactName);
        if (normalizedName.isEmpty()) {
            return "#";
        }

        String firstCharacter = normalizedName.substring(0, 1).toUpperCase(Locale.getDefault());
        char firstChar = firstCharacter.charAt(0);
        return Character.isLetter(firstChar) ? firstCharacter : "#";
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

    private final class PhoneContactsAdapter
            extends android.widget.ArrayAdapter<PhoneContactsListItem> {
        private static final int VIEW_TYPE_SECTION = 0;
        private static final int VIEW_TYPE_CONTACT = 1;
        @Nullable
        private Runnable onFavoritesChanged;
        @Nullable
        private OnPhoneContactClickListener onContactClicked;

        PhoneContactsAdapter(ArrayList<PhoneContactsListItem> contacts) {
            super(requireContext(), R.layout.item_phone_contact, contacts);
        }

        void setOnFavoritesChanged(@Nullable Runnable onFavoritesChanged) {
            this.onFavoritesChanged = onFavoritesChanged;
        }

        void setOnContactClicked(@Nullable OnPhoneContactClickListener onContactClicked) {
            this.onContactClicked = onContactClicked;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            PhoneContactsListItem item = getItem(position);
            return item != null && item.isSection() ? VIEW_TYPE_SECTION : VIEW_TYPE_CONTACT;
        }

        @Override
        public boolean isEnabled(int position) {
            PhoneContactsListItem item = getItem(position);
            return item != null && !item.isSection();
        }

        @Nullable
        private PhoneContact getContact(int position) {
            PhoneContactsListItem item = getItem(position);
            return item == null ? null : item.contact;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            PhoneContactsListItem item = getItem(position);
            if (item != null && item.isSection()) {
                View sectionView = convertView;
                if (sectionView == null || getItemViewType(position) != VIEW_TYPE_SECTION) {
                    sectionView = getLayoutInflater().inflate(
                            R.layout.item_phone_contact_section,
                            parent,
                            false
                    );
                }

                TextView sectionLabelView =
                        sectionView.findViewById(R.id.text_phone_contact_section);
                sectionLabelView.setText(item.sectionLabel);
                return sectionView;
            }

            View itemView = convertView;
            if (itemView == null || getItemViewType(position) != VIEW_TYPE_CONTACT) {
                itemView = getLayoutInflater().inflate(R.layout.item_phone_contact, parent, false);
            }

            PhoneContact contact = item == null ? null : item.contact;
            MaterialButton badgeButton = itemView.findViewById(R.id.button_phone_contact_badge);
            TextView nameView = itemView.findViewById(R.id.text_phone_contact_name);
            TextView phoneView = itemView.findViewById(R.id.text_phone_contact_number);
            AppCompatImageButton favoriteButton =
                    itemView.findViewById(R.id.button_phone_contact_favorite);

            if (contact != null) {
                configurePhoneContactBadgeButton(badgeButton, contact);
                nameView.setText(contact.name);
                phoneView.setText(contact.phoneNumber);
                configurePhoneContactFavoriteButton(favoriteButton, contact);
                itemView.setOnClickListener(view -> {
                    if (onContactClicked != null) {
                        onContactClicked.onPhoneContactClicked(contact);
                    }
                });
                favoriteButton.setOnClickListener(view -> {
                    toggleFavoritePhoneContact(contact);
                    if (onFavoritesChanged != null) {
                        onFavoritesChanged.run();
                    } else {
                        notifyDataSetChanged();
                    }
                });
            }

            return itemView;
        }
    }

    private void configurePhoneContactBadgeButton(
            @NonNull MaterialButton badgeButton,
            @NonNull PhoneContact contact
    ) {
        int buttonSize = dpToPx(52);
        ViewGroup.LayoutParams layoutParams = badgeButton.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.width = buttonSize;
            layoutParams.height = buttonSize;
            badgeButton.setLayoutParams(layoutParams);
        }
        int badgeColor = createStablePhoneContactColor(contact);
        badgeButton.setText(getParticipantInitials(contact.name));
        badgeButton.setAllCaps(false);
        badgeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        badgeButton.setCheckable(false);
        badgeButton.setClickable(false);
        badgeButton.setFocusable(false);
        badgeButton.setInsetTop(0);
        badgeButton.setInsetBottom(0);
        badgeButton.setMinWidth(0);
        badgeButton.setMinHeight(0);
        badgeButton.setMinimumWidth(0);
        badgeButton.setMinimumHeight(0);
        badgeButton.setPadding(0, 0, 0, 0);
        badgeButton.setCornerRadius(buttonSize / 2);
        badgeButton.setStrokeWidth(0);
        badgeButton.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        badgeButton.setTextColor(getParticipantTextColor(badgeColor));
        badgeButton.setContentDescription(contact.name);
    }

    private int createStablePhoneContactColor(@NonNull PhoneContact contact) {
        String contactKey = normalizeWhitespace(contact.name).toLowerCase(Locale.US)
                + "\u001F"
                + normalizePhoneNumber(contact.phoneNumber);
        int stableIndex = (contactKey.hashCode() & 0x7fffffff) % 1024;
        return createParticipantColor(stableIndex);
    }

    private void configurePhoneContactFavoriteButton(
            @NonNull AppCompatImageButton favoriteButton,
            @NonNull PhoneContact contact
    ) {
        boolean isFavorite = AppSettings.isFavoritePhoneContact(
                requireContext(),
                contact.name,
                contact.phoneNumber
        );
        favoriteButton.setImageResource(isFavorite ? R.drawable.star_true : R.drawable.star_false);
        favoriteButton.setContentDescription(
                getString(
                        isFavorite
                                ? R.string.remove_phone_contact_favorite
                                : R.string.add_phone_contact_favorite
                )
        );
    }

    private void toggleFavoritePhoneContact(@NonNull PhoneContact contact) {
        boolean isFavorite = AppSettings.isFavoritePhoneContact(
                requireContext(),
                contact.name,
                contact.phoneNumber
        );
        AppSettings.setFavoritePhoneContact(
                requireContext(),
                contact.name,
                contact.phoneNumber,
                !isFavorite
        );
    }

    private void configureAddParticipantKeyboardBehavior(
            @NonNull View dialogView,
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputEditText nameInput,
            @NonNull TextInputEditText phoneInput,
            @NonNull Runnable updateSearchUi
    ) {
        updateParticipantNameSearchIconVisibility(nameLayout, nameInput);
        View.OnFocusChangeListener focusChangeListener = (view, hasFocus) -> {
            updateSearchUi.run();
            if (hasFocus) {
                return;
            }

            dialogView.post(() -> {
                updateSearchUi.run();
                if (!nameInput.isFocused() && !phoneInput.isFocused()) {
                    hideKeyboardAndClearFocus((TextInputEditText) view, dialogView);
                }
            });
        };

        nameInput.setOnFocusChangeListener(focusChangeListener);
        phoneInput.setOnFocusChangeListener(focusChangeListener);

        TextView.OnEditorActionListener editorActionListener = (textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                hideKeyboardAndClearFocus((TextInputEditText) textView, dialogView);
                dialogView.post(updateSearchUi);
                return true;
            }
            return false;
        };

        nameInput.setOnEditorActionListener(editorActionListener);
        phoneInput.setOnEditorActionListener(editorActionListener);
    }

    private void updateParticipantNameSearchIconVisibility(
            @NonNull TextInputLayout nameLayout,
            @NonNull TextInputEditText nameInput
    ) {
        nameLayout.setEndIconVisible(!nameInput.isFocused());
    }

    private void installAddParticipantKeyboardDismissWatcher(
            @NonNull Dialog dialog,
            @NonNull View dialogView,
            @NonNull TextInputEditText nameInput,
            @NonNull TextInputEditText phoneInput,
            @NonNull Runnable updateSearchUi
    ) {
        Rect visibleFrame = new Rect();
        boolean[] wasKeyboardVisible = {false};
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
            dialogView.getWindowVisibleDisplayFrame(visibleFrame);
            int rootHeight = dialogView.getRootView().getHeight();
            int keyboardHeight = Math.max(0, rootHeight - visibleFrame.height());
            boolean isKeyboardVisible = keyboardHeight > dpToPx(120);

            if (wasKeyboardVisible[0] && !isKeyboardVisible) {
                TextInputEditText focusedInput = nameInput.isFocused()
                        ? nameInput
                        : phoneInput.isFocused() ? phoneInput : null;
                if (focusedInput != null) {
                    hideKeyboardAndClearFocus(focusedInput, dialogView);
                    updateSearchUi.run();
                }
            }

            wasKeyboardVisible[0] = isKeyboardVisible;
        };

        dialogView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        dialog.setOnDismissListener(dismissedDialog -> {
            ViewTreeObserver viewTreeObserver = dialogView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(layoutListener);
            }
        });
    }

    private void hideKeyboardAndClearFocus(
            @NonNull TextInputEditText inputView,
            @NonNull View fallbackView
    ) {
        Context context = getContext();
        if (context == null) {
            inputView.clearFocus();
            return;
        }

        fallbackView.requestFocus();
        InputMethodManager inputMethodManager =
                ContextCompat.getSystemService(context, InputMethodManager.class);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
        }
        inputView.clearFocus();
    }

    private static final class PhoneContact {
        private final String name;
        private final String phoneNumber;

        private PhoneContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
    }

    private static final class PhoneContactsListItem {
        @Nullable
        private final String sectionLabel;
        @Nullable
        private final PhoneContact contact;

        private PhoneContactsListItem(@Nullable String sectionLabel, @Nullable PhoneContact contact) {
            this.sectionLabel = sectionLabel;
            this.contact = contact;
        }

        @NonNull
        private static PhoneContactsListItem createSection(@NonNull String sectionLabel) {
            return new PhoneContactsListItem(sectionLabel, null);
        }

        @NonNull
        private static PhoneContactsListItem createContact(@NonNull PhoneContact contact) {
            return new PhoneContactsListItem(null, contact);
        }

        private boolean isSection() {
            return sectionLabel != null;
        }
    }

    private interface OnPhoneContactClickListener {
        void onPhoneContactClicked(@NonNull PhoneContact contact);
    }
}
