package com.example.bbettercalendar;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.bbettercalendar.configuration.InitialConfiguration;
import com.example.bbettercalendar.notifications.PermissionGate;
import com.example.bbettercalendar.stats.StatsDAO;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.bbettercalendar.databinding.ActivityMainBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    /**
     * u12: destinos de primer nivel = los únicos con barra inferior. Calendar Week cuenta porque es
     * la pestaña Calendar en modo semana (se alterna con Month desde la propia pantalla). Cualquier
     * otro destino (Focus, Cycle complete, All progress, Screen time, App detail, Project detail…)
     * es secundario: sin barra inferior, con barra superior y vuelta atrás.
     */
    private static final Set<Integer> TOP_LEVEL_DESTINATIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    R.id.navigation_home,
                    R.id.navigation_progress,
                    R.id.navigation_calendar_month,
                    R.id.navigation_calendar_week,
                    R.id.navigation_projects)));

    /**
     * Solo debug (u12; u25 puede reutilizarlo): abre un destino secundario al arrancar, para probar
     * la navegación por adb antes de que existan sus entradas reales. SplashActivity lo reenvía.
     * Valores: focus | cycle_complete | all_progress | screen_time | app_detail. Ver
     * {@link #handleDebugNav(Intent)}.
     */
    public static final String EXTRA_DEBUG_NAV = "bb_debug_nav";
    /** Solo debug: pestaña desde la que se abre {@link #EXTRA_DEBUG_NAV} (home|progress|calendar|projects). */
    public static final String EXTRA_DEBUG_NAV_FROM = "bb_debug_from";
    /** Solo debug: packageName para {@code app_detail} (por defecto, el de esta app). */
    public static final String EXTRA_DEBUG_NAV_PACKAGE = "bb_debug_pkg";

    private final String TAG = "MainActivityTAG";
    private ActivityMainBinding binding;
    private StatsDAO statsDao;
    private ExecutorService executorService;
    private NavController navController;

    @Inject PermissionGate permissionGate;

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Sin ActionBar (spec frontend-structure-cleanup): la bottom-nav ya dice en qué pantalla estás.
        // El tema (Theme.BBetter.NoActionBar) viene del manifest; sin setTheme en ejecución (u5).
        super.onCreate(savedInstanceState);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { /* result captured by PermissionGate via Configuration state */ });

        // Sesión de pomodoro: FocusSessionViewModel (ámbito de esta Activity, compartido por Home y
        // Focus). Desde u14 su periodo de gracia por segundo plano lo alimenta un observer a nivel
        // de PROCESO (FocusBackgroundObserver + ProcessLifecycleOwner) que registra el propio
        // ViewModel: abrir otra Activity de la app ya no cuenta como salir de ella.

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Iniciar la configuración si no se ha hecho antes
        if (InitialConfiguration.getInstance().getInitializationStatus().getValue() == null) {
            //InitialConfiguration.getInstance().initialize(this);
        }

        // Con FragmentContainerView, obtener el NavController vía el NavHostFragment (no con
        // Navigation.findNavController(activity, id), que puede fallar en onCreate porque la vista
        // del fragment aún no tiene asignado el NavController en su tag).
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);
        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(binding.navView, navController);

        // u12: chrome por destino (sustituye al setAppChromeVisible de HomeFragment). El listener
        // se dispara también con el destino actual al registrarlo, así que cubre la recreación.
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int visibility = TOP_LEVEL_DESTINATIONS.contains(destination.getId())
                    ? View.VISIBLE : View.GONE;
            binding.navView.setVisibility(visibility);
            binding.navDivider.setVisibility(visibility);
        });

        openProjectFromIntent(getIntent());
        handleDebugNav(getIntent());
    }

    // El Intent de la notificación lleva CLEAR_TOP y el launchMode por defecto es standard, así que
    // puede llegar por cualquiera de los dos caminos: recreando la Activity (onCreate) o
    // reentregado a la instancia viva (onNewIntent). Hay que atender los dos.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openProjectFromIntent(intent);
        handleDebugNav(intent);
    }

    /**
     * Debug-only entry point to the secondary destinations (u12). Example:
     * {@code adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.configuration.SplashActivity
     * --es bb_debug_nav app_detail --es bb_debug_from progress --es bb_debug_pkg com.android.chrome}.
     * No-op in release (BuildConfig.DEBUG) and consumed so a recreation doesn't navigate again.
     */
    private void handleDebugNav(Intent intent) {
        if (!BuildConfig.DEBUG || intent == null || navController == null) return;
        String target = intent.getStringExtra(EXTRA_DEBUG_NAV);
        if (target == null) return;
        String from = intent.getStringExtra(EXTRA_DEBUG_NAV_FROM);
        String pkg = intent.getStringExtra(EXTRA_DEBUG_NAV_PACKAGE);
        intent.removeExtra(EXTRA_DEBUG_NAV);
        intent.removeExtra(EXTRA_DEBUG_NAV_FROM);
        intent.removeExtra(EXTRA_DEBUG_NAV_PACKAGE);

        if (from != null) {
            int tab = debugTab(from);
            // Como un toque en la pestaña: así la vuelta atrás se prueba desde otro origen.
            if (tab != 0) binding.navView.setSelectedItemId(tab);
        }
        Bundle args = null;
        int destination;
        switch (target) {
            case "focus": destination = R.id.navigation_focus; break;
            case "cycle_complete": destination = R.id.navigation_cycle_complete; break;
            case "all_progress": destination = R.id.navigation_all_progress; break;
            case "screen_time": destination = R.id.navigation_screen_time; break;
            case "app_detail":
                destination = R.id.navigation_app_detail;
                args = new Bundle();
                args.putString("packageName", pkg != null ? pkg : getPackageName());
                break;
            default: return;
        }
        navController.navigate(destination, args);
    }

    private static int debugTab(String name) {
        switch (name) {
            case "home": return R.id.navigation_home;
            case "progress": return R.id.navigation_progress;
            case "calendar": return R.id.navigation_calendar_month;
            case "projects": return R.id.navigation_projects;
            default: return 0;
        }
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

    @Override
    protected void onStart() {
        super.onStart();
        permissionGate.maybeRequest(this, notificationPermissionLauncher);
    }
}