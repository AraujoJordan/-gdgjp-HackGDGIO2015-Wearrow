package com.araujo.jordan.wearrow;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

/**
 * Created by Jordan on 11/03/2015.
 */
public class LolipopFeatures {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void actionAndStatusBar(Activity act) {
        if (Build.VERSION.SDK_INT > 19) {
            Window window = act.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.rgb(255, 87, 34));
            window.setNavigationBarColor(Color.rgb(255, 87, 34));
        }
    }


}
