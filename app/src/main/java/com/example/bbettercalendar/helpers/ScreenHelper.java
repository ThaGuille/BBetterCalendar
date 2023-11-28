package com.example.bbettercalendar.helpers;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;

public class ScreenHelper {

    public static float screenWidth;
    public static float screenHeight;

    public static float getScreenWidth() {
        return screenWidth;
    }

    public static float getScreenHeight() {
        return screenHeight;
    }

    public static int getActivityScreenHeightDP(Activity activity) {
        // Altura total de la pantalla
        int screenHeight = convertPixelsToDp(activity.getResources().getDisplayMetrics().heightPixels, activity);

        // Altura de la barra de navegación inferior
        int navigationBarHeight = convertPixelsToDp(getNavigationBarHeight(activity), activity);

        // Altura de la barra de estado
        int statusBarHeight = convertPixelsToDp(getStatusBarHeight(activity), activity);

        // Altura útil = Altura total - Altura de la barra de navegación - Altura de la barra de estado
        return screenHeight - navigationBarHeight - statusBarHeight;
    }

    public static int getActivityScreenHeightPixels(Activity activity) {
        // Altura total de la pantalla
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

        // Altura de la barra de navegación inferior
        int navigationBarHeight = getNavigationBarHeight(activity);

        // Altura de la barra de estado
        int statusBarHeight = getStatusBarHeight(activity);

        // Altura útil = Altura total - Altura de la barra de navegación - Altura de la barra de estado

        return screenHeight - navigationBarHeight - statusBarHeight;
    }

    public static int getTopBottomBarHeight(Activity activity){
        int navigationBarHeight = getNavigationBarHeight(activity);
        int statusBarHeight = getStatusBarHeight(activity);
        return navigationBarHeight + statusBarHeight;
    }

    public static int getNavigationBarHeight(Activity activity) {
        int resourceId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return activity.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private static int getStatusBarHeight(Activity activity) {
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return activity.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static int convertPixelsToDp(int px, Context context){
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float dp = px / (metrics.densityDpi / 160f);
        return Math.round(dp);
    }

    public static int convertDpToPixels(int dp, Context context){
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return Math.round(px);
    }
}
