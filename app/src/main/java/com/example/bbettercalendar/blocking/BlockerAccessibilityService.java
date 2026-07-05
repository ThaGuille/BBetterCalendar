package com.example.bbettercalendar.blocking;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.AppRule;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Servicio de Accesibilidad que hace cumplir los límites diarios (Phase 4a). Sólo escucha
// TYPE_WINDOW_STATE_CHANGED: cuando una app entra en primer plano, pregunta al motor si superó su
// límite (consulta EN VIVO, no el poll con lag de Phase 3) y, si procede, dibuja una portada de
// pantalla completa (TYPE_ACCESSIBILITY_OVERLAY). Si el overlay no puede engancharse, cae a
// GLOBAL_ACTION_HOME (rebote a inicio). No lee ni transmite contenido de pantalla — sólo el nombre
// del paquete en primer plano. La decisión pesada corre en un executor (regla #3); addView/removeView
// vuelven al hilo principal.
public class BlockerAccessibilityService extends AccessibilityService {

    private ExecutorService executor;
    private Handler mainHandler;
    private WindowManager windowManager;
    private BlockDecisionEngine engine;

    private LiveData<List<AppRule>> enforcedRules;
    private Observer<List<AppRule>> enforcedObserver;

    private View coverView;
    private String coveredPackage;

    // Se pone a true al arrancar teardown() (onDestroy/onUnbind, hilo principal). Una decisión que
    // ya estaba en curso en el executor cuando arrancó el teardown termina y hace mainHandler.post()
    // DESPUÉS de que teardown() ya haya corrido: sin esta guarda, ese post podría enganchar una
    // portada nueva vía un WindowManager atado a un servicio ya desvinculado -> portada huérfana que
    // nadie vuelve a retirar (su propio botón Cerrar llama a performGlobalAction en un servicio ya
    // destruido). Se comprueba dentro del propio Runnable posteado, en el hilo principal.
    private volatile boolean destroyed;

    // Paquetes de teclados (IME) instalados: sus ventanas emiten TYPE_WINDOW_STATE_CHANGED sin que
    // el usuario haya cambiado de app — se ignoran (ver isNoise). Se resuelven una vez al conectar.
    private Set<String> imePackages = Collections.emptySet();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        engine = new BlockDecisionEngine(this);

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        Set<String> imes = new HashSet<>();
        if (imm != null) {
            for (InputMethodInfo info : imm.getInputMethodList()) {
                imes.add(info.getPackageName());
            }
        }
        imePackages = imes;

        // observeForever desde el hilo principal (onServiceConnected lo es); se retira en onUnbind.
        enforcedRules = AppDatabase.getDatabase(this).appRuleDao().observeEnforced();
        enforcedObserver = rules -> engine.setEnforcedRules(rules);
        enforcedRules.observeForever(enforcedObserver);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        final String fg = pkgCs.toString();
        if (engine == null || executor == null) return;
        if (isNoise(fg, event.getClassName())) return;

        executor.execute(() -> {
            BlockDecisionEngine.Decision decision = engine.decide(fg);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (decision.block) {
                    showCover(fg, decision.usedMillis);
                } else if (coveredPackage != null && !coveredPackage.equals(fg)) {
                    // El usuario salió de la app cubierta (inicio / otra app) -> retirar la portada.
                    removeCover();
                }
            });
        });
    }

    @Override
    public void onInterrupt() {
        // Sin acción: no procesamos gestos ni feedback continuo.
    }

    // Eventos de ventana que NO significan "el usuario cambió de app". Si se dejaran pasar, la rama
    // de removeCover() los tomaría por una salida de la app cubierta y retiraría la portada:
    //  - Nuestra PROPIA portada al engancharse: addView(TYPE_ACCESSIBILITY_OVERLAY) emite un
    //    TYPE_WINDOW_STATE_CHANGED con nuestro paquete y el className del layout raíz (no de una
    //    Activity nuestra) -> el servicio se quitaba su propia portada un instante después de
    //    ponerla (el icono 🚫 parpadeaba y la app seguía usable). Una Activity NUESTRA real en
    //    primer plano sí pasa (className empieza por nuestro paquete) y retira la portada.
    //  - System UI (persiana de notificaciones, transiciones de recents): abrirla no es salir.
    //  - El teclado: la app cubierta conserva el foco (la portada es FLAG_NOT_FOCUSABLE) y puede
    //    abrir el IME justo al arrancar; la ventana del IME no es un cambio de app.
    private boolean isNoise(String pkg, CharSequence className) {
        if ("com.android.systemui".equals(pkg)) return true;
        if (imePackages.contains(pkg)) return true;
        // OJO: className viene del namespace (com.example.bbettercalendar.*), que tras el rename de
        // applicationId (Phase 4b) YA NO coincide con getPackageName() (io.github.thaguille...). Hay
        // que comparar contra el namespace, no contra el applicationId: si no, una Activity NUESTRA
        // real en primer plano no se reconocería y la portada se quedaría pegada sobre BBetter.
        if (getPackageName().equals(pkg)) {
            // R está generado en el namespace (com.example.bbettercalendar), que es lo que prefijan
            // los className de NUESTRAS Activities — no getPackageName() (el applicationId renombrado).
            // R.class.getPackage() puede devolver null según el ClassLoader (no garantizado en
            // Android) -> se deriva del nombre de clase, que nunca es null, en vez de reflexión.
            String rClassName = R.class.getName();
            String namespace = rClassName.substring(0, rClassName.lastIndexOf('.'));
            return className == null || !className.toString().startsWith(namespace);
        }
        return false;
    }

    // --- portada de bloqueo ---

    private void showCover(String packageName, long usedMillis) {
        if (coverView != null) {
            if (packageName.equals(coveredPackage)) return; // ya cubierta
            removeCover();
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.view_block_cover, null);

        TextView label = view.findViewById(R.id.block_cover_app_label);
        label.setText(labelFor(packageName));
        TextView subtitle = view.findViewById(R.id.block_cover_subtitle);
        subtitle.setText(getString(R.string.block_cover_subtitle,
                FormatHelper.formatDuration(usedMillis)));
        view.findViewById(R.id.block_cover_close).setOnClickListener(v -> {
            removeCover();
            performGlobalAction(GLOBAL_ACTION_HOME);
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        try {
            windowManager.addView(view, params);
            coverView = view;
            coveredPackage = packageName;
        } catch (Exception e) {
            // El overlay no pudo engancharse (p. ej. tipo de ventana no soportado): rebote a inicio.
            coverView = null;
            coveredPackage = null;
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    private void removeCover() {
        if (coverView != null) {
            try {
                windowManager.removeView(coverView);
            } catch (Exception ignored) {
                // La vista ya podía no estar enganchada; ignorar.
            }
            coverView = null;
            coveredPackage = null;
        }
    }

    private String labelFor(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            return l != null ? l.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private void teardown() {
        destroyed = true;
        if (enforcedRules != null && enforcedObserver != null) {
            enforcedRules.removeObserver(enforcedObserver);
            enforcedObserver = null;
        }
        removeCover();
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }
}
