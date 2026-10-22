package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/** F2-u6: {@link QrMatrix} codifica el enlace y el {@code QRCodeReader} de ZXing lo vuelve a leer (sin Android). */
public class QrMatrixTest {

    private static final int DARK = 0xFF000000;
    private static final int LIGHT = 0xFFFFFFFF;

    @Test
    public void encodeThenDecodeGivesTheSameLink() throws Exception {
        String link = InviteLinkFactory.link("ABCD2345");
        QrMatrix qr = QrMatrix.encode(link);

        // Escalado ×4 sin filtrar, como lo pintará la UI.
        int scale = 4;
        int n = qr.size() * scale;
        int[] small = qr.toPixels(DARK, LIGHT);
        int[] pixels = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                pixels[y * n + x] = small[(y / scale) * qr.size() + (x / scale)];
            }
        }
        LuminanceSource source = new RGBLuminanceSource(n, n, pixels);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        Result result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
        assertEquals(link, result.getText());
    }

    @Test
    public void matrixHasTheOneModuleMarginAndQrSize() {
        QrMatrix qr = QrMatrix.encode(InviteLinkFactory.link("ZZZZ9999"));
        int n = qr.size();
        // 4v + 17 módulos más 1 de margen por lado.
        assertEquals(0, (n - 2 * QrMatrix.MARGIN - 17) % 4);
        assertTrue(n >= 21 + 2 * QrMatrix.MARGIN);
        for (int i = 0; i < n; i++) {
            assertFalse(qr.isDark(i, 0));
            assertFalse(qr.isDark(0, i));
            assertFalse(qr.isDark(i, n - 1));
            assertFalse(qr.isDark(n - 1, i));
        }
        // Esquina del patrón de posición (arriba a la izquierda), justo dentro del margen.
        assertTrue(qr.isDark(QrMatrix.MARGIN, QrMatrix.MARGIN));
        int[] px = qr.toPixels(DARK, LIGHT);
        assertEquals(n * n, px.length);
        assertEquals(DARK, px[QrMatrix.MARGIN * n + QrMatrix.MARGIN]);
        assertEquals(LIGHT, px[0]);
    }
}
