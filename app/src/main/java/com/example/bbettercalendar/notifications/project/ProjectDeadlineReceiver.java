package com.example.bbettercalendar.notifications.project;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Recibe las alarmas de {@link ProjectDeadlineScheduler}. Re-lee el proyecto en el executor de IO
 * (goAsync, igual que EventReminderReceiver) porque la alarma se programó en el pasado y el estado
 * pudo cambiar desde entonces: una alarma obsoleta no debe notificar.
 */
@AndroidEntryPoint
public class ProjectDeadlineReceiver extends BroadcastReceiver {

    private static final String TAG = "ProjectDeadlineRcv";

    // Margen sobre el offset al validar la alarma: una alarma inexacta (fallback cuando el
    // dispositivo deniega las exactas) puede llegar tarde, y eso no la hace obsoleta.
    private static final long STALE_GRACE_MILLIS = 5L * 60_000L;

    @Inject ProjectDAO projectDao;
    @Inject BBetterNotifier notifier;
    @Inject @IoExecutor ExecutorService IO;

    @Override
    public void onReceive(Context context, Intent intent) {
        final int projectId = intent.getIntExtra(ProjectDeadlineScheduler.EXTRA_PROJECT_ID, -1);
        final int offsetIndex = intent.getIntExtra(ProjectDeadlineScheduler.EXTRA_OFFSET_INDEX, -1);
        if (projectId < 0 || offsetIndex < 0) return;

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        IO.execute(() -> {
            try {
                Project project = projectDao.getById(projectId);
                if (!shouldNotify(project, offsetIndex, System.currentTimeMillis())) {
                    Log.i(TAG, "Dropping stale deadline alarm for project " + projectId);
                    return;
                }
                fireNotification(appContext, project, offsetIndex);
            } catch (Exception e) {
                Log.e(TAG, "onReceive failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    /**
     * Un aviso sólo es válido si el proyecto sigue existiendo, sigue ACTIVE y el deadline sigue
     * donde estaba: si se movió hacia adelante, {@code deadline - now} sería mucho mayor que el
     * offset de esta alarma y el aviso llegaría fuera de sitio. Un deadline ya pasado tampoco
     * notifica (fuera de alcance: no hay escalado de vencidos).
     *
     * <p>La ventana válida es un intervalo, no sólo un techo. {@code OFFSET_MILLIS} va de mayor a
     * menor, así que la alarma del índice {@code i} manda mientras {@code remaining} caiga entre el
     * offset del índice <em>siguiente</em> (excluido, es el suelo) y el suyo propio más la gracia
     * (incluido, es el techo). Sin ese suelo, una alarma de 3 días que el SO difiera hasta que
     * faltan 2 horas seguiría notificando "faltan 3 días", porque el cuerpo lo elige el índice de
     * la alarma y no el tiempo restante; esa banda es de la alarma del offset siguiente, que ya
     * avisa con el texto correcto.
     */
    static boolean shouldNotify(Project project, int offsetIndex, long nowMillis) {
        if (project == null) return false;
        if (project.status != Project.STATUS_ACTIVE) return false;
        if (offsetIndex < 0 || offsetIndex >= ProjectDeadlineScheduler.OFFSET_MILLIS.length) return false;

        long remaining = project.softDeadlineMillis - nowMillis;
        if (remaining <= 0L) return false;
        if (remaining > ProjectDeadlineScheduler.OFFSET_MILLIS[offsetIndex] + STALE_GRACE_MILLIS) {
            return false;
        }

        int nextIndex = offsetIndex + 1;
        if (nextIndex < ProjectDeadlineScheduler.OFFSET_MILLIS.length) {
            return remaining > ProjectDeadlineScheduler.OFFSET_MILLIS[nextIndex];
        }
        return true;
    }

    private void fireNotification(Context context, Project project, int offsetIndex) {
        String title = project.name != null && !project.name.trim().isEmpty()
                ? project.name
                : context.getString(R.string.notif_project_deadline_default_title);
        String body = context.getString(ProjectDeadlineScheduler.bodyResFor(offsetIndex));

        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_PROJECT_DEADLINES,
                ProjectDeadlineScheduler.notificationIdFor(project.id, offsetIndex))
                .title(title)
                .body(body)
                .openProjectDetail(context, project.id)
                .build();

        notifier.notify(spec);
    }
}
