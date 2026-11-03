package com.example.bbettercalendar.social.block;

/** Lo que devuelve bloquear a alguien (L-u9). */
public enum BlockResult {
    /** El servidor confirmó el bloqueo ({@code {status: 'blocked'}}). */
    BLOCKED,
    /** Cualquier otro fallo (sin sesión, sin red, respuesta rara, argumento inválido…). */
    ERROR
}
