package com.example.testrepo;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PermissionOnboardingDialogFragment extends DialogFragment {
    private static final String TAG = "PermissionOnboardingDialog";

    private final String[] permissionSequence = new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS
    };
    private int nextPermissionIndex;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted ->
                    requestNextPermission()
            );

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new PermissionOnboardingDialogFragment().show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setCancelable(false);

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_permission_onboarding, null);
        MaterialButton skipButton =
                dialogView.findViewById(R.id.button_permission_onboarding_skip);
        MaterialButton grantButton =
                dialogView.findViewById(R.id.button_permission_onboarding_grant);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(dialogInterface -> {
            skipButton.setOnClickListener(view -> {
                AppSettings.setStartupPermissionPromptShown(requireContext(), true);
                dismiss();
            });
            grantButton.setOnClickListener(view -> {
                AppSettings.setStartupPermissionPromptShown(requireContext(), true);
                skipButton.setEnabled(false);
                grantButton.setEnabled(false);
                nextPermissionIndex = 0;
                requestNextPermission();
            });
        });
        return dialog;
    }

    private void requestNextPermission() {
        if (!isAdded()) {
            return;
        }

        while (nextPermissionIndex < permissionSequence.length) {
            String permission = permissionSequence[nextPermissionIndex++];
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    == PackageManager.PERMISSION_GRANTED) {
                continue;
            }

            requestPermissionLauncher.launch(permission);
            return;
        }

        dismissAllowingStateLoss();
    }
}
