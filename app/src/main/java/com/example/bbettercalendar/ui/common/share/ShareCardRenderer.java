package com.example.bbettercalendar.ui.common.share;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.SingleEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F0-u5: share card. Inflates {@code view_share_card.xml} off screen, draws it to a fixed-size
 * bitmap (main thread), writes the PNG to {@code cache/shared/} (ExecutorService) and hands the
 * ready chooser {@link Intent} back through LiveData ({@code postValue}).
 *
 * <p>Q-06: the card carries the "BBetter" brand only and {@code EXTRA_TEXT} is text only, with no
 * link (the app is not published). One instance per screen; {@link #attach} wires the observers to
 * the fragment's view lifecycle.
 */
public final class ShareCardRenderer {

    /** Appended to the applicationId; must match android:authorities in the manifest. */
    private static final String AUTHORITY_SUFFIX = ".fileprovider";
    private static final String SHARED_DIR = "shared";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** What the card shows. {@code stat2*} are optional (null hides the second column). */
    public static final class Card {
        public final String eyebrow;
        public final String heroValue;
        public final String heroSub;
        public final String stat1Value;
        public final String stat1Label;
        @Nullable public final String stat2Value;
        @Nullable public final String stat2Label;
        /** Text-only EXTRA_TEXT (no link). */
        public final String shareText;
        /** PNG name inside cache/shared/ (overwritten on every share of the same kind). */
        public final String fileName;

        public Card(String eyebrow, String heroValue, String heroSub,
                    String stat1Value, String stat1Label,
                    @Nullable String stat2Value, @Nullable String stat2Label,
                    String shareText, String fileName) {
            this.eyebrow = eyebrow;
            this.heroValue = heroValue;
            this.heroSub = heroSub;
            this.stat1Value = stat1Value;
            this.stat1Label = stat1Label;
            this.stat2Value = stat2Value;
            this.stat2Label = stat2Label;
            this.shareText = shareText;
            this.fileName = fileName;
        }
    }

    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<SingleEvent<Intent>> chooser = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Boolean>> failure = new MutableLiveData<>();

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public LiveData<SingleEvent<Intent>> getChooser() {
        return chooser;
    }

    /**
     * Wires the observers: the button is disabled while a render is in flight (no stacked
     * choosers), the chooser opens once, a failure shows a short message. Call from onViewCreated.
     */
    public void attach(@NonNull Fragment fragment, @NonNull View shareButton) {
        busy.observe(fragment.getViewLifecycleOwner(),
                b -> shareButton.setEnabled(!Boolean.TRUE.equals(b)));
        chooser.observe(fragment.getViewLifecycleOwner(), event -> {
            if (event == null || event.isHandled() || fragment.isStateSaved()) {
                return;
            }
            Intent intent = event.consume();
            if (intent != null) {
                fragment.startActivity(intent);
            }
        });
        failure.observe(fragment.getViewLifecycleOwner(), event -> {
            if (event == null || event.isHandled()) {
                return;
            }
            event.consume();
            Toast.makeText(fragment.requireContext(), R.string.share_error, Toast.LENGTH_SHORT).show();
        });
    }

    /** Main thread. Ignored while a previous share is still rendering. */
    public void share(@NonNull Activity activity, @NonNull Card card) {
        if (Boolean.TRUE.equals(busy.getValue())) {
            return;
        }
        busy.setValue(true);
        final Bitmap bitmap;
        try {
            bitmap = drawCard(activity, card);
        } catch (RuntimeException e) {
            busy.setValue(false);
            failure.setValue(new SingleEvent<>(true));
            return;
        }
        final Context app = activity.getApplicationContext();
        final String chooserTitle = activity.getString(R.string.share_chooser_title);
        IO.execute(() -> {
            try {
                Uri uri = writePng(app, bitmap, card.fileName);
                chooser.postValue(new SingleEvent<>(buildChooser(uri, card.shareText, chooserTitle)));
            } catch (IOException | RuntimeException e) {
                failure.postValue(new SingleEvent<>(true));
            } finally {
                bitmap.recycle();
                busy.postValue(false);
            }
        });
    }

    /**
     * Pinned to 360 dpi and font scale 1 so the 1080x1350 px bitmap is always the same 480x600
     * dp/sp card, whatever the phone's density or accessibility font size.
     */
    private static Bitmap drawCard(Activity activity, Card card) {
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.densityDpi = activity.getResources().getInteger(R.integer.share_card_density_dpi);
        config.fontScale = 1f;
        Context themed = new ContextThemeWrapper(activity.createConfigurationContext(config),
                R.style.Theme_BBetter_NoActionBar);

        View view = LayoutInflater.from(themed).inflate(R.layout.view_share_card, null, false);
        text(view, R.id.shareCardEyebrow, card.eyebrow);
        text(view, R.id.shareCardHeroValue, card.heroValue);
        text(view, R.id.shareCardHeroSub, card.heroSub);
        text(view, R.id.shareCardStat1Value, card.stat1Value);
        text(view, R.id.shareCardStat1Label, card.stat1Label);
        if (card.stat2Value == null) {
            view.findViewById(R.id.shareCardStat2).setVisibility(View.GONE);
        } else {
            text(view, R.id.shareCardStat2Value, card.stat2Value);
            text(view, R.id.shareCardStat2Label, card.stat2Label);
        }

        int w = themed.getResources().getInteger(R.integer.share_card_px_width);
        int h = themed.getResources().getInteger(R.integer.share_card_px_height);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void text(View root, int id, @Nullable String value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    /** Background thread. Overwrites {@code fileName} and prunes any other PNG left in the folder. */
    private static Uri writePng(Context app, Bitmap bitmap, String fileName) throws IOException {
        File dir = new File(app.getCacheDir(), SHARED_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.getName().equals(fileName)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        File out = new File(dir, fileName);
        try (FileOutputStream stream = new FileOutputStream(out)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("PNG compress failed");
            }
        }
        return FileProvider.getUriForFile(app, app.getPackageName() + AUTHORITY_SUFFIX, out);
    }

    /** ACTION_SEND image/png + text only (Q-06); ClipData + read grant so the chooser previews it. */
    private static Intent buildChooser(Uri uri, String text, String title) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, text);
        send.setClipData(ClipData.newRawUri(null, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, title);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooser;
    }
}
