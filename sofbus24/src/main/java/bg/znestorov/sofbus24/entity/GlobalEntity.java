package bg.znestorov.sofbus24.entity;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import bg.znestorov.sofbus24.main.HomeScreenSelect;
import bg.znestorov.sofbus24.main.R;
import bg.znestorov.sofbus24.utils.HmsUtils;

/**
 * Global class that extends Application and save state across several
 * Activities and all parts of your application. Each Activity is also a
 * Context, which is information about its execution environment in the broadest
 * sense. Your application also has a context, and Android guarantees that it
 * will exist as a single instance across your application.
 *
 * @author Zdravko Nestorov
 * @version 1.0
 */
@SuppressLint("VisibleForTests")
public class GlobalEntity extends Application {

    // HomeScreenSelect main activity
    private HomeScreenSelect hssContext;
    private boolean isPhoneDevice;
    private boolean isLargeTablet;
    private boolean areServicesAvailable;
    private boolean isGoogleStreetViewAvailable;
    // Indicates if the standard home screen has changed tabs
    private boolean hasToRestart = false;
    private boolean isFavouritesChanged = false;
    private boolean isVbChanged = false;
    private boolean isHomeScreenChanged = false;
    // Indicates if the home activity is changed
    private boolean isHomeActivityChanged = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Sofbus 24 prerequisites
        initialize();
    }

    public HomeScreenSelect getHssContext() {
        return hssContext;
    }

    public void setHssContext(HomeScreenSelect hssContext) {
        this.hssContext = hssContext;
    }

    public boolean isPhoneDevice() {
        return isPhoneDevice;
    }

    public void setPhoneDevice(boolean isPhoneDevice) {
        this.isPhoneDevice = isPhoneDevice;
    }

    public boolean isLargeTablet() {
        return isLargeTablet;
    }

    public void setLargeTablet(boolean isLargeTablet) {
        this.isLargeTablet = isLargeTablet;
    }

    public boolean isHasToRestart() {
        return hasToRestart;
    }

    public void setHasToRestart(boolean hasToRestart) {
        this.hasToRestart = hasToRestart;
    }

    public boolean isFavouritesChanged() {
        return isFavouritesChanged;
    }

    public void setFavouritesChanged(boolean isFavouritesChanged) {
        this.isFavouritesChanged = isFavouritesChanged;
    }

    public boolean isVbChanged() {
        return isVbChanged;
    }

    public void setVbChanged(boolean isVbChanged) {
        this.isVbChanged = isVbChanged;
    }

    public boolean isHomeScreenChanged() {
        return isHomeScreenChanged;
    }

    public void setHomeScreenChanged(boolean isHomeScreenChanged) {
        this.isHomeScreenChanged = isHomeScreenChanged;
    }

    public boolean areServicesAvailable() {
        return areServicesAvailable;
    }

    public void setServicesAvailable(boolean areServicesAvailable) {
        this.areServicesAvailable = areServicesAvailable;
    }

    public boolean isGoogleStreetViewAvailable() {
        return isGoogleStreetViewAvailable;
    }

    public void setGoogleStreetViewAvailable(boolean isGoogleStreetViewAvailable) {
        this.isGoogleStreetViewAvailable = isGoogleStreetViewAvailable;
    }

    public boolean isHomeActivityChanged() {
        return isHomeActivityChanged;
    }

    public void setHomeActivityChanged(boolean isHomeActivityChanged) {
        this.isHomeActivityChanged = isHomeActivityChanged;
    }

    /**
     * Get the type of the device - PHONE, SMALL TABLET or LARGE TABLET
     *
     * @return the device type
     */
    public DeviceTypeEnum getDeviceType() {

        DeviceTypeEnum deviceType;
        if (isPhoneDevice) {
            deviceType = DeviceTypeEnum.PHONE;
        } else if (!isLargeTablet) {
            deviceType = DeviceTypeEnum.SMALL_TABLET;
        } else {
            deviceType = DeviceTypeEnum.LARGE_TABLET;
        }

        return deviceType;
    }

    /**
     * Initialize the main params of the application
     */
    private void initialize() {

        isPhoneDevice = getResources().getBoolean(R.bool.isPhone);
        isLargeTablet = getResources().getBoolean(R.bool.isLargeTablet);

        if (HmsUtils.isGms()) {
            // Google Mobile Services
            areServicesAvailable = GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;

            // Google Street View
            try {
                getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0);
                isGoogleStreetViewAvailable = true;
            } catch (PackageManager.NameNotFoundException e) {
                isGoogleStreetViewAvailable = false;
            }

        } else {
            // Google Street View
            isGoogleStreetViewAvailable = false;
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + " {\n\tisPhoneDevice: " + isPhoneDevice
                + "\n\tisLargeTablet: " + isLargeTablet
                + "\n\tareServicesAvailable: " + areServicesAvailable
                + "\n\tisGoogleStreetViewAvailable: "
                + isGoogleStreetViewAvailable + "\n\thasToRestart: "
                + hasToRestart + "\n\tisFavouritesChanged: "
                + isFavouritesChanged + "\n\tisVbChanged: " + isVbChanged
                + "\n\tisHomeScreenChanged: " + isHomeScreenChanged
                + "\n\tisHomeActivityChanged: " + isHomeActivityChanged + "\n}";
    }
}