package com.example.bbettercalendar.blocking;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        // OJO: resolveActivity(MATCH_DEFAULT_ONLY), NO queryIntentActivities. queryIntentActivities
        // devuelve TODO paquete que pueda manejar HOME, y en AOSP/emuladores eso incluye la
        // FallbackHome de Settings (com.android.settings) -- exentar por ese resultado eximía TODA
        // la app de Ajustes del bloqueo, no sólo su pantalla de inicio de respaldo (visto en pruebas
        // manuales: Ajustes se abría sin portada con el modo bloqueo total activo). resolveActivity
        // con MATCH_DEFAULT_ONLY sólo devuelve el launcher REALMENTE seleccionado por defecto.
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        Set<String> homePackages = new HashSet<>();
        ResolveInfo resolvedHome = getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolvedHome != null && resolvedHome.activityInfo != null) {
            homePackages.add(resolvedHome.activityInfo.packageName);
        }

        // El buscador/"at a glance" de Google va incrustado en la propia pantalla de inicio (Pixel
        // Launcher y similares) pero vive en un paquete DISTINTO del launcher
        // (com.google.android.googlequicksearchbox) que emite su PROPIO TYPE_WINDOW_STATE_CHANGED
        // durante la transición a inicio -- visto en pruebas manuales: al cerrar/salir de la app la
        // portada parpadeaba un instante con la etiqueta "Google" y se retiraba sola en cuanto
        // llegaba el evento real del launcher (confirmado con `dumpsys accessibility`, que mostraba
        // ambos paquetes alternándose en cada viaje a inicio). Es el manejador por defecto de
        // ACTION_ASSIST (rol ROLE_ASSISTANT), así que se resuelve y exime igual que el launcher.
        Intent assistIntent = new Intent(Intent.ACTION_ASSIST);
        ResolveInfo resolvedAssist = getPackageManager().resolveActivity(assistIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolvedAssist != null && resolvedAssist.activityInfo != null) {
            homePackages.add(resolvedAssist.activityInfo.packageName);
        }
        engine.setFocusExemptPackages(homePackages);

        // El servicio comparte proceso con la app (sin android:process): si el proceso muere con el
        // modo bloqueo total armado, no queda ningún timer vivo para desarmarlo -> "kill-trap" que
        // dejaría el teléfono bloqueado. El sistema reconecta este servicio al reiniciar el proceso,
        // así que limpiar aquí es el punto de auto-sanación. Una sesión en curso lo vuelve a activar
        // DESPUÉS de esta línea (HomeFragment sólo llama a FocusBlockState.setActive mientras el
        // timer corre, con el servicio ya conectado), así que esto nunca corta un bloqueo real.
        FocusBlockState.setActive(this, false);

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
        final CharSequence className = event.getClassName();
        if (engine == null || executor == null) return;
        if (isNoise(fg, className)) return;

        executor.execute(() -> {
            // Sólo una ventana que sea una Activity REAL del paquete cuenta como "el usuario cambió
            // de app". La consulta a PackageManager es I/O -> aquí en el executor, no en el hilo
            // principal (regla #3).
            if (!isActivityWindow(fg, className)) return;
            BlockDecisionEngine.Decision decision = engine.decide(fg);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (decision.block) {
                    showCover(fg, decision.usedMillis, decision.focusMode);
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

    // Caché (paquete/clase -> ¿es una Activity real?) para no golpear PackageManager en cada
    // evento. Sólo se toca desde el executor de un único hilo -> HashMap sin sincronizar.
    private final Map<String, Boolean> activityWindowCache = new HashMap<>();

    // ¿El className del evento es una Activity REAL declarada en el paquete? Los launchers y el
    // buscador de Google emiten TYPE_WINDOW_STATE_CHANGED por ventanas TRANSITORIAS (widgets,
    // overlays, className de View o null) durante cada transición de app, SIN que el usuario haya
    // cambiado de app de verdad. Tratarlos como cambio de app causaba dos bugs vistos en uso real:
    //  - portada fantasma "Google" al salir a inicio (la ventana transitoria de
    //    googlequicksearchbox se cubría si la exención de ASSIST no resolvía a ese paquete, p. ej.
    //    con Gemini como asistente por defecto);
    //  - portada retirada al segundo de cubrir una app bloqueada (el evento transitorio de un
    //    paquete exento llegaba después, decidía block=false con paquete distinto y disparaba
    //    removeCover() mientras la app bloqueada seguía delante y usable, porque la portada es
    //    FLAG_NOT_FOCUSABLE y la app nunca se pausó).
    // Visibilidad de paquetes (API 30+): getActivityInfo sólo ve paquetes declarados en <queries>
    // (apps con Activity LAUNCHER/HOME/ASSIST). Un paquete invisible se trata como transitorio
    // (no se bloquea) — aceptable: sin Activity de launcher el usuario no puede "usarlo".
    private boolean isActivityWindow(String pkg, CharSequence className) {
        if (className == null) return false;
        String key = pkg + "/" + className;
        Boolean cached = activityWindowCache.get(key);
        if (cached != null) return cached;
        boolean isActivity;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, className.toString()), 0);
            isActivity = true;
        } catch (PackageManager.NameNotFoundException e) {
            isActivity = false;
        }
        activityWindowCache.put(key, isActivity);
        return isActivity;
    }

    // --- portada de bloqueo ---

    private void showCover(String packageName, long usedMillis, boolean focusMode) {
        if (coverView != null) {
            if (packageName.equals(coveredPackage)) return; // ya cubierta
            removeCover();
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.view_block_cover, null);

        TextView label = view.findViewById(R.id.block_cover_app_label);
        label.setText(labelFor(packageName));
        TextView title = view.findViewById(R.id.block_cover_title);
        TextView subtitle = view.findViewById(R.id.block_cover_subtitle);
        if (focusMode) {
            title.setText(R.string.block_cover_focus_title);
            subtitle.setText(R.string.block_cover_focus_subtitle);
        } else {
            title.setText(R.string.block_cover_title);
            subtitle.setText(getString(R.string.block_cover_subtitle,
                    FormatHelper.formatDuration(usedMillis)));
        }
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
