package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

// Acuse de un consentimiento del usuario (p. ej. la divulgación de Usage Access antes del
// deep-link a Ajustes). Persistir la aceptación nos deja probar el consentimiento afirmativo
// que pide Play y volver a pedirlo si cambia el texto (disclosureVersion).
@Entity(tableName = "consent_record")
public class ConsentRecord {
    public static final String KEY_USAGE_ACCESS = "usage_access";
    public static final String KEY_ACCESSIBILITY_BLOCKING = "accessibility_blocking";

    // Versión del texto de divulgación de Usage Access. Subir este número fuerza a volver a mostrar
    // la divulgación (consentimiento afirmativo sobre el texto nuevo) aunque ya se hubiera aceptado.
    public static final int USAGE_ACCESS_DISCLOSURE_VERSION = 1;

    // Versión del texto de divulgación del bloqueo por Accesibilidad (Phase 4a). Misma semántica:
    // subirlo re-pide el consentimiento afirmativo sobre el texto nuevo.
    public static final int ACCESSIBILITY_DISCLOSURE_VERSION = 1;

    @PrimaryKey
    @NonNull
    public String key;            // identificador del consentimiento (p. ej. "usage_access")
    public long acceptedAt;       // System.currentTimeMillis() en el momento de aceptar
    public int disclosureVersion; // versión del texto aceptado (re-pedir si sube)

    public ConsentRecord() {
        this.key = "";
    }

    @Ignore
    public ConsentRecord(@NonNull String key, long acceptedAt, int disclosureVersion) {
        this.key = key;
        this.acceptedAt = acceptedAt;
        this.disclosureVersion = disclosureVersion;
    }
}
