package com.example.testrepo;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

public class ManagePermissionsDialogFragment extends DialogFragment {
    private static final String TAG = "ManagePermissionsDialog";

    @Nullable
    private TextView contactsDescriptionView;
    @Nullable
    private TextView cameraDescriptionView;
    @Nullable
    private TextView smsDescriptionView;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                updatePermissionDescriptions();
            });

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new ManagePermissionsDialogFragment().show(fragmentManager, TAG);
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
        return inflater.inflate(R.layout.dialog_manage_permissions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View closeButton = view.findViewById(R.id.button_close_manage_permissions);
        View manageContactsButton = view.findViewById(R.id.button_manage_contacts_permission);
        View manageCameraButton = view.findViewById(R.id.button_manage_camera_permission);
        View manageSmsButton = view.findViewById(R.id.button_manage_sms_permission);
        contactsDescriptionView = view.findViewById(R.id.text_permissions_contacts_description);
        cameraDescriptionView = view.findViewById(R.id.text_permissions_camera_description);
        smsDescriptionView = view.findViewById(R.id.text_permissions_sms_description);

        closeButton.setOnClickListener(buttonView -> dismiss());
        manageContactsButton.setOnClickListener(
                buttonView -> managePermission(Manifest.permission.READ_CONTACTS)
        );
        manageCameraButton.setOnClickListener(
                buttonView -> managePermission(Manifest.permission.CAMERA)
        );
        manageSmsButton.setOnClickListener(
                buttonView -> managePermission(Manifest.permission.SEND_SMS)
        );
        updatePermissionDescriptions();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionDescriptions();
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

    private void managePermission(@NonNull String permission) {
        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED) {
            openAppSettings();
            return;
        }

        requestPermissionLauncher.launch(permission);
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null)
        );
        startActivity(intent);
    }

    private void updatePermissionDescriptions() {
        if (!isAdded()) {
            return;
        }

        if (contactsDescriptionView != null) {
            contactsDescriptionView.setText(
                    getString(
                            R.string.settings_permissions_contacts_description,
                            getPermissionAccessLabel(Manifest.permission.READ_CONTACTS)
                    )
            );
        }
        if (cameraDescriptionView != null) {
            cameraDescriptionView.setText(
                    getString(
                            R.string.settings_permissions_camera_description,
                            getPermissionAccessLabel(Manifest.permission.CAMERA)
                    )
            );
        }
        if (smsDescriptionView != null) {
            smsDescriptionView.setText(
                    getString(
                            R.string.settings_permissions_sms_description,
                            getPermissionAccessLabel(Manifest.permission.SEND_SMS)
                    )
            );
        }
    }

    @NonNull
    private String getPermissionAccessLabel(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED
                ? getString(R.string.permission_access_allowed)
                : getString(R.string.permission_access_not_allowed);
    }
}
