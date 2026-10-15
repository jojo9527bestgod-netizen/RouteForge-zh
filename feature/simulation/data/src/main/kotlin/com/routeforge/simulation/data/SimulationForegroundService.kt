package com.routeforge.simulation.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.routeforge.simulation.domain.SimulationController
import com.routeforge.simulation.domain.model.SimulationMode
import com.routeforge.simulation.domain.model.SimulationStatus
import com.routeforge.simulation.domain.model.SpeedSetting
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

private const val NOTIFICATION_CHANNEL_ID = "simulation_active"
private const val NOTIFICATION_CHANNEL_ID_ALERTS = "simulation_alerts"
private const val NOTIFICATION_ID = 1001
private const val ALERT_NOTIFICATION_ID = 1002
private const val ACTION_PAUSE = "com.routeforge.simulation.action.PAUSE"
private const val ACTION_RESUME = "com.routeforge.simulation.action.RESUME"
private const val ACTION_STOP = "com.routeforge.simulation.action.STOP"

/** Carried on the notification's content [PendingIntent] (see [SimulationForegroundService.contentPendingIntent])
 *  so [com.routeforge.app.MainActivity] knows to switch to the Simulate tab even if it was already running on a
 *  different one — not referenced by class, since `feature:simulation:data` must never depend on the `app` module. */
const val EXTRA_OPEN_SIMULATE = "com.routeforge.simulation.extra.OPEN_SIMULATE"

private const val PAUSE_REQUEST_CODE = 1
private const val RESUME_REQUEST_CODE = 2
private const val STOP_REQUEST_CODE = 3
private const val CONTENT_REQUEST_CODE = 4

class SimulationForegroundService : Service() {
    private val simulationController: SimulationController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var floatingControls: SimulationFloatingControls
    private lateinit var powerKeeper: SimulationPowerKeeper

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RouteForge:Simulation")
            .apply { setReferenceCounted(false) }
        powerKeeper = SimulationPowerKeeper(
            serviceScope,
            acquire = { timeout ->
                try { wakeLock.acquire(timeout) } catch (error: SecurityException) {
                    Log.w("SimulationService", "Could not acquire simulation wake lock", error)
                }
            },
            release = {
                try { if (wakeLock.isHeld) wakeLock.release() } catch (error: RuntimeException) {
                    Log.w("SimulationService", "Simulation wake lock was already released", error)
                }
            },
        )
        floatingControls = SimulationFloatingControls(
            this, simulationController::pause, simulationController::resume, simulationController::stop, ::adjustSpeed,
        )
        val initialSession = simulationController.mockedSession.value
        startInForeground(
            if (initialSession != null) {
                buildNotificationFor(initialSession.mode, initialSession.status)
            } else {
                buildNotification(getString(R.string.simulation_notification_text_route_running), emptyList())
            },
        )

        simulationController.mockedSession
            .distinctUntilChangedBy { it?.let { session -> Triple(session.mode, session.status, session.speedSetting) } }
            .onEach { floatingControls.updateSession(it) }
            .launchIn(serviceScope)

        simulationController.mockedSession
            .distinctUntilChangedBy { it?.let { session -> session.mode to session.status } }
            .onEach { mockedSession ->
                powerKeeper.setActive(
                    mockedSession != null && mockedSession.status == SimulationStatus.RUNNING && mockedSession.mode != SimulationMode.STATIONARY,
                )
                if (mockedSession == null) {
                    stopSelf()
                } else {
                    updateNotification(mockedSession.mode, mockedSession.status)
                }
            }.launchIn(serviceScope)

        simulationController.authorizationLost
            .onEach { showAuthorizationLostNotification() }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE -> simulationController.pause()
            ACTION_RESUME -> simulationController.resume()
            ACTION_STOP -> simulationController.stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun adjustSpeed(deltaKmh: Int) {
        val session = simulationController.mockedSession.value ?: return
        if (session.mode == SimulationMode.STATIONARY || session.status == SimulationStatus.COMPLETED) return
        val currentKmh = (session.speedSetting.metersPerSecond * 3.6f).roundToInt()
        val updatedKmh = (currentKmh + deltaKmh).coerceIn(0, 150)
        if (updatedKmh != currentKmh) simulationController.setSpeed(SpeedSetting.Manual(updatedKmh / 3.6f))
    }

    override fun onDestroy() {
        powerKeeper.close()
        floatingControls.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingControls.onConfigurationChanged()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.simulation_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        // A separate, higher-importance channel from the silent ongoing-session one above — this
        // one is for the rare "mock location stopped itself" alert, which does need to actually
        // get the user's attention rather than sit quietly in the shade.
        val alertsChannel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID_ALERTS,
                getString(R.string.simulation_notification_channel_name_alerts),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, alertsChannel))
    }

    private fun actionPendingIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            requestCode,
            Intent(this, SimulationForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    /** Resolved via [android.content.pm.PackageManager.getLaunchIntentForPackage] rather than an explicit
     *  `Intent(this, MainActivity::class.java)` — this module must never depend on `app` (Constitution II). */
    private fun contentPendingIntent(): PendingIntent? {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_OPEN_SIMULATE, true)
            } ?: return null
        return PendingIntent.getActivity(this, CONTENT_REQUEST_CODE, launchIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pauseAction(): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                getString(R.string.simulation_notification_action_pause),
                actionPendingIntent(ACTION_PAUSE, PAUSE_REQUEST_CODE),
            ).build()

    private fun resumeAction(): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_play),
                getString(R.string.simulation_notification_action_resume),
                actionPendingIntent(ACTION_RESUME, RESUME_REQUEST_CODE),
            ).build()

    private fun stopAction(): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                getString(R.string.simulation_notification_action_stop),
                actionPendingIntent(ACTION_STOP, STOP_REQUEST_CODE),
            ).build()

    private fun buildNotification(
        contentText: String,
        actions: List<Notification.Action>,
    ): Notification =
        Notification
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.simulation_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                contentPendingIntent()?.let { setContentIntent(it) }
                actions.forEach { addAction(it) }
            }.build()

    /** Maps [mode]/[status] to this notification's text and action set in one place (FR-001/003/004/005/008)
     *  so the very first notification built in [onCreate] and every later [updateNotification] call agree —
     *  no one-frame mismatch where the first notification shows the wrong text or no actions at all. */
    private fun buildNotificationFor(
        mode: SimulationMode,
        status: SimulationStatus,
    ): Notification {
        val contentText =
            when {
                mode == SimulationMode.STATIONARY -> getString(R.string.simulation_notification_text_stationary)
                status == SimulationStatus.PAUSED -> getString(R.string.simulation_notification_text_route_paused)
                status == SimulationStatus.COMPLETED -> getString(R.string.simulation_notification_text_route_complete)
                else -> getString(R.string.simulation_notification_text_route_running)
            }
        val actions =
            when {
                mode != SimulationMode.ROUTE -> listOf(stopAction())
                status == SimulationStatus.RUNNING -> listOf(pauseAction(), stopAction())
                status == SimulationStatus.PAUSED -> listOf(resumeAction(), stopAction())
                else -> listOf(stopAction()) // COMPLETED: no Pause/Resume for a finished route (FR-008/FR-010)
            }
        return buildNotification(contentText, actions)
    }

    private fun updateNotification(
        mode: SimulationMode,
        status: SimulationStatus,
    ) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotificationFor(mode, status))
    }

    /** The "honest failure" notification: fired once [simulationController] reports it lost mock-
     *  location authorization mid-session, so the user learns why mocking stopped instead of the
     *  app just silently drifting back to the real GPS. Dismissible and on its own channel, unlike
     *  the ongoing session notification above. */
    private fun showAuthorizationLostNotification() {
        val notification =
            Notification
                .Builder(this, NOTIFICATION_CHANNEL_ID_ALERTS)
                .setContentTitle(getString(R.string.simulation_notification_authorization_lost_title))
                .setContentText(getString(R.string.simulation_notification_authorization_lost_text))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setAutoCancel(true)
                .apply { contentPendingIntent()?.let { setContentIntent(it) } }
                .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification)
    }
}
