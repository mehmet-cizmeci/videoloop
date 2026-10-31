package com.example.videoloop.legacy

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("settings_legacy", Context.MODE_PRIVATE) }
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }
    // Uygulama bağlamından alınır: Activity arka plana geçse de (Son Uygulamalar, ana ekran)
    // eklenen üst pencereler gizlenmez
    private val windowManagerService by lazy { applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    private val handler = Handler(Looper.getMainLooper())
    private val playlist = mutableListOf<Uri>()
    private val playlistNames = mutableListOf<String>()
    private var index = 0
    private var controlsVisible = false
    private var isChildLocked = true
    private var volAccum = 0f

    private var tapCount = 0
    private var lastTapTime = 0L

    // Kullanıcının istediği oynatma durumu: duraklatıldıysa video kilitleme, ekranın açılması
    // gibi olaylarda kendiliğinden yeniden başlamaz
    private var wantPlaying = true

    // Güç tuşuyla ekran kapanınca video duraklatılır, açılınca saklanan konumdan devam eder
    private var screenOff = false
    private var savedPosition = -1

    // Art arda açılamayan video sayısı (hepsi hata verirse sonsuz döngü önlenir)
    private var consecutiveErrors = 0

    // Kilit açıkken 30 sn dokunulmazsa çocuk kilidi kendiliğinden devreye girer
    private val autoLockRunnable = Runnable {
        if (isChildLocked || playlist.isEmpty()) return@Runnable
        // Klasör seçici veya bir pencere açıksa kilitleme; süre dolunca yeniden dene
        if (!hasWindowFocus()) {
            scheduleAutoLock()
            return@Runnable
        }
        toggleChildLock()
        showInfo(AUTO_LOCKED_MSG)
    }

    // Donanımsal/Sistem Düzeyinde Dokunma Bariyerleri (Status Bar & Nav Bar Bloklayıcılar)
    private var topBlockerView: View? = null
    private var bottomBlockerView: View? = null

    // Kilitliyken tüm ekranı kaplayıp dokunmaları yutan katman (sadece 5 ardışık dokunuşu sayar)
    private lateinit var lockShield: View

    // Kilitliyken videoyu sistem çubukları dahil her şeyin üstünde tam ekran gösteren pencere
    private var lockOverlay: FrameLayout? = null

    // 30 Saniyelik Uyku Geri Sayım Zamanlayıcısı
    private var sleepCountDownTimer: CountDownTimer? = null

    private lateinit var root: FrameLayout
    private lateinit var videoView: VideoView
    private lateinit var statusText: TextView
    private lateinit var selectButton: Button
    private lateinit var infoText: TextView

    private lateinit var topBar: LinearLayout
    private lateinit var centerControls: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private lateinit var txtTitle: TextView
    private lateinit var lockBadge: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnMute: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var txtTime: TextView
    private lateinit var gesture: GestureDetector

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }
    private val hideInfoRunnable = Runnable { infoText.visibility = View.GONE }

    private val barSuppressionRunnable = object : Runnable {
        override fun run() {
            if (isChildLocked) {
                hideSystemBars()
                lockOverlay?.systemUiVisibility = IMMERSIVE_FLAGS
                collapseStatusBar()
                // Odak başka bir yerdeyse (bildirim paneli, Son Uygulamalar) onları kapat
                if (!hasWindowFocus()) closeSystemDialogs()
                handler.postDelayed(this, 300)
            }
        }
    }

    /**
     * Android 4.4'te bildirim panelini ve Son Uygulamalar ekranını kapatır.
     */
    private fun closeSystemDialogs() {
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (_: Exception) {
        }
        collapseStatusBar()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (videoView.isPlaying && videoView.duration > 0) {
                val cur = videoView.currentPosition
                val dur = videoView.duration
                seekBar.progress = ((cur.toFloat() / dur) * 1000).toInt()
                txtTime.text = String.format(Locale.getDefault(), "%s / %s", formatTime(cur), formatTime(dur))
            }
            if (controlsVisible) {
                handler.postDelayed(this, 500)
            }
        }
    }

    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    cancelSleepCountdown()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    showInfo("🔌 Şarj Takıldı: Uyku İptal Edildi & Oynatılıyor")
                    if (!videoView.isPlaying) play()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    start30SecSleepCountdown()
                }
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    private fun start30SecSleepCountdown() {
        cancelSleepCountdown()
        sleepCountDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000).toInt() + 1
                showInfo(String.format(Locale.getDefault(), "🔋 Güç Kesildi!\n%d saniye içinde uykuya geçilecek...", secLeft))
            }

            override fun onFinish() {
                showInfo("🔋 30 Sn Doldu: Derin Uykuya Geçiliyor")
                pauseVideo()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    private fun requestDeviceAdmin() {
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Şarj çıkarıldığında ekranın aniden kapanması ve cihazın uyku moduna geçmesi için Cihaz Yöneticisi yetkisi gereklidir."
                )
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_MUSIC
        requestMediaAudioFocus()

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        videoView = VideoView(this)
        root.addView(
            videoView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(40, 0, 40, 100)
        }
        root.addView(
            statusText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        infoText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(210, 15, 23, 42))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1.5f), Color.argb(120, 255, 255, 255))
            }
            setPadding(dp(24), dp(16), dp(24), dp(16))
            visibility = View.GONE
        }
        root.addView(
            infoText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        topBar = buildTopBar()
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
        topBar.visibility = View.GONE

        centerControls = buildCenterControls()
        root.addView(
            centerControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        centerControls.visibility = View.GONE

        bottomBar = buildBottomBar()
        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        bottomBar.visibility = View.GONE

        selectButton = Button(this).apply {
            text = "📁 Video Klasörü Seç"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.argb(255, 30, 136, 229))
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(24), dp(12), dp(24), dp(12))
            setOnClickListener { showFolderChooserDialog() }
        }
        root.addView(
            selectButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        // En üstte duran kilit katmanı: kilitliyken hiçbir dokunma alttaki görünümlere ulaşmaz
        lockShield = View(this).apply {
            isClickable = true
            setOnTouchListener { _, event ->
                hideSystemBars()
                collapseStatusBar()
                if (registerUnlockTap(event)) {
                    toggleChildLock()
                } else if (event.actionMasked == MotionEvent.ACTION_UP && tapCount == 1) {
                    showInfo(LOCKED_MSG)
                }
                true
            }
        }
        root.addView(
            lockShield,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        gesture = GestureDetector(this, GestureListener())

        // Kilit açıkken: normal hareketler + art arda 5 dokunuşla tekrar kilitleme
        root.setOnTouchListener { view, event ->
            if (registerUnlockTap(event)) {
                toggleChildLock()
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            gesture.onTouchEvent(event)
            true
        }

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            consecutiveErrors = 0
            if (wantPlaying && !screenOff) videoView.start()
            updatePlayButton()
        }
        videoView.setOnCompletionListener { next() }
        videoView.setOnErrorListener { _, _, _ ->
            // Açılamayan videoyu atla. Sıradakine kısa gecikmeyle geçilir ki art arda hata veren
            // videolar sonsuz bir zincire (ve çökmeye) yol açmasın.
            consecutiveErrors++
            if (consecutiveErrors < playlist.size) {
                handler.postDelayed({ next() }, 300)
            } else {
                // Hiçbir video açılamıyor (örn. hafıza kartı henüz hazır değil): biraz sonra yeniden dene
                showInfo("⚠️ Videolar oynatılamıyor\n10 saniye sonra yeniden denenecek")
                handler.postDelayed({
                    consecutiveErrors = 0
                    next()
                }, 10_000)
            }
            true
        }

        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(powerStateReceiver, powerFilter)

        checkOptimizationPreferenceAndStart()

        if (!dpm.isAdminActive(adminComponent)) {
            requestDeviceAdmin()
        }
    }

    override fun onResume() {
        super.onResume()
        applyLockState()
    }

    /**
     * Art arda dokunuşları sayar; aralarında TAP_INTERVAL_MS'den uzun süre olursa sayaç sıfırlanır.
     * Birden fazla parmakla dokunma sayılmaz. UNLOCK_TAP_COUNT'a ulaşınca true döner.
     */
    private fun registerUnlockTap(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            tapCount = 0
            return false
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false

        val now = System.currentTimeMillis()
        tapCount = if (now - lastTapTime < TAP_INTERVAL_MS) tapCount + 1 else 1
        lastTapTime = now

        if (tapCount >= UNLOCK_TAP_COUNT) {
            tapCount = 0
            return true
        }
        return false
    }

    /**
     * Kilit durumunu ekrana uygular: dokunma katmanı, üst/alt çubuk engelleyicileri ve
     * (Android 5+) ekran sabitleme. Henüz video seçilmediyse klasör seçilebilsin diye devreye girmez.
     */
    private fun applyLockState() {
        val locked = isChildLocked && selectButton.visibility != View.VISIBLE
        lockShield.visibility = if (locked) View.VISIBLE else View.GONE
        handler.removeCallbacks(barSuppressionRunnable)
        if (locked) {
            hideSystemBars()
            collapseStatusBar()
            // Tam ekran üst pencere açılamazsa (izin yoksa) eski üst/alt çubuk engelleyicilerine dön
            if (!showLockOverlay()) enableSystemOverlayBlockers()
            handler.post(barSuppressionRunnable)
            startScreenPinning()
        } else {
            hideLockOverlay()
            disableSystemOverlayBlockers()
            stopScreenPinning()
        }
    }

    /**
     * Videoyu, sistem çubuklarının, Son Uygulamalar'ın ve ana ekranın üstünde duran tam ekran
     * bir pencereye taşır. Bu pencerede sadece art arda 5 dokunuş çalışır.
     */
    private fun showLockOverlay(): Boolean {
        if (lockOverlay != null) return true

        // Tuşlar, odak hangi alt görünümde olursa olsun (örn. video değişince VideoView odağı alır)
        // önce burada yakalanır; yoksa medya tuşları başka uygulamalara (Google Müzik vb.) gider
        val overlay = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                return when (event.keyCode) {
                    // Ses tuşları sisteme bırakılır
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> super.dispatchKeyEvent(event)
                    // Kumanda medya tuşları kilitliyken de çalışır; Geri/Menü vb. yutulur
                    in MEDIA_KEYS -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            handleMediaKey(event.keyCode)
                        }
                        true
                    }
                    else -> true
                }
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            // Odak bu pencerede olunca sistem çubuklarının gizlenmesi buradaki bayraklara göre yapılır
            isFocusableInTouchMode = true
            systemUiVisibility = IMMERSIVE_FLAGS
            setOnTouchListener { _, event ->
                if (registerUnlockTap(event)) {
                    toggleChildLock()
                } else if (event.actionMasked == MotionEvent.ACTION_UP && tapCount == 1) {
                    showInfo(LOCKED_MSG)
                }
                true
            }
        }

        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // Tüm uygulamaların (ana ekran ve Son Uygulamalar dahil) üstünde durur ve dokunma alır.
            // Not: Android 4.4'te alt 3 buton çubuğu bunun da üstündedir; o butonlar video
            // penceresini kapatamaz, sadece altta kalan ekranı değiştirir.
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
        }
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }

        try {
            windowManagerService.addView(overlay, params)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        lockOverlay = overlay
        moveVideoTo(overlay, videoIndex = 0, infoIndex = 1)
        overlay.requestFocus()
        return true
    }

    private fun hideLockOverlay() {
        val overlay = lockOverlay ?: return
        // root içindeki sıra: video(0), statusText(1), infoText(2), ...
        moveVideoTo(root, videoIndex = 0, infoIndex = 2)
        try {
            windowManagerService.removeView(overlay)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        lockOverlay = null
    }

    /**
     * Videoyu ve bilgi yazısını başka bir pencereye taşır; video kaldığı yerden devam eder.
     */
    private fun moveVideoTo(target: ViewGroup, videoIndex: Int, infoIndex: Int) {
        val position = videoView.currentPosition
        (videoView.parent as? ViewGroup)?.removeView(videoView)
        (infoText.parent as? ViewGroup)?.removeView(infoText)
        target.addView(
            videoView,
            videoIndex,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        target.addView(
            infoText,
            infoIndex,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        // Yüzey yeniden oluşunca VideoView videoyu tekrar açar; bu konumdan devam eder.
        // Duraklatılmışsa duraklatılmış kalır.
        if (playlist.isNotEmpty()) {
            videoView.seekTo(position)
            if (wantPlaying && !screenOff) videoView.start()
        }
    }

    private fun isScreenPinned(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    private fun startScreenPinning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || isScreenPinned()) return
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopScreenPinning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !isScreenPinned()) return
        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Android 4.4'te üst ve alt çubukların üzerine görünmez SYSTEM_ALERT_WINDOW katmanı koyarak
     * dokunmaları ve kaydırmaları işletim sistemine hiç ulaşmadan yutar.
     */
    private fun enableSystemOverlayBlockers() {
        if (topBlockerView != null || bottomBlockerView != null) return

        try {
            @Suppress("DEPRECATION")
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            }

            // Üst Bar (Status Bar / Bildirim paneli) Engelleyici
            topBlockerView = View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setOnTouchListener { _, _ ->
                    collapseStatusBar()
                    hideSystemBars()
                    true // Dokunmayı tamamen yut
                }
            }
            val topParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dp(45), // Standart Android 4.4 status bar yüksekliği
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }
            windowManagerService.addView(topBlockerView, topParams)

            // Alt Bar (Navigation Bar: Geri, Ana Ekran, Son Uygulamalar) Engelleyici
            bottomBlockerView = View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setOnTouchListener { _, _ ->
                    hideSystemBars()
                    true // Dokunmayı tamamen yut
                }
            }
            val bottomParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dp(60), // Standart Android 4.4 navigation bar yüksekliği
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            windowManagerService.addView(bottomBlockerView, bottomParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disableSystemOverlayBlockers() {
        try {
            if (topBlockerView != null) {
                windowManagerService.removeView(topBlockerView)
                topBlockerView = null
            }
            if (bottomBlockerView != null) {
                windowManagerService.removeView(bottomBlockerView)
                bottomBlockerView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBackPressed() {
        if (isChildLocked) {
            hideSystemBars()
            collapseStatusBar()
            showInfo("🔒 Çocuk Kilidi AKTİF\n(Geri tuşu kilitli)")
            return
        }
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isChildLocked) {
            hideSystemBars()
            collapseStatusBar()
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_MENU
            ) {
                showInfo("🔒 Çocuk Kilidi AKTİF")
                return true
            }
        }
        // Kumanda / kulaklık medya tuşları kilitliyken de çalışır
        if (keyCode in MEDIA_KEYS) {
            if ((event?.repeatCount ?: 0) == 0) handleMediaKey(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestMediaAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(focusRequest)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            if (videoView.isPlaying) videoView.pause()
                            updatePlayButton()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (!videoView.isPlaying && wantPlaying && !screenOff && playlist.isNotEmpty()) {
                                videoView.start()
                                updatePlayButton()
                            }
                        }
                    }
                }
                .build()
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                { _ -> },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    // Ana Ekran / Son Uygulamalar tuşuna basıldığında çağrılır
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isChildLocked) closeSystemDialogs()
    }

    override fun onPause() {
        super.onPause()
        // Video bu ekrandaysa, ekran kapanınca oynatıcı kapanmadan önce konumu sakla
        if (lockOverlay == null && videoView.duration > 0) savedPosition = videoView.currentPosition
        if (isChildLocked) closeSystemDialogs()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("power_connected", false) == true) {
            cancelSleepCountdown()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            showInfo("🔌 Şarj Takıldı: Video Oynatılıyor")
            if (!videoView.isPlaying) play()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (isChildLocked || hasFocus) {
            hideSystemBars()
            collapseStatusBar()
        }
    }

    /**
     * Android 4.4'te üst bildirim paneli aşağı çekildiğinde onu zorla geri kapatır.
     */
    private fun collapseStatusBar() {
        // Android 12+ bu gizli yönteme erişimi engeller
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        try {
            @Suppress("WrongConstant")
            val statusBarService = getSystemService("statusbar")
            val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
            val collapse: Method = statusBarManager.getMethod("collapsePanels")
            collapse.invoke(statusBarService)
        } catch (_: Exception) {
            try {
                @Suppress("WrongConstant")
                val statusBarService = getSystemService("statusbar")
                val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
                val collapse: Method = statusBarManager.getMethod("collapse")
                collapse.invoke(statusBarService)
            } catch (_: Exception) {
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    // ---------- Dahili Yerel Klasör Seçici (Android 4.4+ Uyumlu) ----------

    private fun showFolderChooserDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 101)
                return
            }
        }

        var startDir = Environment.getExternalStorageDirectory()
        val savedPath = prefs.getString("folder_path", null)
        if (savedPath != null) {
            val savedDir = File(savedPath)
            if (savedDir.exists() && savedDir.isDirectory) {
                startDir = savedDir
            }
        }

        if (!startDir.exists()) {
            startDir = File("/sdcard")
        }

        fun browseDir(dir: File) {
            val filesInDir = dir.listFiles() ?: emptyArray()
            val subFolders = filesInDir.filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
            val videoCount = filesInDir.count { it.isFile && isVideoFile(it) }

            val items = mutableListOf<String>()
            val itemDirs = mutableListOf<File?>()

            if (dir.parentFile != null && dir.absolutePath != "/storage" && dir.absolutePath != "/") {
                items.add("⬆️  .. (Üst Klasöre Çık)")
                itemDirs.add(dir.parentFile)
            }

            subFolders.forEach { folder ->
                val vCount = folder.listFiles()?.count { it.isFile && isVideoFile(it) } ?: 0
                val info = if (vCount > 0) " 🎬 ($vCount Video)" else ""
                items.add("📁 ${folder.name}$info")
                itemDirs.add(folder)
            }

            val dialogTitle = "📂 ${dir.name} ($videoCount Video Burada)\n${dir.absolutePath}"

            AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setItems(items.toTypedArray()) { _, which ->
                    val target = itemDirs[which]
                    if (target != null && target.exists()) {
                        browseDir(target)
                    }
                }
                .setPositiveButton("✅ Bu Klasörü Seç ($videoCount Video)") { _, _ ->
                    prefs.edit().putString("folder_path", dir.absolutePath).apply()
                    showInfo("📁 Klasör Seçildi:\n${dir.name}")
                    startPlayback()
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        browseDir(startDir)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showFolderChooserDialog()
        }
    }

    // ---------- Sistem Optimizasyonu ve Performans Ayarı ----------

    private fun checkOptimizationPreferenceAndStart() {
        if (!prefs.contains("auto_optimize")) {
            AlertDialog.Builder(this)
                .setTitle("⚡ Sistem Optimizasyonu")
                .setMessage("Videoların takılmadan ve kesintisiz oynatılması için, arka plandaki gereksiz uygulamalar temizlenip sistem performansı optimize edilsin mi?")
                .setCancelable(false)
                .setPositiveButton("Evet (Önerilen)") { _, _ ->
                    prefs.edit().putBoolean("auto_optimize", true).apply()
                    performSystemOptimization()
                    proceedAfterOptimizationCheck()
                }
                .setNegativeButton("Hayır") { _, _ ->
                    prefs.edit().putBoolean("auto_optimize", false).apply()
                    proceedAfterOptimizationCheck()
                }
                .show()
        } else {
            if (prefs.getBoolean("auto_optimize", false)) {
                performSystemOptimization()
            }
            proceedAfterOptimizationCheck()
        }
    }

    private fun performSystemOptimization() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = am.runningAppProcesses ?: emptyList()
            val myPkg = packageName
            for (proc in runningApps) {
                if (proc.pkgList != null) {
                    for (pkg in proc.pkgList) {
                        if (pkg != myPkg && !pkg.startsWith("com.android.") && !pkg.startsWith("system")) {
                            am.killBackgroundProcesses(pkg)
                        }
                    }
                }
            }
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            System.gc()
            val availMemMb = memInfo.availMem / (1024 * 1024)
            val isLowRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.isLowRamDevice
            } else {
                memInfo.lowMemory || availMemMb < 400
            }
            val optMsg = if (isLowRam) {
                String.format(
                    Locale.getDefault(),
                    "⚡ Sistem Optimize Edildi (%dMB RAM Serbest)\nℹ️ Cihaz Donanımı: Max 720p HD Önerilir",
                    availMemMb
                )
            } else {
                String.format(
                    Locale.getDefault(),
                    "⚡ Sistem Optimize Edildi (%dMB RAM Serbest)",
                    availMemMb
                )
            }
            showInfo(optMsg)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun proceedAfterOptimizationCheck() {
        if (prefs.getString("folder_path", null) != null) {
            startPlayback()
        } else {
            statusText.text = "Oynatılacak videoların bulunduğu klasörü seçin"
        }
    }

    // ---------- Arayüz Bileşenleri ----------

    private fun createModernButton(
        label: String,
        sizeDp: Int = 56,
        textSizeSp: Float = 22f,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = textSizeSp
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(170, 15, 23, 42))
                setStroke(dp(1.5f), Color.argb(100, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
                setMargins(dp(10), dp(10), dp(10), dp(10))
            }
            setOnClickListener {
                if (!isChildLocked) {
                    onClick()
                    scheduleHideControls()
                }
            }
        }
    }

    private fun buildTopBar(): LinearLayout {
        txtTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        lockBadge = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isClickable = true
            setOnClickListener { toggleChildLock() }
        }
        updateLockBadge()

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT)
            }
            addView(txtTitle)
            addView(lockBadge)
        }
    }

    private fun buildCenterControls(): LinearLayout {
        val btnPrev = createModernButton("⏮", sizeDp = 90, textSizeSp = 36f) { prev() }
        // 300dp x 300dp, 90sp metin boyutu
        btnPlay = createModernButton("⏸", sizeDp = 300, textSizeSp = 90f) { togglePlay() }
        val btnNext = createModernButton("⏭", sizeDp = 90, textSizeSp = 36f) { next() }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(btnPrev)
            addView(btnPlay)
            addView(btnNext)
        }
    }

    private fun buildBottomBar(): LinearLayout {
        seekBar = SeekBar(this).apply {
            max = 1000
            progressDrawable.setColorFilter(Color.argb(255, 0, 242, 254), android.graphics.PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && videoView.duration > 0) {
                        val newPos = (progress / 1000f * videoView.duration).toInt()
                        videoView.seekTo(newPos)
                        txtTime.text = String.format(Locale.getDefault(), "%s / %s", formatTime(newPos), formatTime(videoView.duration))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    handler.removeCallbacks(hideControlsRunnable)
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    scheduleHideControls()
                }
            })
        }

        txtTime = TextView(this).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(12), 0, 0, 0)
        }

        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), dp(12))
            addView(seekBar)
            addView(txtTime)
        }

        btnMute = createModernButton("🔊", sizeDp = 52, textSizeSp = 22f) { toggleMute() }
        updateMuteButton()

        val btnVolDown = createModernButton("🔉", sizeDp = 52, textSizeSp = 22f) { adjustVolume(AudioManager.ADJUST_LOWER) }
        val btnVolUp = createModernButton("🔊", sizeDp = 52, textSizeSp = 22f) { adjustVolume(AudioManager.ADJUST_RAISE) }
        val btnBrightDown = createModernButton("☀−", sizeDp = 52, textSizeSp = 20f) { adjustBrightness(-0.1f) }
        val btnBrightUp = createModernButton("☀+", sizeDp = 52, textSizeSp = 20f) { adjustBrightness(0.1f) }
        val btnOpt = createModernButton("⚡", sizeDp = 52, textSizeSp = 20f) { performSystemOptimization() }
        val btnFolder = createModernButton("📁", sizeDp = 52, textSizeSp = 22f) { showFolderChooserDialog() }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(btnBrightDown)
            addView(btnBrightUp)
            addView(btnVolDown)
            addView(btnMute)
            addView(btnVolUp)
            addView(btnOpt)
            addView(btnFolder)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.BOTTOM_TOP
                colors = intArrayOf(Color.argb(220, 0, 0, 0), Color.TRANSPARENT)
            }
            addView(progressRow)
            addView(buttonsRow)
        }
    }

    private fun toggleChildLock() {
        isChildLocked = !isChildLocked
        tapCount = 0
        updateLockBadge()
        if (isChildLocked) {
            handler.removeCallbacks(autoLockRunnable)
            setControlsVisible(false)
            applyLockState()
            showInfo(LOCKED_MSG)
        } else {
            scheduleAutoLock()
            applyLockState()
            // Kilitliyken video penceresinin altında Ana Ekran / Son Uygulamalar açılmış olabilir
            try {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            showInfo("🔓 Çocuk Kilidi DEVRE DIŞI")
            setControlsVisible(true)
        }
    }

    private fun updateLockBadge() {
        if (::lockBadge.isInitialized) {
            if (isChildLocked) {
                lockBadge.text = "🔒 Kilitli (5 Tık)"
                lockBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.argb(220, 220, 38, 38))
                }
            } else {
                lockBadge.text = "🔓 Kilitle"
                lockBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.argb(160, 30, 41, 59))
                }
            }
        }
    }

    private fun setControlsVisible(visible: Boolean) {
        if (isChildLocked && visible) {
            showInfo(LOCKED_MSG)
            return
        }

        controlsVisible = visible
        val v = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = v
        centerControls.visibility = v
        bottomBar.visibility = v

        handler.removeCallbacks(hideControlsRunnable)

        if (visible) {
            showSystemBars()
            scheduleHideControls()
            handler.post(updateSeekBarRunnable)
        } else {
            hideSystemBars()
            handler.removeCallbacks(updateSeekBarRunnable)
        }
    }

    private fun scheduleHideControls() {
        if (!controlsVisible || isChildLocked) return
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3500)
    }

    private fun showInfo(text: String) {
        infoText.text = text
        infoText.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfoRunnable)
        handler.postDelayed(hideInfoRunnable, 2200)
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            volAccum = 0f
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (playlist.isEmpty()) return true
            if (isChildLocked) return true
            setControlsVisible(!controlsVisible)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (playlist.isEmpty() || isChildLocked) return true
            if (e.x < root.width / 2f) prev() else next()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null || isChildLocked) return false
            if (abs(distanceY) < abs(distanceX)) return false
            if (e1.x < root.width / 2f) changeVolumeByDrag(distanceY) else changeBrightnessByDrag(distanceY)
            return true
        }
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        return if (b < 0) 0.5f else b
    }

    private fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = lp
        showInfo(String.format(Locale.getDefault(), "☀ Parlaklık: %d%%", (lp.screenBrightness * 100).toInt()))
    }

    private fun adjustBrightness(delta: Float) {
        setBrightness(currentBrightness() + delta)
    }

    private fun changeBrightnessByDrag(distanceY: Float) {
        setBrightness(currentBrightness() + distanceY / root.height * 1.5f)
    }

    private fun adjustVolume(direction: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        showInfo(String.format(Locale.getDefault(), "🔊 Ses: %d / %d", cur, max))
        updateMuteButton()
    }

    private fun changeVolumeByDrag(distanceY: Float) {
        volAccum += distanceY
        val step = root.height / 8f
        if (abs(volAccum) >= step) {
            adjustVolume(if (volAccum > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
            volAccum = 0f
        }
    }

    private fun toggleMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        } else {
            @Suppress("DEPRECATION")
            val isMutedNow = audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
            audio.setStreamMute(AudioManager.STREAM_MUSIC, !isMutedNow)
        }
        updateMuteButton()
        showInfo(if (isMuted()) "🔇 Sessiz" else "🔊 Ses Açık")
    }

    private fun isMuted(): Boolean =
        audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

    private fun updateMuteButton() {
        if (::btnMute.isInitialized) {
            btnMute.text = if (isMuted()) "🔇" else "🔊"
        }
    }

    private fun play() {
        wantPlaying = true
        if (playlist.isNotEmpty()) videoView.start()
        updatePlayButton()
    }

    private fun pauseVideo() {
        wantPlaying = false
        // Video henüz yükleniyorsa da duraklatılmış kalsın (yüklenince kendiliğinden başlamasın)
        videoView.pause()
        updatePlayButton()
    }

    private fun togglePlay() {
        if (videoView.isPlaying) pauseVideo() else play()
    }

    private fun handleMediaKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> play()
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> pauseVideo()
            KeyEvent.KEYCODE_MEDIA_NEXT -> next()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> prev()
        }
    }

    /**
     * Güç tuşuyla ekran kapandı: video duraklatılır ve konumu saklanır. Kilitliyken video ayrı bir
     * pencerede oynadığı için Android onu kendisi durdurmaz.
     */
    private fun onScreenOff() {
        screenOff = true
        // Oynatıcı hâlâ açıksa konumu buradan al; kapandıysa onPause'da saklanan konum kullanılır
        if (videoView.duration > 0) savedPosition = videoView.currentPosition
        videoView.pause()
        updatePlayButton()
    }

    /** Ekran açıldı: video kaldığı yerden devam eder (duraklatılmışsa duraklatılmış kalır). */
    private fun onScreenOn() {
        screenOff = false
        if (playlist.isEmpty()) return
        if (savedPosition >= 0) {
            videoView.seekTo(savedPosition)
            savedPosition = -1
        }
        if (wantPlaying) videoView.start()
        updatePlayButton()
    }

    private fun scheduleAutoLock() {
        handler.removeCallbacks(autoLockRunnable)
        if (!isChildLocked) handler.postDelayed(autoLockRunnable, AUTO_LOCK_DELAY_MS)
    }

    // Kilit açıkken her dokunuş ve tuş, otomatik kilit süresini yeniden başlatır
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isChildLocked) scheduleAutoLock()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isChildLocked) scheduleAutoLock()
        return super.dispatchKeyEvent(event)
    }

    private fun updatePlayButton() {
        if (::btnPlay.isInitialized) {
            btnPlay.text = if (videoView.isPlaying) "⏸" else "▶"
        }
    }

    private fun startPlayback() {
        val savedPath = prefs.getString("folder_path", null)
        var targetDir: File? = null

        if (savedPath != null) {
            val f = File(savedPath)
            if (f.exists() && f.isDirectory) {
                targetDir = f
            }
        }

        if (targetDir == null) {
            val candidateDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStorageDirectory()
            )
            for (candidate in candidateDirs) {
                if (candidate != null && candidate.exists() && candidate.isDirectory) {
                    val hasVideos = candidate.listFiles()?.any { it.isFile && isVideoFile(it) } == true
                    if (hasVideos) {
                        targetDir = candidate
                        break
                    }
                }
            }
        }

        if (targetDir == null) {
            selectButton.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            applyLockState()
            statusText.text = "Oynatılacak video bulunamadı.\nLütfen 'Video Klasörü Seç' butonuna tıklayın."
            return
        }

        val videoFiles = targetDir.listFiles()
            ?.filter { it.isFile && isVideoFile(it) }
            ?.shuffled() ?: emptyList()

        if (videoFiles.isEmpty()) {
            selectButton.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            applyLockState()
            statusText.text = "Seçilen klasörde (${targetDir.name}) video bulunamadı."
            return
        }

        playlist.clear()
        playlistNames.clear()
        videoFiles.forEach { file ->
            playlist.add(Uri.fromFile(file))
            playlistNames.add(file.name)
        }
        index = 0

        selectButton.visibility = View.GONE
        statusText.visibility = View.GONE
        playCurrent()
        applyLockState()

        if (!isChildLocked) {
            setControlsVisible(true)
        } else {
            showInfo(LOCKED_MSG)
        }
    }

    private fun isVideoFile(file: File): Boolean {
        val n = file.name.lowercase(Locale.getDefault())
        return n.endsWith(".mp4") || n.endsWith(".mkv") ||
                n.endsWith(".3gp") || n.endsWith(".webm") ||
                n.endsWith(".mov") || n.endsWith(".avi")
    }

    private fun playCurrent() {
        wantPlaying = true
        savedPosition = -1
        videoView.setVideoURI(playlist[index])
        videoView.requestFocus()
        if (::txtTitle.isInitialized && index < playlistNames.size) {
            txtTitle.text = String.format(Locale.getDefault(), "%d/%d - %s", index + 1, playlist.size, playlistNames[index])
        }
        showInfo(String.format(Locale.getDefault(), "%d / %d\n%s", index + 1, playlist.size, playlistNames.getOrNull(index) ?: ""))
    }

    private fun next() {
        if (playlist.isEmpty()) return
        index++
        if (index >= playlist.size) {
            val combined = playlist.zip(playlistNames).shuffled()
            playlist.clear()
            playlistNames.clear()
            combined.forEach { (u, n) ->
                playlist.add(u)
                playlistNames.add(n)
            }
            index = 0
        }
        playCurrent()
    }

    private fun prev() {
        if (playlist.isEmpty()) return
        index = if (index > 0) index - 1 else playlist.lastIndex
        playCurrent()
    }

    override fun onDestroy() {
        cancelSleepCountdown()
        disableSystemOverlayBlockers()
        lockOverlay?.let {
            try {
                windowManagerService.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lockOverlay = null
        handler.removeCallbacks(barSuppressionRunnable)
        try {
            unregisterReceiver(powerStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val UNLOCK_TAP_COUNT = 5
        private const val TAP_INTERVAL_MS = 500L
        private const val LOCKED_MSG = "🔒 Çocuk Kilidi AKTİF\n(Açmak için art arda 5 kez dokunun)"
        private const val AUTO_LOCK_DELAY_MS = 30_000L
        private const val AUTO_LOCKED_MSG = "🔒 30 sn dokunulmadı\nÇocuk Kilidi otomatik AKTİF"

        // Kumanda / kulaklık medya tuşları (kilitliyken de çalışır)
        private val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_HEADSETHOOK
        )

        @Suppress("DEPRECATION")
        private const val IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
