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

public class EditUsernameDialogFragment extends DialogFragment {
    public static final String REQUEST_KEY = "edit_username_dialog_result";
    private static final String TAG = "EditUsernameDialog";
    private static final String ARG_REQUIRE_USERNAME = "require_username";

    public static void show(
            @NonNull FragmentManager fragmentManager,
            boolean requireUsername
    ) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        EditUsernameDialogFragment dialogFragment = new EditUsernameDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_REQUIRE_USERNAME, requireUsername);
        dialogFragment.setArguments(arguments);
        dialogFragment.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        boolean requireUsername = isUsernameRequired();
        setCancelable(!requireUsername);

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_username, null);
        TextInputEditText usernameInput = dialogView.findViewById(R.id.input_edit_username);
        MaterialButton applyButton = dialogView.findViewById(R.id.button_edit_username_confirm);

        usernameInput.setText(AppSettings.getUsernameNickname(requireContext()));
        usernameInput.setSelection(usernameInput.getText() != null
                ? usernameInput.getText().length()
                : 0);
        applyButton.setEnabled(hasUsernameText(usernameInput.getText()));
        usernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyButton.setEnabled(hasUsernameText(s));
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();
        dialog.setCanceledOnTouchOutside(!requireUsername);

        applyButton.setOnClickListener(buttonView -> {
            AppSettings.setUsernameNickname(
                    requireContext(),
                    usernameInput.getText() == null ? "" : usernameInput.getText().toString()
            );
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, Bundle.EMPTY);
            dismiss();
            Toast.makeText(requireContext(), R.string.username_changed, Toast.LENGTH_SHORT).show();
        });

        return dialog;
    }

    private boolean isUsernameRequired() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(ARG_REQUIRE_USERNAME, false);
    }

    private boolean hasUsernameText(@Nullable Editable editable) {
        return editable != null && editable.toString().trim().length() > 0;
    }
}
