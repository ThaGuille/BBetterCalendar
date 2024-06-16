package com.example.bbettercalendar.ui.home;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Debug;
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
import com.example.bbettercalendar.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements View.OnClickListener{

    private final String TAG = "HomeFragmentTag";
    private FragmentHomeBinding binding;
    private boolean timerActive = false;
    private ImageButton homeTimerButton;
    private ImageButton playButton;
    private TextView timerText;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        homeTimerButton = binding.homeTimerButton;
        homeTimerButton.setOnClickListener(this);
        playButton = binding.homePlayButton;
        playButton.setOnClickListener(this);
        timerText = binding.homeTimerText;

        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        homeViewModel.getTimerText().observe(getViewLifecycleOwner(), timerText::setText);
        return root;
    }

    private void startTimer(){
        new CountDownTimer(60000 * 20, 1000) { // 60000 milisegundos = 60 segundos, con intervalo de 1000 milisegundos = 1 segundo

            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                timerText.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
            }

            public void onFinish() {
                timerText.setText("00:00");
            }
        }.start();
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
                startTimer();
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