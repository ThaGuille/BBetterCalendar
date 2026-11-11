package com.example.bbettercalendar.debug;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.InsetsHelper;

/**
 * Page of the debug gallery for the Canvas chart kit of {@code ui/common/chart} (plan mejora-visual u4):
 * every chart view with sample data and one without data ({@link ChartGallerySection}). A page of its own
 * keeps the scroll positions the recorded flows of {@link DebugGalleryActivity} rely on. Debug only. Open it
 * from the gallery ("Canvas charts (u4)") or with
 * <pre>
 * adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugChartsGalleryActivity
 * </pre>
 */
public class DebugChartsGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_debug_charts_gallery);
        InsetsHelper.applyToContent(this);
        ChartGallerySection.bind(this);
    }
}
