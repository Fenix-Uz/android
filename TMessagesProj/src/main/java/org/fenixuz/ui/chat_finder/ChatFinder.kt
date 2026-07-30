package org.fenixuz.ui.chat_finder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.fenixuz.utils.LanguageCode
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import java.util.Locale

/**
 * Chat finder — type a username, check if it resolves to a user/chat, and open it.
 * Native Telegram look: seamless (transparent) toolbar, themed views, debounced lookup.
 */
class ChatFinder : BaseFragment() {

    private lateinit var progressDialog: AlertDialog
    private var usernameField: EditTextBoldCursor? = null
    private var statusView: TextView? = null
    private var openButton: TextView? = null

    private var resolvedUsername = ""
    private var available = false

    private val handler = Handler(Looper.getMainLooper())
    private val lookupRunnable = Runnable { resolveUsername() }

    override fun createView(context: Context): View {
        progressDialog = AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER)

        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LanguageCode.getMyTitles(123))
        actionBar.setCastShadows(false) // seamless / transparent-looking toolbar
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
            }
        })

        val scroll = ScrollView(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            isFillViewport = true
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(AndroidUtilities.dp(22f), AndroidUtilities.dp(8f), AndroidUtilities.dp(22f), AndroidUtilities.dp(22f))
        }
        scroll.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))

        val animation = RLottieImageView(context).apply {
            setAutoRepeat(true)
            setAnimation(R.raw.utyan_schedule, 130, 130)
            playAnimation()
        }
        content.addView(animation, LayoutHelper.createLinear(140, 140, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 12))

        val hint = TextView(context).apply {
            text = LanguageCode.getMyTitles(124)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            textSize = 15f
            gravity = Gravity.CENTER
        }
        content.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 22))

        usernameField = EditTextBoldCursor(context).apply {
            setHintText(LanguageCode.getMyTitles(178))
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))
            setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setCursorSize(AndroidUtilities.dp(20f))
            setCursorWidth(1.5f)
            textSize = 17f
            setBackgroundDrawable(Theme.createEditTextDrawable(context, true))
            maxLines = 1
            filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
                if (source != null && source.contains(" ")) "" else null
            })
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    handler.removeCallbacks(lookupRunnable)
                    val name = s?.toString()?.trim().orEmpty()
                    if (name.length >= 4) {
                        handler.postDelayed(lookupRunnable, 350)
                    } else {
                        setUnavailable("")
                    }
                }
            })
        }
        content.addView(usernameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16))

        statusView = TextView(context).apply {
            textSize = 15f
            gravity = Gravity.CENTER
        }
        content.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 22))

        openButton = TextView(context).apply {
            text = LocaleController.getString(R.string.Open)
            setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f))
            setOnClickListener { openResolvedChat() }
        }
        content.addView(openButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0))

        setUnavailable("")

        fragmentView = scroll
        return scroll
    }

    // Blend the bottom system navigation bar into the white page (it defaults to grey, which leaves a seam) —
    // the same trick Telegram uses on its own white screens.
    override fun getNavigationBarColor(): Int = Theme.getColor(Theme.key_windowBackgroundWhite)

    private fun setUnavailable(typed: String) {
        available = false
        resolvedUsername = ""
        statusView?.text = LanguageCode.getMyTitles(179) + if (typed.isNotEmpty()) "  @$typed" else ""
        statusView?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
        updateOpenButton()
    }

    private fun setAvailable(username: String) {
        available = true
        resolvedUsername = username
        statusView?.text = LanguageCode.getMyTitles(180) + "  @$username"
        statusView?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText))
        updateOpenButton()
    }

    private fun updateOpenButton() {
        val btn = openButton ?: return
        val color = if (available) Theme.getColor(Theme.key_featuredStickers_addButton)
        else Theme.getColor(Theme.key_picker_disabledButton)
        btn.background = Theme.createSimpleSelectorRoundRectDrawable(
            AndroidUtilities.dp(16f), color,
            Theme.getColor(Theme.key_featuredStickers_addButtonPressed)
        )
        btn.isEnabled = available
    }

    private fun openResolvedChat() {
        if (available && resolvedUsername.isNotEmpty()) {
            Browser.openUrl(parentActivity, "https://t.me/$resolvedUsername")
        }
    }

    private fun resolveUsername() {
        val name = usernameField?.text?.toString()?.lowercase(Locale.getDefault())?.trim().orEmpty()
        if (name.length < 4) return
        progressDialog.show()
        val req = TLRPC.TL_contacts_resolveUsername()
        req.username = name
        ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response: TLObject?, error: TLRPC.TL_error? ->
            AndroidUtilities.runOnUIThread {
                progressDialog.dismiss()
                if (error == null && response is TLRPC.TL_contacts_resolvedPeer) {
                    val found = extractUsername(response)
                    if (!found.isNullOrEmpty()) setAvailable(found) else setUnavailable(name)
                } else {
                    setUnavailable(name)
                }
            }
        }
    }

    private fun extractUsername(res: TLRPC.TL_contacts_resolvedPeer): String? {
        if (res.users.isNotEmpty()) {
            val user = res.users[0]
            if (!user.username.isNullOrEmpty()) return user.username
            user.usernames?.firstOrNull { it?.active == true }?.let { return it.username }
        } else if (res.chats.isNotEmpty()) {
            val chat = res.chats[0]
            if (!chat.username.isNullOrEmpty()) return chat.username
            chat.usernames?.firstOrNull { it?.active == true }?.let { return it.username }
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        usernameField?.requestFocus()
        AndroidUtilities.showKeyboard(usernameField)
    }

    override fun onPause() {
        super.onPause()
        progressDialog.dismiss()
    }

    override fun onFragmentDestroy() {
        handler.removeCallbacks(lookupRunnable)
        progressDialog.dismiss()
        super.onFragmentDestroy()
    }
}
