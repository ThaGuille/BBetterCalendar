package com.example.bbettercalendar.ui.home;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.FragmentNavigator;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.databinding.FragmentHomeBinding;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.popups.FocusStreakPopup;
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.popups.TimerPopup;
import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.ui.common.motion.Motion;
import com.example.bbettercalendar.ui.common.motion.ScreenTransitions;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.example.bbettercalendar.ui.common.widget.RingState;
import com.example.bbettercalendar.ui.focus.FocusSessionEventPresenter;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.focus.FocusUiState;
import com.example.bbettercalendar.ui.focus.RingStateMapper;
import com.example.bbettercalendar.ui.social.AvatarPalette;
import com.example.bbettercalendar.ui.social.StudyingSummary;
import com.example.bbettercalendar.ui.social.EntryTarget;
import com.example.bbettercalendar.ui.social.SocialEntryNavigator;
import com.example.bbettercalendar.ui.social.SocialEntryViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Home (migración visual u13, html 04). Sólo muestra y arranca: la sesión de pomodoro vive en
 * {@link FocusSessionViewModel} (ámbito de actividad) y su UI en marcha es la pantalla Focus
 * ({@code navigation_focus}). Play arranca la sesión y navega a Focus; con una sesión viva la
 * tarjeta del temporizador muestra el restante y un toque abre Focus. Pausa, stop, saltar
 * descanso, modo bloqueo y fallos de hoy viven en Focus (u14).
 */
@AndroidEntryPoint
public class HomeFragment extends Fragment implements View.OnClickListener, OnPopupListener<Object> {

    private final String TAG = "HomeFragmentTag";
    private static final int MAX_FRIEND_AVATARS = 3;

    private FragmentHomeBinding binding;
    HomeViewModel homeViewModel;
    // El pomodoro (máquina de estados, ticker, gracia por segundo plano, guardado, servicio en
    // primer plano) vive en este ViewModel de ÁMBITO DE ACTIVIDAD (u2/u13): Home observa y delega.
    private FocusSessionViewModel focusSession;
    // Último estado pintado: sólo se re-pintan los controles cuando cambia algo más que el texto.
    private FocusUiState lastRenderedState;
    private final TimerPopup timerPopup = new TimerPopup();
    private FocusSessionEventPresenter sessionEvents;
    // Solo con SocialFeature.isAccountEnabled(); lo comparten el avatar y el diálogo del nombre.
    @Nullable private SocialEntryViewModel socialEntry;

    // "Today" task list (spec tasks-home-today)
    private TodayTaskAdapter todayTaskAdapter;
    private TodayTaskAdapter overdueTaskAdapter;
    private boolean overdueExpanded = false;
    // Primera tarea pendiente de hoy: el "Next: <tarea>" de la tarjeta del temporizador.
    private String nextTaskTitle;
    // u6: hasta la primera emisión de tareas la tarjeta enseña el esqueleto.
    private boolean tasksLoaded;

    @Inject
    ConfigurationManager configurationManager;

    // u15: transiciones de pantalla (ScreenTransitions: fundido entre pestañas, eje Z/X, contenedor).
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenTransitions.apply(this);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        focusSession = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        binding.homePlayButton.setOnClickListener(this);
        // u15: el play crece hasta el anillo de Focus (contenedor que se transforma).
        ViewCompat.setTransitionName(binding.homePlayButton, ScreenTransitions.FOCUS_RING);
        binding.homeTimerCard.setOnClickListener(this);
        binding.homeCycleChip.setOnClickListener(this);
        binding.homeBellButton.setOnClickListener(this);
        binding.homeNextUpCard.setOnClickListener(this);
        // Racha: tap = FocusStreakPopup (spec focus-mode-and-streak).
        binding.homeStreakButton.setOnClickListener(v -> showStreakPopup());

        timerPopup.setOnPopupListener(this);

        // Header (F1-u8): saludo e iniciales salen del perfil, del nombre local o, sin nada, un
        // saludo genérico (GreetingResolver). Tocar el avatar abre Mi perfil o el nombre local.
        homeViewModel.getGreeting().observe(getViewLifecycleOwner(), this::renderGreeting);
        binding.homeAvatar.setContentDescription(getString(R.string.local_name_avatar_cd));
        binding.homeAvatar.setOnClickListener(v -> onAvatarClicked());
        binding.homeDateText.setText(todayLabel());
        // El halo ámbar se sale por la esquina: la tarjeta lo recorta con su contorno redondeado.
        binding.homeTimerCard.setClipToOutline(true);

        // Tarjeta del temporizador: texto, modo y ciclo salen del estado de la sesión.
        lastRenderedState = null;
        focusSession.getUiState().observe(getViewLifecycleOwner(), this::renderTimer);
        // Resultados de un solo uso de la sesión (ciclos completados → Cycle complete, fallo,
        // primer pomodoro del día, tarea auto-completada): los pinta el presentador compartido con
        // Focus (u14); el primero de los dos que esté visible los consume.
        sessionEvents = new FocusSessionEventPresenter(this, focusSession, this::renderCycleLine);
        sessionEvents.observe(getViewLifecycleOwner());

        // Racha semanal real (StreakCalculator): «2 semanas», semana L→D y «2 de 3 días».
        homeViewModel.getStreak().observe(getViewLifecycleOwner(), this::renderStreak);

        // Amigos estudiando (maqueta tras FEATURE_SOCIAL_MOCK; GONE sin hueco con el flag apagado).
        SocialFeature.applyVisibility(binding.homeFriendsCard);
        if (SocialFeature.isEnabled()) {
            homeViewModel.getSocialSnapshot().observe(getViewLifecycleOwner(), this::renderFriends);
        }
        setUpSocialEntry();
        // F2-u8: el punto de la campana. Sin perfil (o en release) queda fijo, como antes.
        homeViewModel.getBellRequests().observe(getViewLifecycleOwner(), requests -> {
            if (binding == null) {
                return;
            }
            boolean legacy = requests == null || requests == HomeViewModel.BELL_LEGACY;
            // Sin cuenta o en release el punto queda fijo; con perfil, solo con avisos sin leer o solicitudes.
            binding.homeBellDot.setVisibility(legacy || requests > 0 ? View.VISIBLE : View.GONE);
        });

        homeViewModel.getNextUp().observe(getViewLifecycleOwner(), this::renderNextUp);

        setUpTaskList();

        SoundFeedback.get(requireContext()); // warm singleton so first tap is silent-fast

        // u6: entrada escalonada de las tarjetas, solo la primera vez (no al volver de Focus).
        if (homeViewModel.consumeEntrance()) {
            Motion.staggerIn(binding.homeContent);
        }

        // Sin restauración manual: la sesión sobrevive a la rotación en el ViewModel de actividad
        // y a la muerte del proceso en su SavedStateHandle (instante de fin, no ticks).
        return binding.getRoot();
    }

    /**
     * Tarjeta "Tasks" de Home (spec tasks-home-today): lista de hoy con checkbox, chip de
     * proyecto (u13), alta rápida (bottom sheet) y sección plegable de tareas atrasadas.
     */
    private void setUpTaskList() {
        // u6: la fila y la cuenta «2 de 5» cambian al instante; el guardado llega después.
        todayTaskAdapter = new TodayTaskAdapter(false, (entry, done) -> {
            homeViewModel.setTaskDoneSoon(entry, done);
            renderTaskSummary(true);
        });
        // "Focus this" (spec focus-attribution): vincula el timer a la tarea, arranca y abre Focus.
        todayTaskAdapter.setOnTaskFocusListener(this::onFocusRequested);
        // La sección de atrasadas pasa un removeListener: habilita el botón "quitar" que retira
        // (sin borrar) la serie/tarea atrasada — datos conservados para stats (spec tasks-recurrence).
        overdueTaskAdapter = new TodayTaskAdapter(true,
                (entry, done) -> homeViewModel.setTaskDoneSoon(entry, done),
                entry -> homeViewModel.dismissSeries(entry));
        binding.homeTasksEmpty.setOnActionClickListener(v -> openQuickAdd());
        tasksLoaded = false;
        binding.homeTasksSkeleton.show();

        binding.homeTodayTaskList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.homeTodayTaskList.setAdapter(todayTaskAdapter);
        binding.homeOverdueTaskList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.homeOverdueTaskList.setAdapter(overdueTaskAdapter);
        // u6: las filas se mueven (DiffUtil) pero no se funden al cambiar: el fundido del item animator
        // dejaría la fila hecha a alfa 1 en vez del atenuado de las hechas.
        disableChangeFade(binding.homeTodayTaskList);
        disableChangeFade(binding.homeOverdueTaskList);

        binding.homeAddTaskButton.setOnClickListener(this);
        Motion.pressable(binding.homeAddTaskButton);
        binding.homeShowOverdueButton.setOnClickListener(this);
        updateOverdueToggle();

        // La vista siempre renace plegada (overdueExpanded = false), pero el ViewModel
        // sobrevive a la rotación: sin este reset su query de atrasadas seguiría viva
        // e invisible tras recrear la vista.
        overdueExpanded = false;
        homeViewModel.setOverdueVisible(false);

        homeViewModel.getTodayTasks().observe(getViewLifecycleOwner(), tasks -> {
            boolean first = !tasksLoaded;
            tasksLoaded = true;
            if (first) {
                binding.homeTasksSkeleton.hide();
            }
            todayTaskAdapter.submitList(tasks);
            boolean empty = tasks == null || tasks.isEmpty();
            binding.homeTasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.homeTodayTaskList.setVisibility(empty ? View.GONE : View.VISIBLE);
            renderTaskSummary(!first);
            nextTaskTitle = firstPendingTitle(tasks);
            renderCycleLine();
        });
        homeViewModel.getOverdueTasks().observe(getViewLifecycleOwner(), tasks -> {
            overdueTaskAdapter.submitList(tasks);
            binding.homeOverdueEmptyText.setVisibility(
                    overdueExpanded && tasks.isEmpty() ? View.VISIBLE : View.GONE);
        });
        homeViewModel.getProjects().observe(getViewLifecycleOwner(), projects -> {
            todayTaskAdapter.setProjects(projects);
            overdueTaskAdapter.setProjects(projects);
        });
    }

    @Nullable
    private static String firstPendingTitle(@Nullable List<CalendarEntry> tasks) {
        if (tasks == null) {
            return null;
        }
        for (CalendarEntry task : tasks) {
            if (!task.isDone() && task.getTitle() != null && !task.getTitle().isEmpty()) {
                return task.getTitle();
            }
        }
        return null;
    }

    private static void disableChangeFade(@NonNull RecyclerView list) {
        if (list.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
    }

    /** «2 de 5 tareas» + barra de avance, con lo que se ve ahora (incluye lo recién marcado). */
    private void renderTaskSummary(boolean animate) {
        if (binding == null || todayTaskAdapter == null) {
            return;
        }
        int total = todayTaskAdapter.getItemCount();
        if (total == 0) {
            binding.homeTasksCount.setVisibility(View.GONE);
            binding.homeTasksProgress.setVisibility(View.GONE);
            return;
        }
        int done = todayTaskAdapter.countDoneNow();
        binding.homeTasksCount.setVisibility(View.VISIBLE);
        binding.homeTasksCount.setText(getResources().getQuantityString(
                R.plurals.home_tasks_progress, total, done, total));
        binding.homeTasksProgress.setVisibility(View.VISIBLE);
        binding.homeTasksProgress.setProgressCompat(Math.round(100f * done / total),
                animate && Motion.enabled(requireContext()));
    }

    /** Tarjeta de racha (M/40): «Racha», «2 semanas» en ámbar, semana L→D y «2 de 3 días». */
    private void renderStreak(@Nullable StreakCalculator.Result streak) {
        if (binding == null) {
            return;
        }
        int weeks = streak == null ? 0 : streak.weekStreak;
        binding.homeStreakValue.setText(
                getResources().getQuantityString(R.plurals.streak_weeks, weeks, weeks));
        binding.homeStreakStrip.setWeek(streak == null ? null : streak.week);
        if (streak == null || streak.goal <= 0) {
            binding.homeStreakGoal.setVisibility(View.GONE);
            binding.homeStreakButton.setContentDescription(getString(R.string.home_streak_desc));
            return;
        }
        String goal = getResources().getQuantityString(R.plurals.home_streak_days_goal,
                streak.goal, streak.daysThisWeek, streak.goal);
        binding.homeStreakGoal.setVisibility(View.VISIBLE);
        binding.homeStreakGoal.setText(goal);
        binding.homeStreakButton.setContentDescription(getString(R.string.home_streak_desc) + ". "
                + binding.homeStreakValue.getText() + ". "
                + getString(R.string.streak_week_goal_desc, streak.daysThisWeek, streak.goal));
    }

    /** «Miércoles, 11 nov»: patrón del idioma (día de la semana, día, mes corto), con mayúscula. */
    @NonNull
    private static String todayLabel() {
        Locale locale = Locale.getDefault();
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMM");
        String text = new SimpleDateFormat(pattern, locale).format(new Date());
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    private void openQuickAdd() {
        // childFragmentManager: QuickAddTaskSheet localiza este fragment como parent
        // para compartir el HomeViewModel.
        if (getChildFragmentManager().findFragmentByTag(QuickAddTaskSheet.SHEET_TAG) == null) {
            new QuickAddTaskSheet().show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG);
        }
    }

    private void toggleOverdueSection() {
        overdueExpanded = !overdueExpanded;
        binding.homeOverdueSection.setVisibility(overdueExpanded ? View.VISIBLE : View.GONE);
        updateOverdueToggle();
        homeViewModel.setOverdueVisible(overdueExpanded);
    }

    /** "Show older uncompleted tasks ⌄" (html 04): texto + chevron según el estado. */
    private void updateOverdueToggle() {
        binding.homeShowOverdueButton.setText(overdueExpanded
                ? R.string.home_tasks_hide_overdue : R.string.home_tasks_show_overdue);
        Drawable chevron = ContextCompat.getDrawable(requireContext(),
                overdueExpanded ? R.drawable.ic_arrow_drop_up_24 : R.drawable.ic_expand_more_24);
        if (chevron != null) {
            chevron = DrawableCompat.wrap(chevron.mutate());
            DrawableCompat.setTint(chevron,
                    ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted));
            int size = getResources().getDimensionPixelSize(R.dimen.home_inline_stat_icon_size);
            chevron.setBounds(0, 0, size, size);
        }
        binding.homeShowOverdueButton.setCompoundDrawablesRelative(null, null, chevron, null);
    }

    // ------------------------------------------------------------------ timer card

    /**
     * Pinta el estado de la sesión. El texto se actualiza en cada tick; chip, botón y línea de
     * ciclo sólo cuando cambia algo más que el tiempo restante.
     */
    private void renderTimer(FocusUiState state) {
        if (binding == null || state == null) return;
        binding.homeTimerText.setText(
                FormatHelper.formatTime((int) state.getRemainingMillis(), "mm:ss"));
        // u6: anillo vivo. Mismo RingStateMapper que Focus → misma fracción en las dos pantallas.
        RingStateMapper.Ring ring = RingStateMapper.map(state);
        boolean firstRender = lastRenderedState == null;
        binding.homePlayButton.setRest(ring.rest);
        binding.homePlayButton.setRemaining(ring.remaining);
        binding.homePlayButton.setState(ring.state);
        if (firstRender && RingStateMapper.justStarted(state)) {
            binding.homePlayButton.playStartSweep();
        }
        if (!state.controlsDifferFrom(lastRenderedState)) {
            lastRenderedState = state;
            return;
        }
        lastRenderedState = state;

        // «Concentración · 2 de 4» (mismo cálculo que el chip de Focus).
        String mode = getString(state.isPaused() ? R.string.home_mode_paused
                : state.isRest() ? R.string.home_mode_rest : R.string.home_mode_concentration);
        int cycle = state.isInfiniteCycles()
                ? state.getCurrentCycle()
                : Math.min(state.getCurrentCycle(), Math.max(1, state.getTotalCycles()));
        binding.homeTimerTypeText.setText(state.isInfiniteCycles()
                ? getString(R.string.focus_mode_chip_infinite_format, mode, cycle)
                : getString(R.string.focus_mode_chip_format, mode, cycle, state.getTotalCycles()));

        // Idle: play. Sesión viva (corriendo, en pausa o en descanso): el botón y la tarjeta
        // llevan a Focus, donde están pausa / stop / saltar descanso (u14).
        boolean active = state.isSessionActive();
        binding.homePlayButton.setContentDescription(
                getString(active ? R.string.home_open_focus : R.string.home_play));
        binding.homeTimerCard.setClickable(active);
        binding.homeTimerCard.setContentDescription(active ? getString(R.string.home_open_focus) : null);
        renderCycleLine();
    }

    /**
     * u6 (M/40): bajo el anillo, «Ahora: <tarea>» si el temporizador está vinculado a una o
     * «Siguiente: <tarea>» (primera pendiente de hoy); sin ninguna, la línea no sale. El chip de arriba
     * («Concentración · 2 de 4») abre la configuración del temporizador.
     */
    private void renderCycleLine() {
        if (binding == null || lastRenderedState == null) {
            return;
        }
        String line;
        if (FocusTarget.isSet() && FocusTarget.getTitle() != null) {
            // Vinculada: «Ahora» con la sesión viva; en reposo es la que arrancará el play.
            line = getString(lastRenderedState.isSessionActive()
                    ? R.string.home_timer_now : R.string.home_timer_next, FocusTarget.getTitle());
        } else if (nextTaskTitle != null) {
            line = getString(R.string.home_timer_next, nextTaskTitle);
        } else {
            line = null;
        }
        binding.homeCycleText.setText(line);
        binding.homeCycleText.setVisibility(line == null ? View.GONE : View.VISIBLE);
        binding.homeCycleChip.setContentDescription(getString(R.string.home_cycle_chip_desc,
                binding.homeTimerTypeText.getText()));
    }

    /**
     * "Focus this" (spec focus-attribution): vincula el ÚNICO timer a esta tarea/item y, si está
     * parado, arranca una concentración. Si ya hay una sesión corriendo, sólo re-vincula (la sesión
     * en curso se atribuirá al completar). En ambos casos abre Focus (u13).
     */
    private void onFocusRequested(CalendarEntry entry) {
        FocusTarget.set(entry.getId(), entry.getTitle(), entry.getTargetMinutes());
        FocusTarget.consumePendingAutoStart(); // el arranque lo hacemos aquí; evita doble arranque en onResume
        startBoundConcentrationIfIdle();
        renderCycleLine();
        openFocus();
    }

    /** Arranca una sesión de concentración sólo si el timer está parado (no pisa una en curso). */
    private void startBoundConcentrationIfIdle() {
        // Si el item vinculado trae objetivo de minutos, el pomodoro arranca con esa duración
        // (lo que el usuario eligió en el item); si no, la duración por defecto del timer.
        if (focusSession.startBoundFocusIfIdle()) {
            SoundFeedback.get(requireContext()).playStart();
        }
    }

    /** Play: arranca la sesión (si está parada) y abre Focus; con sesión viva sólo abre Focus. */
    private void onPlayTapped() {
        FocusUiState state = focusSession.getUiState().getValue();
        boolean active = state != null && state.isSessionActive();
        if (!active) {
            Log.i(TAG, "Timer started");
            if (focusSession.toggle()) {
                SoundFeedback.get(requireContext()).playStart();
            }
        }
        openFocus();
    }

    /** Navega a Focus (u12/u14) sólo desde Home: un doble toque no apila dos Focus. */
    private void openFocus() {
        NavController nav = NavHostFragment.findNavController(this);
        NavDestination current = nav.getCurrentDestination();
        if (current != null && current.getId() == R.id.navigation_home) {
            // u15: el botón es el elemento compartido con la caja del anillo de Focus.
            FragmentNavigator.Extras extras = binding == null ? null
                    : new FragmentNavigator.Extras.Builder()
                            .addSharedElement(binding.homePlayButton, ScreenTransitions.FOCUS_RING)
                            .build();
            nav.navigate(R.id.navigation_focus, null, null, extras);
        }
    }

    // ------------------------------------------------------------------ friends / next up

    /**
     * F1-u4: estado «sin cuenta». Con {@link SocialFeature#isAccountEnabled()} y sesión cerrada, la
     * tarjeta de amigos enseña la llamada a la acción y tocarla sigue SocialEntryRouter. Con sesión,
     * la maqueta de siempre (F4). En release no hace nada (la tarjeta sigue GONE).
     */
    private void setUpSocialEntry() {
        if (!SocialFeature.isAccountEnabled()) {
            return;
        }
        SocialEntryViewModel entry = new ViewModelProvider(this).get(SocialEntryViewModel.class);
        socialEntry = entry;
        entry.getEntryStep().observe(getViewLifecycleOwner(), step -> {
            if (step == null) {
                return;
            }
            entry.consumeEntryStep();
            SocialEntryNavigator.go(this, step, entry.getEntryTarget());
        });
        entry.getAccountState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) {
                return;
            }
            boolean signedOut = state == AccountState.SIGNED_OUT;
            if (signedOut) {
                binding.homeFriendsCard.setVisibility(View.VISIBLE);
                binding.homeFriendsTitle.setText(R.string.social_cta_title);
                binding.homeFriendsAvatars.setVisibility(View.GONE);
                binding.homeFriendsJoin.setVisibility(View.VISIBLE);
                binding.homeFriendsCard.setOnClickListener(v -> entry.requestEntry());
            } else {
                binding.homeFriendsAvatars.setVisibility(View.VISIBLE);
                binding.homeFriendsJoin.setVisibility(View.GONE);
                // F2-u7: con sesión, tocar la tarjeta abre Amigos (F4-u6: con datos reales).
                binding.homeFriendsCard.setOnClickListener(v -> entry.requestEntry(EntryTarget.FRIENDS));
                SocialFeature.applyVisibility(binding.homeFriendsCard);
                // El título lo pisó la llamada a la acción: repintarlo.
                renderFriends(homeViewModel.getSocialSnapshot().getValue());
            }
        });
    }

    private void renderGreeting(@Nullable GreetingResolver.Greeting greeting) {
        if (binding == null || greeting == null) {
            return;
        }
        binding.textHome.setText(greeting.kind == GreetingResolver.Kind.GENERIC
                ? getString(R.string.home_greeting_generic)
                : getString(R.string.home_greeting, greeting.name));
        AvatarView avatar = binding.homeAvatar;
        avatar.setInitials(greeting.initials);
        if (greeting.kind == GreetingResolver.Kind.PROFILE) {
            avatar.setFillColor(AvatarPalette.color(requireContext(), greeting.colorIndex));
            avatar.setInitialsColor(AvatarPalette.onColor(requireContext()));
        } else {
            avatar.setFillColor(ContextCompat.getColor(requireContext(), R.color.bb_primary_container));
            avatar.setInitialsColor(ContextCompat.getColor(requireContext(), R.color.bb_on_primary_container));
        }
    }

    /**
     * Avatar de Home: con perfil abre Mi perfil; con sesión pero sin perfil sigue al router (Crear
     * perfil); sin cuenta, o en release, abre el editor del nombre local.
     */
    private void onAvatarClicked() {
        if (SocialFeature.isAccountEnabled()) {
            GreetingResolver.Greeting greeting = homeViewModel.getGreeting().getValue();
            if (greeting != null && greeting.kind == GreetingResolver.Kind.PROFILE) {
                NavHostFragment.findNavController(this).navigate(R.id.navigation_my_profile);
                return;
            }
            if (socialEntry != null
                    && socialEntry.getAccountState().getValue() == AccountState.SIGNED_IN) {
                socialEntry.requestEntry();
                return;
            }
        }
        if (getChildFragmentManager().findFragmentByTag(LocalNameDialog.TAG) == null) {
            LocalNameDialog.newInstance(homeViewModel.getLocalName())
                    .show(getChildFragmentManager(), LocalNameDialog.TAG);
        }
    }

    private void renderFriends(SocialSnapshot snapshot) {
        if (binding == null || snapshot == null) {
            return;
        }
        // Con la cuenta cerrada manda la llamada a la acción de setUpSocialEntry.
        if (socialEntry != null
                && socialEntry.getAccountState().getValue() == AccountState.SIGNED_OUT) {
            return;
        }
        StudyingSummary summary = StudyingSummary.of(snapshot, MAX_FRIEND_AVATARS);
        LinearLayout row = binding.homeFriendsAvatars;
        // Quitar los avatares de una emisión anterior.
        row.removeAllViews();
        binding.homeFriendsJoin.setVisibility(View.GONE);
        if (!summary.hasFriends()) {
            // Sin amigos: una línea que invita (nunca nombres de maqueta).
            binding.homeFriendsTitle.setText(R.string.home_friends_invite_title);
            row.setVisibility(View.GONE);
            binding.homeFriendsNote.setText(R.string.home_friends_invite_note);
            binding.homeFriendsNote.setVisibility(View.VISIBLE);
            return;
        }
        binding.homeFriendsNote.setVisibility(View.GONE);
        if (summary.isNobodyStudying()) {
            // Con amigos pero sin nadie estudiando: una línea tranquila, sin avatares.
            binding.homeFriendsTitle.setText(R.string.home_friends_nobody);
            row.setVisibility(View.GONE);
            return;
        }
        // M/40: avatares solapados encima y «2 estudiando ahora» debajo.
        int count = summary.getStudyingCount();
        binding.homeFriendsTitle.setText(getResources().getQuantityString(
                R.plurals.home_friends_studying_short, count, count));
        row.setVisibility(View.VISIBLE);

        int size = getResources().getDimensionPixelSize(R.dimen.home_friend_avatar_size);
        int overlap = getResources().getDimensionPixelSize(R.dimen.home_friend_avatar_overlap);
        List<Friend> shown = summary.getShown();
        for (int i = 0; i < shown.size(); i++) {
            Friend friend = shown.get(i);
            AvatarView avatar = new AvatarView(requireContext());
            avatar.setName(friend.getName());
            // html 04: una sola letra en los avatares solapados de 32dp.
            String initials = friend.getInitials();
            avatar.setInitials(initials.isEmpty() ? "" : initials.substring(0, 1));
            avatar.setFillColor(AvatarPalette.color(requireContext(), friend.getAvatarColor()));
            avatar.setInitialsColor(AvatarPalette.onColor(requireContext()));
            avatar.setStatus(AvatarView.Status.STUDYING);
            avatar.setShowPresenceDot(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(i == 0 ? 0 : overlap);
            row.addView(avatar, i, lp);
        }
        // Los nombres quedan para lectores de pantalla (la maqueta no los pinta).
        StringBuilder names = new StringBuilder();
        for (String name : summary.getShownFirstNames()) {
            names.append(names.length() == 0 ? "" : ", ").append(name);
        }
        String desc = summary.getMoreCount() > 0
                ? getString(R.string.home_friends_names_more, names, summary.getMoreCount())
                : names.toString();
        row.setContentDescription(desc);
    }

    /** «A continuación · 16:30» (o día + hora si no es hoy) y, debajo, el título del siguiente evento. */
    private void renderNextUp(@Nullable CalendarEntry entry) {
        if (binding == null) {
            return;
        }
        if (entry == null) {
            binding.homeNextUpLabel.setText(R.string.home_next_up);
            binding.homeNextUpText.setText(R.string.home_next_up_empty);
            return;
        }
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(entry.getStartMillis());
        Calendar now = Calendar.getInstance();
        boolean today = start.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && start.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
        String when = new SimpleDateFormat(today ? "HH:mm" : "EEE HH:mm", Locale.getDefault())
                .format(new Date(entry.getStartMillis()));
        binding.homeNextUpLabel.setText(getString(R.string.home_next_up_when, when));
        binding.homeNextUpText.setText(entry.getTitle());
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        HapticFeedback.lightTap(view);
        if (id == R.id.homePlayButton) {
            onPlayTapped();
        } else if (id == R.id.homeTimerCard) {
            openFocus();
        } else if (id == R.id.homeCycleChip) {
            showTimerSettings();
        } else if (id == R.id.homeBellButton) {
            Integer requests = homeViewModel.getBellRequests().getValue();
            if (requests != null && requests != HomeViewModel.BELL_LEGACY) {
                // F5-u7: con perfil, la campana abre la bandeja (ánimos, empujones y solicitudes).
                NavHostFragment.findNavController(this).navigate(R.id.navigation_inbox);
            } else {
                // Sin cuenta o en release: la campana sigue como antes (aviso «pronto»).
                Toast.makeText(requireContext(), R.string.home_notifications_soon, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.homeNextUpCard) {
            openCalendarTab();
        } else if (id == R.id.homeAddTaskButton) {
            openQuickAdd();
        } else if (id == R.id.homeShowOverdueButton) {
            toggleOverdueSection();
        }
    }

    /** Como un toque en la pestaña Calendar (mantiene el estado de la barra inferior). */
    private void openCalendarTab() {
        View nav = requireActivity().findViewById(R.id.nav_view);
        if (nav instanceof BottomNavigationView) {
            ((BottomNavigationView) nav)
                    .setSelectedItemId(R.id.navigation_calendar_month);
        }
    }

    @Override
    public void onDestroyView() {
        lastRenderedState = null;
        super.onDestroyView();
        binding = null;
    }

    // La detección de primer/segundo plano y el periodo de gracia (FAIL_GRACE_MILLIS) viven en
    // FocusSessionViewModel + FocusBackgroundObserver (a nivel de proceso, u14). El servicio en
    // primer plano lo arranca/para FocusSessionViewModel según el estado de la sesión (u13).

    @Override
    public void onResume() {
        super.onResume();
        // The accessibility service clears the live block flag when it reconnects: re-assert it.
        focusSession.syncBlockState();
        // Recalcula [startOfToday, endOfToday] (sólo re-dispara la query si el día cambió) y
        // re-ancla la ventana de "Next up" a ahora.
        homeViewModel.refreshToday();
        // "Focus this" desde otra pantalla (p. ej. detalle de proyecto) fija FocusTarget y navega a
        // Home; aquí consumimos el arranque pendiente (una vez) y abrimos Focus.
        if (FocusTarget.consumePendingAutoStart()) {
            startBoundConcentrationIfIdle();
            openFocus();
            return;
        }
        renderCycleLine();
        // Red de seguridad: un resultado de la sesión (premio, fallo, ciclos…) que llegara con el
        // estado guardado sigue pendiente; aquí ya es seguro comitear.
        sessionEvents.recheck();
    }

    @Override
    public void OnClosePopup(int popupType) {
    }

    //Método genérico llamado al cerrar los diferentes popups
    @Override
    public void OnClosePopup(int popupType, Object result) {
        try {
            if (popupType == PopupHelper.TIMER_POPUP) {
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

    /** Tap en la tarjeta de racha (spec focus-mode-and-streak). */
    private void showStreakPopup() {
        new FocusStreakPopup().show(getParentFragmentManager(), FocusStreakPopup.POPUP_TAG);
    }

    /** Tap en el chip "Cycle x of N": configuración del pomodoro (antes el engranaje). */
    private void showTimerSettings() {
        timerPopup.setConfiguration(configurationManager.getConfiguration());
        timerPopup.show(getParentFragmentManager(), "popup_tag");
    }
}
