package com.example.testrepo;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

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
        View managePreAddedParticipantsButton =
                view.findViewById(R.id.button_manage_pre_added_participants);
        MaterialSwitch autoRotateSwitch = view.findViewById(R.id.switch_auto_rotate_image);
        MaterialSwitch splitItemsSwitch = view.findViewById(R.id.switch_split_items);
        closeButton.setOnClickListener(buttonView -> dismiss());
        managePreAddedParticipantsButton.setOnClickListener(buttonView ->
                PreAddedParticipantsDialogFragment.show(getParentFragmentManager())
        );
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
