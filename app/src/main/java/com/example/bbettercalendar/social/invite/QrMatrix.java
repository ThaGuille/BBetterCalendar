package com.example.bbettercalendar.social.invite;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * El QR de un enlace de invitación como matriz de módulos (F2-u6): {@code QRCodeWriter} de ZXing core, margen
 * de 1 módulo y corrección M. Cada celda es un píxel (tamaño mínimo); la UI (F2-u8) monta el {@code Bitmap}
 * con {@link #toPixels(int, int)} y lo escala sin filtrar, oscuro sobre claro también en tema oscuro
 * ({@code bb_qr_foreground} / {@code bb_qr_background}). Puro: sin clases de Android.
 */
public final class QrMatrix {

    public static final int MARGIN = 1;
    public static final ErrorCorrectionLevel ERROR_CORRECTION = ErrorCorrectionLevel.M;

    private final int size;
    private final boolean[] dark;

    private QrMatrix(int size, @NonNull boolean[] dark) {
        this.size = size;
        this.dark = dark;
    }

    /** Codifica {@code text} (UTF-8). Un texto que no cabe en un QR lanza {@link IllegalArgumentException}. */
    @NonNull
    public static QrMatrix encode(@NonNull String text) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, MARGIN);
        hints.put(EncodeHintType.ERROR_CORRECTION, ERROR_CORRECTION);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix m;
        try {
            // Ancho y alto 0: ZXing usa el tamaño mínimo, un píxel por módulo más el margen.
            m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException e) {
            throw new IllegalArgumentException("Cannot encode QR", e);
        }
        int n = m.getWidth();
        boolean[] cells = new boolean[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                cells[y * n + x] = m.get(x, y);
            }
        }
        return new QrMatrix(n, cells);
    }

    /** Lado en módulos, margen incluido (versión v: 4v + 17 + 2·{@link #MARGIN}). */
    public int size() {
        return size;
    }

    public boolean isDark(int x, int y) {
        return dark[y * size + x];
    }

    /** Píxeles fila a fila ({@code size × size}) para {@code Bitmap.createBitmap(pixels, size, size, ARGB_8888)}. */
    @NonNull
    public int[] toPixels(int darkColor, int lightColor) {
        int[] px = new int[dark.length];
        for (int i = 0; i < dark.length; i++) {
            px[i] = dark[i] ? darkColor : lightColor;
        }
        return px;
    }
}
