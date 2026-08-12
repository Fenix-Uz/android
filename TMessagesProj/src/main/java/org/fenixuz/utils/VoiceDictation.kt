package org.fenixuz.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import java.util.Locale

/**
 * Novagram voice dictation engine (the speech-to-text half of the voice-translate feature).
 *
 * Recognises speech (in the chosen "speak" language) into the message field via the on-device Android
 * [SpeechRecognizer] — no server, no bundled model, no Firebase; only the standard RECORD_AUDIO
 * permission the app already requests for voice messages. The translation step (speak language → target
 * language) lives in [VoiceTranslate]; the from/to languages are picked in
 * `org.fenixuz.ui.voice_translate.VoiceTranslateSheet` and stored here device-only.
 *
 * CONTINUOUS BY DESIGN. Android's [SpeechRecognizer] captures only ONE utterance per `startListening`
 * and ends as soon as it hears a pause (end-of-speech endpointing). That made the old single-shot version
 * stop the moment the user paused — speak, pause, speak again, and the second half was lost. So this
 * engine keeps the session alive across pauses: every time the recogniser finishes a phrase ([onResults])
 * or reports silence ([onError] NO_MATCH / SPEECH_TIMEOUT), it transparently restarts and APPENDS the next
 * phrase to [committed]. The session ends only when the USER stops it ([finish], via the on-screen mic /
 * stop button), it is cancelled ([stop]/[destroy]), or the engine hits a real, repeated hard error.
 *
 * Because it is the user who decides when to stop, the caller keeps a STOP control visible the whole time
 * (see ChatActivityEnterView's red stop button) — [onListeningChanged] fires exactly once true at start
 * and once false at the end, never flickering on the internal restarts.
 *
 * Translation timing: live partials stream into the field via [onText] as you speak; the full text is
 * translated once, at the end, when [Listener.onFinished] fires (on user stop).
 *
 * Clean rewrite of pro's `MySpeechRecognizer`, keeping its working idea (continuous dictation) but fixing
 * its real bugs: pro restarted on EVERY error (a dead engine looped forever, beep-spamming), never
 * released the recognizer (leak), probed every RecognitionService via bindService (slow/fragile), typed
 * char-by-char, and mangled language codes ("en-EN"). Here restarts are bounded for hard errors (only
 * silence restarts indefinitely, which is the whole point), [destroy] releases the engine, the platform
 * default recogniser is created directly, partials stream straight in, and the speak language is a clean
 * BCP-47 tag defaulting to the device locale.
 *
 * STOP semantics: [stop] is a QUIET cancel (pause/send/destroy) — the engine is cancelled and NO result is
 * delivered. [finish] is the USER stop — the engine is asked to flush its final phrase, and the COMPLETE
 * accumulated text is then delivered via [Listener.onFinished]. Either way nothing is delivered twice.
 *
 * Main-thread only (a hard SpeechRecognizer requirement) — every public method is called from the UI.
 */
class VoiceDictation(
    private val activity: Activity,
    private val listener: Listener
) : RecognitionListener {

    interface Listener {
        /** Live text to put in the field: the pre-dictation base + everything recognised so far. */
        fun onText(text: CharSequence)
        /** Session turned on/off — for the toast and the mic / stop-button state. Fires once per session edge. */
        fun onListeningChanged(listening: Boolean)
        /** Fired once when the user stops, with the COMPLETE text — the caller translates it. */
        fun onFinished(finalText: CharSequence)
        /** No usable recogniser / permission on this device — show a toast, never crash. */
        fun onUnavailable()
    }

    private var recognizer: SpeechRecognizer? = null
    private var intent: Intent? = null

    private var listening = false        // session active (mic shown red)
    private var finishing = false        // user asked to stop — flush the final phrase, then deliver
    private var cancelled = false        // quiet-stopped — ignore any late callback, never deliver
    private var delivered = false        // onFinished already sent for this session — never deliver twice
    private var baseText = ""            // field text that existed before this dictation session
    private var committed = ""           // finalised recognised text accumulated across phrases this session

    // Continuous restart: grab the next phrase. Guarded so a pending restart can't fire after stop/finish.
    private val restartRunnable = Runnable {
        if (!cancelled && !finishing && listening) beginListening()
    }
    // Fallback: if the user stops but the engine emits no final result, deliver what we already have.
    private val finishRunnable = Runnable { endSession() }

    val isListening: Boolean get() = listening

    fun start(currentFieldText: CharSequence?) {
        if (listening) return
        AndroidUtilities.cancelRunOnUIThread(restartRunnable)
        AndroidUtilities.cancelRunOnUIThread(finishRunnable)
        if (!hasRecognizer(activity) || !ensureRecognizer()) {
            listener.onUnavailable()
            return
        }
        // Apply the currently-saved speak language each start, so a change takes effect immediately.
        intent?.putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolveLanguageTag())
        baseText = currentFieldText?.toString()?.trimEnd() ?: ""
        committed = ""
        cancelled = false
        finishing = false
        delivered = false
        listening = true
        listener.onListeningChanged(true)
        beginListening()
    }

    /** Quiet stop (pause / send / destroy): cancel the engine, deliver nothing. */
    fun stop() {
        if (!listening && !finishing) return
        cancelled = true
        listening = false
        finishing = false
        AndroidUtilities.cancelRunOnUIThread(restartRunnable)
        AndroidUtilities.cancelRunOnUIThread(finishRunnable)
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            FileLog.e(e)
        }
        listener.onListeningChanged(false)
    }

    /** User stop: flush the final phrase; the complete text arrives via [onResults]/fallback → [onFinished]. */
    fun finish() {
        if (!listening || finishing) return
        finishing = true
        AndroidUtilities.cancelRunOnUIThread(restartRunnable)
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            FileLog.e(e)
        }
        // Fallback in case no final result is emitted (some engines drop straight to silence).
        AndroidUtilities.runOnUIThread(finishRunnable, FINISH_TIMEOUT_MS)
    }

    /** Release the engine. Call from the host's onDestroy. Idempotent. */
    fun destroy() {
        cancelled = true
        listening = false
        finishing = false
        AndroidUtilities.cancelRunOnUIThread(restartRunnable)
        AndroidUtilities.cancelRunOnUIThread(finishRunnable)
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            FileLog.e(e)
        }
        recognizer = null
        intent = null
    }

    // End the session exactly once: turn the indicator off and deliver the accumulated text (if any).
    private fun endSession() {
        if (listening) {
            listening = false
            listener.onListeningChanged(false)
        }
        deliver()
    }

    // Deliver the complete text exactly once, translating only when something was actually dictated.
    private fun deliver() {
        AndroidUtilities.cancelRunOnUIThread(finishRunnable)
        if (delivered || cancelled || committed.isBlank()) return
        delivered = true
        listener.onFinished(join(baseText, committed))
    }

    private fun ensureRecognizer(): Boolean {
        if (recognizer != null) return true
        return try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(this@VoiceDictation)
            }
            intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolveLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            }
            true
        } catch (e: Exception) {
            FileLog.e(e)
            recognizer = null
            false
        }
    }

    private fun beginListening() {
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            FileLog.e(e)
            endSession()
        }
    }

    // Schedule a continuous restart to capture the next phrase (no-op if the session is ending).
    private fun restart() {
        if (cancelled || finishing || !listening) return
        AndroidUtilities.cancelRunOnUIThread(restartRunnable)
        AndroidUtilities.runOnUIThread(restartRunnable, RESTART_DELAY_MS)
    }

    // The chosen speak language (Telegram code → BCP-47), else the device language.
    private fun resolveLanguageTag(): String {
        val saved = getSpeakLang(activity)
        if (saved.isNotEmpty()) {
            return try {
                Locale.forLanguageTag(saved.replace("_", "-")).toLanguageTag()
            } catch (e: Exception) {
                saved
            }
        }
        return try {
            Locale.getDefault().toLanguageTag()
        } catch (e: Exception) {
            "en-US"
        }
    }

    private fun best(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return if (!list.isNullOrEmpty()) list[0] ?: "" else ""
    }

    private fun join(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString(" ").trim()

    // ---- RecognitionListener (all delivered on the main thread) ----

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onPartialResults(partialResults: Bundle?) {
        if (cancelled || !listening) return
        val text = best(partialResults)
        if (text.isNotEmpty()) {
            listener.onText(join(baseText, committed, text))
        }
    }

    override fun onResults(results: Bundle?) {
        if (cancelled) return
        val text = best(results)
        if (text.isNotEmpty()) {
            committed = join(committed, text)
            listener.onText(join(baseText, committed))
        }
        if (finishing) {
            endSession()       // user stopped → deliver the complete text for translation
        } else {
            restart()          // continuous → keep listening for the next phrase
        }
    }

    override fun onError(error: Int) {
        if (cancelled) return
        // User already asked to stop → deliver whatever we captured (don't restart).
        if (finishing) {
            endSession()
            return
        }
        // No mic permission is the ONLY thing we can't recover from — surface it (or deliver if we have text).
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            if (committed.isNotBlank()) {
                endSession()
            } else {
                listening = false
                listener.onListeningChanged(false)
                listener.onUnavailable()
            }
            return
        }
        // EVERY other error (silence/no-match/timeout, busy, client, network, server, audio) just means the
        // current listen segment ended — restart and keep going until the user stops. This is the whole
        // point of continuous dictation and mirrors pro's MySpeechRecognizer, which restarts on every error
        // unconditionally; a bounded retry cap was why dictation died after the first phrase (restarting the
        // engine briefly throws BUSY/CLIENT, which the cap counted as fatal). The short restart delay both
        // lets the engine settle and stops a permanently-broken engine from spinning in a tight loop.
        if (listening) {
            restart()
        }
    }

    companion object {
        private const val RESTART_DELAY_MS = 120L    // brief gap before re-listening so the engine settles
        private const val FINISH_TIMEOUT_MS = 2500L  // fallback if the engine emits no final result on stop
        private const val PREF = "db"                // device-only store shared by the other fenix toggles
        private const val KEY_FROM = "dictation_from"
        private const val KEY_TO = "dictation_to"
        private const val KEY_MIC = "dictation_mic_enabled"

        // Both answers are cached: [isMicVisible] is asked while BUILDING the composer, which happens for
        // every chat the user opens, so it must never touch disk or PackageManager on that path.
        @Volatile private var micEnabledCache: Boolean? = null
        @Volatile private var recognizerCache: Boolean? = null

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        /** User switch for the composer mic + the chat-menu "Voice input" entry. Default ON. */
        @JvmStatic
        fun isMicEnabled(context: Context): Boolean =
            micEnabledCache ?: prefs(context).getBoolean(KEY_MIC, true).also { micEnabledCache = it }

        @JvmStatic
        fun setMicEnabled(context: Context, value: Boolean) {
            micEnabledCache = value
            prefs(context).edit().putBoolean(KEY_MIC, value).apply()
        }

        /**
         * Does this device have ANY speech recogniser at all? On ROMs shipped without Google services —
         * common in our Russian user base — there is none, [start] can only ever answer onUnavailable(),
         * and the mic is a permanently dead button. So we hide it rather than offer a control that cannot
         * work. This is the exact same call [start] gates on, so hiding never removes working behaviour.
         *
         * Needs the <queries> RecognitionService entry in the manifest: under targetSdk 35 package-visibility
         * filtering, queryIntentServices() can hide a perfectly good recogniser and we would wrongly say no.
         *
         * Cached — a PackageManager query whose answer cannot change without a package install, and it is
         * asked on the composer-build path.
         */
        @JvmStatic
        fun hasRecognizer(context: Context): Boolean =
            recognizerCache ?: (try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (e: Exception) {
                FileLog.e(e)
                true    // fail OPEN — never hide a working mic just because the query itself blew up
            }).also { recognizerCache = it }

        /** The single gate every mic entry point asks. */
        @JvmStatic
        fun isMicVisible(context: Context): Boolean =
            isMicEnabled(context) && hasRecognizer(context)

        /** Speak/recognition language (Telegram pluralLangCode, e.g. "en"). Empty = device default. */
        @JvmStatic
        fun getSpeakLang(context: Context): String = prefs(context).getString(KEY_FROM, "") ?: ""

        @JvmStatic
        fun setSpeakLang(context: Context, code: String) {
            prefs(context).edit().putString(KEY_FROM, code).apply()
        }

        /** Translate-to language (Telegram pluralLangCode). Empty = no translation (dictation only). */
        @JvmStatic
        fun getTranslateLang(context: Context): String = prefs(context).getString(KEY_TO, "") ?: ""

        @JvmStatic
        fun setTranslateLang(context: Context, code: String) {
            prefs(context).edit().putString(KEY_TO, code).apply()
        }
    }
}
