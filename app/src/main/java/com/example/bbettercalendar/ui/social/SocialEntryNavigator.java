package com.example.bbettercalendar.ui.social;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.account.SocialEntryRouter;

/**
 * F1-u4: lleva a la pantalla que le toca a cada {@link SocialEntryRouter.Step}. Compartido por
 * Focus, Home y las pantallas de {@code ui/social}. {@code popUpTo} saca de la pila la pantalla
 * desde la que se navega (puerta de edad, hoja), de modo que «atrás» vuelve al origen.
 */
public final class SocialEntryNavigator {

    private SocialEntryNavigator() { }

    /** Navega desde un bloque social (Focus / Home): el origen se queda en la pila. */
    public static void go(@NonNull Fragment from, @NonNull SocialEntryRouter.Step step) {
        navigate(NavHostFragment.findNavController(from), step, 0, EntryTarget.MY_PROFILE);
    }

    /** F2-u7: igual, pero {@code DESTINATION} lleva a donde pidió {@code target} (Mi perfil o Amigos). */
    public static void go(@NonNull Fragment from, @NonNull SocialEntryRouter.Step step,
                          @NonNull EntryTarget target) {
        navigate(NavHostFragment.findNavController(from), step, 0, target);
    }

    /** Navega desde una pantalla de entrada, que se retira de la pila. */
    public static void goReplacing(@NonNull Fragment from, @NonNull SocialEntryRouter.Step step,
                                   int currentDestinationId) {
        navigate(NavHostFragment.findNavController(from), step, currentDestinationId, EntryTarget.MY_PROFILE);
    }

    private static void navigate(@NonNull NavController nav, @NonNull SocialEntryRouter.Step step,
                                 int popUpToInclusive, @NonNull EntryTarget target) {
        int destination;
        Bundle args = null;
        switch (step) {
            case AGE_GATE:
                destination = R.id.navigation_age_gate;
                break;
            case UNDERAGE:
                destination = R.id.navigation_underage;
                break;
            case JOIN:
                destination = R.id.navigation_join_friends;
                break;
            case CREATE_PROFILE:
                destination = R.id.navigation_create_profile;
                break;
            case ACCEPT_TERMS:
            case DESTINATION:
            default:
                // F1-u5: Mi perfil es el destino de la cuenta. ACCEPT_TERMS no puede darse mientras
                // TERMS_VERSION sea 1; su pantalla de re-aceptación queda para cuando suba.
                destination = R.id.navigation_my_profile;
                if (step == SocialEntryRouter.Step.DESTINATION && target != EntryTarget.MY_PROFILE) {
                    // F2-u7: Amigos; FRIEND_REQUESTS sube hasta las solicitudes (argumento section).
                    destination = R.id.navigation_friends;
                    args = new Bundle();
                    args.putString("section", target == EntryTarget.FRIEND_REQUESTS ? "requests" : "");
                }
                break;
        }
        NavOptions options = popUpToInclusive == 0 ? null
                : new NavOptions.Builder().setPopUpTo(popUpToInclusive, true).build();
        nav.navigate(destination, args, options);
    }
}
