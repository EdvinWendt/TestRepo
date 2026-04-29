package com.example.testrepo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public final class AnchoredDropdownMenuHelper {
    private AnchoredDropdownMenuHelper() {
    }

    public static void showSingleActionMenu(
            @NonNull View parentView,
            float rawTouchX,
            float rawTouchY,
            @StringRes int labelResId,
            @DrawableRes int iconResId,
            @NonNull Runnable onAction
    ) {
        showActionMenu(
                parentView,
                rawTouchX,
                rawTouchY,
                java.util.Collections.singletonList(new ActionItem(labelResId, iconResId, onAction))
        );
    }

    public static void showActionMenu(
            @NonNull View parentView,
            float rawTouchX,
            float rawTouchY,
            @NonNull List<ActionItem> actions
    ) {
        if (actions.isEmpty()) {
            return;
        }

        Context context = parentView.getContext();
        View contentView = LayoutInflater.from(context)
                .inflate(R.layout.dropdown_single_action_menu, null, false);
        LinearLayout actionsLayout = contentView.findViewById(R.id.layout_dropdown_actions);

        PopupWindow popupWindow = new PopupWindow(
                contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.setElevation(dpToPx(context, 12));
        }
        LayoutInflater inflater = LayoutInflater.from(context);
        for (ActionItem action : actions) {
            View actionView = inflater.inflate(R.layout.item_dropdown_action, actionsLayout, false);
            MaterialButton actionButton = actionView.findViewById(R.id.button_dropdown_action);
            actionButton.setText(action.labelResId);
            actionButton.setIconResource(action.iconResId);
            actionButton.setEnabled(action.enabled);
            actionButton.setAlpha(action.enabled ? 1f : 0.38f);
            if (action.enabled) {
                actionButton.setOnClickListener(view -> {
                    popupWindow.dismiss();
                    action.onAction.run();
                });
            } else {
                actionButton.setOnClickListener(null);
            }
            actionsLayout.addView(actionView);
        }

        Rect visibleFrame = new Rect();
        parentView.getWindowVisibleDisplayFrame(visibleFrame);
        int widthSpec = MeasureSpec.makeMeasureSpec(
                Math.max(visibleFrame.width(), 0),
                MeasureSpec.AT_MOST
        );
        int heightSpec = MeasureSpec.makeMeasureSpec(
                Math.max(visibleFrame.height(), 0),
                MeasureSpec.AT_MOST
        );
        contentView.measure(widthSpec, heightSpec);

        int popupWidth = contentView.getMeasuredWidth();
        int popupHeight = contentView.getMeasuredHeight();
        int clampedX = clamp(
                Math.round(rawTouchX),
                visibleFrame.left,
                Math.max(visibleFrame.left, visibleFrame.right - popupWidth)
        );
        int clampedY = clamp(
                Math.round(rawTouchY),
                visibleFrame.top,
                Math.max(visibleFrame.top, visibleFrame.bottom - popupHeight)
        );

        popupWindow.showAtLocation(
                parentView.getRootView(),
                Gravity.TOP | Gravity.START,
                clampedX,
                clampedY
        );
    }

    private static int clamp(int value, int minValue, int maxValue) {
        return Math.min(Math.max(value, minValue), maxValue);
    }

    private static int dpToPx(@NonNull Context context, int valueDp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                valueDp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static final class ActionItem {
        private final int labelResId;
        private final int iconResId;
        private final Runnable onAction;
        private final boolean enabled;

        public ActionItem(
                @StringRes int labelResId,
                @DrawableRes int iconResId,
                @NonNull Runnable onAction
        ) {
            this(labelResId, iconResId, onAction, true);
        }

        public ActionItem(
                @StringRes int labelResId,
                @DrawableRes int iconResId,
                @NonNull Runnable onAction,
                boolean enabled
        ) {
            this.labelResId = labelResId;
            this.iconResId = iconResId;
            this.onAction = onAction;
            this.enabled = enabled;
        }
    }
}
