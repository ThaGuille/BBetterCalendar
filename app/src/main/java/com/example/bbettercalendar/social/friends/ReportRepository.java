package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.SerialExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Reportar a alguien (F2-u5, 04 §2, D-027, D-028 §4): {@code reports/{reporter_reported}} con los mapas de
 * {@link ReportWrites}, un motivo predefinido ({@link ReportReason}) y sin texto libre.
 *
 * <ul>
 *   <li>Con {@code context = REQUEST}, el reporte y el rechazo (borrar {@code friendRequests/{reported_me}})
 *       van en el mismo lote: las reglas ven la relación en el estado previo.</li>
 *   <li>Un {@code PERMISSION_DENIED} al crear es «ya reportado» (un segundo reporte sería un update, que
 *       las reglas deniegan): mismo agradecimiento. Si el lote con el rechazo se deniega, se prueba el
 *       reporte solo y después el rechazo solo, para que el rechazo no se pierda por un reporte repetido.</li>
 * </ul>
 * Regla #3: un {@link SerialExecutor} propio sobre el {@code @IoExecutor}; el {@link Callback} llega en ese
 * hilo de E/S (quien lo reciba usa {@code postValue}). Firebase solo a través de {@link FriendsBackend}.
 */
@Singleton
public class ReportRepository {

    public interface Callback {
        /** Reporte guardado; {@code alreadyReported} si ya existía uno tuyo sobre esa persona. */
        void onReported(boolean alreadyReported);

        void onError(@NonNull Throwable error);
    }

    static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    private final FriendsBackend backend;
    private final Executor serial;

    @Inject
    public ReportRepository(FirestoreFriendsBackend backend, @IoExecutor ExecutorService ioExecutor) {
        this(backend, new SerialExecutor(ioExecutor));
    }

    /** Para tests: Firebase solo por {@link FriendsBackend}. */
    ReportRepository(@NonNull FriendsBackend backend, @NonNull Executor serial) {
        this.backend = backend;
        this.serial = serial;
    }

    /**
     * Reporta a {@code reportedUid} ({@code @reportedHandle}). Con {@link ReportReason.Context#REQUEST}
     * también rechaza su solicitud en el mismo lote.
     */
    public void report(@NonNull String reportedUid, @NonNull String reportedHandle, @NonNull ReportReason reason,
                       @NonNull ReportReason.Context context, @NonNull Callback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            if (me == null) {
                callback.onError(new FriendsException(FriendsException.Code.NOT_SIGNED_IN));
                return;
            }
            String reportPath = ReportWrites.reportPath(me, reportedUid);
            String requestPath = FriendRequestWrites.requestPath(FriendRequestWrites.requestId(reportedUid, me));
            FriendsBackend.Batch batch = backend.batch()
                    .set(reportPath, ReportWrites.report(me, reportedUid, reportedHandle, reason, context));
            boolean withDecline = context == ReportReason.Context.REQUEST;
            if (withDecline) {
                batch.delete(requestPath);
            }
            batch.commit(serial, new FriendsBackend.Result<Void>() {
                @Override
                public void onSuccess(@Nullable Void value) {
                    callback.onReported(false);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    if (!isPermissionDenied(error)) {
                        callback.onError(error);
                    } else if (!withDecline) {
                        callback.onReported(true);
                    } else {
                        // ¿Reporte repetido o solicitud ya borrada? Reporte solo y luego rechazo solo.
                        reportAloneThenDecline(me, reportedUid, reportedHandle, reason, context, requestPath,
                                callback);
                    }
                }
            });
        });
    }

    private void reportAloneThenDecline(@NonNull String me, @NonNull String reportedUid,
                                        @NonNull String reportedHandle, @NonNull ReportReason reason,
                                        @NonNull ReportReason.Context context, @NonNull String requestPath,
                                        @NonNull Callback callback) {
        backend.batch()
                .set(ReportWrites.reportPath(me, reportedUid),
                        ReportWrites.report(me, reportedUid, reportedHandle, reason, context))
                .commit(serial, new FriendsBackend.Result<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void value) {
                        declineThen(requestPath, false, callback);
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        if (isPermissionDenied(error)) {
                            declineThen(requestPath, true, callback);
                        } else {
                            callback.onError(error);
                        }
                    }
                });
    }

    /** Rechazo solo; si ya no existe (denegado), da igual: el reporte es lo que cuenta. */
    private void declineThen(@NonNull String requestPath, boolean alreadyReported, @NonNull Callback callback) {
        backend.batch().delete(requestPath).commit(serial, new FriendsBackend.Result<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                callback.onReported(alreadyReported);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                if (isPermissionDenied(error)) {
                    callback.onReported(alreadyReported);
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    static boolean isPermissionDenied(@NonNull Throwable error) {
        return error instanceof ListenerException && PERMISSION_DENIED.equals(((ListenerException) error).code);
    }
}
