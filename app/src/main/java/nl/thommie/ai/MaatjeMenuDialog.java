package nl.thommie.ai;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

final class MaatjeMenuDialog {

    static final class Item {
        final String badge;
        final String title;
        final String subtitle;
        final Runnable action;

        Item(
                String badge,
                String title,
                String subtitle,
                Runnable action
        ) {
            this.badge = badge;
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
        }
    }

    private static final int BG =
            Color.rgb(4, 8, 5);
    private static final int PANEL =
            Color.rgb(9, 17, 11);
    private static final int PANEL_2 =
            Color.rgb(12, 24, 15);
    private static final int MINT =
            Color.rgb(54, 220, 104);
    private static final int TEXT =
            Color.rgb(226, 244, 230);
    private static final int MUTED =
            Color.rgb(112, 145, 120);
    private static final int BORDER =
            Color.rgb(28, 63, 38);

    private MaatjeMenuDialog() {}

    static Item item(
            String badge,
            String title,
            String subtitle,
            Runnable action
    ) {
        return new Item(
                badge,
                title,
                subtitle,
                action
        );
    }

    static void show(
            Activity activity,
            String title,
            String subtitle,
            Item... items
    ) {
        show(
                activity,
                title,
                subtitle,
                Arrays.asList(items)
        );
    }

    static void show(
            Activity activity,
            String title,
            String subtitle,
            List<Item> items
    ) {
        Dialog dialog =
                new Dialog(activity);

        dialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        ScrollView scroll =
                new ScrollView(activity);

        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root =
                new LinearLayout(activity);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(activity, 18),
                dp(activity, 20),
                dp(activity, 18),
                dp(activity, 18)
        );

        TextView eyebrow =
                text(
                        activity,
                        "MAATJE • CONTROL CENTER",
                        11,
                        MINT,
                        true
                );

        eyebrow.setLetterSpacing(.18f);
        root.addView(eyebrow);

        TextView heading =
                text(
                        activity,
                        title,
                        24,
                        TEXT,
                        true
                );

        heading.setPadding(
                0,
                dp(activity, 8),
                0,
                0
        );

        root.addView(heading);

        TextView desc =
                text(
                        activity,
                        subtitle,
                        12,
                        MUTED,
                        false
                );

        desc.setPadding(
                0,
                dp(activity, 6),
                0,
                dp(activity, 18)
        );

        root.addView(desc);

        for (Item item : items) {
            View row =
                    row(
                            activity,
                            item,
                            dialog
                    );

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            lp.bottomMargin =
                    dp(activity, 9);

            root.addView(
                    row,
                    lp
            );
        }

        TextView close =
                text(
                        activity,
                        "SLUITEN",
                        13,
                        MINT,
                        true
                );

        close.setGravity(
                Gravity.CENTER
        );

        close.setPadding(
                dp(activity, 16),
                dp(activity, 16),
                dp(activity, 16),
                dp(activity, 16)
        );

        close.setBackground(
                rounded(
                        PANEL,
                        BORDER,
                        18
                )
        );

        close.setOnClickListener(
                v -> dialog.dismiss()
        );

        LinearLayout.LayoutParams closeLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        closeLp.topMargin =
                dp(activity, 8);

        root.addView(
                close,
                closeLp
        );

        scroll.addView(root);
        dialog.setContentView(scroll);

        Window window =
                dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawableResource(
                    android.R.color.transparent
            );

            window.addFlags(
                    WindowManager.LayoutParams
                            .FLAG_DIM_BEHIND
            );

            WindowManager.LayoutParams attrs =
                    window.getAttributes();

            attrs.dimAmount = .74f;
            attrs.width =
                    WindowManager.LayoutParams
                            .MATCH_PARENT;
            attrs.height =
                    WindowManager.LayoutParams
                            .MATCH_PARENT;

            window.setAttributes(attrs);
        }

        dialog.show();

        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }

    private static View row(
            Activity activity,
            Item item,
            Dialog dialog
    ) {
        LinearLayout outer =
                new LinearLayout(activity);

        outer.setOrientation(
                LinearLayout.HORIZONTAL
        );

        outer.setGravity(
                Gravity.CENTER_VERTICAL
        );

        outer.setPadding(
                dp(activity, 14),
                dp(activity, 14),
                dp(activity, 12),
                dp(activity, 14)
        );

        outer.setBackground(
                rounded(
                        PANEL_2,
                        BORDER,
                        18
                )
        );

        TextView badge =
                text(
                        activity,
                        item.badge,
                        item.badge.length() > 2
                                ? 11
                                : 13,
                        MINT,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setBackground(
                rounded(
                        PANEL,
                        MINT,
                        15
                )
        );

        outer.addView(
                badge,
                new LinearLayout.LayoutParams(
                        dp(activity, 48),
                        dp(activity, 48)
                )
        );

        LinearLayout copy =
                new LinearLayout(activity);

        copy.setOrientation(
                LinearLayout.VERTICAL
        );

        copy.setPadding(
                dp(activity, 14),
                0,
                dp(activity, 8),
                0
        );

        TextView title =
                text(
                        activity,
                        item.title,
                        15,
                        TEXT,
                        true
                );

        TextView subtitle =
                text(
                        activity,
                        item.subtitle,
                        11,
                        MUTED,
                        false
                );

        subtitle.setPadding(
                0,
                dp(activity, 3),
                0,
                0
        );

        copy.addView(title);
        copy.addView(subtitle);

        outer.addView(
                copy,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView arrow =
                text(
                        activity,
                        "›",
                        28,
                        MINT,
                        false
                );

        arrow.setGravity(
                Gravity.CENTER
        );

        outer.addView(
                arrow,
                new LinearLayout.LayoutParams(
                        dp(activity, 28),
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );

        outer.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    if (item.action != null) {
                        item.action.run();
                    }
                }
        );

        outer.setOnTouchListener(
                (v, event) -> {
                    switch (event.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            v.setAlpha(.72f);
                            break;

                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1f);
                            break;

                        default:
                            break;
                    }

                    return false;
                }
        );

        return outer;
    }

    private static TextView text(
            Activity activity,
            String value,
            float size,
            int color,
            boolean bold
    ) {
        TextView view =
                new TextView(activity);

        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);

        view.setTypeface(
                Typeface.create(
                        Typeface.SANS_SERIF,
                        bold
                                ? Typeface.BOLD
                                : Typeface.NORMAL
                )
        );

        return view;
    }

    private static GradientDrawable rounded(
            int fill,
            int stroke,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(fill);
        drawable.setCornerRadius(
                radiusDp * 2.4f
        );

        drawable.setStroke(
                1,
                stroke
        );

        return drawable;
    }

    private static int dp(
            Activity activity,
            int value
    ) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
