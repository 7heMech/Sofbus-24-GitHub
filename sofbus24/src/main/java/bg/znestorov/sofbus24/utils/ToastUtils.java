package bg.znestorov.sofbus24.utils;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import bg.znestorov.sofbus24.main.R;

/**
 * Centralised factory for {@link Toast} popups. When the AMOLED app theme is
 * active, the returned toast is given a flat true-black surface with white
 * text so it visually matches the rest of the AMOLED palette (the system
 * default toast otherwise inherits the OS-level light/dark styling, which
 * looks out of place on top of a true-black UI).
 *
 * <p>The Light and Dark themes keep the standard system toast appearance.
 *
 * <p>{@link Toast#setView(View)} is deprecated since API 30 but is still
 * honoured for foreground toasts (which is how the app uses them); when the
 * platform decides to ignore the custom view, callers fall back to the
 * standard system toast automatically.
 */
public class ToastUtils {

    private ToastUtils() {
        // Utility class — no instances.
    }

    /**
     * Drop-in replacement for {@link Toast#makeText(Context, CharSequence, int)}
     * that paints the toast with the AMOLED palette when that theme is active.
     */
    public static Toast makeText(Context context, CharSequence text, int duration) {
        Toast toast = Toast.makeText(context, text, duration);
        applyAmoledStyleIfNeeded(context, toast, text);
        return toast;
    }

    /**
     * Drop-in replacement for {@link Toast#makeText(Context, int, int)}
     * that paints the toast with the AMOLED palette when that theme is active.
     */
    public static Toast makeText(Context context, int resId, int duration) {
        return makeText(context, context.getString(resId), duration);
    }

    /**
     * Swap the toast's view for the AMOLED layout when the AMOLED theme is the
     * user's current selection. No-op for Light/Dark, or if the inflate fails.
     */
    private static void applyAmoledStyleIfNeeded(Context context, Toast toast,
                                                 CharSequence text) {
        if (context == null || toast == null) {
            return;
        }
        if (!isAmoledThemeSelected(context)) {
            return;
        }
        try {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.toast_amoled, null, false);
            TextView label = view.findViewById(R.id.toast_amoled_text);
            if (label != null) {
                label.setText(text);
            }
            toast.setView(view);
        } catch (Exception ignored) {
            // Fall back silently to the standard toast if the custom view
            // cannot be inflated for any reason (e.g. a stripped-down context).
        }
    }

    /**
     * Read the persisted theme preference from any {@link Context}. Mirrors
     * {@link ThemeChange#getAppTheme(android.app.Activity)} but accepts a
     * non-Activity context, since toasts are sometimes built from services,
     * fragments and async callbacks.
     */
    private static boolean isAmoledThemeSelected(Context context) {
        try {
            String chosenTheme = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getString(Constants.PREFERENCE_KEY_APP_THEME,
                            Constants.PREFERENCE_DEFAULT_VALUE_APP_THEME);
            return Constants.PREFERENCE_VALUE_APP_THEME_AMOLED.equals(chosenTheme);
        } catch (Exception ignored) {
            return false;
        }
    }
}
