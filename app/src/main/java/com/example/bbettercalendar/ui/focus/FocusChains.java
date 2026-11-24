package com.example.bbettercalendar.ui.focus;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Cadenas de Focus con el bloqueo de apps activo y la concentración en marcha (diseño A2g del
 * prototipo «Cadenas en Focus»). Cuatro cadenas rectas van de las esquinas de la zona del temporizador
 * (bajo la barra superior hasta bajo los botones, antes de Hoy / Racha) al anillo. Tres se atan con
 * una abrazadera atornillada al anillo; la de arriba a la derecha llega suelta y la engancha un candado.
 *
 * <p>Dos capas, sin tocar el layout: las cadenas son el fondo del contenido (van detrás del chip, el
 * anillo y los botones) y las abrazaderas y el candado van en el overlay del contenido, sobre el anillo.
 * Las cadenas se pintan al 70 % como un todo (una capa con alfa, para que los eslabones no se sumen
 * donde se tocan); las piezas de encima son opacas, del mismo tono que da la cadena sobre el fondo,
 * porque si se transparentan el anillo ámbar se ve a través y parecen pegatinas.
 *
 * <p>La geometría sale en cada dibujado de las posiciones actuales del anillo, la fila de botones y la
 * tarjeta Hoy / Racha (hijos directos del contenido). El calendario está en {@link FocusChainsMotion}.
 */
final class FocusChains {

    /** Llamado al terminar la entrada (el candado ya está cerrado). */
    interface OnLockedListener {
        void onLocked();
    }

    private enum Phase { HIDDEN, ENTERING, SHOWN, EXITING }

    // Medidas del prototipo, en dp.
    private static final float LINK_STEP = 9.4f;
    private static final float LINK_WIDTH = 7.4f;
    private static final float LINK_STROKE = 1.85f;
    /** La cadena empieza un poco más allá de la esquina para que no se vea su extremo. */
    private static final float CHAIN_OVERHANG = 12f;
    /** Donde acaba la cadena, medido desde el trazo del anillo: con abrazadera y con candado. */
    private static final float CHAIN_STOP_PLATE = 18.5f;
    private static final float CHAIN_STOP_LOCK = 12.7f;
    private static final float OVERSHOOT = 5f;
    /** Margen extra para sacar la cadena de la pantalla al retirarla. */
    private static final float RETRACT_MARGIN = 24f;
    /** La zona acaba al menos esto bajo los botones y esto antes de la tarjeta Hoy / Racha. */
    private static final float ZONE_BELOW_CONTROLS = 5f;
    private static final float ZONE_ABOVE_STATS = 8f;
    private static final float LOCK_SCALE = 1.2f;
    private static final float LOCK_DROP = 16f;
    /** Radio del anillo dentro de su caja (FocusRingView: 118 / 270). */
    private static final float RING_RADIUS_OF_BOX = 118f / 270f;

    private static final float CHAIN_ALPHA = 0.7f;

    private final ViewGroup content;
    private final View ringBox;
    private final View controls;
    private final View stats;
    private final float dp;

    private final Paint linkStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linkFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint metalFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint metalStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path shackle = new Path();

    private final Drawable under = new Layer(true);
    private final Drawable over = new Layer(false);

    // Geometría actual (px, coordenadas del contenido)
    private float width;
    private float zoneBottom;
    private float cx;
    private float cy;
    private float radius;

    private Phase phase = Phase.HIDDEN;
    /** Milisegundos desde que empezó la entrada o la salida en curso. */
    private long t;
    /** Al empezar una salida: dónde estaba cada cadena y la opacidad del candado. */
    private final double[] exitFrom = new double[FocusChainsMotion.CORNERS];
    private double exitLockFrom = 1;
    @Nullable private ValueAnimator animator;
    @Nullable private OnLockedListener onLocked;

    private final View.OnLayoutChangeListener boundsKeeper =
            (v, l, top, r, b, ol, ot, or, ob) -> over.setBounds(0, 0, r - l, b - top);

    FocusChains(@NonNull ViewGroup content, @NonNull View ringBox, @NonNull View controls,
                @NonNull View stats) {
        this.content = content;
        this.ringBox = ringBox;
        this.controls = controls;
        this.stats = stats;
        Context context = content.getContext();
        dp = context.getResources().getDisplayMetrics().density;

        @ColorInt int chain = ContextCompat.getColor(context, R.color.bb_sanct_chain);
        @ColorInt int surface = ContextCompat.getColor(context, R.color.bb_sanct_surface);
        @ColorInt int metal = ColorUtils.blendARGB(surface, chain, CHAIN_ALPHA);

        linkStroke.setStyle(Paint.Style.STROKE);
        linkStroke.setStrokeWidth(LINK_STROKE * dp);
        linkStroke.setColor(chain);
        linkFill.setColor(chain);
        metalFill.setColor(metal);
        metalStroke.setStyle(Paint.Style.STROKE);
        metalStroke.setColor(metal);
        hole.setColor(surface);

        content.setBackground(under);
        content.getOverlay().add(over);
        over.setBounds(0, 0, content.getWidth(), content.getHeight());
        content.addOnLayoutChangeListener(boundsKeeper);
    }

    /**
     * Pone o quita las cadenas. Con {@code animate} y las animaciones del sistema activas, entran desde
     * las esquinas o vuelven a ellas; si no, aparecen o desaparecen sin más.
     */
    void setLocked(boolean locked, boolean animate, @Nullable OnLockedListener listener) {
        boolean showing = phase == Phase.ENTERING || phase == Phase.SHOWN;
        if (locked == showing) {
            return;
        }
        boolean motion = animate && Motion.enabled(content.getContext());
        if (locked) {
            onLocked = listener;
            if (motion) {
                run(Phase.ENTERING, FocusChainsMotion.ENTER_MS);
            } else {
                settle(Phase.SHOWN);
            }
        } else {
            onLocked = null;
            if (motion && measure()) {
                // La salida parte de donde estaba cada cadena (también a mitad de la entrada)
                for (int c = 0; c < FocusChainsMotion.CORNERS; c++) {
                    exitFrom[c] = shift(c);
                }
                exitLockFrom = lockAlpha();
                run(Phase.EXITING, FocusChainsMotion.EXIT_MS);
            } else {
                settle(Phase.HIDDEN);
            }
        }
    }

    /** Para la animación y quita las dos capas (onDestroyView). */
    void release() {
        cancel();
        onLocked = null;
        content.removeOnLayoutChangeListener(boundsKeeper);
        content.getOverlay().remove(over);
        if (content.getBackground() == under) {
            content.setBackground(null);
        }
    }

    // ================================================================== animación

    private void run(@NonNull Phase next, long duration) {
        cancel();
        phase = next;
        t = 0;
        ValueAnimator a = ValueAnimator.ofFloat(0f, duration);
        a.setDuration(duration);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(v -> {
            t = (long) (float) (Float) v.getAnimatedValue();
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled || animator != animation) {
                    return;
                }
                animator = null;
                settle(next == Phase.ENTERING ? Phase.SHOWN : Phase.HIDDEN);
            }
        });
        animator = a;
        a.start();
        invalidate();
    }

    private void settle(@NonNull Phase end) {
        cancel();
        phase = end;
        invalidate();
        if (end == Phase.SHOWN && onLocked != null) {
            OnLockedListener l = onLocked;
            onLocked = null;
            l.onLocked();
        }
    }

    private void cancel() {
        ValueAnimator a = animator;
        animator = null;
        if (a != null) {
            a.cancel();
        }
    }

    private void invalidate() {
        under.invalidateSelf();
        over.invalidateSelf();
    }

    /** Desplazamiento actual de la cadena de una esquina hacia el centro (px). */
    private double shift(int corner) {
        double retracted = retractDistance(corner);
        switch (phase) {
            case ENTERING:
                return FocusChainsMotion.enterShift(corner, t, retracted, OVERSHOOT * dp);
            case EXITING:
                return FocusChainsMotion.exitShift(t, exitFrom[corner], retracted);
            default:
                return 0;
        }
    }

    private double lockAlpha() {
        switch (phase) {
            case ENTERING:
                return FocusChainsMotion.lockAlpha(t);
            case EXITING:
                return FocusChainsMotion.exitLockAlpha(t, exitLockFrom);
            default:
                return 1;
        }
    }

    // ================================================================== geometría

    /** Recalcula la zona y el anillo; false si aún no hay medidas. */
    private boolean measure() {
        if (ringBox.getWidth() == 0 || content.getWidth() == 0) {
            return false;
        }
        width = content.getWidth();
        cx = ringBox.getLeft() + ringBox.getWidth() / 2f;
        cy = ringBox.getTop() + ringBox.getHeight() / 2f;
        radius = Math.min(ringBox.getWidth(), ringBox.getHeight()) * RING_RADIUS_OF_BOX;
        // Simétrica respecto al anillo si cabe, nunca por encima de los botones ni sobre Hoy / Racha
        float bottom = Math.max(2 * cy, controls.getBottom() + ZONE_BELOW_CONTROLS * dp);
        zoneBottom = Math.min(bottom, stats.getTop() - ZONE_ABOVE_STATS * dp);
        return true;
    }

    private float cornerX(int corner) {
        return corner == 0 || corner == 2 ? 0 : width;
    }

    private float cornerY(int corner) {
        return corner < 2 ? 0 : zoneBottom;
    }

    /** Distancia de la esquina al centro del anillo. */
    private float cornerDistance(int corner) {
        return (float) Math.hypot(cx - cornerX(corner), cy - cornerY(corner));
    }

    private double retractDistance(int corner) {
        return cornerDistance(corner) - (radius - 6.5f * dp) + RETRACT_MARGIN * dp;
    }

    // ================================================================== dibujo

    private void drawUnder(@NonNull Canvas canvas) {
        if (phase == Phase.HIDDEN || !measure()) {
            return;
        }
        float pad = CHAIN_OVERHANG * dp;
        int save = canvas.saveLayerAlpha(-pad, -pad, width + pad, zoneBottom + pad,
                Math.round(255 * CHAIN_ALPHA));
        for (int c = 0; c < FocusChainsMotion.CORNERS; c++) {
            float d = cornerDistance(c);
            float ux = (cx - cornerX(c)) / d;
            float uy = (cy - cornerY(c)) / d;
            float stop = radius + (c == FocusChainsMotion.LOCK_CORNER ? CHAIN_STOP_LOCK : CHAIN_STOP_PLATE) * dp;
            float s = (float) shift(c);
            canvas.save();
            canvas.translate(ux * s, uy * s);
            drawChain(canvas, cornerX(c) - ux * pad, cornerY(c) - uy * pad, cx - ux * stop, cy - uy * stop);
            canvas.restore();
        }
        canvas.restoreToCount(save);
    }

    /** Eslabones alternos: uno de frente (óvalo hueco) y uno de canto (barra corta). */
    private void drawChain(@NonNull Canvas canvas, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.hypot(dx, dy);
        int n = Math.max(3, Math.round(length / (LINK_STEP * dp)) + 1);
        if (n % 2 == 0) {
            n++;
        }
        float step = length / (n - 1);
        float faceLength = step * 1.62f;
        float faceWidth = LINK_WIDTH * dp;
        float stroke = LINK_STROKE * dp;
        float edgeLength = step * 1.05f;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            canvas.save();
            canvas.translate(ax + dx * f, ay + dy * f);
            canvas.rotate(angle);
            if (i % 2 == 0) {
                rect.set(-faceLength / 2, -faceWidth / 2, faceLength / 2, faceWidth / 2);
                canvas.drawRoundRect(rect, faceWidth / 2, faceWidth / 2, linkStroke);
            } else {
                float h = stroke * 0.62f;
                rect.set(-edgeLength / 2, -h, edgeLength / 2, h);
                canvas.drawRoundRect(rect, h, h, linkFill);
            }
            canvas.restore();
        }
    }

    private void drawOver(@NonNull Canvas canvas) {
        if (phase == Phase.HIDDEN || !measure()) {
            return;
        }
        for (int c = 0; c < FocusChainsMotion.CORNERS; c++) {
            if (c == FocusChainsMotion.LOCK_CORNER) {
                continue;
            }
            float d = cornerDistance(c);
            float ux = (cx - cornerX(c)) / d;
            float uy = (cy - cornerY(c)) / d;
            float s = (float) shift(c);
            canvas.save();
            canvas.translate(cx - ux * radius + ux * s, cy - uy * radius + uy * s);
            canvas.rotate((float) Math.toDegrees(Math.atan2(-uy, -ux))); // +x hacia la esquina
            drawPlate(canvas);
            canvas.restore();
        }
        drawLock(canvas);
    }

    /** Abrazadera: placa con dos remaches sobre el anillo y una anilla hacia la cadena. */
    private void drawPlate(@NonNull Canvas canvas) {
        metalStroke.setStrokeWidth(2.2f * dp);
        canvas.drawCircle(10.1f * dp, 0, 4.2f * dp, metalStroke);
        rect.set(-6.5f * dp, -11.5f * dp, 6.5f * dp, 11.5f * dp);
        canvas.drawRoundRect(rect, 3.2f * dp, 3.2f * dp, metalFill);
        canvas.drawCircle(0, -6.8f * dp, 1.8f * dp, hole);
        canvas.drawCircle(0, 6.8f * dp, 1.8f * dp, hole);
    }

    /**
     * Candado recto en el trazo del anillo, arriba a la derecha: su arco abraza el anillo y la cadena.
     * La pata izquierda es corta y sale del cuerpo al abrirse; la derecha es larga y se queda dentro.
     */
    private void drawLock(@NonNull Canvas canvas) {
        double alpha = lockAlpha();
        if (alpha <= 0) {
            return;
        }
        int c = FocusChainsMotion.LOCK_CORNER;
        float d = cornerDistance(c);
        float px = cx - (cx - cornerX(c)) / d * radius;
        float py = cy - (cy - cornerY(c)) / d * radius;
        float s = LOCK_SCALE * dp;
        float r = 5.2f * s;
        float top = 4f * s;
        double lift = phase == Phase.ENTERING ? FocusChainsMotion.lockLift(t) : 0;
        double open = phase == Phase.ENTERING ? FocusChainsMotion.shackleOpen(t) : 0;

        int save = alpha < 1
                ? canvas.saveLayerAlpha(px - 12 * s, py - 12 * s - LOCK_DROP * dp, px + 12 * s, py + 22 * s,
                        (int) Math.round(255 * alpha))
                : canvas.save();
        canvas.translate(px, py - (float) (lift * LOCK_DROP * dp));

        canvas.save();
        canvas.translate(0, (float) (-open * 5f * s));
        shackle.reset();
        shackle.moveTo(-r, top + 0.8f * s);
        shackle.lineTo(-r, 0);
        rect.set(-r, -r, r, r);
        shackle.arcTo(rect, 180, 180, false);
        shackle.lineTo(r, top + 7f * s);
        metalStroke.setStrokeWidth(2.4f * s);
        canvas.drawPath(shackle, metalStroke);
        canvas.restore();

        rect.set(-9f * s, top, 9f * s, top + 15f * s);
        canvas.drawRoundRect(rect, 3.2f * s, 3.2f * s, metalFill);
        canvas.drawCircle(0, top + 6f * s, 2.1f * s, hole);
        rect.set(-0.95f * s, top + 6.5f * s, 0.95f * s, top + 11.1f * s);
        canvas.drawRoundRect(rect, 0.95f * s, 0.95f * s, hole);
        canvas.restoreToCount(save);
    }

    /** Una de las dos capas: fondo del contenido (cadenas) u overlay (abrazaderas y candado). */
    private final class Layer extends Drawable {
        private final boolean chains;

        Layer(boolean chains) {
            this.chains = chains;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (chains) {
                drawUnder(canvas);
            } else {
                drawOver(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
