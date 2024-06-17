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
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.databinding.FragmentHomeBinding;
import com.example.bbettercalendar.events.EventDao;
import com.example.bbettercalendar.stats.StatsDAO;

public class HomeFragment extends Fragment implements View.OnClickListener{

    private final int TIMER_STOPPED = 0;
    private final int TIMER_RUNNING = 1;
    private final int TIMER_PAUSED = 2;

    private final String TAG = "HomeFragmentTag";
    private FragmentHomeBinding binding;
    HomeViewModel homeViewModel;
    private boolean timerActive = false;
    private ImageButton homeTimerButton;
    private ImageButton playButton;
    private TextView timerText;
    private TextView currentStreakText;
    private TextView todayFailsText;
    private TextView todayTimeStudiedText;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private int timer_state = TIMER_STOPPED;
    private int lastTimerTime =10000;

    private CountDownTimer countDownTimer;
    //todo volver a poner esto a 20 min y el lastTimerTime
    private long timeLeftInMillis = 10000; // 20 minutos --> 60000 * 20
    private boolean isBackground = false;
    private StatsDAO statsDao;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        statsDao = AppDatabase.getDatabase(getContext()).statsDao();

        final TextView textView = binding.textHome;
        homeTimerButton = binding.homeTimerButton;
        homeTimerButton.setOnClickListener(this);
        playButton = binding.homePlayButton;
        playButton.setOnClickListener(this);
        timerText = binding.homeTimerText;
        currentStreakText = binding.homeCurrentStreakText;
        todayFailsText = binding.homeToadyFailsText;
        todayTimeStudiedText = binding.homeTodayTimeText;

        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        homeViewModel.getTimerText().observe(getViewLifecycleOwner(), timerText::setText);
        homeViewModel.getCurrentStreakText().observe(getViewLifecycleOwner(), currentStreakText::setText);
        homeViewModel.getTodayFailsText().observe(getViewLifecycleOwner(), todayFailsText::setText);
        homeViewModel.getTodayTimeStudiedText().observe(getViewLifecycleOwner(), todayFailsText::setText);
        return root;
    }

    private void startTimer(){
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) { // 60000 milisegundos = 60 segundos, con intervalo de 1000 milisegundos = 1 segundo

            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                long seconds = millisUntilFinished / 1000;
                timerText.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
            }

            public void onFinish() {
                //reproducir sonido, resetear timer y anotar resultado
                completeTimer();

            }
        }.start();
        timer_state = TIMER_RUNNING;
    }

    private void completeTimer(){
        timer_state = TIMER_STOPPED;
        //reproducir sonido indicador
        homeViewModel.completeTimer(lastTimerTime);
        timeLeftInMillis = 60000 * 20;
    }

    private void pauseTimer(){
        countDownTimer.cancel();
        timer_state = TIMER_PAUSED;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.homeTimerButton:
                timerActive= !timerActive;
                homeTimerButton.setImageResource(timerActive ? R.drawable.ic_timer_filled_24 : R.drawable.ic_timer_empty_24);
                break;
            case R.id.homePlayButton:
                Log.i(TAG, "Timer started");
                if(timer_state == TIMER_STOPPED || timer_state == TIMER_PAUSED){
                    startTimer();
                }else if(timer_state == TIMER_RUNNING){
                    pauseTimer();
                }
                break;
            default:
                break;
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Sistema per detectar si l'aplicació està en primer pla o en segon pla */

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            private int numStarted = 0;

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {
                if (numStarted == 0) {
                    // App enters foreground
                    Log.d(TAG, "App enters foreground");
                    isBackground = false;
                }
                numStarted++;
            }

            @Override
            public void onActivityResumed(Activity activity) {}

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {
                numStarted--;
                if (numStarted == 0) {
                    // App enters background
                    Log.i(TAG, "App enters background");
                    if(timer_state == TIMER_RUNNING){
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
                        }, 5000); // Retraso en milisegundos, por ejemplo, 5000 para 5 segundos
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
                    //statsDao.addFails();

                    pauseTimer();
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
    /** --------------------------------------------------------------------------- */
}