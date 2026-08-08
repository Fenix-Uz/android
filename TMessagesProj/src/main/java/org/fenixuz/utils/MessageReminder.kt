package org.fenixuz.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.fenixuz.ui.secret_chat.SecretPassword
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R

/**
 * Novagram "unread message reminder" (pro item 44 — budilnik/eslatma), re-thought as a senior would.
 *
 * Behaviour (matches pro's model, cleanly): while enabled, if an incoming message stays unread for the
 * chosen delay, a single high-importance notification (with the chosen sound) fires. Reading cancels it.
 *
 * Senior fixes over pro's version:
 *  - NO exact-alarm permissions. Pro used USE_EXACT_ALARM/SCHEDULE_EXACT_ALARM (Play restricts these to
 *    clock/calendar apps → rejection risk). We use the INEXACT [AlarmManager.setAndAllowWhileIdle],
 *    which needs no permission and still wakes from Doze (may be deferred — acceptable for a reminder).
 *  - ONE reminder, not one alarm per incoming message (pro drained battery + spammed AlarmManager).
 *  - Cancels when read (pro fired even after you'd read the message).
 *  - Respects the user's volume via a notification channel (pro forced STREAM_MUSIC to max — rude) and
 *    uses system tones (pro bundled a 1.6 MB wav).
 *  - O(1) cached reads on the message hot path; all writes are async ([apply]); no Activity refs.
 *
 * Arming keys off the incoming MESSAGE itself, NOT NotificationsController.total_unread_count (which is
 * badge/mute-settings dependent and reads 0 in many real cases). State lives in device-only "db" prefs.
 */
object MessageReminder {

    private const val PREF = "db"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_DELAY_MIN = "reminder_delay_min"
    private const val KEY_SOUND = "reminder_sound"
    private const val KEY_ARMED = "reminder_armed"

    const val DEFAULT_DELAY_MIN = 5
    const val MIN_DELAY_MIN = 1
    const val MAX_DELAY_MIN = 59
    const val SOUND_COUNT = 2

    private const val ALARM_REQUEST_CODE = 770144
    private const val CHANNEL_ID = "fenix_reminder_v2_silent"
    private const val NOTIFICATION_ID = 770144

    // Like pro, the single fire plays a continuous ~8s tone (pro used a ~10-15s wav). We loop the chosen
    // system tone for this long via MediaPlayer under a goAsync() window — the alarm broadcast's ~10s
    // wakelock covers it, so no foreground service / extra permission is needed.
    private const val RING_DURATION_MS = 8000L

    @Volatile private var loaded = false
    @Volatile private var enabled = false
    @Volatile private var delayMin = DEFAULT_DELAY_MIN
    @Volatile private var sound = 0
    // Whether a reminder is currently outstanding (persisted so the receiver works after process death).
    @Volatile private var armed = false
    // The currently-ringing player (held so a read mid-ring can silence it). Main-thread only.
    @Volatile private var player: MediaPlayer? = null

    private val prefs by lazy {
        ApplicationLoader.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            enabled = prefs.getBoolean(KEY_ENABLED, false)
            delayMin = prefs.getInt(KEY_DELAY_MIN, DEFAULT_DELAY_MIN).coerceIn(MIN_DELAY_MIN, MAX_DELAY_MIN)
            sound = prefs.getInt(KEY_SOUND, 0).coerceIn(0, SOUND_COUNT - 1)
            armed = prefs.getBoolean(KEY_ARMED, false)
            loaded = true
        }
    }

    fun isEnabled(): Boolean {
        ensureLoaded()
        return enabled
    }

    fun getDelayMin(): Int {
        ensureLoaded()
        return delayMin
    }

    fun getSound(): Int {
        ensureLoaded()
        return sound
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        // Arms on the next incoming message; disable cancels anything pending.
        if (!value) disarm()
    }

    fun setDelayMin(value: Int) {
        ensureLoaded()
        delayMin = value.coerceIn(MIN_DELAY_MIN, MAX_DELAY_MIN)
        prefs.edit().putInt(KEY_DELAY_MIN, delayMin).apply()
    }

    fun setSound(value: Int) {
        ensureLoaded()
        sound = value.coerceIn(0, SOUND_COUNT - 1)
        prefs.edit().putInt(KEY_SOUND, sound).apply()
    }

    /** Sound 0 = soft system NOTIFICATION tone, sound 1 = louder system ALARM tone (harder to miss). */
    fun soundUri(index: Int): Uri? = if (index == 1) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    } else {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun soundUsage(index: Int): Int =
        if (index == 1) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION

    /**
     * New messages arrived (NotificationsController new-message hook). Arms a single reminder when an
     * INCOMING message is present.
     */
    fun onNewMessages(messages: List<MessageObject>?) {
        ensureLoaded()
        if (!enabled || armed) return
        if (!hasIncoming(messages)) return
        arm()
    }

    /**
     * Dialog read-state changed (NotificationsController read hook). Only disarm on an actual READ
     * (unread count went DOWN) — this hook also fires on new messages (count up), which must NOT cancel
     * the reminder we just armed.
     */
    fun onRead(wasRead: Boolean) {
        ensureLoaded()
        if (!wasRead) return
        if (armed) {
            disarm()
        }
        stopRing() // silence an in-progress ring if the user just read
    }

    /**
     * Called by [ReminderReceiver] when the scheduled time arrives. Fires ONCE (pro's model), but the
     * ring is a continuous ~8s tone. [pendingResult] (from goAsync) keeps the receiver's process alive
     * for the duration; it's finished when the ring stops.
     */
    fun onFired(pendingResult: BroadcastReceiver.PendingResult? = null) {
        ensureLoaded()
        val context = ApplicationLoader.applicationContext
        // A new message later re-arms; clearing here keeps it a single fire per unread cycle.
        setArmed(false)
        if (!enabled) {
            pendingResult?.finish()
            return
        }
        // No unread re-check: reading cancels the alarm (onRead), so reaching here means it stayed unread.
        // (A cold-start re-check would read 0 unread before the stack loads and wrongly suppress it.)
        showNotification(context)
        playRing(context, pendingResult)
    }

    private fun playRing(context: Context, pendingResult: BroadcastReceiver.PendingResult?) {
        // All MediaPlayer work happens on the UI thread so `player` is only ever touched from one thread
        // (stopRing may be invoked from the notifications background thread on read).
        AndroidUtilities.runOnUIThread {
            releasePlayer()
            val uri = soundUri(sound)
            if (uri == null) {
                pendingResult?.finish()
                return@runOnUIThread
            }
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(soundUsage(sound))
                        .build()
                )
                mp.setDataSource(context, uri)
                mp.isLooping = true            // loop in case the tone is shorter than RING_DURATION_MS
                mp.prepare()
                mp.start()
                player = mp
                AndroidUtilities.runOnUIThread({
                    releasePlayer()
                    pendingResult?.finish()
                }, RING_DURATION_MS)
            } catch (e: Exception) {
                releasePlayer()
                pendingResult?.finish()
            }
        }
    }

    /** Stop the ring from ANY thread — marshals to the UI thread where the player is owned. */
    private fun stopRing() {
        AndroidUtilities.runOnUIThread { releasePlayer() }
    }

    /** UI-thread only: tear down the current player. */
    private fun releasePlayer() {
        val p = player ?: return
        player = null
        try {
            if (p.isPlaying) p.stop()
        } catch (ignore: Exception) {
        }
        try {
            p.release()
        } catch (ignore: Exception) {
        }
    }

    // Only PRIVATE (user) chats arm the reminder — matches pro and avoids nagging on busy channels/groups.
    private fun hasIncoming(messages: List<MessageObject>?): Boolean {
        if (messages == null) return false
        for (m in messages) {
            // Secret-folder chats never notify (see the NotificationsController guard), so they must not
            // arm the unread reminder either — otherwise a locked chat would still ring after the delay.
            if (!m.isOut && DialogObject.isUserDialog(m.dialogId)
                && !SecretPassword.isSecret(m.dialogId)) return true
        }
        return false
    }

    private fun arm() {
        val context = ApplicationLoader.applicationContext
        setArmed(true)
        val triggerAt = System.currentTimeMillis() + delayMin.toLong() * 60_000L
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmPendingIntent(context) ?: return
        try {
            // INEXACT on purpose: exact alarms (setExact*/setAlarmClock) require SCHEDULE_EXACT_ALARM/
            // USE_EXACT_ALARM, which Google Play restricts to clock/calendar apps (the user refuses to
            // declare them). setAndAllowWhileIdle needs NO permission and still fires in Doze.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (ignore: Exception) {
        }
    }

    private fun disarm() {
        val context = ApplicationLoader.applicationContext
        setArmed(false)
        stopRing()
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmPendingIntent(context) ?: return
        try {
            am.cancel(pi)
        } catch (ignore: Exception) {
        }
    }

    private fun setArmed(value: Boolean) {
        armed = value
        prefs.edit().putBoolean(KEY_ARMED, value).apply()
    }

    private fun alarmPendingIntent(context: Context): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }

    private fun showNotification(context: Context) {
        // The notification is SILENT (no channel sound): the continuous ring is played by playRing()'s
        // MediaPlayer so we control its full ~8s duration. The channel only drives the heads-up + vibrate.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    LanguageCode.getMyTitles(269),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }

        val contentIntent = openAppIntent(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(LanguageCode.getMyTitles(277))
            .setContentText(LanguageCode.getMyTitles(278))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)             // audio handled by playRing(); avoid a double sound
            .setAutoCancel(true)
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (ignore: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+) — nothing to show.
        } catch (ignore: Exception) {
        }
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(context, ALARM_REQUEST_CODE, launch, flags)
    }
}
