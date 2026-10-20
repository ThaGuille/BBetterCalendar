package com.example.bbettercalendar.ui.home;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

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
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.example.bbettercalendar.ui.focus.FocusSessionEventPresenter;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.focus.FocusUiState;
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

    // "Today" task list (spec tasks-home-today)
    private TodayTaskAdapter todayTaskAdapter;
    private TodayTaskAdapter overdueTaskAdapter;
    private boolean overdueExpanded = false;
    // Primera tarea pendiente de hoy: el "Next: <tarea>" de la tarjeta del temporizador.
    private String nextTaskTitle;

    @Inject
    ConfigurationManager configurationManager;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        focusSession = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        binding.homePlayButton.setOnClickListener(this);
        binding.homeTimerCard.setOnClickListener(this);
        binding.homeCycleChip.setOnClickListener(this);
        binding.homeBellButton.setOnClickListener(this);
        binding.homeNextUpCard.setOnClickListener(this);
        // Racha: tap = FocusStreakPopup (spec focus-mode-and-streak).
        binding.homeStreakButton.setOnClickListener(v -> showStreakPopup());

        timerPopup.setOnPopupListener(this);

        // Header. PLACEHOLDER(ui-placeholders#home-user-name): no hay nombre de usuario guardado,
        // saludo y avatar usan el nombre del diseño (html 04: "Hi, Guille", avatar "G").
        homeViewModel.getText().observe(getViewLifecycleOwner(), binding.textHome::setText);
        binding.homeAvatar.setInitials(
                getString(R.string.home_user_name_placeholder).substring(0, 1));
        binding.homeDateText.setText(
                new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date()));

        // Tarjeta del temporizador: texto, modo y ciclo salen del estado de la sesión.
        lastRenderedState = null;
        focusSession.getUiState().observe(getViewLifecycleOwner(), this::renderTimer);
        // Resultados de un solo uso de la sesión (ciclos completados → Cycle complete, fallo,
        // primer pomodoro del día, tarea auto-completada): los pinta el presentador compartido con
        // Focus (u14); el primero de los dos que esté visible los consume.
        sessionEvents = new FocusSessionEventPresenter(this, focusSession, this::renderCycleLine);
        sessionEvents.observe(getViewLifecycleOwner());

        // Racha real (StreakCalculator, u3): días seguidos + semana L→D.
        homeViewModel.getStreak().observe(getViewLifecycleOwner(), binding.homeStreakWeek::setStreak);

        // Amigos estudiando (maqueta tras FEATURE_SOCIAL_MOCK; GONE sin hueco con el flag apagado).
        SocialFeature.applyVisibility(binding.homeFriendsCard);
        if (SocialFeature.isEnabled()) {
            homeViewModel.getSocialSnapshot().observe(getViewLifecycleOwner(), this::renderFriends);
        }
        setUpSocialEntry();

        homeViewModel.getNextUp().observe(getViewLifecycleOwner(), this::renderNextUp);

        setUpTaskList();

        SoundFeedback.get(requireContext()); // warm singleton so first tap is silent-fast

        // Sin restauración manual: la sesión sobrevive a la rotación en el ViewModel de actividad
        // y a la muerte del proceso en su SavedStateHandle (instante de fin, no ticks).
        return binding.getRoot();
    }

    /**
     * Tarjeta "Tasks" de Home (spec tasks-home-today): lista de hoy con checkbox, chip de
     * proyecto (u13), alta rápida (bottom sheet) y sección plegable de tareas atrasadas.
     */
    private void setUpTaskList() {
        todayTaskAdapter = new TodayTaskAdapter(false,
                (entry, done) -> homeViewModel.setTaskDone(entry, done));
        // "Focus this" (spec focus-attribution): vincula el timer a la tarea, arranca y abre Focus.
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
        updateOverdueToggle();

        // La vista siempre renace plegada (overdueExpanded = false), pero el ViewModel
        // sobrevive a la rotación: sin este reset su query de atrasadas seguiría viva
        // e invisible tras recrear la vista.
        overdueExpanded = false;
        homeViewModel.setOverdueVisible(false);

        homeViewModel.getTodayTasks().observe(getViewLifecycleOwner(), tasks -> {
            todayTaskAdapter.submitList(tasks);
            binding.homeTasksEmptyText.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
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
        if (!state.controlsDifferFrom(lastRenderedState)) {
            lastRenderedState = state;
            return;
        }
        lastRenderedState = state;

        int mode;
        if (state.isPaused()) {
            mode = R.string.home_mode_paused;
        } else if (state.isRest()) {
            mode = R.string.home_mode_rest;
        } else {
            mode = R.string.home_mode_concentration;
        }
        binding.homeTimerTypeText.setText(mode);

        // Idle: play. Sesión viva (corriendo, en pausa o en descanso): el botón y la tarjeta
        // llevan a Focus, donde están pausa / stop / saltar descanso (u14).
        boolean active = state.isSessionActive();
        binding.homePlayButton.setImageResource(
                active ? R.drawable.ic_arrow_forward_24 : R.drawable.ic_play_v2_filled_24);
        binding.homePlayButton.setContentDescription(
                getString(active ? R.string.home_open_focus : R.string.home_play));
        binding.homeTimerCard.setClickable(active);
        binding.homeTimerCard.setContentDescription(active ? getString(R.string.home_open_focus) : null);
        renderCycleLine();
    }

    /** "Cycle x of N · Next: <tarea>" (html 04) o "· Focusing: <tarea>" si hay una vinculada. */
    private void renderCycleLine() {
        if (binding == null || lastRenderedState == null) {
            return;
        }
        FocusUiState state = lastRenderedState;
        // En reposo cuenta los ciclos hechos ("Cycle 0 of 4", como el diseño); en marcha, el actual.
        int cycle = state.isSessionActive() ? state.getCurrentCycle() : state.getCyclesCompleted();
        String cycleText = state.isInfiniteCycles()
                ? getString(R.string.home_cycle_infinite_format, cycle)
                : getString(R.string.home_cycle_format, cycle, state.getTotalCycles());
        String line;
        if (FocusTarget.isSet() && FocusTarget.getTitle() != null) {
            line = getString(R.string.home_cycle_focusing_format, cycleText, FocusTarget.getTitle());
        } else if (nextTaskTitle != null) {
            line = getString(R.string.home_cycle_next_format, cycleText, nextTaskTitle);
        } else {
            line = cycleText;
        }
        binding.homeCycleText.setText(line);
        binding.homeCycleChip.setContentDescription(getString(R.string.home_cycle_chip_desc, line));
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
            nav.navigate(R.id.navigation_focus);
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
        entry.getEntryStep().observe(getViewLifecycleOwner(), step -> {
            if (step == null) {
                return;
            }
            entry.consumeEntryStep();
            SocialEntryNavigator.go(this, step);
        });
        entry.getAccountState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) {
                return;
            }
            boolean signedOut = state == AccountState.SIGNED_OUT;
            if (signedOut) {
                binding.homeFriendsCard.setVisibility(View.VISIBLE);
                binding.homeFriendsTitle.setText(R.string.social_cta_title);
                binding.homeFriendsDot.setVisibility(View.GONE);
                binding.homeFriendsAvatars.setVisibility(View.GONE);
                binding.homeFriendsJoin.setVisibility(View.VISIBLE);
                binding.homeFriendsCard.setOnClickListener(v -> entry.requestEntry());
            } else {
                binding.homeFriendsDot.setVisibility(View.VISIBLE);
                binding.homeFriendsAvatars.setVisibility(View.VISIBLE);
                binding.homeFriendsJoin.setVisibility(View.GONE);
                binding.homeFriendsCard.setOnClickListener(null);
                binding.homeFriendsCard.setClickable(false);
                SocialFeature.applyVisibility(binding.homeFriendsCard);
                // La maqueta pisó el título con la llamada a la acción: repintarla.
                renderFriends(homeViewModel.getSocialSnapshot().getValue());
            }
        });
    }

    // PLACEHOLDER(ui-placeholders#social-home-friends): maqueta (MockSocialRepository).
    private void renderFriends(SocialSnapshot snapshot) {
        if (binding == null || snapshot == null) {
            return;
        }
        List<Friend> studying = snapshot.getStudying();
        binding.homeFriendsTitle.setText(getResources().getQuantityString(
                R.plurals.home_friends_studying, studying.size(), studying.size()));

        LinearLayout row = binding.homeFriendsAvatars;
        // Quitar los avatares de una emisión anterior (el último hijo es el texto de nombres).
        while (row.getChildCount() > 1) {
            row.removeViewAt(0);
        }
        int size = getResources().getDimensionPixelSize(R.dimen.home_friend_avatar_size);
        int overlap = getResources().getDimensionPixelSize(R.dimen.home_friend_avatar_overlap);
        int shown = Math.min(MAX_FRIEND_AVATARS, studying.size());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            Friend friend = studying.get(i);
            AvatarView avatar = new AvatarView(requireContext());
            avatar.setName(friend.getName());
            // html 04: una sola letra en los avatares solapados de 32dp.
            avatar.setInitials(friend.getName().substring(0, 1));
            avatar.setStatus(AvatarView.Status.STUDYING);
            avatar.setShowPresenceDot(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(i == 0 ? 0 : overlap);
            row.addView(avatar, i, lp);
            names.add(friend.getName().split(" ")[0]);
        }
        binding.homeFriendsNames.setText(TextUtils.join(", ", names));
    }

    /** "Next up": hora (o día + hora si no es hoy) y título del siguiente evento. */
    private void renderNextUp(@Nullable CalendarEntry entry) {
        if (binding == null) {
            return;
        }
        if (entry == null) {
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
        binding.homeNextUpText.setText(when + " " + entry.getTitle());
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
            // PLACEHOLDER(ui-placeholders#home-notification-centre)
            Toast.makeText(requireContext(), R.string.home_notifications_soon, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.homeNextUpCard) {
            openCalendarTab();
        } else if (id == R.id.homeAddTaskButton) {
            // childFragmentManager: QuickAddTaskSheet localiza este fragment como parent
            // para compartir el HomeViewModel.
            new QuickAddTaskSheet().show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG);
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
