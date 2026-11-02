package com.example.bbettercalendar.social.interaction;

/** Lo que devuelve enviar un ánimo o un empujón (F5-u5). */
public enum InteractionResult {
    /** Enviado ahora. */
    SENT,
    /** El servidor dice que ya se envió hoy a ese amigo (la última palabra es suya). */
    ALREADY_SENT,
    /** Solo empujón: el amigo ya ha estudiado hoy. */
    STUDIED_TODAY,
    /** Ya no es amigo. */
    NOT_FRIENDS,
    /** Cualquier otro fallo (sin sesión, sin red, sin perfil, día fuera de rango…). */
    ERROR
}
