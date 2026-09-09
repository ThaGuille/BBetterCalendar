package com.example.bbettercalendar;


import android.content.Intent;
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

    /**
     * Extra del deep link de la notificación de deadline de proyecto (spec
     * project-deadlines-progress): id del proyecto que hay que abrir al entrar. Lo pone
     * {@code NotificationSpec.Builder.openProjectDetail()}.
     */
    public static final String EXTRA_OPEN_PROJECT_ID = "open_project_id";

    private final String TAG = "MainActivityTAG";
    private ActivityMainBinding binding;
    private StatsDAO statsDao;
    private ExecutorService executorService;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

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
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_progress, R.id.navigation_calendar_month, R.id.navigation_projects)
                .build();
        // Con FragmentContainerView, obtener el NavController vía el NavHostFragment (no con
        // Navigation.findNavController(activity, id), que puede fallar en onCreate porque la vista
        // del fragment aún no tiene asignado el NavController en su tag).
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);
        navController = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        openProjectFromIntent(getIntent());
    }

    // El Intent de la notificación lleva CLEAR_TOP y el launchMode por defecto es standard, así que
    // puede llegar por cualquiera de los dos caminos: recreando la Activity (onCreate) o
    // reentregado a la instancia viva (onNewIntent). Hay que atender los dos.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openProjectFromIntent(intent);
    }

    private void openProjectFromIntent(Intent intent) {
        if (intent == null || navController == null) return;
        int projectId = intent.getIntExtra(EXTRA_OPEN_PROJECT_ID, 0);
        if (projectId <= 0) return;
        // Consumido: sin esto una rotación (o cualquier recreación) volvería a navegar al detalle
        // y el usuario no podría salir de él.
        intent.removeExtra(EXTRA_OPEN_PROJECT_ID);

        Bundle args = new Bundle();
        args.putInt("projectId", projectId);
        navController.navigate(R.id.action_global_project_detail, args);
    }

    // Sin esto la flecha "Up" de la ActionBar (destinos no top-level, p. ej. el detalle de proyecto)
    // se muestra pero no navega. Delega en el NavController para volver a la lista de proyectos.
    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onStart() {
        super.onStart();
        permissionGate.maybeRequest(this, notificationPermissionLauncher);
    }
}