package org.fenixuz.ui.secret_chat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.IdRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.FileLog
import org.telegram.messenger.FingerprintController
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat
import org.fenixuz.ui.lock.LockCredential
import org.fenixuz.utils.MyColors
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.MotionBackgroundDrawable
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.LaunchActivity
import org.telegram.ui.Stories.recorder.KeyboardNotifier
import java.util.Collections
import java.util.LinkedList
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-screen passcode lock for the secret folder. This is Telegram's own PasscodeView UX (PIN pad
 * with the animated dots, motion-background reaction, fingerprint) wired to [SecretPassword] instead
 * of the app-wide passcode, so the two locks stay independent.
 *
 * Trimmed from the original port: the never-instantiated fingerprint dialog / blur-background classes
 * and a handful of always-null fields were removed.
 */
class SecretLockScreen(
    context: Context,
    private val credential: LockCredential,
    var secretLockScreenInterFace: SecretLockScreenInterFace
) : FrameLayout(context), NotificationCenterDelegate {

    private val type = credential.type
    private val isFingerPrint = credential.fingerPrint

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any) {
        if (id == NotificationCenter.didGenerateFingerprintKeyPair) {
            checkFingerprintButton()
            if (args[0] as Boolean && SharedConfig.appLocked) {
                checkFingerprint()
            }
        } else if (id == NotificationCenter.passcodeDismissed) {
            if (args[0] !== this) {
                visibility = GONE
            }
        }
    }

    interface SecretLockScreenDelegate {
        fun didAcceptedPassword(view: SecretLockScreen?)
    }

    interface SecretLockScreenInterFace {
        fun onAnimationUpdate(open: Float) {}
        fun onHidden() {}
    }

    private val BUTTON_X_MARGIN = 28
    private val BUTTON_Y_MARGIN = 16
    private val BUTTON_SIZE = 60

    private fun checkTitle() {
        val isEmpty = passwordEditText2 == null || passwordEditText2.length() > 0
        numbersTitleContainer.animate().cancel()
        numbersTitleContainer.animate().alpha(if (isEmpty) 0f else 1f)
            .scaleX(if (isEmpty) .8f else 1f).scaleY(if (isEmpty) .8f else 1f)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start()
    }

    private inner class AnimatingTextView(context: Context) : FrameLayout(context) {
        private val characterTextViews = ArrayList<TextView>(4)
        private val dotTextViews = ArrayList<TextView>(4)
        private val stringBuilder = StringBuilder(4)
        private var currentAnimation: AnimatorSet? = null
        private var dotRunnable: Runnable? = null

        init {
            val DOT = "•"
            for (a in 0..3) {
                var textView = TextView(context)
                textView.setTextColor(-0x1)
                textView.typeface = AndroidUtilities.bold()
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36f)
                textView.gravity = Gravity.CENTER
                textView.alpha = 0f
                textView.pivotX = AndroidUtilities.dp(25f).toFloat()
                textView.pivotY = AndroidUtilities.dp(25f).toFloat()
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP or Gravity.LEFT))
                characterTextViews.add(textView)

                textView = TextView(context)
                textView.setTextColor(-0x1)
                textView.typeface = AndroidUtilities.bold()
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36f)
                textView.gravity = Gravity.CENTER
                textView.alpha = 0f
                textView.text = DOT
                textView.pivotX = AndroidUtilities.dp(25f).toFloat()
                textView.pivotY = AndroidUtilities.dp(25f).toFloat()
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP or Gravity.LEFT))
                dotTextViews.add(textView)
            }
        }

        private fun getXForTextView(pos: Int): Int {
            return (measuredWidth - stringBuilder.length * AndroidUtilities.dp(30f)) / 2 +
                pos * AndroidUtilities.dp(30f) - AndroidUtilities.dp(10f)
        }

        fun appendCharacter(c: String) {
            if (stringBuilder.length == 4) {
                return
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {
                FileLog.e(e)
            }

            val animators = ArrayList<Animator>()
            val newPos = stringBuilder.length
            stringBuilder.append(c)

            var textView = characterTextViews[newPos]
            textView.text = c
            textView.translationX = getXForTextView(newPos).toFloat()
            animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f, 1f))
            animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f, 1f))
            animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f, 1f))
            animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_Y, AndroidUtilities.dp(20f).toFloat(), 0f))
            textView = dotTextViews[newPos]
            textView.translationX = getXForTextView(newPos).toFloat()
            textView.alpha = 0f
            animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f, 1f))
            animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f, 1f))
            animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_Y, AndroidUtilities.dp(20f).toFloat(), 0f))

            for (a in newPos + 1..3) {
                textView = characterTextViews[a]
                if (textView.alpha != 0f) {
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                }
                textView = dotTextViews[a]
                if (textView.alpha != 0f) {
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                }
            }

            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable)
            }
            dotRunnable = object : Runnable {
                override fun run() {
                    if (dotRunnable !== this) {
                        return
                    }
                    val anims = ArrayList<Animator>()
                    var tv = characterTextViews[newPos]
                    anims.add(ObjectAnimator.ofFloat(tv, SCALE_X, 0f))
                    anims.add(ObjectAnimator.ofFloat(tv, SCALE_Y, 0f))
                    anims.add(ObjectAnimator.ofFloat(tv, ALPHA, 0f))
                    tv = dotTextViews[newPos]
                    anims.add(ObjectAnimator.ofFloat(tv, SCALE_X, 1f))
                    anims.add(ObjectAnimator.ofFloat(tv, SCALE_Y, 1f))
                    anims.add(ObjectAnimator.ofFloat(tv, ALPHA, 1f))

                    currentAnimation = AnimatorSet()
                    currentAnimation!!.duration = 150
                    currentAnimation!!.playTogether(anims)
                    currentAnimation!!.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (currentAnimation == animation) {
                                currentAnimation = null
                            }
                        }
                    })
                    currentAnimation!!.start()
                }
            }
            AndroidUtilities.runOnUIThread(dotRunnable, 1500)

            for (a in 0 until newPos) {
                textView = characterTextViews[a]
                animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_X, getXForTextView(a).toFloat()))
                animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_Y, 0f))
                textView = dotTextViews[a]
                animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_X, getXForTextView(a).toFloat()))
                animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 1f))
                animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 1f))
                animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 1f))
                animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_Y, 0f))
            }

            currentAnimation?.cancel()
            currentAnimation = AnimatorSet()
            currentAnimation!!.duration = 150
            currentAnimation!!.playTogether(animators)
            currentAnimation!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentAnimation == animation) {
                        currentAnimation = null
                    }
                }
            })
            currentAnimation!!.start()

            checkTitle()
        }

        val string: String
            get() = stringBuilder.toString()

        fun length(): Int = stringBuilder.length

        fun eraseLastCharacter(): Boolean {
            if (stringBuilder.isEmpty()) {
                return false
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {
                FileLog.e(e)
            }

            val animators = ArrayList<Animator>()
            val deletingPos = stringBuilder.length - 1
            if (deletingPos != 0) {
                stringBuilder.deleteCharAt(deletingPos)
            }

            for (a in deletingPos..3) {
                var textView = characterTextViews[a]
                if (textView.alpha != 0f) {
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_Y, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_X, getXForTextView(a).toFloat()))
                }
                textView = dotTextViews[a]
                if (textView.alpha != 0f) {
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_Y, 0f))
                    animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_X, getXForTextView(a).toFloat()))
                }
            }

            if (deletingPos == 0) {
                stringBuilder.deleteCharAt(deletingPos)
            }

            for (a in 0 until deletingPos) {
                var textView = characterTextViews[a]
                animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_X, getXForTextView(a).toFloat()))
                textView = dotTextViews[a]
                animators.add(ObjectAnimator.ofFloat(textView, TRANSLATION_X, getXForTextView(a).toFloat()))
            }

            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable)
                dotRunnable = null
            }

            currentAnimation?.cancel()
            currentAnimation = AnimatorSet()
            currentAnimation!!.duration = 150
            currentAnimation!!.playTogether(animators)
            currentAnimation!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentAnimation == animation) {
                        currentAnimation = null
                    }
                }
            })
            currentAnimation!!.start()

            checkTitle()
            return true
        }

        fun eraseAllCharacters(animated: Boolean) {
            if (stringBuilder.isEmpty()) {
                return
            }
            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable)
                dotRunnable = null
            }
            currentAnimation?.cancel()
            currentAnimation = null
            stringBuilder.delete(0, stringBuilder.length)
            if (animated) {
                val animators = ArrayList<Animator>()
                for (a in 0..3) {
                    var textView = characterTextViews[a]
                    if (textView.alpha != 0f) {
                        animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                        animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                        animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                    }
                    textView = dotTextViews[a]
                    if (textView.alpha != 0f) {
                        animators.add(ObjectAnimator.ofFloat(textView, SCALE_X, 0f))
                        animators.add(ObjectAnimator.ofFloat(textView, SCALE_Y, 0f))
                        animators.add(ObjectAnimator.ofFloat(textView, ALPHA, 0f))
                    }
                }
                currentAnimation = AnimatorSet()
                currentAnimation!!.duration = 150
                currentAnimation!!.playTogether(animators)
                currentAnimation!!.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentAnimation == animation) {
                            currentAnimation = null
                        }
                    }
                })
                currentAnimation!!.start()
            } else {
                for (a in 0..3) {
                    characterTextViews[a].alpha = 0f
                    dotTextViews[a].alpha = 0f
                }
            }
            checkTitle()
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable)
                dotRunnable = null
            }
            currentAnimation?.cancel()
            currentAnimation = null

            for (a in 0..3) {
                if (a < stringBuilder.length) {
                    var textView = characterTextViews[a]
                    textView.alpha = 0f
                    textView.scaleX = 1f
                    textView.scaleY = 1f
                    textView.translationY = 0f
                    textView.translationX = getXForTextView(a).toFloat()

                    textView = dotTextViews[a]
                    textView.alpha = 1f
                    textView.scaleX = 1f
                    textView.scaleY = 1f
                    textView.translationY = 0f
                    textView.translationX = getXForTextView(a).toFloat()
                } else {
                    characterTextViews[a].alpha = 0f
                    dotTextViews[a].alpha = 0f
                }
            }
            super.onLayout(changed, left, top, right, bottom)
        }
    }

    private var backgroundDrawable: Drawable? = null
    private val numbersTitleContainer: FrameLayout
    private val subtitleView: TextView
    private val numbersContainer: FrameLayout
    var numbersFrameLayout: FrameLayout
    private val numberFrameLayouts: ArrayList<FrameLayout>
    private val passwordFrameLayout: FrameLayout
    private var fingerprintView: PasscodeButton? = null
    private val passwordEditText: EditTextBoldCursor
    private val passwordEditText2: AnimatingTextView
    private val backgroundFrameLayout: FrameLayout
    private val passcodeTextView: TextView
    private val retryTextView: TextView
    private val checkImage: ImageView
    private val fingerprintImage: ImageView
    private val border: View
    private var keyboardHeight = 0
    private var imageY = 0
    private val imageView: RLottieImageView
    private val rect = Rect()

    private var delegate: SecretLockScreenDelegate? = null

    private var backgroundAnimationSpring: SpringAnimation? = null
    private val backgroundSpringQueue = LinkedList<Runnable>()
    private val backgroundSpringNextQueue = LinkedList<Boolean>()

    private class InnerAnimator {
        var animatorSet: AnimatorSet? = null
        var startRadius: Float = 0f
    }

    private val innerAnimators = ArrayList<InnerAnimator>()

    private fun animateBackground(motionBackgroundDrawable: MotionBackgroundDrawable) {
        if (backgroundAnimationSpring != null && backgroundAnimationSpring!!.isRunning) {
            backgroundAnimationSpring!!.cancel()
        }

        val animationValue = FloatValueHolder(0f)
        motionBackgroundDrawable.setAnimationProgressProvider { animationValue.value / 100f }
        backgroundAnimationSpring = SpringAnimation(animationValue).setSpring(
            SpringForce(100f).setStiffness(BACKGROUND_SPRING_STIFFNESS)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        )
        backgroundAnimationSpring?.addEndListener { _: DynamicAnimation<*>?, canceled: Boolean, _: Float, _: Float ->
            backgroundAnimationSpring = null
            motionBackgroundDrawable.setAnimationProgressProvider(null)
            if (!canceled) {
                motionBackgroundDrawable.setPosAnimationProgress(1f)
                if (!backgroundSpringQueue.isEmpty()) {
                    backgroundSpringQueue.poll()?.run()
                    backgroundSpringNextQueue.poll()
                }
            }
        }
        backgroundAnimationSpring?.addUpdateListener { _: DynamicAnimation<*>?, _: Float, _: Float ->
            motionBackgroundDrawable.updateAnimation(true)
        }
        backgroundAnimationSpring?.start()
    }

    private fun setNextFocus(view: View, @IdRes nextId: Int) {
        view.nextFocusForwardId = nextId
        if (Build.VERSION.SDK_INT >= 22) {
            view.accessibilityTraversalBefore = nextId
        }
    }

    fun setDelegate(delegate: SecretLockScreenDelegate?) {
        this.delegate = delegate
    }

    private fun processDone(fingerprint: Boolean) {
        if (!fingerprint) {
            if (SharedConfig.passcodeRetryInMs > 0) {
                return
            }
            var password = ""
            if (type == SharedConfig.PASSCODE_TYPE_PIN) {
                password = passwordEditText2.string
            } else if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                password = passwordEditText.text.toString()
            }
            if (password.isEmpty()) {
                onPasscodeError()
                return
            }
            if (!credential.check(password)) {
                passwordEditText.setText("")
                passwordEditText2.eraseAllCharacters(true)
                onPasscodeError()
                if (backgroundDrawable is MotionBackgroundDrawable) {
                    val motionBackgroundDrawable = backgroundDrawable as MotionBackgroundDrawable
                    if (backgroundAnimationSpring != null) {
                        backgroundAnimationSpring!!.cancel()
                        motionBackgroundDrawable.setPosAnimationProgress(1f)
                    }
                    if (motionBackgroundDrawable.posAnimationProgress >= 1f) {
                        motionBackgroundDrawable.rotatePreview(true)
                    }
                }
                return
            }
        }
        passwordEditText.clearFocus()
        AndroidUtilities.hideKeyboard(passwordEditText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && FingerprintController.isKeyReady() && FingerprintController.checkDeviceFingerprintsChanged()) {
            FingerprintController.deleteInvalidKey()
        }

        setOnTouchListener(null)
        delegate?.didAcceptedPassword(this)

        imageView.animatedDrawable.setCustomEndFrame(71)
        imageView.animatedDrawable.setCurrentFrame(37, false)
        imageView.playAnimation()

        AndroidUtilities.runOnUIThread {
            val va = ValueAnimator.ofFloat(shownT, 0f)
            va.addUpdateListener { anm ->
                shownT = anm.animatedValue as Float
                secretLockScreenInterFace.onAnimationUpdate(shownT)
                alpha = shownT
            }
            va.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    secretLockScreenInterFace.onHidden()
                    secretLockScreenInterFace.onAnimationUpdate(0f.also { shownT = it })
                    alpha = 0f
                }
            })
            va.duration = 420
            va.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            va.start()
        }
    }

    private var shownT = 0f

    private var shiftDp = -12
    private fun shakeTextView() {
        AndroidUtilities.shakeViewSpring(numbersTitleContainer, -shiftDp.also { shiftDp = it }.toFloat())
    }

    private val checkRunnable: Runnable = object : Runnable {
        override fun run() {
            checkRetryTextView()
            AndroidUtilities.runOnUIThread(this, 100)
        }
    }
    private var lastValue = 0

    private fun checkRetryTextView() {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime > SharedConfig.lastUptimeMillis) {
            SharedConfig.passcodeRetryInMs -= (currentTime - SharedConfig.lastUptimeMillis)
            if (SharedConfig.passcodeRetryInMs < 0) {
                SharedConfig.passcodeRetryInMs = 0
            }
        }
        SharedConfig.lastUptimeMillis = currentTime
        SharedConfig.saveConfig()
        if (SharedConfig.passcodeRetryInMs > 0) {
            val value = max(1.0, ceil(SharedConfig.passcodeRetryInMs / 1000.0).toInt().toDouble()).toInt()
            if (value != lastValue) {
                retryTextView.text = LocaleController.formatString(
                    R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)
                )
                lastValue = value
            }
            if (retryTextView.visibility != VISIBLE) {
                retryTextView.visibility = VISIBLE
                passwordFrameLayout.visibility = INVISIBLE
                showPin(false)
                AndroidUtilities.hideKeyboard(passwordEditText)
            }
            AndroidUtilities.cancelRunOnUIThread(checkRunnable)
            AndroidUtilities.runOnUIThread(checkRunnable, 100)
        } else {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable)
            if (retryTextView.visibility == VISIBLE) {
                retryTextView.visibility = INVISIBLE
                passwordFrameLayout.visibility = VISIBLE
                showPin(true)
                if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                    AndroidUtilities.showKeyboard(passwordEditText)
                }
            }
        }
    }

    private fun onPasscodeError() {
        BotWebViewVibrationEffect.NOTIFICATION_ERROR.vibrate()
        shakeTextView()
    }

    fun onResume() {
        checkRetryTextView()
        if (retryTextView.visibility != VISIBLE) {
            if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                passwordEditText.requestFocus()
                AndroidUtilities.showKeyboard(passwordEditText)
                AndroidUtilities.runOnUIThread({
                    if (retryTextView.visibility != VISIBLE) {
                        passwordEditText.requestFocus()
                        AndroidUtilities.showKeyboard(passwordEditText)
                    }
                }, 200)
            }
            checkFingerprint()
        }
    }

    fun onBackPressed(): Boolean {
        if (keyboardNotifier != null && keyboardNotifier!!.keyboardVisible()) {
            AndroidUtilities.hideKeyboard(passwordEditText)
            return false
        }
        return true
    }

    fun onPause() {
        AndroidUtilities.cancelRunOnUIThread(checkRunnable)
    }

    private var keyboardNotifier: KeyboardNotifier? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didGenerateFingerprintKeyPair)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.passcodeDismissed)

        if (keyboardNotifier == null && parent is View) {
            keyboardNotifier = KeyboardNotifier((parent as View), Utilities.Callback { keyboardHeightLocal: Int ->
                var kbHeight = keyboardHeightLocal
                val landscape =
                    context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                kbHeight -= AndroidUtilities.navigationBarHeight
                if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                    passwordFrameLayout.animate()
                        .translationY(if (kbHeight <= AndroidUtilities.dp(20f)) 0f else (height - kbHeight) / 2f - passwordFrameLayout.height / (if (landscape) 1f else 2f) - passwordFrameLayout.top)
                        .setDuration(320)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start()
                    imageView.animate()
                        .alpha(if (kbHeight <= AndroidUtilities.dp(20f)) 1f else 0f)
                        .setDuration(320)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                }
            })
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didGenerateFingerprintKeyPair)
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.passcodeDismissed)
    }

    private var pinShown = true
    private var pinAnimator: ValueAnimator? = null
    private fun showPin(show: Boolean) {
        pinAnimator?.cancel()
        pinShown = show
        pinAnimator = ValueAnimator.ofFloat(numbersFrameLayout.alpha, if (show) 1f else 0f)
        pinAnimator?.addUpdateListener(AnimatorUpdateListener { anm ->
            val t = anm.animatedValue as Float
            numbersFrameLayout.scaleX = AndroidUtilities.lerp(.8f, 1f, t)
            numbersFrameLayout.scaleY = AndroidUtilities.lerp(.8f, 1f, t)
            numbersFrameLayout.alpha = AndroidUtilities.lerp(0f, 1f, t)
            passcodeTextView.scaleX = AndroidUtilities.lerp(1f, .9f, t)
            passcodeTextView.scaleY = AndroidUtilities.lerp(1f, .9f, t)
            passcodeTextView.alpha = AndroidUtilities.lerp(1f, 0f, t)
            passwordEditText2.alpha = AndroidUtilities.lerp(0f, 1f, t)
        })
        pinAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val t = if (show) 1f else 0f
                numbersFrameLayout.scaleX = AndroidUtilities.lerp(.8f, 1f, t)
                numbersFrameLayout.scaleY = AndroidUtilities.lerp(.8f, 1f, t)
                numbersFrameLayout.alpha = AndroidUtilities.lerp(0f, 1f, t)
                passcodeTextView.scaleX = AndroidUtilities.lerp(1f, .9f, t)
                passcodeTextView.scaleY = AndroidUtilities.lerp(1f, .9f, t)
                passcodeTextView.alpha = AndroidUtilities.lerp(1f, 0f, t)
                passwordEditText2.alpha = AndroidUtilities.lerp(0f, 1f, t)
            }
        })
        pinAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
        pinAnimator?.duration = 320
        pinAnimator?.start()
    }

    private fun checkFingerprint() {
        if (Build.VERSION.SDK_INT < 23) {
            return
        }
        val parentActivity = AndroidUtilities.findActivity(context)
        if (parentActivity != null && fingerprintView!!.visibility == VISIBLE && !ApplicationLoader.mainInterfacePaused) {
            try {
                if (BiometricManager.from(context)
                        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS &&
                    FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()
                ) {
                    val executor = ContextCompat.getMainExecutor(context)
                    val prompt = BiometricPrompt(LaunchActivity.instance, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
                                FileLog.d("SecretLockScreen onAuthenticationError $errMsgId \"$errString\"")
                                showPin(true)
                            }

                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                FileLog.d("SecretLockScreen onAuthenticationSucceeded")
                                processDone(true)
                            }

                            override fun onAuthenticationFailed() {
                                FileLog.d("SecretLockScreen onAuthenticationFailed")
                                showPin(true)
                            }
                        })
                    val promptInfo = PromptInfo.Builder()
                        .setTitle(LocaleController.getString(R.string.UnlockToUse))
                        .setNegativeButtonText(LocaleController.getString(R.string.UsePIN))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                    prompt.authenticate(promptInfo)
                    showPin(false)
                }
            } catch (e: Exception) {
                FileLog.e(e)
            }
        }
    }

    private fun hasFingerprint(): Boolean {
        val parentActivity = AndroidUtilities.findActivity(context)
        if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && SharedConfig.useFingerprintLock) {
            try {
                val fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext)
                return fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints() &&
                    FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()
            } catch (e: Throwable) {
                FileLog.e(e)
            }
        }
        return false
    }

    private fun checkFingerprintButton() {
        var hasFingerprint = false
        val parentActivity = AndroidUtilities.findActivity(context)
        if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && isFingerPrint) {
            try {
                // Gate on the SAME modern API the real BiometricPrompt uses (see checkFingerprint()),
                // NOT the deprecated FingerprintManagerCompat: on many current devices the legacy API
                // reports isHardwareDetected/hasEnrolledFingerprints == false even though BiometricPrompt
                // works fine, which used to hide this button forever (fingerprint "never showed").
                val biometricReady = BiometricManager.from(context)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                // Generate the AndroidKeyStore key lazily if it isn't there yet. checkKeyReady() only acts
                // when the keyguard is secure + biometric enrolled, runs off-thread, and posts
                // didGenerateFingerprintKeyPair — the observer above then re-runs this and auto-prompts.
                if (biometricReady && !FingerprintController.isKeyReady()) {
                    FingerprintController.checkKeyReady()
                }
                if (biometricReady && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()) {
                    hasFingerprint = true
                    fingerprintView!!.visibility = VISIBLE
                } else {
                    fingerprintView!!.visibility = GONE
                }
            } catch (e: Throwable) {
                FileLog.e(e)
                fingerprintView!!.visibility = GONE
            }
        } else {
            fingerprintView!!.visibility = GONE
        }
        if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            fingerprintImage.visibility = fingerprintView!!.visibility
        }
        subtitleView.text =
            LocaleController.getString(if (hasFingerprint) R.string.EnterPINorFingerprint else R.string.EnterPIN)
    }

    @JvmOverloads
    fun onShow(
        fingerprint: Boolean,
        animated: Boolean,
        x: Int = -1,
        y: Int = -1,
        onShow: Runnable? = null,
        onStart: Runnable? = null
    ) {
        checkFingerprintButton()
        checkRetryTextView()
        // Honour the long-dead `fingerprint` flag: when the lock screen is FIRST shown for unlocking and
        // the fingerprint button is available (key already generated), pop the biometric prompt immediately
        // — "open a locked chat → it asks for your fingerprint". The `visibility != VISIBLE` guard mirrors
        // the early-return below so a repeat onShow() can't stack a second prompt. If the key is still
        // generating the button is GONE here and the didGenerateFingerprintKeyPair observer fires it once ready.
        if (fingerprint && visibility != VISIBLE && fingerprintView!!.visibility == VISIBLE) {
            checkFingerprint()
        }
        val parentActivity = AndroidUtilities.findActivity(context)
        if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            if (!animated && retryTextView.visibility != VISIBLE) {
                passwordEditText.requestFocus()
                AndroidUtilities.showKeyboard(passwordEditText)
            }
        } else {
            if (parentActivity != null) {
                val currentFocus = parentActivity.currentFocus
                if (currentFocus != null) {
                    currentFocus.clearFocus()
                    AndroidUtilities.hideKeyboard(parentActivity.currentFocus)
                }
            }
        }
        if (visibility == VISIBLE) {
            return
        }
        translationY = 0f
        // Novagram brand lock screen: an "ocean blue" motion gradient instead of the chat wallpaper,
        // so the PIN UI matches the app's main colour. Retune the shades in MyColors.
        backgroundDrawable = MotionBackgroundDrawable(
            MyColors.secretLockColor1,
            MyColors.secretLockColor2,
            MyColors.secretLockColor3,
            MyColors.secretLockColor4,
            false
        )
        backgroundFrameLayout.setBackgroundColor(0x22000000)
        (backgroundDrawable as MotionBackgroundDrawable).setParentView(backgroundFrameLayout)

        passcodeTextView.text = LocaleController.getString(R.string.AppLocked)

        if (type == SharedConfig.PASSCODE_TYPE_PIN) {
            if (retryTextView.visibility != VISIBLE) {
                numbersFrameLayout.visibility = VISIBLE
            }
            passwordEditText.visibility = GONE
            passwordEditText2.visibility = VISIBLE
            checkImage.visibility = GONE
            fingerprintImage.visibility = GONE
        } else if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            passwordEditText.filters = arrayOfNulls(0)
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            numbersFrameLayout.visibility = GONE
            passwordEditText.isFocusable = true
            passwordEditText.isFocusableInTouchMode = true
            passwordEditText.visibility = VISIBLE
            passwordEditText2.visibility = GONE
            checkImage.visibility = VISIBLE
            fingerprintImage.visibility = fingerprintView!!.visibility
        }
        visibility = VISIBLE
        passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
        passwordEditText.setText("")
        passwordEditText2.eraseAllCharacters(false)
        if (animated) {
            alpha = 0.0f
            viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    alpha = 1.0f
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    imageView.animatedDrawable.setCurrentFrame(0, false)
                    imageView.animatedDrawable.setCustomEndFrame(37)
                    imageView.playAnimation()
                    showPin(true)
                    AndroidUtilities.runOnUIThread({
                        imageView.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                    }, 350)
                    val animatorSet = AnimatorSet()
                    val animators = ArrayList<Animator>()
                    val w = AndroidUtilities.displaySize.x
                    val h = AndroidUtilities.displaySize.y + (if (Build.VERSION.SDK_INT >= 21) AndroidUtilities.statusBarHeight else 0)
                    if (Build.VERSION.SDK_INT >= 21) {
                        val d1 = sqrt(((w - x) * (w - x) + (h - y) * (h - y)).toDouble())
                        val d2 = sqrt((x * x + (h - y) * (h - y)).toDouble())
                        val d3 = sqrt((x * x + y * y).toDouble())
                        val d4 = sqrt(((w - x) * (w - x) + y * y).toDouble())
                        val finalRadius = max(max(max(d1, d2), d3), d4)

                        innerAnimators.clear()

                        var a = 0
                        val N = numbersFrameLayout.childCount
                        while (a < N) {
                            val child = numbersFrameLayout.getChildAt(a)
                            child.scaleX = 0.7f
                            child.scaleY = 0.7f
                            child.alpha = 0.0f
                            val innerAnimator = InnerAnimator()
                            child.getLocationInWindow(pos)
                            val buttonX = pos[0] + child.measuredWidth / 2
                            val buttonY = pos[1] + child.measuredHeight / 2
                            innerAnimator.startRadius =
                                sqrt(((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY)).toDouble()).toFloat() - AndroidUtilities.dp(40f)

                            innerAnimator.animatorSet = AnimatorSet()
                            innerAnimator.animatorSet?.playTogether(
                                ObjectAnimator.ofFloat(child, SCALE_X, 0.6f, 1.04f),
                                ObjectAnimator.ofFloat(child, SCALE_Y, 0.6f, 1.04f),
                                ObjectAnimator.ofFloat(child, ALPHA, 0.0f, 1.0f)
                            )
                            val animatorSetInner = AnimatorSet()
                            animatorSetInner.playTogether(
                                ObjectAnimator.ofFloat(child, SCALE_X, 1.0f),
                                ObjectAnimator.ofFloat(child, SCALE_Y, 1.0f)
                            )
                            animatorSetInner.duration = 140
                            animatorSetInner.interpolator = DecelerateInterpolator()
                            innerAnimator.animatorSet?.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    animatorSetInner.start()
                                }
                            })
                            innerAnimator.animatorSet?.duration = 200
                            innerAnimator.animatorSet?.interpolator = DecelerateInterpolator()
                            innerAnimators.add(innerAnimator)
                            a++
                        }

                        animators.add(ObjectAnimator.ofFloat(backgroundFrameLayout, ALPHA, 0.0f, 1.0f))
                        val animator = ValueAnimator.ofFloat(0f, 1f)
                        animators.add(animator)
                        animator.addUpdateListener { animation ->
                            val fraction = animation.animatedFraction
                            val rad = finalRadius * fraction
                            var i = 0
                            while (i < innerAnimators.size) {
                                val innerAnimator = innerAnimators[i]
                                if (innerAnimator.startRadius > rad) {
                                    i++
                                    continue
                                }
                                innerAnimator.animatorSet!!.start()
                                innerAnimators.removeAt(i)
                            }
                        }
                        animatorSet.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                        animatorSet.duration = 500
                    } else {
                        animatorSet.duration = 350
                    }
                    val va = ValueAnimator.ofFloat(shownT, 1f)
                    va.addUpdateListener { anm ->
                        secretLockScreenInterFace.onAnimationUpdate((anm.animatedValue as Float).also { shownT = it })
                    }
                    va.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            secretLockScreenInterFace.onAnimationUpdate(1f.also { shownT = it })
                        }
                    })
                    va.duration = 420
                    va.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                    animators.add(va)

                    animatorSet.playTogether(animators)
                    animatorSet.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onShow?.run()
                            if (type == SharedConfig.PASSCODE_TYPE_PASSWORD && retryTextView.visibility != VISIBLE) {
                                passwordEditText.requestFocus()
                                AndroidUtilities.showKeyboard(passwordEditText)
                            }
                        }
                    })
                    animatorSet.start()

                    val animatorSet2 = AnimatorSet()
                    animatorSet2.duration = 332
                    val ix = if (!AndroidUtilities.isTablet() && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        (if (type == SharedConfig.PASSCODE_TYPE_PIN) w / 2f else w.toFloat()) / 2 - AndroidUtilities.dp(30f)
                    } else {
                        w / 2f - AndroidUtilities.dp(29f)
                    }

                    animatorSet2.playTogether(
                        ObjectAnimator.ofFloat(imageView, TRANSLATION_X, (x - AndroidUtilities.dp(29f)).toFloat(), ix),
                        ObjectAnimator.ofFloat(imageView, TRANSLATION_Y, (y - AndroidUtilities.dp(29f)).toFloat(), imageY.toFloat()),
                        ObjectAnimator.ofFloat(imageView, SCALE_X, 0.5f, 1.0f),
                        ObjectAnimator.ofFloat(imageView, SCALE_Y, 0.5f, 1.0f)
                    )
                    animatorSet2.interpolator = CubicBezierInterpolator.EASE_OUT
                    animatorSet2.start()
                }
            })
            requestLayout()
        } else {
            alpha = 1.0f
            secretLockScreenInterFace.onAnimationUpdate(1f.also { shownT = it })
            imageView.scaleX = 1.0f
            imageView.scaleY = 1.0f
            imageView.stopAnimation()
            imageView.animatedDrawable.setCurrentFrame(38, false)
            onShow?.run()
        }

        setOnTouchListener { _: View?, _: MotionEvent? -> true }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var height = AndroidUtilities.displaySize.y - (if (Build.VERSION.SDK_INT >= 21) 0 else AndroidUtilities.statusBarHeight)

        var layoutParams: LayoutParams

        val sizeBetweenNumbersX = AndroidUtilities.dp(BUTTON_X_MARGIN.toFloat())
        val sizeBetweenNumbersY = AndroidUtilities.dp(BUTTON_Y_MARGIN.toFloat())
        val buttonSize = AndroidUtilities.dp(BUTTON_SIZE.toFloat())

        val landscape = !AndroidUtilities.isTablet() && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        border.visibility = if (type == SharedConfig.PASSCODE_TYPE_PASSWORD) VISIBLE else GONE

        if (landscape) {
            imageView.translationX = (if (type == SharedConfig.PASSCODE_TYPE_PIN) width / 2f else width.toFloat()) / 2 - AndroidUtilities.dp(29f)

            layoutParams = passwordFrameLayout.layoutParams as LayoutParams
            layoutParams.width = if (type == SharedConfig.PASSCODE_TYPE_PIN) width / 2 else width
            layoutParams.height = AndroidUtilities.dp(180f)
            layoutParams.topMargin = (height - AndroidUtilities.dp(140f)) / 2 + (if (type == SharedConfig.PASSCODE_TYPE_PIN) AndroidUtilities.dp(40f) else 0)
            passwordFrameLayout.layoutParams = layoutParams

            layoutParams = numbersContainer.layoutParams as LayoutParams
            layoutParams.height = height
            layoutParams.leftMargin = width / 2
            layoutParams.topMargin = height - layoutParams.height + (if (Build.VERSION.SDK_INT >= 21) AndroidUtilities.statusBarHeight else 0)
            layoutParams.width = width / 2
            numbersContainer.layoutParams = layoutParams

            val cols = 3
            val rows = 4
            layoutParams = numbersFrameLayout.layoutParams as LayoutParams
            layoutParams.height = (AndroidUtilities.dp(82f) + buttonSize * rows + sizeBetweenNumbersY * max(0.0, (rows - 1).toDouble())).toInt()
            layoutParams.width = (buttonSize * cols + sizeBetweenNumbersX * max(0.0, (cols - 1).toDouble())).toInt()
            layoutParams.gravity = Gravity.CENTER
            numbersFrameLayout.layoutParams = layoutParams
        } else {
            imageView.translationX = width / 2f - AndroidUtilities.dp(29f)

            var top = AndroidUtilities.statusBarHeight
            var left = 0
            if (AndroidUtilities.isTablet()) {
                if (width > AndroidUtilities.dp(498f)) {
                    left = (width - AndroidUtilities.dp(498f)) / 2
                    width = AndroidUtilities.dp(498f)
                }
                if (height > AndroidUtilities.dp(528f)) {
                    top = (height - AndroidUtilities.dp(528f)) / 2
                    height = AndroidUtilities.dp(528f)
                }
            }
            layoutParams = passwordFrameLayout.layoutParams as LayoutParams
            layoutParams.height = height / 4 + (if (type == SharedConfig.PASSCODE_TYPE_PIN) AndroidUtilities.dp(40f) else 0)
            layoutParams.width = width
            layoutParams.topMargin = top
            layoutParams.leftMargin = left
            passwordFrameLayout.tag = top
            passwordFrameLayout.layoutParams = layoutParams
            val passwordTop = layoutParams.topMargin + layoutParams.height

            val cols = 3
            val rows = 4
            layoutParams = numbersFrameLayout.layoutParams as LayoutParams
            layoutParams.height = (AndroidUtilities.dp(82f) + buttonSize * rows + sizeBetweenNumbersY * max(0.0, (rows - 1).toDouble())).toInt()
            layoutParams.width = (buttonSize * cols + sizeBetweenNumbersX * max(0.0, (cols - 1).toDouble())).toInt()
            if (AndroidUtilities.isTablet()) {
                layoutParams.gravity = Gravity.CENTER
            } else {
                layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            numbersFrameLayout.layoutParams = layoutParams

            val buttonHeight = height - layoutParams.height
            layoutParams = numbersContainer.layoutParams as LayoutParams
            layoutParams.leftMargin = left
            if (AndroidUtilities.isTablet()) {
                layoutParams.topMargin = (height - buttonHeight) / 2
            } else {
                layoutParams.topMargin = passwordTop
            }
            layoutParams.width = width
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            numbersContainer.layoutParams = layoutParams
        }

        val headerMargin = AndroidUtilities.dp((if (landscape) 52 else 82).toFloat())
        for (a in 0..11) {
            val num = when (a) {
                0 -> 10
                10 -> 11
                11 -> 9
                else -> a - 1
            }
            val row = num / 3
            val col = num % 3
            val frameLayout = numberFrameLayouts[a]
            val lp = frameLayout.layoutParams as LayoutParams
            lp.topMargin = headerMargin + (buttonSize + sizeBetweenNumbersY) * row
            lp.leftMargin = (buttonSize + sizeBetweenNumbersX) * col
            frameLayout.layoutParams = lp
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private val pos = IntArray(2)

    init {
        setWillNotDraw(false)
        visibility = GONE

        backgroundFrameLayout = object : FrameLayout(context) {
            private val paint = Paint()

            override fun onDraw(canvas: Canvas) {
                if (backgroundDrawable != null) {
                    if (backgroundDrawable is MotionBackgroundDrawable || backgroundDrawable is ColorDrawable || backgroundDrawable is GradientDrawable) {
                        backgroundDrawable!!.setBounds(0, 0, measuredWidth, measuredHeight)
                        backgroundDrawable!!.draw(canvas)
                    } else {
                        val scaleX = measuredWidth.toFloat() / backgroundDrawable!!.intrinsicWidth.toFloat()
                        val scaleY = (measuredHeight + keyboardHeight).toFloat() / backgroundDrawable!!.intrinsicHeight.toFloat()
                        val scale = max(scaleX.toDouble(), scaleY.toDouble()).toFloat()
                        val w = ceil((backgroundDrawable!!.intrinsicWidth * scale).toDouble()).toInt()
                        val h = ceil((backgroundDrawable!!.intrinsicHeight * scale).toDouble()).toInt()
                        val x = (measuredWidth - w) / 2
                        val y = (measuredHeight - h + keyboardHeight) / 2
                        backgroundDrawable!!.setBounds(x, y, x + w, y + h)
                        backgroundDrawable!!.draw(canvas)
                    }
                } else {
                    super.onDraw(canvas)
                }
                canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), paint)
            }

            override fun setBackgroundColor(color: Int) {
                paint.color = color
            }
        }
        backgroundFrameLayout.setWillNotDraw(false)
        addView(backgroundFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        imageView = RLottieImageView(context)
        imageView.setAnimation(R.raw.passcode_lock, 58, 58)
        imageView.setAutoRepeat(false)
        addView(imageView, LayoutHelper.createFrame(58, 58, Gravity.LEFT or Gravity.TOP))

        passwordFrameLayout = FrameLayout(context)
        backgroundFrameLayout.addView(passwordFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        passcodeTextView = TextView(context)
        passcodeTextView.setTextColor(-0x1)
        passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18.33f)
        passcodeTextView.gravity = Gravity.CENTER_HORIZONTAL
        passcodeTextView.typeface = AndroidUtilities.bold()
        passcodeTextView.alpha = 0f
        passwordFrameLayout.addView(
            passcodeTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0f, 0f, 0f, 128f
            )
        )

        retryTextView = TextView(context)
        retryTextView.setTextColor(-0x1)
        retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        retryTextView.gravity = Gravity.CENTER_HORIZONTAL
        retryTextView.visibility = INVISIBLE
        backgroundFrameLayout.addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

        passwordEditText2 = AnimatingTextView(context)
        passwordFrameLayout.addView(
            passwordEditText2, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 70f, 0f, 70f, 46f
            )
        )

        passwordEditText = EditTextBoldCursor(context)
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36f)
        passwordEditText.setTextColor(-0x1)
        passwordEditText.maxLines = 1
        passwordEditText.setLines(1)
        passwordEditText.gravity = Gravity.CENTER_HORIZONTAL
        passwordEditText.isSingleLine = true
        passwordEditText.imeOptions = EditorInfo.IME_ACTION_DONE
        passwordEditText.typeface = Typeface.DEFAULT
        passwordEditText.setBackgroundDrawable(null)
        passwordEditText.setCursorColor(-0x1)
        passwordEditText.setCursorSize(AndroidUtilities.dp(32f))
        passwordFrameLayout.addView(
            passwordEditText, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 70f, 0f, 70f, 0f
            )
        )
        passwordEditText.setOnEditorActionListener(OnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                processDone(false)
                return@OnEditorActionListener true
            }
            false
        })
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (backgroundDrawable is MotionBackgroundDrawable) {
                    var needAnimation = false
                    val motionBackgroundDrawable = backgroundDrawable as MotionBackgroundDrawable
                    motionBackgroundDrawable.setAnimationProgressProvider(null)
                    val progress = motionBackgroundDrawable.posAnimationProgress
                    val next: Boolean
                    if (count == 0 && after == 1) {
                        motionBackgroundDrawable.switchToNextPosition(true)
                        needAnimation = true
                        next = true
                    } else if (count == 1 && after == 0) {
                        motionBackgroundDrawable.switchToPrevPosition(true)
                        needAnimation = true
                        next = false
                    } else {
                        next = false
                    }

                    if (needAnimation) {
                        if (progress >= 1f) {
                            animateBackground(motionBackgroundDrawable)
                        } else {
                            backgroundSpringQueue.offer(Runnable {
                                if (next) {
                                    motionBackgroundDrawable.switchToNextPosition(true)
                                } else {
                                    motionBackgroundDrawable.switchToPrevPosition(true)
                                }
                                animateBackground(motionBackgroundDrawable)
                            })
                            backgroundSpringNextQueue.offer(next)

                            val remove: MutableList<Runnable> = ArrayList()
                            val removeIndex: MutableList<Int> = ArrayList()
                            for (i in backgroundSpringQueue.indices) {
                                val callback = backgroundSpringQueue[i]
                                val qNext = backgroundSpringNextQueue[i]
                                if (qNext != next) {
                                    remove.add(callback)
                                    removeIndex.add(i)
                                }
                            }
                            for (callback in remove) {
                                backgroundSpringQueue.remove(callback)
                            }
                            Collections.sort(removeIndex) { o1, o2 -> o2 - o1 }
                            for (i in removeIndex) {
                                if (i < backgroundSpringNextQueue.size) {
                                    backgroundSpringNextQueue.removeAt(i)
                                }
                            }
                        }
                    }
                }
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                if (passwordEditText.length() == 4 && type == SharedConfig.PASSCODE_TYPE_PIN) {
                    processDone(false)
                }
            }
        })
        passwordEditText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) {}
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
        }

        checkImage = ImageView(context)
        checkImage.setImageResource(R.drawable.passcode_check)
        checkImage.scaleType = ImageView.ScaleType.CENTER
        checkImage.setBackgroundResource(R.drawable.bar_selector_lock)
        passwordFrameLayout.addView(
            checkImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE.toFloat(), Gravity.BOTTOM or Gravity.RIGHT, 0f, 0f, 10f, 4f)
        )
        checkImage.contentDescription = LocaleController.getString(R.string.Done)
        checkImage.setOnClickListener { processDone(false) }

        fingerprintImage = ImageView(context)
        fingerprintImage.setImageResource(R.drawable.fingerprint)
        fingerprintImage.scaleType = ImageView.ScaleType.CENTER
        fingerprintImage.setBackgroundResource(R.drawable.bar_selector_lock)
        passwordFrameLayout.addView(
            fingerprintImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 10f, 0f, 0f, 4f)
        )
        fingerprintImage.contentDescription = LocaleController.getString(R.string.AccDescrFingerprint)
        fingerprintImage.setOnClickListener { checkFingerprint() }

        border = View(context)
        border.setBackgroundColor(0x30FFFFFF)
        passwordFrameLayout.addView(
            border, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT.toFloat(), 1f / AndroidUtilities.density,
                Gravity.FILL_HORIZONTAL or Gravity.BOTTOM
            )
        )

        numbersContainer = FrameLayout(context)
        backgroundFrameLayout.addView(numbersContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

        numbersFrameLayout = object : FrameLayout(context) {
            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                if (parent is View) {
                    val parentHeight = (parent as View).height
                    val h = height
                    val scale = min((parentHeight.toFloat() / h).toDouble(), 1.0).toFloat()
                    pivotX = width / 2f
                    pivotY = if ((layoutParams as LayoutParams).gravity == Gravity.CENTER) getHeight() / 2f else 0f
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
        numbersContainer.addView(numbersFrameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

        numbersTitleContainer = FrameLayout(context)
        numbersFrameLayout.addView(numbersTitleContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val title = TextView(context)
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        title.typeface = AndroidUtilities.bold()
        title.gravity = Gravity.CENTER_HORIZONTAL
        title.setTextColor(-0x1)
        title.text = LocaleController.getString(R.string.UnlockToUse)
        numbersTitleContainer.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 0f, 0f, 0f))

        subtitleView = TextView(context)
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        subtitleView.setTextColor(-0x1)
        subtitleView.text = LocaleController.getString(R.string.EnterPINorFingerprint)
        numbersTitleContainer.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 40f, 0f, 0f))

        numberFrameLayouts = ArrayList(12)
        for (a in 0..11) {
            val frameLayout = PasscodeButton(context)
            ScaleStateListAnimator.apply(frameLayout, .15f, 1.5f)
            frameLayout.tag = a
            if (a == 11) {
                frameLayout.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(30f), 0, 0x26ffffff)
                frameLayout.setImage(R.drawable.filled_clear)
                frameLayout.setOnLongClickListener {
                    passwordEditText.setText("")
                    passwordEditText2.eraseAllCharacters(true)
                    if (backgroundDrawable is MotionBackgroundDrawable) {
                        (backgroundDrawable as MotionBackgroundDrawable).switchToPrevPosition(true)
                    }
                    true
                }
                frameLayout.contentDescription = LocaleController.getString(R.string.AccDescrBackspace)
                setNextFocus(frameLayout, R.id.passcode_btn_0)
            } else if (a == 10) {
                fingerprintView = frameLayout
                frameLayout.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(30f), 0, 0x26ffffff)
                frameLayout.contentDescription = LocaleController.getString(R.string.AccDescrFingerprint)
                frameLayout.setImage(R.drawable.fingerprint)
                setNextFocus(frameLayout, R.id.passcode_btn_1)
            } else {
                frameLayout.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(30f), 0x26ffffff, 0x4cffffff)
                frameLayout.contentDescription = a.toString()
                frameLayout.setNum(a)
                when (a) {
                    0 -> setNextFocus(frameLayout, R.id.passcode_btn_backspace)
                    9 -> if (hasFingerprint()) setNextFocus(frameLayout, R.id.passcode_btn_fingerprint) else setNextFocus(frameLayout, R.id.passcode_btn_0)
                    else -> setNextFocus(frameLayout, ids[a + 1])
                }
            }
            frameLayout.id = ids[a]
            frameLayout.setOnClickListener { v ->
                if (!pinShown) return@setOnClickListener
                val tag = v.tag as Int
                var erased = false
                when (tag) {
                    0 -> passwordEditText2.appendCharacter("0")
                    1 -> passwordEditText2.appendCharacter("1")
                    2 -> passwordEditText2.appendCharacter("2")
                    3 -> passwordEditText2.appendCharacter("3")
                    4 -> passwordEditText2.appendCharacter("4")
                    5 -> passwordEditText2.appendCharacter("5")
                    6 -> passwordEditText2.appendCharacter("6")
                    7 -> passwordEditText2.appendCharacter("7")
                    8 -> passwordEditText2.appendCharacter("8")
                    9 -> passwordEditText2.appendCharacter("9")
                    10 -> checkFingerprint()
                    11 -> erased = passwordEditText2.eraseLastCharacter()
                }
                if (passwordEditText2.length() == 4) {
                    processDone(false)
                }
                if (tag != 11 && backgroundDrawable is MotionBackgroundDrawable) {
                    val motionBackgroundDrawable = backgroundDrawable as MotionBackgroundDrawable
                    motionBackgroundDrawable.setAnimationProgressProvider(null)
                    var needAnimation = false
                    val progress = motionBackgroundDrawable.posAnimationProgress
                    val next: Boolean
                    if (tag == 10) {
                        if (erased) {
                            motionBackgroundDrawable.switchToPrevPosition(true)
                            needAnimation = true
                        }
                        next = false
                    } else {
                        motionBackgroundDrawable.switchToNextPosition(true)
                        needAnimation = true
                        next = true
                    }

                    if (needAnimation) {
                        if (progress >= 1f) {
                            animateBackground(motionBackgroundDrawable)
                        } else {
                            backgroundSpringQueue.offer(Runnable {
                                if (next) {
                                    motionBackgroundDrawable.switchToNextPosition(true)
                                } else {
                                    motionBackgroundDrawable.switchToPrevPosition(true)
                                }
                                animateBackground(motionBackgroundDrawable)
                            })
                            backgroundSpringNextQueue.offer(next)

                            val remove: MutableList<Runnable> = ArrayList()
                            val removeIndex: MutableList<Int> = ArrayList()
                            for (i in backgroundSpringQueue.indices) {
                                val callback = backgroundSpringQueue[i]
                                val qNext = backgroundSpringNextQueue[i]
                                if (qNext != next) {
                                    remove.add(callback)
                                    removeIndex.add(i)
                                }
                            }
                            for (callback in remove) {
                                backgroundSpringQueue.remove(callback)
                            }
                            Collections.sort(removeIndex) { o1, o2 -> o2 - o1 }
                            for (i in removeIndex) {
                                backgroundSpringNextQueue.removeAt(i)
                            }
                        }
                    }
                }
            }
            numberFrameLayouts.add(frameLayout)
        }
        for (a in 11 downTo 0) {
            val frameLayout = numberFrameLayouts[a]
            numbersFrameLayout.addView(frameLayout, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.TOP or Gravity.LEFT))
        }
        checkFingerprintButton()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val rootView = rootView
        val usableViewHeight = rootView.height - AndroidUtilities.statusBarHeight - AndroidUtilities.getViewInset(rootView)
        getWindowVisibleDisplayFrame(rect)
        keyboardHeight = usableViewHeight - (rect.bottom - rect.top)

        if (type == SharedConfig.PASSCODE_TYPE_PASSWORD && (AndroidUtilities.isTablet() || context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE)) {
            var t = 0
            if (passwordFrameLayout.tag != null) {
                t = passwordFrameLayout.tag as Int
            }
            val layoutParams = passwordFrameLayout.layoutParams as LayoutParams
            layoutParams.topMargin = t + layoutParams.height - keyboardHeight / 2 - (if (Build.VERSION.SDK_INT >= 21) AndroidUtilities.statusBarHeight else 0)
            passwordFrameLayout.layoutParams = layoutParams
        }

        super.onLayout(changed, left, top, right, bottom)

        val fixedImageY = AndroidUtilities.dp(80f).toFloat()
        imageView.translationY = fixedImageY.also { imageY = it.toInt() }
    }

    class PasscodeButton(context: Context) : FrameLayout(context) {
        private val imageView = ImageView(context)
        private val textView1: TextView
        private val textView2: TextView

        init {
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.fingerprint)
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL))

            textView1 = TextView(context)
            textView1.typeface = AndroidUtilities.bold()
            textView1.setTextColor(-0x1)
            textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26f)
            textView1.gravity = Gravity.CENTER
            addView(textView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, -5.33f, 0f, 0f))

            textView2 = TextView(context)
            textView2.typeface = AndroidUtilities.bold()
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
            textView2.setTextColor(0x7fffffff)
            textView2.gravity = Gravity.CENTER
            addView(textView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 14f, 0f, 0f))
        }

        fun setImage(resId: Int) {
            imageView.visibility = VISIBLE
            textView1.visibility = GONE
            textView2.visibility = GONE
            imageView.setImageResource(resId)
        }

        fun setNum(num: Int) {
            imageView.visibility = GONE
            textView1.visibility = VISIBLE
            textView2.visibility = VISIBLE
            textView1.text = "" + num
            textView2.text = letter(num)
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.Button"
        }

        companion object {
            fun letter(num: Int): String = when (num) {
                0 -> "+"
                2 -> "ABC"
                3 -> "DEF"
                4 -> "GHI"
                5 -> "JKL"
                6 -> "MNO"
                7 -> "PQRS"
                8 -> "TUV"
                9 -> "WXYZ"
                else -> ""
            }
        }
    }

    companion object {
        private const val BACKGROUND_SPRING_STIFFNESS = 300f

        @IdRes
        private val ids = intArrayOf(
            R.id.passcode_btn_0,
            R.id.passcode_btn_1,
            R.id.passcode_btn_2,
            R.id.passcode_btn_3,
            R.id.passcode_btn_4,
            R.id.passcode_btn_5,
            R.id.passcode_btn_6,
            R.id.passcode_btn_7,
            R.id.passcode_btn_8,
            R.id.passcode_btn_9,
            R.id.passcode_btn_backspace,
            R.id.passcode_btn_fingerprint
        )
    }
}
