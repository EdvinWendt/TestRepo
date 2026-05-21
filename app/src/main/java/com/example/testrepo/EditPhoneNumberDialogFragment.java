package com.example.testrepo;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class EditPhoneNumberDialogFragment extends DialogFragment {
    public static final String REQUEST_KEY = "edit_phone_number_dialog_result";
    private static final String TAG = "EditPhoneNumberDialog";

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new EditPhoneNumberDialogFragment().show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_phone_number, null);
        TextInputLayout phoneInputLayout = dialogView.findViewById(R.id.input_layout_edit_phone_number);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.input_edit_phone_number);
        TextInputEditText phoneInputEditText = phoneInput;
        MaterialButton applyButton = dialogView.findViewById(R.id.button_edit_phone_number_confirm);

        phoneInput.setText(AppSettings.getLoginPhoneNumber(requireContext()));
        phoneInput.setSelection(phoneInput.getText() != null
                ? phoneInput.getText().length()
                : 0);
        applyButton.setEnabled(isValidPhoneNumber(phoneInput.getText()));
        phoneInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                phoneInputLayout.setError(null);
                boolean valid = isValidPhoneNumber(s);
                applyButton.setEnabled(valid);
                if (s != null && s.length() > 0 && !valid) {
                    phoneInputLayout.setError(getString(R.string.contact_phone_invalid));
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();
        dialog.setCanceledOnTouchOutside(true);

        applyButton.setOnClickListener(buttonView -> {
            Editable phoneEditable = phoneInput.getText();
            if (!isValidPhoneNumber(phoneEditable)) {
                phoneInputLayout.setError(getString(R.string.contact_phone_invalid));
                return;
            }

            String phoneNumber = phoneEditable == null ? "" : phoneEditable.toString();
            applyButton.setEnabled(false);
            SupabaseAuthService.updateProfile(
                    requireContext(),
                    AppSettings.getLoginAccessToken(requireContext()),
                    AppSettings.getUsernameNickname(requireContext()),
                    phoneNumber,
                    new SupabaseAuthService.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            if (!isAdded()) {
                                return;
                            }

                            AppSettings.setLoginPhoneNumber(requireContext(), phoneNumber);
                            getParentFragmentManager().setFragmentResult(REQUEST_KEY, Bundle.EMPTY);
                            dismiss();
                            Toast.makeText(
                                    requireContext(),
                                    R.string.phone_number_changed,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            if (!isAdded()) {
                                return;
                            }

                            applyButton.setEnabled(isValidPhoneNumber(phoneInputEditText.getText()));
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        return dialog;
    }

    private boolean isValidPhoneNumber(@Nullable Editable editable) {
        return editable != null && AppSettings.isValidPhoneNumber(editable.toString());
    }
}
