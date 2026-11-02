package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link InteractionBackend} sobre Functions y Auth (F5-u5): el único sitio de {@code social/interaction} con Firebase. */
@Singleton
public class FunctionsInteractionBackend implements InteractionBackend {

    private final FirebaseAuth auth;
    private final FirebaseFunctions functions;

    @Inject
    public FunctionsInteractionBackend(FirebaseAuth auth, FirebaseFunctions functions) {
        this.auth = auth;
        this.functions = functions;
    }

    @Nullable
    @Override
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void call(@NonNull String callable, @NonNull String friendUid, @NonNull String dayKey,
                     @NonNull Executor executor, @NonNull Result result) {
        Map<String, Object> data = new HashMap<>();
        data.put("friendUid", friendUid);
        data.put("dayKey", dayKey);
        functions.getHttpsCallable(callable).call(data)
                .addOnSuccessListener(executor, r -> {
                    Object body = r.getData();
                    result.onSuccess(body instanceof Map ? (Map<String, Object>) body : null);
                })
                .addOnFailureListener(executor, e -> {
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException f = (FirebaseFunctionsException) e;
                        result.onError(new Failure(f.getCode().name(), f.getMessage(), e));
                    } else {
                        result.onError(new Failure("UNKNOWN", e.getMessage(), e));
                    }
                });
    }
}
