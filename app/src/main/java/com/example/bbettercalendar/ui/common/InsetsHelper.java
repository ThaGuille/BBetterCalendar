package com.example.bbettercalendar.ui.common;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * F0-u2 (edge-to-edge): único punto de la app que trata los insets del sistema.
 *
 * Con target 35+ en Android 15+ todo se dibuja bajo las barras de estado y navegación (en 36 ya no
 * se puede desactivar). {@link #enableEdgeToEdge(Activity)} deja el mismo comportamiento en API 24-34
 * y {@link #apply(View, int)} / {@link #applyWithIme(View, int)} suman el inset al padding que la
 * vista ya tenía, de modo que el fondo de la propia vista se extiende bajo la barra.
 *
 * Importante: sustituye el OnApplyWindowInsetsListener de la vista (también el que Material instala
 * en la BottomNavigationView), así que el inset nunca se aplica dos veces.
 */
public final class InsetsHelper {

    public static final int TOP = 1;
    public static final int BOTTOM = 1 << 1;
    /** Izquierda y derecha: cutouts y barras laterales (pantallas grandes sin orientación fija). */
    public static final int HORIZONTAL = 1 << 2;
    public static final int ALL = TOP | BOTTOM | HORIZONTAL;

    private InsetsHelper() { }

    /**
     * A sangre completa con barras transparentes e iconos claros (el tema es oscuro). Llamar en
     * {@code onCreate}, antes de {@code setContentView}.
     */
    @SuppressWarnings("deprecation")
    public static void enableEdgeToEdge(@NonNull Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        // Desde API 35 estos colores no tienen efecto (las barras ya son transparentes), pero en
        // API 24-34 hay que quitarlos o la barra seguiría opaca encima del contenido.
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Sin el velo translúcido que el sistema pone tras la navegación por 3 botones: el
            // fondo lo pone la propia app.
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    /**
     * Padding = padding inicial + inset de {@code systemBars() | displayCutout()} en los lados
     * pedidos. Reaplica solo si cambian los insets.
     */
    public static void apply(@NonNull View view, int sides) {
        install(view, sides, false);
    }

    /** Como {@link #apply} y, además, el inferior sube con el teclado ({@code ime()}). */
    public static void applyWithIme(@NonNull View view, int sides) {
        install(view, sides, true);
    }

    /**
     * Pantallas de formulario (Activities sueltas): inset en los cuatro lados sobre el contenedor
     * {@code android.R.id.content} y el inferior sube con el teclado. Va en el contenedor, no en el
     * layout, para que sobreviva a los {@code setContentView} posteriores (AddEventActivity cambia
     * de layout al alternar evento/tarea). Llamar tras el primer {@code setContentView}.
     */
    public static void applyToContent(@NonNull Activity activity) {
        applyWithIme(activity.findViewById(android.R.id.content), ALL);
    }

    private static void install(View view, int sides, boolean ime) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        final int mask = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
        // Altura fija (en px) que la vista tuviera al recibir los primeros insets: si es exacta, el
        // padding superior se la comería (FocusFragment fija la de su barra), así que se le suma.
        final int[] fixedHeight = { Integer.MIN_VALUE };

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(mask);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && (sides & TOP) != 0) {
                if (fixedHeight[0] == Integer.MIN_VALUE) fixedHeight[0] = lp.height;
                if (fixedHeight[0] > 0 && lp.height != fixedHeight[0] + bars.top) {
                    lp.height = fixedHeight[0] + bars.top;
                    v.setLayoutParams(lp);
                }
            }
            int bottomInset = (sides & BOTTOM) != 0 ? bars.bottom : 0;
            if (ime && (sides & BOTTOM) != 0) {
                bottomInset = Math.max(bottomInset,
                        windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            }
            boolean horizontal = (sides & HORIZONTAL) != 0;
            v.setPadding(
                    left + (horizontal ? bars.left : 0),
                    top + ((sides & TOP) != 0 ? bars.top : 0),
                    right + (horizontal ? bars.right : 0),
                    bottom + bottomInset);
            // Sin consumir: los hermanos y los hijos reciben los mismos insets y cada uno toma el
            // lado que le toca.
            return windowInsets;
        });
        requestApply(view);
    }

    private static void requestApply(View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }
                @Override public void onViewDetachedFromWindow(View v) { }
            });
        }
    }
}
