package com.example.bbettercalendar.ui.social;

/**
 * F2-u7 (D-012): a dónde quiere llegar quien toca un bloque social, una vez que el router
 * ({@code SocialEntryRouter}) dice {@code DESTINATION}. Los pasos previos (edad, entrar, crear perfil)
 * son los mismos para todos; solo cambia el destino final.
 */
public enum EntryTarget {
    /** Mi perfil: el avatar de Home y el destino por defecto ({@code requestEntry()}). */
    MY_PROFILE,
    /** La lista de Amigos: la tarjeta de Home y la cabecera del bloque social de Focus. */
    FRIENDS,
    /** Amigos, subido hasta las solicitudes (la campana de Home, en u8). */
    FRIEND_REQUESTS,
    /** F2-u8: la hoja Invitar (el botón de Focus). Quien atiende {@code DESTINATION} la abre; no es una pantalla del grafo. */
    INVITE
}
