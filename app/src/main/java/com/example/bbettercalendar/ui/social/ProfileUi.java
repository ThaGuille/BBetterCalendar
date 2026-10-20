package com.example.bbettercalendar.ui.social;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.profile.HandleValidator;
import com.example.bbettercalendar.ui.common.widget.AvatarView;

/**
 * F1-u5: piezas compartidas por Crear perfil y Mi perfil (texto del estado del {@code @usuario},
 * pintado del avatar y un TextWatcher mínimo).
 */
final class ProfileUi {

    private ProfileUi() { }

    /** Un TextWatcher que solo avisa de que el texto cambió. */
    abstract static class OnTextChanged implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            onChanged(s.toString());
        }

        abstract void onChanged(@NonNull String text);
    }

    /** Pinta el color y las iniciales de un perfil (o de lo que se está editando) en el avatar. */
    static void paintAvatar(@NonNull AvatarView avatar, @Nullable String name, int colorIndex) {
        avatar.setName(name == null ? "" : name.trim());
        avatar.setFillColor(AvatarPalette.color(avatar.getContext(), colorIndex));
        avatar.setInitialsColor(AvatarPalette.onColor(avatar.getContext()));
    }

    /**
     * Pone el texto y el estilo (error / correcto / neutro) de la línea bajo el campo de {@code @usuario}.
     *
     * @param idleText cadena a enseñar sin nada que decir, o 0 para ocultar la línea
     */
    static void renderHandleStatus(@NonNull TextView view, @NonNull ProfileViewModel.HandleStatus status,
                                   @NonNull String rawHandle, @StringRes int idleText) {
        int text;
        int style;
        switch (status.kind) {
            case INVALID:
                text = invalidText(status.invalid);
                style = R.style.TextAppearance_BBetter_SocialError;
                break;
            case CHECKING:
                text = R.string.handle_status_checking;
                style = R.style.TextAppearance_BBetter_Caption;
                break;
            case FREE:
                view.setTextAppearance(R.style.TextAppearance_BBetter_SocialOk);
                view.setText(view.getContext().getString(R.string.handle_status_free,
                        HandleValidator.normalizeHandle(rawHandle)));
                view.setVisibility(TextView.VISIBLE);
                return;
            case TAKEN:
                text = R.string.handle_status_taken;
                style = R.style.TextAppearance_BBetter_SocialError;
                break;
            case ERROR:
                text = R.string.handle_status_error;
                style = R.style.TextAppearance_BBetter_SocialError;
                break;
            default:
                if (idleText == 0) {
                    view.setVisibility(TextView.GONE);
                    return;
                }
                text = idleText;
                style = R.style.TextAppearance_BBetter_Caption;
                break;
        }
        view.setTextAppearance(style);
        view.setText(text);
        view.setVisibility(TextView.VISIBLE);
    }

    @StringRes
    private static int invalidText(@Nullable HandleValidator.HandleResult result) {
        if (result == null) {
            return R.string.handle_status_invalid_chars;
        }
        switch (result) {
            case TOO_SHORT:
                return R.string.handle_status_too_short;
            case TOO_LONG:
                return R.string.handle_status_too_long;
            case RESERVED:
                return R.string.handle_status_reserved;
            case BANNED:
                return R.string.handle_status_banned;
            default:
                return R.string.handle_status_invalid_chars;
        }
    }

    /** Cadena del error de guardado. */
    @StringRes
    static int failureText(@Nullable ProfileViewModel.Failure failure) {
        if (failure == null) {
            return R.string.profile_error_generic;
        }
        switch (failure) {
            case HANDLE_TAKEN:
                return R.string.profile_error_taken;
            case RENAME_TOO_SOON:
                return R.string.profile_error_rename_soon;
            case NETWORK:
                return R.string.profile_error_network;
            case INVALID:
                return R.string.profile_error_invalid;
            default:
                return R.string.profile_error_generic;
        }
    }
}
