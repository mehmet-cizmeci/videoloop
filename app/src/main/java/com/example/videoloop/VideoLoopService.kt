package com.example.videoloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager

class VideoLoopService : Service() {

    private var sleepCountDownTimer: CountDownTimer? = null
    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    cancelSleepCountdown()
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm != null) {
                        @Suppress("DEPRECATION")
                        val wakeLock = pm.newWakeLock(
                            PowerManager.FULL_WAKE_LOCK or
                                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                    PowerManager.ON_AFTER_RELEASE,
                            "videoloop:ServiceWakeLock"
                        )
                        wakeLock.acquire(5000)
                    }

                    val i = Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra("power_connected", true)
                    }
                    context.startActivity(i)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    start30SecSleepCountdown()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)
    }

    /**
     * Android 8+ startForegroundService ile başlatılan servisin birkaç saniye içinde ön plana
     * geçmesini şart koşar; aksi halde uygulamayı kapatır. Bunun için kalıcı bir bildirim gösterilir.
     */
    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Video Döngü", NotificationManager.IMPORTANCE_MIN)
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Video Döngü çalışıyor")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Ön plana geçilemezse uygulama çökmesin diye servis kapatılır
            e.printStackTrace()
            stopSelf()
        }
    }
    private fun start30SecSleepCountdown() {
        cancelSleepCountdown()
        sleepCountDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Arka plan servis çalışıyor
            }

            override fun onFinish() {
                if (dpm.isAdminActive(adminComponent)) {
                    try {
                        dpm.lockNow()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    private fun cancelSleepCountdown() {
        sleepCountDownTimer?.cancel()
        sleepCountDownTimer = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelSleepCountdown()
        try {
            unregisterReceiver(powerReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "videoloop_service"
        private const val NOTIFICATION_ID = 1
    }
}
