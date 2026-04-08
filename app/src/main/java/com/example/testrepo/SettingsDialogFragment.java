package com.example.testrepo;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsDialogFragment extends DialogFragment {
    private static final String TAG = "SettingsDialog";

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new SettingsDialogFragment().show(fragmentManager, TAG);
    }

    @Override
    public int getTheme() {
        return R.style.TestRepo_FullScreenDialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.dialog_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View closeButton = view.findViewById(R.id.button_close_settings);
        View managePermissionsButton = view.findViewById(R.id.button_manage_permissions);
        View managePreAddedParticipantsButton =
                view.findViewById(R.id.button_manage_pre_added_participants);
        View editUsernameButton = view.findViewById(R.id.button_edit_username_nickname);
        TextView usernameDescriptionView =
                view.findViewById(R.id.text_settings_username_description);
        MaterialSwitch autoRotateSwitch = view.findViewById(R.id.switch_auto_rotate_image);
        MaterialSwitch splitItemsSwitch = view.findViewById(R.id.switch_split_items);
        getParentFragmentManager().setFragmentResultListener(
                EditUsernameDialogFragment.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> updateUsernameDescription(usernameDescriptionView)
        );
        closeButton.setOnClickListener(buttonView -> dismiss());
        managePermissionsButton.setOnClickListener(buttonView ->
                ManagePermissionsDialogFragment.show(getParentFragmentManager())
        );
        managePreAddedParticipantsButton.setOnClickListener(buttonView ->
                PreAddedParticipantsDialogFragment.show(getParentFragmentManager())
        );
        editUsernameButton.setOnClickListener(
                buttonView -> EditUsernameDialogFragment.show(
                        getParentFragmentManager(),
                        false
                )
        );
        updateUsernameDescription(usernameDescriptionView);
        autoRotateSwitch.setChecked(AppSettings.isAutoRotateImageEnabled(requireContext()));
        autoRotateSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        AppSettings.setAutoRotateImageEnabled(requireContext(), isChecked)
        );
        splitItemsSwitch.setChecked(AppSettings.isSplitItemsEnabled(requireContext()));
        splitItemsSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        AppSettings.setSplitItemsEnabled(requireContext(), isChecked)
        );
    }

    private void updateUsernameDescription(@NonNull TextView usernameDescriptionView) {
        usernameDescriptionView.setText(
                getString(
                        R.string.settings_username_description,
                        AppSettings.getUsernameNickname(requireContext())
                )
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }
}
