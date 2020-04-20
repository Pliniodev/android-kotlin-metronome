package geva.oren.android_kotlin_metronome.services

import android.app.*
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.util.Log
import geva.oren.android_kotlin_metronome.MainActivity
import geva.oren.android_kotlin_metronome.R
import kotlinx.coroutines.*


/**
 * The Metronome service is responsible for playing, stoping and timing the ticks.
 * It is a started AND bound service, so it can persist and survive device rotation, and allow
 * The fragments to bind keep referencing it.
 * The service is starting foreground mode on play() and exits it on stop().
 */
class MetronomeService : Service() {
    private val TAG = "METRONOME_SERVICE"
    private val CHANNEL_ID = "METRONOME SERVICE"
    private val STOP_SERVICE = "STOP_METRONOME_SERVICE"
    private val binder = MetronomeBinder()
    private lateinit var soundPool: SoundPool
    private var tickJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var bpm = 100
    private var beatsPerMeasure = 4
    private var interval = 600
    var isPlaying = false
        private set
    private val tickListeners = arrayListOf<TickListener>()
    private var tone =
        Tone.WOOD
    private var rhythm =
        Rhythm.QUARTER
    var emphasis = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action==STOP_SERVICE) {
            pause()
            stopService(intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Metronome service created")
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
        soundPool.load(this, R.raw.wood, 1)
        soundPool.load(this, R.raw.click, 1)
        soundPool.load(this, R.raw.ding, 1)
        soundPool.load(this, R.raw.beep, 1)
    }

    // Notification for enabling a foreground service
    private fun startForegroundNotification() {
        val mChannel = NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        val stopSelf = Intent(this, MetronomeService::class.java)
        stopSelf.action = STOP_SERVICE
        val pStopSelf = PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT)
        val stopAction = Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop", pStopSelf).build()

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getText(R.string.notification_title))
            .setContentText(getText(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_eighth_note)
            .setLargeIcon(Icon.createWithResource(this, R.drawable.ic_eighth_note))
            .setContentIntent(pendingIntent)
            .addAction(stopAction)
            .setDeleteIntent(pStopSelf)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Metronome service destroyed")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun play() {
        if(!isPlaying) {
            startForegroundNotification()
            tickJob = coroutineScope.launch(Dispatchers.Default) {
                isPlaying = true
                var tick = 0
                while (isPlaying && isActive) {
                    var rate = 1f
                    delay(interval.toLong())
                    if (tick % rhythm.value == 0) {
                        for (t in tickListeners)
                            t.onTick(interval)
                        if (emphasis && tick == 0)
                            rate = 1.4f
                    }
                    if (isPlaying) soundPool.play(tone.value, 1f, 1f, 1, 0, rate)
                    if (tick < beatsPerMeasure * rhythm.value - 1)
                        tick++
                    else
                        tick = 0
                }
            }
        }
    }

    fun pause() {
        if(isPlaying) {
            tickJob?.cancel()
            stopForeground(true)
            isPlaying = false
        }
    }

    /**
     * Accepts bpm value an sets the interval in ms
     * @param bpm - the bpm value
     */
    fun setInterval(bpm: Int) {
        this.bpm = bpm
        interval = 60000 / (bpm * rhythm.value)
    }

    /**
     * Toggle emphasis on/off
     */
    fun toggleEmphasis(): Boolean {
        emphasis = !emphasis
        return emphasis
    }

    /**
     * Rotates to the next rhythm
     */
    fun nextRhythm(): Rhythm {
        val isPlaying = this.isPlaying
        rhythm = rhythm.next()
        setInterval(bpm)
        if (isPlaying) {
            pause()
            play()
        }
        return rhythm
    }

    /**
     * Rotates to the next sound
     */
    fun nextTone(): Tone {
        tone = tone.next()
        setInterval(bpm)
        return tone
    }

    fun addTickListener(tickListener: TickListener) {
        tickListeners.add(tickListener)
        Log.i(TAG, "number of listeners ${tickListeners.size}")
    }

    fun removeTickListener(tickListener: TickListener) {
        tickListeners.remove(tickListener)
        Log.i(TAG, "number of listeners ${tickListeners.size}")
    }

    inner class MetronomeBinder : Binder() {
        fun getService(): MetronomeService {
            return this@MetronomeService
        }
    }

    enum class Tone(val value: Int) {
        WOOD(1),
        CLICK(2),
        DING(3),
        BEEP(4);

        companion object {
            private val values = values()
        }

        fun next(): Tone {
            return values()[(this.ordinal + 1) % values.size]
        }
    }

    enum class Rhythm(val value: Int) {
        QUARTER(1),
        EIGHTH(2),
        SIXTEENTH(4);

        companion object {
            private val values = values()
        }

        fun next(): Rhythm {
            return values()[(this.ordinal + 1) % values.size]
        }
    }

    interface TickListener {
        fun onTick(interval: Int)
    }
}



