package com.example.bbettercalendar.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.databinding.FragmentHomeBinding;
import com.example.bbettercalendar.notifications.focus.FocusCompleteNotifier;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.popups.AlertPopup;
import com.example.bbettercalendar.popups.FirstFocusCelebrationPopup;
import com.example.bbettercalendar.popups.FocusStreakPopup;
import com.example.bbettercalendar.popups.MessagePopup;
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.popups.TimerPopup;
import com.example.bbettercalendar.ui.common.SingleEvent;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.focus.FocusUiState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment implements View.OnClickListener, OnPopupListener<Object>, AccessibilityDisclosureDialog.OnConsentGrantedListener {

    private final String TAG = "HomeFragmentTag";
    private FragmentHomeBinding binding;
    HomeViewModel homeViewModel;
    // El pomodoro (máquina de estados, ticker, gracia por segundo plano, guardado) vive en este
    // ViewModel de ÁMBITO DE ACTIVIDAD (migración visual, u2): Home sólo observa y delega.
    private FocusSessionViewModel focusSession;
    // Último estado pintado: sólo se re-pintan los controles cuando cambia algo más que el texto.
    private FocusUiState lastRenderedState;
    private AlertPopup alertPopup = new AlertPopup();
    private TimerPopup timerPopup = new TimerPopup();
    private MessagePopup messagePopup;

    private ImageButton playButton;
    private ImageButton cancelButton;
    private TextView skipRestButton;
    private TextView blockModeButton;
    private TextView focusBanner;
    private TextView timerText;
    private TextView todayFailsText;
    private TextView todayTimeStudiedText;
    private TextView timerModeText;
    // Focus mode (spec focus-mode-and-streak): con el timer en marcha la tarjeta del pomodoro
    // ocupa la pantalla y desaparece todo lo demás. Es un estado DERIVADO del estado de la sesión
    // (FocusUiState.isSessionActive()), no una preferencia: se recalcula al re-renderizar.
    private boolean focusModeActive = false;

    // "Today" task list (spec tasks-home-today)
    private TodayTaskAdapter todayTaskAdapter;
    private TodayTaskAdapter overdueTaskAdapter;
    private boolean overdueExpanded = false;

    @Inject
    ConfigurationManager configurationManager;

    @Inject
    FocusCompleteNotifier focusCompleteNotifier;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        focusSession = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        playButton = binding.homePlayButton;
        playButton.setOnClickListener(this);
        cancelButton = binding.homeCancelButton;
        cancelButton.setOnClickListener(this);
        skipRestButton = binding.homeSkipRestButton;
        skipRestButton.setOnClickListener(this);
        blockModeButton = binding.homeBlockModeButton;
        blockModeButton.setOnClickListener(this);
        focusBanner = binding.homeFocusBanner;
        // Tap en el banner = desvincular la sesión en curso (spec focus-attribution).
        focusBanner.setOnClickListener(v -> {
            FocusTarget.clear();
            updateFocusBanner();
        });
        timerText = binding.homeTimerText;
        todayFailsText = binding.homeToadyFailsText;
        todayTimeStudiedText = binding.homeTodayTimeText;
        timerModeText = binding.homeTimerTypeText;

        // Racha y configuración del timer vivían en la toolbar; ahora están en el propio contenido
        // (spec frontend-structure-cleanup).
        binding.homeStreakButton.setOnClickListener(v -> showStreakPopup());
        binding.homeTimerSettingsButton.setOnClickListener(v -> showTimerSettings());

        alertPopup.setOnPopupListener(this);
        timerPopup.setOnPopupListener(this);
        messagePopup = new MessagePopup();
        messagePopup.setOnPopupListener(this);



        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        // Texto, modo, controles y focus mode salen del estado de la sesión (antes: timerText /
        // timerModeText de HomeViewModel + escrituras directas desde el contador del fragment).
        lastRenderedState = null;
        focusSession.getUiState().observe(getViewLifecycleOwner(), this::renderTimer);
        focusSession.getCycleCompletedEvents()
                .observe(getViewLifecycleOwner(), this::maybeShowCycleFinished);
        focusSession.getSessionFailedEvents()
                .observe(getViewLifecycleOwner(), this::maybeShowSessionFailed);
        // Los fallos ya no tienen tarjeta propia: acompañan a la fecha y sólo si los hay
        // (spec focus-mode-and-streak).
        homeViewModel.getTodayFailsText().observe(getViewLifecycleOwner(), fails -> {
            todayFailsText.setText(fails);
            boolean any = fails != null && !fails.isEmpty() && !"0".equals(fails);
            binding.homeFailsChip.setVisibility(any ? View.VISIBLE : View.GONE);
        });
        // Racha = días de los últimos 30 con un pomodoro completado; vive junto al saludo.
        homeViewModel.getActiveDaysLast30().observe(getViewLifecycleOwner(), days ->
                binding.homeStreakCount.setText(String.valueOf(days == null ? 0 : days)));
        // Primer pomodoro del día -> celebración.
        homeViewModel.getFirstFocusOfDay()
                .observe(getViewLifecycleOwner(), this::maybeShowFirstFocusCelebration);
        homeViewModel.getTodayTimeStudiedText().observe(getViewLifecycleOwner(), time ->
                todayTimeStudiedText.setText(getString(R.string.home_stat_studied_format, time)));

        // Auto-completado de una tarea al alcanzar su objetivo (spec focus-attribution): feedback
        // in-app (háptico + sonido) + notificación de fallback, y se desvincula el timer.
        homeViewModel.getAutoCompletedTaskTitle().observe(getViewLifecycleOwner(), title -> {
            if (title == null) {
                return;
            }
            HapticFeedback.confirm(focusBanner);
            SoundFeedback.get(requireContext()).playSuccess();
            focusCompleteNotifier.fire(title);
            FocusTarget.clear();
            updateFocusBanner();
            homeViewModel.clearAutoCompletedTaskTitle();
        });

        binding.homeDateText.setText(
                new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date()));

        setUpTaskList();

        SoundFeedback.get(requireContext()); // warm singleton so first tap is silent-fast

        // Sin restauración manual: la sesión sobrevive a la rotación en el ViewModel de actividad
        // y a la muerte del proceso en su SavedStateHandle (instante de fin, no ticks).
        return root;
    }

    /**
     * Tarjeta "Tasks" de Home (spec tasks-home-today): lista de hoy con checkbox,
     * alta rápida (bottom sheet) y sección plegable de tareas atrasadas.
     */
    private void setUpTaskList() {
        todayTaskAdapter = new TodayTaskAdapter(false,
                (entry, done) -> homeViewModel.setTaskDone(entry, done));
        // "Focus this" (spec focus-attribution): vincula el timer de Home a la tarea y arranca.
        todayTaskAdapter.setOnTaskFocusListener(this::onFocusRequested);
        // La sección de atrasadas pasa un removeListener: habilita el botón "quitar" que retira
        // (sin borrar) la serie/tarea atrasada — datos conservados para stats (spec tasks-recurrence).
        overdueTaskAdapter = new TodayTaskAdapter(true,
                (entry, done) -> homeViewModel.setTaskDone(entry, done),
                entry -> homeViewModel.dismissSeries(entry));

        binding.homeTodayTaskList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.homeTodayTaskList.setAdapter(todayTaskAdapter);
        binding.homeOverdueTaskList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.homeOverdueTaskList.setAdapter(overdueTaskAdapter);

        binding.homeAddTaskButton.setOnClickListener(this);
        binding.homeShowOverdueButton.setOnClickListener(this);

        // La vista siempre renace plegada (overdueExpanded = false), pero el ViewModel
        // sobrevive a la rotación: sin este reset su query de atrasadas seguiría viva
        // e invisible tras recrear la vista.
        homeViewModel.setOverdueVisible(false);

        homeViewModel.getTodayTasks().observe(getViewLifecycleOwner(), tasks -> {
            todayTaskAdapter.submitList(tasks);
            binding.homeTasksEmptyText.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        });
        homeViewModel.getOverdueTasks().observe(getViewLifecycleOwner(), tasks -> {
            overdueTaskAdapter.submitList(tasks);
            binding.homeOverdueEmptyText.setVisibility(
                    overdueExpanded && tasks.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    /**
     * Muestra la celebración del primer pomodoro del día si hay una pendiente.
     *
     * <p>La guarda es {@code isStateSaved()} y NO {@code isResumed()}: un observador de LiveData
     * se activa en STARTED y el valor retenido se entrega ANTES de onResume, así que exigir
     * RESUMED aquí descartaba el premio en la propia entrega — y como LiveData sólo reentrega al
     * pasar de inactivo a activo, nadie volvía a intentarlo y la celebración se perdía del todo.
     * Lo único que hay que evitar de verdad es el commit posterior a onSaveInstanceState; de ese
     * caso se recupera onResume() releyendo el valor, que sigue sin limpiar.
     */
    private void maybeShowFirstFocusCelebration(Integer daysThisMonth) {
        if (daysThisMonth == null || isStateSaved()) {
            return;
        }
        FirstFocusCelebrationPopup.newInstance(daysThisMonth)
                .show(getParentFragmentManager(), FirstFocusCelebrationPopup.POPUP_TAG);
        homeViewModel.clearFirstFocusOfDay();
    }

    private void toggleOverdueSection() {
        overdueExpanded = !overdueExpanded;
        binding.homeOverdueSection.setVisibility(overdueExpanded ? View.VISIBLE : View.GONE);
        binding.homeShowOverdueButton.setText(overdueExpanded
                ? R.string.home_tasks_hide_overdue : R.string.home_tasks_show_overdue);
        homeViewModel.setOverdueVisible(overdueExpanded);
    }

    /**
     * Pinta el estado de la sesión. El texto se actualiza en cada tick; chip, modo, controles y
     * focus mode sólo cuando cambia algo más que el tiempo restante.
     */
    private void renderTimer(FocusUiState state) {
        if (binding == null || state == null) return;
        timerText.setText(FormatHelper.formatTime((int) state.getRemainingMillis(), "mm:ss"));
        if (!state.controlsDifferFrom(lastRenderedState)) {
            lastRenderedState = state;
            return;
        }
        lastRenderedState = state;
        timerModeText.setText(state.isRest()
                ? R.string.home_mode_rest : R.string.home_mode_concentration);
        updateModeChip(state.isRest());
        updateTimerControls();
    }

    private void updateModeChip(boolean isRest) {
        if (timerModeText == null) return;
        timerModeText.setBackgroundResource(
                isRest ? R.drawable.bg_chip_secondary : R.drawable.bg_chip_energy);
    }

    /** Todos los ciclos configurados completados (antes MessagePopup desde completeTimer/Rest). */
    private void maybeShowCycleFinished(SingleEvent<Integer> event) {
        if (event == null || event.isHandled() || isStateSaved()) {
            return;
        }
        event.consume();
        messagePopup.setText(getString(R.string.home_cycle_finished));
        messagePopup.show(getParentFragmentManager(), "popup_tag");
    }

    /** La sesión falló por quedarse en segundo plano (antes onActivityResumed + isTimerFailed). */
    private void maybeShowSessionFailed(SingleEvent<Boolean> event) {
        if (event == null || event.isHandled() || isStateSaved()) {
            return;
        }
        event.consume();
        alertPopup.selectView(PopupHelper.ALERT_POPUP);
        alertPopup.show(getParentFragmentManager(), "popup_tag");
    }

    /**
     * "Focus this" (spec focus-attribution): vincula el ÚNICO timer de Home a esta tarea/item y,
     * si el timer está parado, arranca una sesión de concentración. Si ya hay una sesión corriendo,
     * sólo re-vincula (la sesión en curso se atribuirá al completar). Punto de entrada primario
     * (decisión #3) tanto desde la lista de hoy como, tras navegar a Home, desde el detalle de proyecto.
     */
    private void onFocusRequested(CalendarEntry entry) {
        FocusTarget.set(entry.getId(), entry.getTitle(), entry.getTargetMinutes());
        FocusTarget.consumePendingAutoStart(); // el arranque lo hacemos aquí; evita doble arranque en onResume
        startBoundConcentrationIfIdle();
        updateFocusBanner();
    }

    /** Arranca una sesión de concentración sólo si el timer está parado (no pisa una en curso). */
    private void startBoundConcentrationIfIdle() {
        // Si el item vinculado trae objetivo de minutos, el pomodoro arranca con esa duración
        // (lo que el usuario eligió en el item); si no, la duración por defecto del timer.
        if (focusSession.startBoundFocusIfIdle()) {
            SoundFeedback.get(requireContext()).playStart();
        }
    }

    /** Muestra/oculta el banner "Focusing: <tarea>" según el estado de FocusTarget. */
    private void updateFocusBanner() {
        if (binding == null || focusBanner == null) {
            return;
        }
        if (FocusTarget.isSet()) {
            focusBanner.setVisibility(View.VISIBLE);
            focusBanner.setText(getString(R.string.home_focus_banner, FocusTarget.getTitle()));
        } else {
            focusBanner.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        HapticFeedback.lightTap(view);
        if (id == R.id.homePlayButton) {
            Log.i(TAG, "Timer started");
            SoundFeedback feedback = SoundFeedback.get(requireContext());
            if (focusSession.toggle()) {
                feedback.playStart();
            } else {
                feedback.playStop();
            }
        } else if (id == R.id.homeCancelButton) {
            cancelSession();
        } else if (id == R.id.homeSkipRestButton) {
            focusSession.skipRest();
            SoundFeedback.get(requireContext()).playStop();
        } else if (id == R.id.homeBlockModeButton) {
            handleBlockModeToggle();
        } else if (id == R.id.homeAddTaskButton) {
            // childFragmentManager: QuickAddTaskSheet localiza este fragment como parent
            // para compartir el HomeViewModel.
            new QuickAddTaskSheet().show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG);
        } else if (id == R.id.homeShowOverdueButton) {
            toggleOverdueSection();
        }
    }

    /**
     * Tap on the 🚫 block-mode toggle. Off -> on requires the accessibility grant (same gate as
     * the Progress enforce toggle): if already granted, arm immediately; otherwise show the shared
     * disclosure dialog and arm only once the user consents (onAccessibilityConsentGranted()).
     * On -> off never needs permission. El botón está SIEMPRE activo, también con el timer
     * corriendo (spec focus-mode-and-streak): armar a mitad de sesión bloquea al instante y
     * desarmar devuelve el móvil al usuario, que si no quedaría atrapado hasta acabar el ciclo.
     */
    private void handleBlockModeToggle() {
        if (focusSession.isBlockModeArmed()) {
            focusSession.setBlockModeArmed(false);
            return;
        }
        if (AccessibilityAccess.isEnabled(requireContext())) {
            focusSession.setBlockModeArmed(true);
        } else {
            AccessibilityDisclosureDialog.newInstance()
                    .show(getChildFragmentManager(), "accessibility_disclosure");
        }
    }

    @Override
    public void onAccessibilityConsentGranted() {
        focusSession.setBlockModeArmed(true);
    }

    /**
     * Cancelar la sesión desde el focus mode (spec focus-mode-and-streak): corta el contador, tira
     * el ciclo entero y vuelve a Home en reposo. NO registra fallo — un fallo es haberse ido de la
     * app a mitad de sesión; esto es una decisión explícita del usuario y penalizarla haría que la
     * única salida "barata" fuese aguantar mirando la pantalla.
     */
    private void cancelSession() {
        // La sesión también desvincula FocusTarget: dejar el banner "Focusing: X" colgado sin
        // sesión viva confunde sobre si la siguiente cuenta atrás se atribuiría sola a esa tarea.
        if (!focusSession.cancelSession()) {
            return;
        }
        updateFocusBanner();
        SoundFeedback.get(requireContext()).playStop();
    }

    /** Keep the play/pause icon and the skip-rest button in sync with the session state. */
    private void updateTimerControls() {
        if (binding == null || lastRenderedState == null) return;
        FocusUiState state = lastRenderedState;

        boolean isRunning = state.isRunning();
        playButton.setImageResource(
                isRunning ? R.drawable.ic_pause_v2_filled_24 : R.drawable.ic_play_v2_filled_24);
        playButton.setContentDescription(
                getString(isRunning ? R.string.home_pause : R.string.home_play));

        skipRestButton.setVisibility(state.canSkipRest() ? View.VISIBLE : View.GONE);

        updateBlockModeButton();
        updateFocusMode();
    }

    /**
     * Focus mode (spec focus-mode-and-streak): el estado se DERIVA del estado de la sesión, no de
     * un flag propio, así que cualquier salida del temporizador (fin de ciclo, cancelación, fallo
     * por background, cambio de configuración) devuelve Home a la normalidad sin ruta especial.
     */
    private void updateFocusMode() {
        boolean shouldBeActive = lastRenderedState != null && lastRenderedState.isSessionActive();
        if (binding == null || shouldBeActive == focusModeActive) {
            return;
        }
        focusModeActive = shouldBeActive;
        applyFocusMode();
    }

    private void applyFocusMode() {
        if (binding == null) {
            return;
        }
        TransitionManager.beginDelayedTransition(binding.homeContent,
                new AutoTransition().setDuration(220));

        binding.homeHeaderSection.setVisibility(focusModeActive ? View.GONE : View.VISIBLE);
        binding.homeTasksCard.setVisibility(focusModeActive ? View.GONE : View.VISIBLE);
        binding.homeTimerSettingsButton.setVisibility(focusModeActive ? View.GONE : View.VISIBLE);
        cancelButton.setVisibility(focusModeActive ? View.VISIBLE : View.GONE);
        // El hueco sólo existe para compensar el botón de cancelar y mantener centrado el play.
        binding.homeControlsSpacer.setVisibility(focusModeActive ? View.VISIBLE : View.GONE);

        // match_parent + el resto GONE: con fillViewport el ScrollView re-mide su hijo a la altura
        // del viewport, así que la tarjeta pasa a ocupar la pantalla entera (ver fragment_home.xml).
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) binding.homeTimerCard.getLayoutParams();
        params.height = focusModeActive
                ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        params.topMargin = focusModeActive
                ? 0 : getResources().getDimensionPixelSize(R.dimen.spacing_md);
        binding.homeTimerCard.setLayoutParams(params);

        // A sangre: sin el padding del contenedor, sin esquinas y sin sombra, la tarjeta deja de
        // leerse como "una tarjeta estirada" y pasa a ser la pantalla. Con el marco puesto se veía
        // el fondo beige alrededor y delataba que seguíamos en Home.
        applyFocusModeChrome();

        setAppChromeVisible(!focusModeActive);
    }

    /** Quita/restaura el marco de tarjeta del timer según el focus mode. */
    private void applyFocusModeChrome() {
        int side = focusModeActive ? 0 : getResources().getDimensionPixelSize(R.dimen.spacing_md);
        int bottom = focusModeActive ? 0 : getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        binding.homeContent.setPadding(side, side, side, bottom);

        if (focusModeActive) {
            binding.homeTimerCard.setBackgroundResource(R.color.bb_surface_card);
            binding.homeTimerCard.setElevation(0f);
        } else {
            binding.homeTimerCard.setBackgroundResource(R.drawable.bg_card);
            binding.homeTimerCard.setElevation(
                    getResources().getDimensionPixelSize(R.dimen.elevation_card));
        }
        // setBackgroundResource pisa el padding del propio card: hay que reponerlo siempre.
        int cardPadding = getResources().getDimensionPixelSize(R.dimen.home_card_padding);
        binding.homeTimerCard.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
    }

    /**
     * Oculta/restaura la navegación inferior de la Activity (ya no hay ActionBar). En focus mode las
     * únicas acciones son pausar, cancelar y el modo bloqueo: dejar la bottom-nav a mano invitaría
     * a irse a otra pestaña con la sesión corriendo. Idempotente y siempre restaurado en
     * onDestroyView, así que ninguna otra pantalla puede heredar el chrome escondido.
     */
    private void setAppChromeVisible(boolean visible) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        View navView = activity.findViewById(R.id.nav_view);
        View navDivider = activity.findViewById(R.id.nav_divider);
        if (navView != null) {
            navView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (navDivider != null) {
            navDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Render the 🚫 block-mode toggle's 3 states (muted off / bb_danger active / bb_accent_reward
     * pending-permission — same visual language as the Progress enforce toggle). Se puede armar y
     * desarmar en cualquier momento, también a mitad de sesión (spec focus-mode-and-streak). El
     * flag vivo FocusBlockState ya NO se escribe aquí: su único escritor es FocusSessionViewModel,
     * que lo recalcula en cada transición (armado Y concentración corriendo).
     */
    private void updateBlockModeButton() {
        if (binding == null || blockModeButton == null) return;
        boolean blockModeArmed = focusSession.isBlockModeArmed();

        // El texto dice el estado (off / on / falta permiso): con sólo el color, "off" se leía
        // como un botón deshabilitado (spec frontend-structure-cleanup).
        int tint;
        float alpha;
        int label;
        if (!blockModeArmed) {
            tint = R.color.bb_on_surface_muted;
            alpha = 0.7f;
            label = R.string.home_block_mode_off;
        } else if (AccessibilityAccess.isEnabled(requireContext())) {
            tint = R.color.bb_danger;
            alpha = 1f;
            label = R.string.home_block_mode_on;
        } else {
            tint = R.color.bb_accent_reward;
            alpha = 0.9f;
            label = R.string.home_block_mode_pending;
        }
        blockModeButton.setText(label);
        blockModeButton.setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), tint));
        blockModeButton.setAlpha(alpha);
    }


    @Override
    public void onDestroyView() {
        // Antes de soltar el binding: si la vista muere en focus mode (rotación, proceso), la
        // bottom-nav es de la Activity y se quedaría oculta para siempre.
        setAppChromeVisible(true);
        focusModeActive = false;
        lastRenderedState = null;
        super.onDestroyView();
        binding = null;
    }

    // La detección de primer/segundo plano y el periodo de gracia (FAIL_GRACE_MILLIS) viven en
    // FocusSessionViewModel + FocusBackgroundObserver, registrado por MainActivity.

    @Override
    public void onResume() {
        super.onResume();
        // No callback from Settings -> Accessibility (same caveat as ProgressFragment): re-check
        // and re-render in case the user granted/revoked the service while away, and re-assert
        // the live block flag (the accessibility service clears it when it reconnects).
        updateBlockModeButton();
        focusSession.syncBlockState();
        // Recalcula [startOfToday, endOfToday]: sólo re-dispara la query si el día cambió con
        // la app abierta -- volver de AddEventActivity no lo necesita, la lista de hoy ya se
        // re-emite sola (LiveData de Room, ver repository-layer-consolidation T2).
        homeViewModel.refreshToday();
        // "Focus this" desde otra pantalla (p. ej. detalle de proyecto) fija FocusTarget y navega a
        // Home; aquí consumimos el arranque pendiente (una vez) y refrescamos el banner.
        if (FocusTarget.consumePendingAutoStart()) {
            startBoundConcentrationIfIdle();
        }
        updateFocusBanner();
        // Red de seguridad: si el premio llegó mientras el estado estaba guardado, el observador
        // no pudo abrirlo y el valor sigue sin limpiar. Aquí ya es seguro comitear.
        maybeShowFirstFocusCelebration(homeViewModel.getFirstFocusOfDay().getValue());
        // Misma red para los avisos de la sesión que llegaran con el estado guardado.
        maybeShowCycleFinished(focusSession.getCycleCompletedEvents().getValue());
        maybeShowSessionFailed(focusSession.getSessionFailedEvents().getValue());
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getActivity() != null) {
            Intent serviceIntent = new Intent(getActivity(), HomeForegroundService.class);
            ContextCompat.startForegroundService(getActivity(), serviceIntent);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() != null) {
            Intent serviceIntent = new Intent(getActivity(), HomeForegroundService.class);
            getActivity().stopService(serviceIntent);
        }
    }

    @Override
    public void OnClosePopup(int popupType) {
        if (popupType==PopupHelper.ALERT_POPUP){
            focusSession.resetIdleDisplay();
        }
    }

    //Método genérico llamado al cerrar los diferentes popups
    @Override
    public void OnClosePopup(int popupType, Object result) {
        try {
            if (popupType == PopupHelper.ALERT_POPUP) {
                focusSession.resetIdleDisplay();
            } else if (popupType == PopupHelper.TIMER_POPUP) {
                Configuration config = (Configuration) result;
                // Antes checkConfigChanges(): descanso desactivado corta el descanso en curso,
                // auto-ciclo desactivado reinicia el contador de ciclos.
                focusSession.onConfigurationChanged(config);
                homeViewModel.updateConfiguration(config);
                Log.i(TAG, "Configuration received: ");
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Error on closing popup: " + e.getMessage());
        }
    }

    /** Tap en la racha del header (spec focus-mode-and-streak). */
    private void showStreakPopup() {
        new FocusStreakPopup().show(getParentFragmentManager(), FocusStreakPopup.POPUP_TAG);
    }

    /** Tap en el engranaje de la tarjeta del timer: configuración del pomodoro. */
    private void showTimerSettings() {
        timerPopup.setConfiguration(configurationManager.getConfiguration());
        timerPopup.show(getParentFragmentManager(), "popup_tag");
    }
    /** --------------------------------------------------------------------------- */
}