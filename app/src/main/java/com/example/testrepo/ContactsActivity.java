package com.example.testrepo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ContactsActivity extends AppCompatActivity {
    private static final String PREFERENCES_NAME = "contacts_preferences";
    private static final String PREFERENCE_CONTACTS = "contacts";
    private static final String CONTACT_SEPARATOR = "\u001F";

    private final ArrayList<Contact> contacts = new ArrayList<>();
    private ArrayAdapter<Contact> contactsAdapter;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        loadContacts();

        ListView contactsList = findViewById(R.id.list_contacts);
        contactsAdapter = new ContactsAdapter();
        contactsList.setAdapter(contactsAdapter);
        contactsList.setEmptyView(findViewById(R.id.text_no_contacts));

        MaterialButton backButton = findViewById(R.id.button_back);
        MaterialButton addButton = findViewById(R.id.button_add);

        backButton.setOnClickListener(view -> finish());
        addButton.setOnClickListener(view -> showAddContactDialog());

        sortContacts();
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.layout_contact_name);
        TextInputLayout phoneLayout = dialogView.findViewById(R.id.layout_contact_phone);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_contact_name);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.input_contact_phone);
        MaterialButton addNewContactButton = dialogView.findViewById(R.id.button_add_new_contact);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.contact_information_title)
                .setView(dialogView)
                .create();

        addNewContactButton.setOnClickListener(view -> {
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
            }
            if (hasError) {
                return;
            }

            contacts.add(new Contact(name, phoneNumber));
            sortContacts();
            saveContacts();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void loadContacts() {
        Set<String> savedContacts = preferences.getStringSet(PREFERENCE_CONTACTS, Collections.emptySet());
        contacts.clear();
        for (String savedContact : new HashSet<>(savedContacts)) {
            contacts.add(Contact.fromStorage(savedContact));
        }
    }

    private void saveContacts() {
        Set<String> storedContacts = new HashSet<>();
        for (Contact contact : contacts) {
            storedContacts.add(contact.toStorage());
        }
        preferences.edit()
                .putStringSet(PREFERENCE_CONTACTS, storedContacts)
                .apply();
    }

    private void removeContact(Contact contact) {
        contacts.remove(contact);
        saveContacts();
        contactsAdapter.notifyDataSetChanged();
    }

    private void sortContacts() {
        contacts.sort(Comparator
                .comparing((Contact contact) -> contact.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(contact -> contact.phoneNumber, String.CASE_INSENSITIVE_ORDER));
        contactsAdapter.notifyDataSetChanged();
    }

    private String getText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private final class ContactsAdapter extends ArrayAdapter<Contact> {
        ContactsAdapter() {
            super(ContactsActivity.this, R.layout.item_contact, contacts);
        }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_contact, parent, false);
            }

            Contact contact = getItem(position);
            TextView nameView = itemView.findViewById(R.id.text_contact_name);
            TextView phoneView = itemView.findViewById(R.id.text_contact_phone);
            MaterialButton removeButton = itemView.findViewById(R.id.button_remove_contact);

            if (contact != null) {
                nameView.setText(contact.name);
                phoneView.setText(contact.phoneNumber);
                removeButton.setOnClickListener(view -> removeContact(contact));
            }

            return itemView;
        }
    }

    private static final class Contact {
        private final String name;
        private final String phoneNumber;

        private Contact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }

        private String toStorage() {
            return name + CONTACT_SEPARATOR + phoneNumber;
        }

        private static Contact fromStorage(String rawValue) {
            int separatorIndex = rawValue.indexOf(CONTACT_SEPARATOR);
            if (separatorIndex < 0) {
                return new Contact(rawValue, "");
            }

            String storedName = rawValue.substring(0, separatorIndex);
            String storedPhone = rawValue.substring(separatorIndex + CONTACT_SEPARATOR.length());
            return new Contact(storedName, storedPhone);
        }
    }
}
