package com.example.bbettercalendar;


import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.bbettercalendar.configuration.InitialConfiguration;
import com.example.bbettercalendar.notifications.PermissionGate;
import com.example.bbettercalendar.stats.StatsDAO;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.bbettercalendar.databinding.ActivityMainBinding;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivityTAG";
    private ActivityMainBinding binding;
    private StatsDAO statsDao;
    private ExecutorService executorService;

    @Inject PermissionGate permissionGate;

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.ThemeChatGPTBlue);
        super.onCreate(savedInstanceState);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { /* result captured by PermissionGate via Configuration state */ });

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Iniciar la configuración si no se ha hecho antes
        if (InitialConfiguration.getInstance().getInitializationStatus().getValue() == null) {
            //InitialConfiguration.getInstance().initialize(this);
        }

        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_progress, R.id.navigation_calendar_month, R.id.navigation_projects)
                .build();
        // Con FragmentContainerView, obtener el NavController vía el NavHostFragment (no con
        // Navigation.findNavController(activity, id), que puede fallar en onCreate porque la vista
        // del fragment aún no tiene asignado el NavController en su tag).
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
    }

    @Override
    protected void onStart() {
        super.onStart();
        permissionGate.maybeRequest(this, notificationPermissionLauncher);
    }
}