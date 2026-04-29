package bg.znestorov.sofbus24.utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.widget.ImageView;

import bg.znestorov.sofbus24.entity.AppThemeEnum;
import bg.znestorov.sofbus24.main.R;

public class ThemeChange {

    /**
     * Alpha applied to in-list vehicle / line PNG icons on AMOLED so the
     * fully-opaque, vivid raster colours don't visually scream against the
     * surrounding true-black background. Picked just low enough to take the
     * edge off without making the icons look greyed-out / disabled. Map
     * markers must NOT use this dim — they need to stay fully visible on
     * the map tile.
     */
    private static final float AMOLED_ICON_DIM_ALPHA = 0.65f;
    private static final float DEFAULT_ICON_ALPHA = 1.0f;

    /**
     * Check if the selected application theme is LIGHT one
     *
     * @param context the Context of the current activity
     * @return if the theme is light
     */
    public static boolean isLightTheme(Activity context) {
        return getAppTheme(context) == AppThemeEnum.LIGHT;
    }

    /**
     * Check if the selected application theme is the AMOLED one
     *
     * @param context the Context of the current activity
     * @return if the theme is AMOLED (true black)
     */
    public static boolean isAmoledTheme(Activity context) {
        return getAppTheme(context) == AppThemeEnum.AMOLED;
    }

    /**
     * Check the type of the selected theme for the application
     *
     * @param context the Context of the current activity
     * @return the type of the application theme
     */
    public static AppThemeEnum getAppTheme(Activity context) {

        String chosenTheme = PreferenceManager.getDefaultSharedPreferences(
                context).getString(Constants.PREFERENCE_KEY_APP_THEME,
                Constants.PREFERENCE_DEFAULT_VALUE_APP_THEME);

        if (Constants.PREFERENCE_DEFAULT_VALUE_APP_THEME.equals(chosenTheme)) {
            return AppThemeEnum.LIGHT;
        } else if (Constants.PREFERENCE_VALUE_APP_THEME_AMOLED.equals(chosenTheme)) {
            return AppThemeEnum.AMOLED;
        } else {
            return AppThemeEnum.DARK;
        }
    }

    /**
     * Set the selected theme as a default for the application.
     * <p>
     * Activities whose class name ends with {@code Dialog} are shown as a
     * popup window (declared with {@code android:theme="@style/PopupTheme"}
     * in the manifest). For those, a matching popup theme variant is applied
     * so dialogs do not flash a light surface and the content matches the
     * rest of the selected app theme (most notably the AMOLED look).
     *
     * @param context the Context of the current activity
     */
    public static void selectTheme(Activity context) {

        AppThemeEnum appTheme = getAppTheme(context);
        boolean isPopup = isPopupActivity(context);

        switch (appTheme) {
            case LIGHT:
                context.setTheme(isPopup ? R.style.PopupTheme : R.style.AppThemeLight);
                break;
            case AMOLED:
                context.setTheme(isPopup ? R.style.PopupThemeAmoled : R.style.AppThemeAmoled);
                break;
            default:
                context.setTheme(isPopup ? R.style.PopupTheme : R.style.AppThemeDark);
                break;
        }
    }

    /**
     * Check whether the activity is shown as a popup window. The convention in
     * this project is that all popup-style activities are declared with
     * {@code android:theme="@style/PopupTheme"} in the manifest and their
     * class name ends with {@code Dialog} (e.g. {@code AboutDialog},
     * {@code PreferencesDialog}, {@code HistoryDialog}).
     *
     * @param context the current activity
     * @return {@code true} if the activity is a popup-style dialog activity
     */
    private static boolean isPopupActivity(Activity context) {
        return context.getClass().getSimpleName().endsWith("Dialog");
    }

    /**
     * Dim a list-row vehicle / line icon when AMOLED is active so the bright,
     * fully-opaque raster PNG doesn't visually clash with the surrounding
     * true-black background. On Light / Dark themes the icon is left fully
     * opaque. Safe to call on any thread that touches the {@link ImageView}'s
     * view tree (i.e. the UI thread, which is where adapters bind views).
     * <p/>
     * Note: do NOT call this for map markers - those need to stay fully
     * visible on the map tile.
     *
     * @param context   the current Activity context
     * @param imageView the icon ImageView to (un)dim
     */
    public static void applyAmoledIconDim(Activity context, ImageView imageView) {
        if (imageView == null || context == null) {
            return;
        }

        // Always reset the alpha (and not just set it on AMOLED) because the
        // adapter may recycle a row that was previously bound under a
        // different theme - we don't want a stale 0.65 alpha to leak.
        imageView.setAlpha(isAmoledTheme(context)
                ? AMOLED_ICON_DIM_ALPHA
                : DEFAULT_ICON_ALPHA);
    }

    /**
     * Make a dark-coloured monochrome PNG icon (e.g. the History list's
     * {@code ic_history_*} silhouettes) visible on AMOLED by tinting it
     * white. On Light / Dark themes any previously-applied tint is cleared
     * so the icon renders with its original PNG colours. The icon's alpha
     * is always reset to fully opaque because adapters recycle row views
     * across themes and we don't want a stale dim or tint to leak.
     * <p/>
     * Use this helper for adapters whose icons are dark / black silhouettes
     * (where {@link #applyAmoledIconDim(Activity, ImageView)} would make
     * them invisible against the AMOLED black background). Keep using
     * {@link #applyAmoledIconDim(Activity, ImageView)} for the colourful
     * vehicle PNGs.
     *
     * @param context   the current Activity context
     * @param imageView the icon ImageView to tint / un-tint
     */
    public static void applyAmoledIconWhiteTint(Activity context, ImageView imageView) {
        if (imageView == null || context == null) {
            return;
        }

        imageView.setAlpha(DEFAULT_ICON_ALPHA);
        if (isAmoledTheme(context)) {
            imageView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } else {
            imageView.clearColorFilter();
        }
    }
}
