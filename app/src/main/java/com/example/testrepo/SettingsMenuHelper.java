package com.example.testrepo;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

final class SettingsMenuHelper {
    private SettingsMenuHelper() {
    }

    static void showSettingsMenu(@NonNull AppCompatActivity activity, @NonNull View anchorView) {
        PopupMenu popupMenu = new PopupMenu(activity, anchorView);
        popupMenu.inflate(R.menu.menu_settings_actions);
        popupMenu.setForceShowIcon(true);
        tintPopupMenuIcons(activity, popupMenu);
        popupMenu.setOnMenuItemClickListener(menuItem ->
                handleMenuItemClick(activity, menuItem)
        );
        popupMenu.show();
    }

    private static boolean handleMenuItemClick(
            @NonNull AppCompatActivity activity,
            @NonNull MenuItem menuItem
    ) {
        if (menuItem.getItemId() == R.id.action_settings) {
            SettingsDialogFragment.show(activity.getSupportFragmentManager());
            return true;
        }
        return false;
    }

    private static void tintPopupMenuIcons(
            @NonNull AppCompatActivity activity,
            @NonNull PopupMenu popupMenu
    ) {
        ColorStateList iconTint = resolvePopupMenuIconTint(activity);
        for (int index = 0; index < popupMenu.getMenu().size(); index++) {
            Drawable icon = popupMenu.getMenu().getItem(index).getIcon();
            if (icon == null) {
                continue;
            }

            Drawable tintedIcon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTintList(tintedIcon, iconTint);
            popupMenu.getMenu().getItem(index).setIcon(tintedIcon);
        }
    }

    @NonNull
    private static ColorStateList resolvePopupMenuIconTint(@NonNull AppCompatActivity activity) {
        TypedValue typedValue = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ColorStateList.valueOf(
                        ContextCompat.getColor(activity, typedValue.resourceId)
                );
            }
            return ColorStateList.valueOf(typedValue.data);
        }
        return ColorStateList.valueOf(0xFFFFFFFF);
    }
}
