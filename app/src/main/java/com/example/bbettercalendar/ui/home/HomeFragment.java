package com.example.bbettercalendar.ui.home;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.blocking.FocusBlockState;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.databinding.FragmentHomeBinding;
import com.example.bbettercalendar.notifications.focus.FocusFailNotifier;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.OnToolbarHomeListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.popups.AlertPopup;
import com.example.bbettercalendar.popups.MessagePopup;
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.popups.TimerPopup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment implements View.OnClickListener, OnToolBarListener, OnToolbarHomeListener, OnPopupListener<Object>, AccessibilityDisclosureDialog.OnConsentGrantedListener {

    private final int TIMER_STOPPED = 0;
    private final int TIMER_RUNNING = 1;
    private final int TIMER_PAUSED = 3;
    private final int TIMER_STOPPED_REST = 4;
    private final int TIMER_RUNNING_REST = 5;
    private final int TIMER_PAUSED_REST = 6;

    // Saved-instance keys: keep the live Pomodoro state across fragment recreation
    // (rotation, back-stack return, short-lived process death).
    private static final String KEY_TIMER_STATE = "bb_home_timer_state";
    private static final String KEY_TIME_LEFT = "bb_home_time_left_millis";
    private static final String KEY_LAST_TIMER_TIME = "bb_home_last_timer_time";
    private static final String KEY_CYCLES_COMPLETED = "bb_home_cycles_completed";
    private static final String KEY_BLOCK_MODE_ARMED = "bb_home_block_mode_armed";


    private final String TAG = "HomeFragmentTag";
    private FragmentHomeBinding binding;
    HomeViewModel homeViewModel;
    private AlertPopup alertPopup = new AlertPopup();
    private TimerPopup timerPopup = new TimerPopup();
    private MessagePopup messagePopup;

    private ToolbarHelper toolbarHelper;
    private ImageButton playButton;
    private TextView skipRestButton;
    private TextView blockModeButton;
    private TextView timerText;
    private TextView currentStreakText;
    private TextView todayFailsText;
    private TextView todayTimeStudiedText;
    private TextView timerModeText;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private int timer_state = TIMER_STOPPED;
    private int lastTimerTime =10000;

    private CountDownTimer countDownTimer = null;
    private long timeLeftInMillis = 10000; // 20 minutos --> 60000 * 20
    private boolean isBackground = false;
    private boolean isTimerFailed = false;

    private int cyclesCompleted = 0;
    private boolean blockModeArmed = false;

    // "Today" task list (spec tasks-home-today)
    private TodayTaskAdapter todayTaskAdapter;
    private TodayTaskAdapter overdueTaskAdapter;
    private boolean overdueExpanded = false;

    @Inject
    ConfigurationManager configurationManager;

    @Inject
    FocusFailNotifier focusFailNotifier;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        homeViewModel.setConfigManager(configurationManager);
        final TextView textView = binding.textHome;
        playButton = binding.homePlayButton;
        playButton.setOnClickListener(this);
        skipRestButton = binding.homeSkipRestButton;
        skipRestButton.setOnClickListener(this);
        blockModeButton = binding.homeBlockModeButton;
        blockModeButton.setOnClickListener(this);
        timerText = binding.homeTimerText;
        currentStreakText = binding.homeCurrentStreakText;
        todayFailsText = binding.homeToadyFailsText;
        todayTimeStudiedText = binding.homeTodayTimeText;
        timerModeText = binding.homeTimerTypeText;
        timeLeftInMillis = homeViewModel.configManager.getConfiguration().getHomeTimerTime();

        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.home_toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        toolbarHelper.setOnToolbarHomeListener(this);
        //todo crear listener personalizado
        // toolbarHelper.setOnToolbarCalendarListener(this);

        alertPopup.setOnPopupListener(this);
        timerPopup.setOnPopupListener(this);
        messagePopup = new MessagePopup();
        messagePopup.setOnPopupListener(this);



        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        homeViewModel.getTimerText().observe(getViewLifecycleOwner(), timerText::setText);
        homeViewModel.getCurrentStreakText().observe(getViewLifecycleOwner(), currentStreakText::setText);
        homeViewModel.getTodayFailsText().observe(getViewLifecycleOwner(), todayFailsText::setText);
        homeViewModel.getTodayTimeStudiedText().observe(getViewLifecycleOwner(), todayTimeStudiedText::setText);
        homeViewModel.getTimerModeText().observe(getViewLifecycleOwner(), timerModeText::setText);

        binding.homeDateText.setText(
                new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date()));

        setUpTaskList();

        SoundFeedback.get(requireContext()); // warm singleton so first tap is silent-fast

        if (savedInstanceState != null) {
            timer_state = savedInstanceState.getInt(KEY_TIMER_STATE, timer_state);
            timeLeftInMillis = savedInstanceState.getLong(KEY_TIME_LEFT, timeLeftInMillis);
            lastTimerTime = savedInstanceState.getInt(KEY_LAST_TIMER_TIME, lastTimerTime);
            cyclesCompleted = savedInstanceState.getInt(KEY_CYCLES_COMPLETED, cyclesCompleted);
            blockModeArmed = savedInstanceState.getBoolean(KEY_BLOCK_MODE_ARMED, blockModeArmed);
            // Defer the render: setConfigManager() above posts the default configured time
            // to the timerText LiveData, which would otherwise clobber the restored countdown.
            // Posting to the view queue runs after that pending LiveData delivery.
            root.post(this::renderRestoredState);
        }

        setTopMenu();
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TIMER_STATE, timer_state);
        outState.putLong(KEY_TIME_LEFT, timeLeftInMillis);
        outState.putInt(KEY_LAST_TIMER_TIME, lastTimerTime);
        outState.putInt(KEY_CYCLES_COMPLETED, cyclesCompleted);
        outState.putBoolean(KEY_BLOCK_MODE_ARMED, blockModeArmed);
    }

    /**
     * Tarjeta "Tasks" de Home (spec tasks-home-today): lista de hoy con checkbox,
     * alta rápida (bottom sheet) y sección plegable de tareas atrasadas.
     */
    private void setUpTaskList() {
        todayTaskAdapter = new TodayTaskAdapter(false,
                (entry, done) -> homeViewModel.setTaskDone(entry, done));
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

    private void toggleOverdueSection() {
        overdueExpanded = !overdueExpanded;
        binding.homeOverdueSection.setVisibility(overdueExpanded ? View.VISIBLE : View.GONE);
        binding.homeShowOverdueButton.setText(overdueExpanded
                ? R.string.home_tasks_hide_overdue : R.string.home_tasks_show_overdue);
        homeViewModel.setOverdueVisible(overdueExpanded);
    }

    /**
     * Re-render the timer UI from restored instance fields and, if it was running,
     * resume the countdown from the saved remaining time. Paused/stopped states are
     * only re-rendered, never auto-started.
     */
    private void renderRestoredState() {
        if (binding == null) return;

        boolean isRest = timer_state == TIMER_RUNNING_REST
                || timer_state == TIMER_PAUSED_REST
                || timer_state == TIMER_STOPPED_REST;
        updateModeChip(isRest);
        timerText.setText(FormatHelper.formatTime((int) timeLeftInMillis, "mm:ss"));

        if (timer_state == TIMER_RUNNING) {
            // startTimer() flips PAUSED -> RUNNING; pretend we were paused so it resumes cleanly.
            timer_state = TIMER_PAUSED;
            startTimer(timeLeftInMillis);
        } else if (timer_state == TIMER_RUNNING_REST) {
            timer_state = TIMER_PAUSED_REST;
            startTimer(timeLeftInMillis);
        }
        // PAUSED* / STOPPED* : text + chip restored above, do not auto-start.
        updateTimerControls();
    }

    private void updateModeChip(boolean isRest) {
        if (timerModeText == null) return;
        timerModeText.setBackgroundResource(
                isRest ? R.drawable.bg_chip_secondary : R.drawable.bg_chip_energy);
    }

    private void startTimer(long time){

        countDownTimer = new CountDownTimer(time, 1000) { // 60000 milisegundos = 60 segundos, con intervalo de 1000 milisegundos = 1 segundo

            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                timerText.setText(FormatHelper.formatTime((int)millisUntilFinished, "mm:ss"));
            }

            public void onFinish() {
                //reproducir sonido, resetear timer y anotar resultado
                if(timer_state == TIMER_RUNNING)
                    completeTimer();
                else
                    completeRest();
            }
        }.start();
        if(timer_state == TIMER_STOPPED || timer_state == TIMER_PAUSED)
            timer_state = TIMER_RUNNING;
        else if(timer_state == TIMER_STOPPED_REST || timer_state == TIMER_PAUSED_REST)
            timer_state = TIMER_RUNNING_REST;
        else
            Log.e(TAG, "Error en el estado del timer");
        updateTimerControls();
    }

    //Cuando el timer logra llegar a 0
    private void completeTimer(){
        timer_state = TIMER_STOPPED;
        //reproducir sonido indicador
        homeViewModel.completeTimer(lastTimerTime);
        //Si el descanso está activado, y no se ha llegado al límite de descansos predefinidos, se activa el descanso
        if(configurationManager.getConfiguration().isHomeIsRestEnabled() )
        {
            if(cyclesCompleted < configurationManager.getConfiguration().getHomeNumberOfCycles()
                    || configurationManager.getConfiguration().isHomeIsInfiniteCycleEnabled())
            {
                setRestTimer();
            }
            else if(cyclesCompleted >= configurationManager.getConfiguration().getHomeNumberOfCycles())
            {
                messagePopup.setText("Ciclo finalizado, Has completado el ciclo de estudio");
                messagePopup.show(getParentFragmentManager(), "popup_tag");
                resetTimerAndCycles();
            }
        }else{ resetTimer();}
    }

    private void completeRest(){
        timer_state = TIMER_STOPPED;
        cyclesCompleted++;
        resetTimer();
        if(configurationManager.getConfiguration().isHomeIsAutoCycle()){
            if(cyclesCompleted < configurationManager.getConfiguration().getHomeNumberOfCycles()
                    || configurationManager.getConfiguration().isHomeIsInfiniteCycleEnabled())
            {   //si aun quedan ciclos por realizar, se inicia el siguiente ciclo
                startTimer(configurationManager.getConfiguration().getHomeTimerTime());
            }
            else if(cyclesCompleted >= configurationManager.getConfiguration().getHomeNumberOfCycles())
            {   //si se han completado todos los ciclos, se muestra un mensaje y se resetea el contador
                messagePopup.setText("Ciclo finalizado, Has completado el ciclo de estudio");
                messagePopup.show(getParentFragmentManager(), "popup_tag");
                cyclesCompleted = 0;
            }
        }
    }

    private void setRestTimer(){
        timer_state = TIMER_STOPPED_REST;
        int actualTime = homeViewModel.configManager.getConfiguration().getHomeRestTime();
        homeViewModel.setRestTimer();
        timeLeftInMillis = actualTime;
        updateModeChip(true);
        //todo se puede implementar una función para que solo se inicicie el descanso automático si el usuario lo desea
        startTimer(actualTime);
    }

    private void resetTimerAndCycles(){
        cyclesCompleted = 0;
        resetTimer();
    }

    private void resetTimer(){
        timeLeftInMillis =  configurationManager.getConfiguration().getHomeTimerTime();
        homeViewModel.resetTimer();
        updateModeChip(false);
        updateTimerControls();
    }

    //Se pulsa el timer para pausarlo
    private void pauseTimer(boolean isRest){
        countDownTimer.cancel();
        timer_state = isRest ? TIMER_PAUSED_REST : TIMER_PAUSED;
        updateTimerControls();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        HapticFeedback.lightTap(view);
        if (id == R.id.homePlayButton) {
            Log.i(TAG, "Timer started");
            SoundFeedback feedback = SoundFeedback.get(requireContext());
            if(timer_state == TIMER_STOPPED){
                int actualTime = homeViewModel.configManager.getConfiguration().getHomeTimerTime();
                lastTimerTime = actualTime;
                timeLeftInMillis = actualTime;
                startTimer(actualTime);
                feedback.playStart();
            } else if( timer_state == TIMER_PAUSED || timer_state == TIMER_PAUSED_REST){
                startTimer(timeLeftInMillis);
                feedback.playStart();
            }else if(timer_state == TIMER_RUNNING){
                pauseTimer(false);
                feedback.playStop();
            } else if (timer_state == TIMER_RUNNING_REST) {
                pauseTimer(true);
                feedback.playStop();
            }else if(timer_state == TIMER_STOPPED_REST){
                int actualTime = homeViewModel.configManager.getConfiguration().getHomeRestTime();
                timeLeftInMillis = actualTime;
                startTimer(actualTime);
                feedback.playStart();
            }
            updateTimerControls();
        } else if (id == R.id.homeSkipRestButton) {
            cancelRest();
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
     * On -> off never needs permission. The button itself is disabled while TIMER_RUNNING (see
     * updateBlockModeButton()), so this never fires mid-block.
     */
    private void handleBlockModeToggle() {
        if (blockModeArmed) {
            blockModeArmed = false;
            updateTimerControls();
            return;
        }
        if (AccessibilityAccess.isEnabled(requireContext())) {
            blockModeArmed = true;
            updateTimerControls();
        } else {
            AccessibilityDisclosureDialog.newInstance()
                    .show(getChildFragmentManager(), "accessibility_disclosure");
        }
    }

    @Override
    public void onAccessibilityConsentGranted() {
        blockModeArmed = true;
        updateTimerControls();
    }

    /**
     * Cancel the rest period (running, paused or pending) and return to a stopped
     * concentration state so the user can start working again immediately.
     * The focus cycle that triggered this rest still counts as completed.
     */
    private void cancelRest() {
        if (timer_state != TIMER_RUNNING_REST
                && timer_state != TIMER_PAUSED_REST
                && timer_state != TIMER_STOPPED_REST) {
            return;
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        cyclesCompleted++;
        timer_state = TIMER_STOPPED;
        resetTimer();
        updateTimerControls();
    }

    /** Keep the play/pause icon and the skip-rest button in sync with timer_state. */
    private void updateTimerControls() {
        if (binding == null) return;

        boolean isRunning = timer_state == TIMER_RUNNING || timer_state == TIMER_RUNNING_REST;
        playButton.setImageResource(
                isRunning ? R.drawable.ic_pause_v2_filled_24 : R.drawable.ic_play_v2_filled_24);
        playButton.setContentDescription(
                getString(isRunning ? R.string.home_pause : R.string.home_play));

        boolean isRest = timer_state == TIMER_RUNNING_REST
                || timer_state == TIMER_PAUSED_REST
                || timer_state == TIMER_STOPPED_REST;
        skipRestButton.setVisibility(isRest ? View.VISIBLE : View.GONE);

        updateBlockModeButton();
    }

    /**
     * Render the 🚫 block-mode toggle's 3 states (muted off / bb_danger active / bb_accent_reward
     * pending-permission — same visual language as the Progress enforce toggle) and push the actual
     * live-blocking flag. FocusBlockState is only ever true here, exactly while a concentration run
     * (not rest) is running AND armed — this is the single writer for that state (see
     * blocking/FocusBlockState.java for the kill-trap mitigation this depends on).
     */
    private void updateBlockModeButton() {
        if (binding == null || blockModeButton == null) return;

        int tint;
        float alpha;
        if (!blockModeArmed) {
            tint = R.color.bb_on_surface_muted;
            alpha = 0.35f;
        } else if (AccessibilityAccess.isEnabled(requireContext())) {
            tint = R.color.bb_danger;
            alpha = 1f;
        } else {
            tint = R.color.bb_accent_reward;
            alpha = 0.9f;
        }
        blockModeButton.setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), tint));
        blockModeButton.setAlpha(alpha);
        blockModeButton.setEnabled(timer_state != TIMER_RUNNING);

        FocusBlockState.setActive(requireContext(), blockModeArmed && timer_state == TIMER_RUNNING);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onToolbarLoaded(int result) {
        switch (result){
            case ToolbarHelper.FINISH:
                break;
            default:
                break;
        }
    }

    /** Sistema per detectar si l'aplicació està en primer pla o en segon pla */

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            private int numStarted = 1;

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            //Esto se activa cuando se vuelve a la app después de haberla minimizado
            @Override
            public void onActivityStarted(Activity activity) {
                if (numStarted == 0) {
                    // App enters foreground
                    Log.d(TAG, "App enters foreground");
                    isBackground = false;
                }
                numStarted++;
            }

            //Detectamos cuando se vuelve a la app despu´se de joder el timer
            @Override
            public void onActivityResumed(Activity activity) {
                if(isTimerFailed){
                    isTimerFailed = false;
                    alertPopup.selectView(PopupHelper.ALERT_POPUP);
                    alertPopup.show(getParentFragmentManager(), "popup_tag");
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {
                numStarted--;
                if (numStarted == 0) {
                    // App enters background
                    Log.i(TAG, "App enters background");
                    // En modo bloqueo total (blockModeArmed), FocusBlockState ya cubre cualquier
                    // otra app con la portada -- el usuario no puede realmente "irse" a usar otra
                    // cosa, así que salir a segundo plano no debe fallar la sesión como en el modo
                    // normal (ver blocking/FocusBlockState.java y el proposal de pomodoro-block-mode).
                    if(timer_state == TIMER_RUNNING && !blockModeArmed){
                        isBackground = true;

                        // Crea una instancia de Handler
                        Handler handler = new Handler(Looper.getMainLooper());

                        // Ejecuta un Runnable después de un retraso de X milisegundos
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Código a ejecutar después del retraso
                                failTimer();
                            }
                        }, FocusSessionConstants.FAIL_GRACE_MILLIS);
                    }
                }
            }

            private void failTimer(){
                if(isBackground){
                    // Resetear el timer, mostrar notificación de fallo y guardar el error en la base de datos
                    Log.i(TAG, "fragment, Timer failed");
                    countDownTimer.cancel();
                    timer_state = TIMER_STOPPED;
                    homeViewModel.addFails();
                    focusFailNotifier.fire();
                    isTimerFailed=true;
                    isBackground=false;
                    updateTimerControls();
                }
            }


            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        };

        Application application = (Application) context.getApplicationContext();
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
    }


    @Override
    public void onDetach() {
        super.onDetach();
        Application application = (Application) getContext().getApplicationContext();
        application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
    }


    @Override
    public void onResume() {
        super.onResume();
        // No callback from Settings -> Accessibility (same caveat as ProgressFragment): re-check
        // and re-render in case the user granted/revoked the service while away.
        updateBlockModeButton();
        // Rango de hoy + requery: cubre volver de AddEventActivity (lag del
        // InvalidationTracker) y el cambio de día con la app abierta.
        homeViewModel.refreshToday();
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
            resetTimer();
        }
    }

    private void checkConfigChanges(Configuration config){

        if(!config.isHomeIsRestEnabled()) {
            if (timer_state == TIMER_STOPPED_REST) {
                timer_state = TIMER_STOPPED;
                resetTimerAndCycles();
            }
            if (timer_state == TIMER_RUNNING_REST || timer_state == TIMER_PAUSED_REST) {
                countDownTimer.cancel();
                timer_state = TIMER_STOPPED;
                resetTimerAndCycles();
            }
        }
        if(!config.isHomeIsAutoCycle()){
            cyclesCompleted = 0;
        }


        //todo mirar otras cosas como el autoCycle y eso
    }


    //Método genérico llamado al cerrar los diferentes popups
    @Override
    public void OnClosePopup(int popupType, Object result) {
        try {
            if (popupType == PopupHelper.ALERT_POPUP) {
                resetTimer();
            } else if (popupType == PopupHelper.TIMER_POPUP) {
                Configuration config = (Configuration) result;
                checkConfigChanges(config);
                homeViewModel.updateConfiguration(config);
                Log.i(TAG, "Configuration received: ");
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Error on closing popup: " + e.getMessage());
        }
    }

    @Override
    public void onToolbarTimerClick(){
        timerPopup.setConfiguration(configurationManager.getConfiguration());
        timerPopup.show(getParentFragmentManager(), "popup_tag");
    }


    private void setTopMenu() {
        getActivity().addMenuProvider(toolbarHelper, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }
    /** --------------------------------------------------------------------------- */
}