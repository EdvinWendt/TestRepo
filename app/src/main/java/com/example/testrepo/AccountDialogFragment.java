package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

public class AccountDialogFragment extends DialogFragment {
    private static final String TAG = "AccountDialog";

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new AccountDialogFragment().show(fragmentManager, TAG);
    }

    @Override
    public int getTheme() {
        if (getContext() == null) {
            return R.style.TestRepo_FullScreenDialog;
        }
        return AppSettings.getFullScreenDialogThemeResId(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.dialog_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View closeButton = view.findViewById(R.id.button_close_account);
        View signOutButton = view.findViewById(R.id.button_account_sign_out);
        View managePhoneButton = view.findViewById(R.id.button_manage_account_phone);
        View manageUsernameButton = view.findViewById(R.id.button_manage_account_username);
        TextView emailView = view.findViewById(R.id.text_account_email_description);
        TextView phoneView = view.findViewById(R.id.text_account_phone_description);
        TextView usernameView = view.findViewById(R.id.text_account_username_description);
        emailView.setText(AppSettings.getLoginEmail(requireContext()));
        getParentFragmentManager().setFragmentResultListener(
                EditPhoneNumberDialogFragment.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> updatePhoneDescription(phoneView)
        );
        getParentFragmentManager().setFragmentResultListener(
                EditUsernameDialogFragment.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> updateUsernameDescription(usernameView)
        );
        closeButton.setOnClickListener(buttonView -> dismiss());
        signOutButton.setOnClickListener(buttonView -> signOut());
        managePhoneButton.setOnClickListener(
                buttonView -> EditPhoneNumberDialogFragment.show(getParentFragmentManager())
        );
        manageUsernameButton.setOnClickListener(
                buttonView -> EditUsernameDialogFragment.show(getParentFragmentManager(), false)
        );
        updatePhoneDescription(phoneView);
        updateUsernameDescription(usernameView);
    }

    private void updatePhoneDescription(@NonNull TextView phoneView) {
        String phoneNumber = AppSettings.getLoginPhoneNumber(requireContext());
        phoneView.setText(
                phoneNumber.isEmpty()
                        ? getString(R.string.participant_phone_unavailable)
                        : phoneNumber
        );
    }

    private void updateUsernameDescription(@NonNull TextView usernameView) {
        usernameView.setText(
                getString(
                        R.string.settings_username_description,
                        AppSettings.getUsernameNickname(requireContext())
                )
        );
    }

    private void signOut() {
        AppSettings.setLoginCompleted(requireContext(), false);
        AppSettings.setLoginEmail(requireContext(), "");
        AppSettings.setLoginPhoneNumber(requireContext(), "");

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        requireActivity().finish();
        dismissAllowingStateLoss();
    }
}
