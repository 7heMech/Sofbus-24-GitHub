package bg.znestorov.sofbus24.utils;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;

/**
 * Utils methods fixing the edge-to-edge screens of Sofbus 24
 *
 * @author Zdravko Nestorov
 * @version 1.0
 */
public class EdgeToEdgeUtils {

    /**
     * Resolve the issue of the action bar overlapping on Android 16+:
     * <a href="https://stackoverflow.com/a/79521682/7794942">Action Bar in Android 16+</a>
     *
     * @param context the activity context
     */
    public static void fixActionBar(Activity context) {
        context.findViewById(android.R.id.content).setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Inset representing the action bars for displaying status (top).
                Insets statusBarInsets = insets.getInsets(WindowInsets.Type.statusBars());
                // Inset representing all system bars (bottom).
                Insets systemBarInsets = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0, statusBarInsets.top, 0, systemBarInsets.bottom);
            }
            return insets;
        });
    }
}
