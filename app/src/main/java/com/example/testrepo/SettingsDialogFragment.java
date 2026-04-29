package com.example.testrepo;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsDialogFragment extends DialogFragment {
    private static final String TAG = "SettingsDialog";
    private static final String ACCENT_COLOR_BLUE = "blue";
    private static final String ACCENT_COLOR_GREEN = "green";
    private static final long MENU_ARROW_ROTATION_DURATION_MS = 180L;

    @Nullable
    private View appearanceRowView;
    @Nullable
    private AppCompatImageButton appearanceMenuButton;
    @Nullable
    private TextView appearanceSummaryValueView;
    @Nullable
    private PopupWindow appearancePopupWindow;
    @Nullable
    private View accentColorRowView;
    @Nullable
    private AppCompatImageButton accentColorMenuButton;
    @Nullable
    private AppCompatImageView accentColorSummarySwatchView;
    @Nullable
    private TextView accentColorSummaryValueView;
    @Nullable
    private PopupWindow accentColorPopupWindow;

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }

        new SettingsDialogFragment().show(fragmentManager, TAG);
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
        appearanceRowView = view.findViewById(R.id.layout_settings_appearance_row);
        appearanceMenuButton = view.findViewById(R.id.button_settings_appearance_menu);
        appearanceSummaryValueView = view.findViewById(R.id.text_settings_appearance_value);
        accentColorRowView = view.findViewById(R.id.layout_settings_accent_color_row);
        accentColorMenuButton = view.findViewById(R.id.button_settings_accent_color_menu);
        accentColorSummarySwatchView = view.findViewById(R.id.image_settings_accent_color_swatch);
        accentColorSummaryValueView = view.findViewById(R.id.text_settings_accent_color_value);
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
        if (appearanceMenuButton != null) {
            appearanceMenuButton.setOnClickListener(buttonView -> toggleAppearanceMenu());
        }
        if (accentColorMenuButton != null) {
            accentColorMenuButton.setOnClickListener(buttonView -> toggleAccentColorMenu());
        }

        updateUsernameDescription(usernameDescriptionView);
        updateAppearanceSummary();
        updateAccentColorSummary();

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

    private void toggleAppearanceMenu() {
        if (appearancePopupWindow != null && appearancePopupWindow.isShowing()) {
            dismissAppearanceMenu();
        } else {
            showAppearanceMenu();
        }
    }

    private void showAppearanceMenu() {
        if (appearanceRowView == null || appearanceMenuButton == null) {
            return;
        }

        View popupView = LayoutInflater.from(requireContext()).inflate(
                R.layout.popup_settings_appearance_menu,
                null
        );
        View lightRow = popupView.findViewById(R.id.row_appearance_light);
        View darkRow = popupView.findViewById(R.id.row_appearance_dark);
        TextView lightTextView = popupView.findViewById(R.id.text_appearance_light);
        TextView darkTextView = popupView.findViewById(R.id.text_appearance_dark);

        boolean darkSelected = AppSettings.isDarkThemeEnabled(requireContext());
        lightTextView.setAlpha(darkSelected ? 0.82f : 1f);
        darkTextView.setAlpha(darkSelected ? 1f : 0.82f);

        lightRow.setOnClickListener(view -> applyAppearanceSelection(false));
        darkRow.setOnClickListener(view -> applyAppearanceSelection(true));

        appearancePopupWindow = showAnchoredPopup(
                popupView,
                appearanceRowView,
                () -> {
                    if (appearancePopupWindow != null) {
                        appearancePopupWindow = null;
                    }
                    setMenuExpanded(appearanceMenuButton, false);
                }
        );
        setMenuExpanded(appearanceMenuButton, true);
    }

    private void dismissAppearanceMenu() {
        dismissPopupWindow(appearancePopupWindow);
        appearancePopupWindow = null;
        setMenuExpanded(appearanceMenuButton, false);
    }

    private void applyAppearanceSelection(boolean darkThemeEnabled) {
        if (AppSettings.isDarkThemeEnabled(requireContext()) == darkThemeEnabled) {
            dismissAppearanceMenu();
            return;
        }

        AppSettings.setDarkThemeEnabled(requireContext(), darkThemeEnabled);
        updateAppearanceSummary();
        dismissAppearanceMenu();
        requireActivity().recreate();
    }

    private void updateAppearanceSummary() {
        if (appearanceSummaryValueView == null) {
            return;
        }

        appearanceSummaryValueView.setText(
                AppSettings.isDarkThemeEnabled(requireContext())
                        ? R.string.settings_appearance_dark
                        : R.string.settings_appearance_light_default
        );
    }

    private void toggleAccentColorMenu() {
        if (accentColorPopupWindow != null && accentColorPopupWindow.isShowing()) {
            dismissAccentColorMenu();
        } else {
            showAccentColorMenu();
        }
    }

    private void showAccentColorMenu() {
        if (accentColorRowView == null || accentColorMenuButton == null) {
            return;
        }

        View popupView = LayoutInflater.from(requireContext()).inflate(
                R.layout.popup_settings_accent_color_menu,
                null
        );
        View blueRow = popupView.findViewById(R.id.row_accent_color_blue);
        View greenRow = popupView.findViewById(R.id.row_accent_color_green);
        TextView blueTextView = popupView.findViewById(R.id.text_accent_color_blue);
        TextView greenTextView = popupView.findViewById(R.id.text_accent_color_green);

        boolean blueSelected = AppSettings.isBlueAccentColor(requireContext());
        blueTextView.setAlpha(blueSelected ? 1f : 0.82f);
        greenTextView.setAlpha(blueSelected ? 0.82f : 1f);

        blueRow.setOnClickListener(view -> applyAccentColorSelection(ACCENT_COLOR_BLUE));
        greenRow.setOnClickListener(view -> applyAccentColorSelection(ACCENT_COLOR_GREEN));

        accentColorPopupWindow = showAnchoredPopup(
                popupView,
                accentColorRowView,
                () -> {
                    if (accentColorPopupWindow != null) {
                        accentColorPopupWindow = null;
                    }
                    setMenuExpanded(accentColorMenuButton, false);
                }
        );
        setMenuExpanded(accentColorMenuButton, true);
    }

    private void dismissAccentColorMenu() {
        dismissPopupWindow(accentColorPopupWindow);
        accentColorPopupWindow = null;
        setMenuExpanded(accentColorMenuButton, false);
    }

    private void applyAccentColorSelection(@NonNull String accentColor) {
        if (accentColor.equals(AppSettings.getAccentColor(requireContext()))) {
            dismissAccentColorMenu();
            return;
        }

        AppSettings.setAccentColor(requireContext(), accentColor);
        updateAccentColorSummary();
        dismissAccentColorMenu();
        requireActivity().recreate();
    }

    private void updateAccentColorSummary() {
        if (accentColorSummarySwatchView == null || accentColorSummaryValueView == null) {
            return;
        }

        String accentColor = AppSettings.getAccentColor(requireContext());
        accentColorSummarySwatchView.setImageResource(getAccentColorSwatchResId(accentColor));
        accentColorSummaryValueView.setText(getAccentColorLabelResId(accentColor));
    }

    @DrawableRes
    private int getAccentColorSwatchResId(@NonNull String accentColor) {
        if (ACCENT_COLOR_GREEN.equals(accentColor)) {
            return R.drawable.accent_color_swatch_green;
        }
        return R.drawable.accent_color_swatch_blue;
    }

    private int getAccentColorLabelResId(@NonNull String accentColor) {
        if (ACCENT_COLOR_GREEN.equals(accentColor)) {
            return R.string.settings_accent_color_green;
        }
        return R.string.settings_accent_color_blue_default;
    }

    @Nullable
    private PopupWindow showAnchoredPopup(
            @NonNull View popupView,
            @NonNull View anchorView,
            @NonNull Runnable onDismiss
    ) {
        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dpToPx(10));
        popupWindow.setOnDismissListener(onDismiss::run);

        int popupWidth = popupView.getMeasuredWidth();
        int xOffset = Math.max(0, anchorView.getWidth() - popupWidth);
        popupWindow.showAsDropDown(anchorView, xOffset, dpToPx(8));
        return popupWindow;
    }

    private void dismissPopupWindow(@Nullable PopupWindow popupWindow) {
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }

    private void setMenuExpanded(@Nullable AppCompatImageButton button, boolean expanded) {
        if (button == null) {
            return;
        }

        button.animate()
                .rotation(expanded ? 180f : 0f)
                .setDuration(MENU_ARROW_ROTATION_DURATION_MS)
                .start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        dismissAppearanceMenu();
        dismissAccentColorMenu();
        appearanceRowView = null;
        appearanceMenuButton = null;
        appearanceSummaryValueView = null;
        accentColorRowView = null;
        accentColorMenuButton = null;
        accentColorSummarySwatchView = null;
        accentColorSummaryValueView = null;
        super.onDestroyView();
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
