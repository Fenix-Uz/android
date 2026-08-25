package org.fenixuz.ui.chat_preview

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fenixuz.utils.ChatPreviewState
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.URLSpanNoUnderline
import org.telegram.ui.PhotoViewer

/**
 * Ghost chat preview shown on long-press of a dialog avatar.
 *
 * Opens the chat's messages in a bottom sheet where the user can read text, view photos/videos and
 * listen to voice/round-video — WITHOUT sending any read receipt to the other side:
 *  - the general "read" (blue checks) is never sent because we only [MessagesController.loadMessages]
 *    and never call markDialogAsRead;
 *  - the "listened/watched" content-read (voice & round video) is suppressed via [ChatPreviewState],
 *    which the single `markMessageContentAsRead` core hook consults while this sheet is open.
 *
 * Senior cleanups vs. the original heavy version:
 *  - a single message observer handles the initial load AND live updates (no redundant temp observer);
 *  - the ≤100-item merge/sort runs on the UI thread (trivial cost) instead of bouncing through a
 *    background queue, removing a thread hop and a class of races;
 *  - every observer is unregistered and the message list is cleared on [dismiss] so nothing leaks.
 */
class ChatPreviewBottomSheet(
    context: Context,
    private val dialogId: Long,
    private val activity: Activity
) : BottomSheet(context, true) {

    private val currentAccount = UserConfig.selectedAccount
    private val classGuid = ConnectionsManager.generateClassGuid()

    private val messages = ArrayList<MessageObject>()
    private val messageIds = HashSet<Int>() // O(1) dedup so full-history paging never goes quadratic
    private var initialLoaded = false
    private var loadingMore = false
    private var reachedBeginning = false

    private lateinit var listView: RecyclerListView
    private lateinit var previewAdapter: PreviewAdapter

    private var containerFrame: FrameLayout? = null
    private var progressView: RadialProgressView? = null
    private var avatarImageView: BackupImageView? = null
    private var nameTextView: SimpleTextView? = null
    private var subtitleTextView: SimpleTextView? = null

    private var emptyViewContainer: FrameLayout? = null
    private var stickerImageView: BackupImageView? = null

    private val audioDelegate: NotificationCenter.NotificationCenterDelegate
    private val messagesDelegate: NotificationCenter.NotificationCenterDelegate

    init {
        // Mark this dialog as "ghost" so the core read-receipt hook suppresses content-read for it.
        ChatPreviewState.setActive(dialogId)

        setupContainer()
        setupEmptyView()
        setupListView()
        setupHeader()
        setupProgressView()

        audioDelegate = createAudioDelegate()
        messagesDelegate = createMessagesDelegate()
        registerObservers()

        setUserInfo()
        loadMessages()
    }

    private fun setupContainer() {
        containerFrame = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxHeight = (AndroidUtilities.displaySize.y * 0.7f).toInt()
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY))
            }
        }
        containerFrame?.clipToOutline = true
        containerFrame?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val r = AndroidUtilities.dp(14f)
                outline.setRoundRect(0, 0, view.width, view.height + r, r.toFloat())
            }
        }

        val wallpaperDrawable = Theme.getCachedWallpaperNonBlocking()
        if (wallpaperDrawable != null) {
            containerFrame?.background = wallpaperDrawable
        } else {
            Theme.loadWallpaper(true)
            containerFrame?.setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper))
        }

        setCustomView(containerFrame)
        setAllowNestedScroll(true)
        setApplyTopPadding(false)
    }

    private fun setupEmptyView() {
        emptyViewContainer = FrameLayout(context).apply {
            visibility = View.GONE
            background = Theme.createRoundRectDrawable(
                AndroidUtilities.dp(20f),
                Theme.getColor(Theme.key_chat_serviceBackground)
            )
            setPadding(AndroidUtilities.dp(20f), AndroidUtilities.dp(20f), AndroidUtilities.dp(20f), AndroidUtilities.dp(20f))
        }

        val innerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val titleView = TextView(context).apply {
            text = LocaleController.getString(R.string.NoMessages)
            textSize = 13f
            setTextColor(Theme.getColor(Theme.key_chat_serviceText))
            gravity = Gravity.CENTER
        }

        val subtitleView = TextView(context).apply {
            text = LocaleController.getString(R.string.NoMessagesGreetingsDescription)
            textSize = 14f
            setTextColor(Theme.getColor(Theme.key_chat_serviceText))
            gravity = Gravity.CENTER
            setPadding(0, AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(20f))
        }

        stickerImageView = BackupImageView(context).apply {
            setAspectFit(true)
            setOnClickListener { sendGreetingsSticker() }
        }

        innerLayout.addView(titleView)
        innerLayout.addView(subtitleView)
        innerLayout.addView(stickerImageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER))

        emptyViewContainer?.addView(innerLayout)
        containerFrame?.addView(emptyViewContainer, LayoutHelper.createFrame(210, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun setupListView() {
        previewAdapter = PreviewAdapter()
        listView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            isNestedScrollingEnabled = true
            adapter = previewAdapter
            setPadding(0, AndroidUtilities.dp(58f), 0, AndroidUtilities.dp(8f))
            clipToPadding = false
            visibility = View.INVISIBLE
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            setFastScrollVisible(true)
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
        }
        containerFrame?.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        // Pagination: load older messages as the user scrolls toward the top, so the full history
        // (start -> end) becomes reachable. loadMessages is async -> no main-thread work here.
        listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy >= 0 || loadingMore || reachedBeginning || !initialLoaded) return
                val lm = listView.layoutManager as LinearLayoutManager
                if (lm.findFirstVisibleItemPosition() <= LOAD_MORE_THRESHOLD) {
                    loadMoreOlder()
                }
            }
        })
    }

    private fun setupHeader() {
        val headerLayout = object : FrameLayout(context) {
            override fun onDraw(canvas: Canvas) {
                // Messages scroll underneath this header (the list keeps a 58dp top padding with
                // clipToPadding = false), so whatever alpha is left here is filled in by moving message
                // bubbles and the chat wallpaper. At the original 80% that was enough to wash the name and
                // the online line out on busy wallpapers - keep the layered look, but only just.
                val color = Theme.getColor(Theme.key_windowBackgroundWhite)
                canvas.drawColor((color and 0x00ffffff) or HEADER_SCRIM_ALPHA)
                super.onDraw(canvas)
            }
        }
        headerLayout.setWillNotDraw(false)

        avatarImageView = BackupImageView(context).apply { setRoundRadius(AndroidUtilities.dp(21f)) }
        headerLayout.addView(
            avatarImageView,
            LayoutHelper.createFrame(42, 42f, Gravity.LEFT or Gravity.CENTER_VERTICAL, 16f, 0f, 0f, 0f)
        )

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nameTextView = SimpleTextView(context).apply {
            setTextSize(17)
            setTypeface(Typeface.DEFAULT_BOLD)
            // Must be the key that pairs with the header's windowBackgroundWhite base. chats_menuItemText
            // belongs to the navigation drawer, and themes that give the drawer a dark photo background set
            // it to white - which rendered the name white-on-white here.
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        }
        subtitleTextView = SimpleTextView(context).apply {
            setTextSize(14)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        }
        textLayout.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        textLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 2f, 0f, 0f))

        headerLayout.addView(
            textLayout,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.CENTER_VERTICAL, 70f, 0f, 0f, 0f)
        )
        containerFrame?.addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58f))
    }

    private fun setupProgressView() {
        progressView = RadialProgressView(context).apply {
            setProgressColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            setSize(AndroidUtilities.dp(60f))
        }
        containerFrame?.addView(progressView, LayoutHelper.createFrame(70, 70, Gravity.CENTER))
    }

    private fun registerObservers() {
        val nc = NotificationCenter.getInstance(currentAccount)
        nc.addObserver(audioDelegate, NotificationCenter.messagePlayingProgressDidChanged)
        nc.addObserver(audioDelegate, NotificationCenter.messagePlayingPlayStateChanged)
        nc.addObserver(audioDelegate, NotificationCenter.messagePlayingDidSeek)
        nc.addObserver(messagesDelegate, NotificationCenter.messagesDidLoad)
        nc.addObserver(messagesDelegate, NotificationCenter.didReceiveNewMessages)
        nc.addObserver(messagesDelegate, NotificationCenter.replaceMessagesObjects)
        nc.addObserver(messagesDelegate, NotificationCenter.updateInterfaces)
        nc.addObserver(messagesDelegate, NotificationCenter.chatInfoDidLoad)
        nc.addObserver(messagesDelegate, NotificationCenter.userInfoDidLoad)
    }

    private fun createAudioDelegate() = NotificationCenter.NotificationCenterDelegate { id, _, args ->
        if (id == NotificationCenter.messagePlayingProgressDidChanged ||
            id == NotificationCenter.messagePlayingPlayStateChanged ||
            id == NotificationCenter.messagePlayingDidSeek
        ) {
            val messageId = (args[0] as Number).toInt()
            val playing = MediaController.getInstance().playingMessageObject
            if (playing != null && playing.id == messageId) {
                updatePlayingCell(messageId, playing)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createMessagesDelegate() = NotificationCenter.NotificationCenterDelegate { id, _, args ->
        when (id) {
            NotificationCenter.messagesDidLoad -> {
                // classGuid is unique to THIS sheet's loadMessages calls, so we only react to our own
                // loads (initial + pagination), never to background/ChatActivity loads of the same dialog.
                if (args[10] as Int == classGuid) {
                    val loaded = args[2] as ArrayList<MessageObject>
                    val isEnd = args[9] as Boolean
                    if (!initialLoaded) handleInitialLoad(loaded, isEnd)
                    else handlePaginationLoad(loaded, isEnd)
                }
            }
            NotificationCenter.didReceiveNewMessages -> {
                if (args[0] as Long == dialogId) {
                    mergeMessages(args[1] as ArrayList<MessageObject>)
                }
            }
            NotificationCenter.replaceMessagesObjects -> {
                if (args[0] as Long == dialogId) {
                    mergeMessages(args[1] as ArrayList<MessageObject>)
                }
            }
            NotificationCenter.updateInterfaces -> {
                val mask = args[0] as? Int ?: 0
                if (mask and MessagesController.UPDATE_MASK_STATUS != 0 ||
                    mask and MessagesController.UPDATE_MASK_USER_PRINT != 0 ||
                    mask and MessagesController.UPDATE_MASK_CHAT_MEMBERS != 0
                ) {
                    updateSubtitle()
                }
            }
            NotificationCenter.chatInfoDidLoad, NotificationCenter.userInfoDidLoad -> updateSubtitle()
        }
    }

    private fun handleInitialLoad(loaded: ArrayList<MessageObject>, isEnd: Boolean) {
        initialLoaded = true
        reachedBeginning = isEnd
        loadingMore = false
        progressView?.visibility = View.GONE
        messages.clear()
        messageIds.clear()
        for (obj in loaded) {
            val action = obj.messageOwner?.action
            if (action == null || action is TLRPC.TL_messageActionEmpty) {
                if (messageIds.add(obj.id)) messages.add(obj)
            }
        }
        if (messages.isNotEmpty()) {
            messages.sortBy { it.messageOwner.date }
            emptyViewContainer?.visibility = View.GONE
            listView.visibility = View.VISIBLE
            previewAdapter.notifyDataSetChanged()
            listView.scrollToPosition(messages.size - 1)
        } else {
            listView.visibility = View.GONE
            showGreetingsEmptyView()
        }
    }

    /**
     * Prepend a page of older messages while keeping the user's exact scroll anchor, so paging up reads
     * as one continuous list with no jump. notifyDataSetChanged only rebinds the ~10 visible cells.
     */
    private fun handlePaginationLoad(loaded: ArrayList<MessageObject>, isEnd: Boolean) {
        loadingMore = false
        if (isEnd) reachedBeginning = true
        if (loaded.isEmpty()) {
            reachedBeginning = true
            return
        }

        val lm = listView.layoutManager as LinearLayoutManager
        val anchorPos = lm.findFirstVisibleItemPosition()
        val anchorOffset = if (anchorPos == RecyclerView.NO_POSITION) 0 else (lm.findViewByPosition(anchorPos)?.top ?: 0)
        val oldSize = messages.size

        for (obj in loaded) {
            val action = obj.messageOwner?.action
            if (action != null && action !is TLRPC.TL_messageActionEmpty) continue
            if (messageIds.add(obj.id)) messages.add(obj)
        }
        val added = messages.size - oldSize
        if (added == 0) return

        messages.sortBy { it.messageOwner.date }
        previewAdapter.notifyDataSetChanged()
        if (anchorPos != RecyclerView.NO_POSITION) {
            lm.scrollToPositionWithOffset(anchorPos + added, anchorOffset)
        }
    }

    private fun loadMoreOlder() {
        if (loadingMore || reachedBeginning) return
        val oldest = messages.firstOrNull() ?: return
        loadingMore = true
        // load_type 0 + max_id = oldest loaded id -> messages older than it; fromCache=true prefers cache.
        MessagesController.getInstance(currentAccount).loadMessages(
            dialogId, 0, false, PAGE_SIZE, oldest.id, 0, true, 0, classGuid, 0, 0, 0, 0, 0, 0, false
        )
    }

    /** Merge live new/replaced messages. Runs on the UI thread — a ≤100-item dedup+sort is negligible. */
    private fun mergeMessages(newMsgs: ArrayList<MessageObject>) {
        if (listView.adapter == null) return
        if (newMsgs.isEmpty()) return
        var added = 0
        for (obj in newMsgs) {
            if (messageIds.add(obj.id)) {
                messages.add(obj); added++
            } else {
                val index = messages.indexOfFirst { it.id == obj.id }
                if (index != -1) messages[index] = obj
            }
        }
        messages.sortBy { it.messageOwner.date }
        previewAdapter.notifyDataSetChanged()
        if (added > 0) {
            val lm = listView.layoutManager as LinearLayoutManager
            if (lm.findLastCompletelyVisibleItemPosition() >= messages.size - added - 2) {
                listView.smoothScrollToPosition(messages.size - 1)
            }
        }
        if (messages.isNotEmpty() && emptyViewContainer?.visibility == View.VISIBLE) {
            emptyViewContainer?.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
    }

    private fun updatePlayingCell(messageId: Int, playing: MessageObject) {
        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i)
            if (child is ChatMessageCell && child.messageObject?.id == messageId) {
                child.messageObject.audioProgress = playing.audioProgress
                child.messageObject.audioProgressSec = playing.audioProgressSec
                child.updateButtonState(false, true, false)
                child.invalidate()
            }
        }
    }

    private fun showGreetingsEmptyView() {
        val document = MediaDataController.getInstance(currentAccount).greetingsSticker
        if (document != null) {
            stickerImageView?.setImage(
                ImageLocation.getForDocument(document), "120_120", "tgs", null as Drawable?, document
            )
            emptyViewContainer?.visibility = View.VISIBLE
        } else {
            stickerImageView?.visibility = View.GONE
            emptyViewContainer?.visibility = View.VISIBLE
        }
    }

    private fun sendGreetingsSticker() {
        val document = MediaDataController.getInstance(currentAccount).greetingsSticker
        if (document is TLRPC.TL_document) {
            val params = SendMessagesHelper.SendMessageParams.of(
                document, null, null, dialogId, null, null, null, null, null, null,
                true, 0, 0, 0, null, null, false
            )
            SendMessagesHelper.getInstance(currentAccount).sendMessage(params)
            stickerImageView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            dismiss()
        }
    }

    private fun setUserInfo() {
        val mc = MessagesController.getInstance(currentAccount)
        val user = mc.getUser(dialogId)
        val chat = if (user == null) mc.getChat(-dialogId) else null
        if (user != null) {
            processNameAndStatus(UserObject.getUserName(user) ?: "", user, null)
            avatarImageView?.setForUserOrChat(user, AvatarDrawable(user))
            mc.loadUserInfo(user, true, classGuid)
        } else if (chat != null) {
            processNameAndStatus(chat.title ?: "", null, chat)
            avatarImageView?.setForUserOrChat(chat, AvatarDrawable(chat))
            mc.loadFullChat(chat.id, classGuid, true)
        }
        updateSubtitle()
    }

    private fun processNameAndStatus(name: String, user: TLRPC.User?, chat: TLRPC.Chat?) {
        val spannable = SpannableString("$name  ")
        var hasStatus = false
        if (user != null) {
            val emojiStatus = user.emoji_status
            if (emojiStatus is TLRPC.TL_emojiStatus) {
                val span = AnimatedEmojiSpan(emojiStatus.document_id, nameTextView?.paint?.fontMetricsInt)
                spannable.setSpan(span, spannable.length - 1, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                hasStatus = true
            } else if (user.premium) {
                val star = context.resources.getDrawable(R.drawable.msg_premium_normal).mutate()
                star.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_premiumGradient1), PorterDuff.Mode.SRC_IN)
                star.setBounds(0, 0, AndroidUtilities.dp(18f), AndroidUtilities.dp(18f))
                spannable.setSpan(ImageSpan(star, ImageSpan.ALIGN_BOTTOM), spannable.length - 1, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                hasStatus = true
            }
        } else if (chat != null && chat.verified) {
            val verified = context.resources.getDrawable(R.drawable.verified_area).mutate()
            verified.setBounds(0, 0, AndroidUtilities.dp(18f), AndroidUtilities.dp(18f))
            spannable.setSpan(ImageSpan(verified, ImageSpan.ALIGN_BOTTOM), spannable.length - 1, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            hasStatus = true
        }
        nameTextView?.text = if (hasStatus) spannable else name
    }

    private fun updateSubtitle() {
        try {
            val mc = MessagesController.getInstance(currentAccount)
            val printString: CharSequence? = mc.getPrintingString(dialogId, 0L, false)
            val subtitleText: CharSequence
            var useOnlineColor = false

            if (!TextUtils.isEmpty(printString ?: "")) {
                subtitleText = TextUtils.replace(printString, arrayOf("..."), arrayOf(""))
                useOnlineColor = true
            } else {
                val user = mc.getUser(dialogId)
                val chat = if (user == null) mc.getChat(-dialogId) else null
                if (user != null) {
                    subtitleText = when {
                        user.id == UserConfig.getInstance(currentAccount).clientUserId -> LocaleController.getString(R.string.ChatYourSelf)
                        user.id == 333000L || user.id == 777000L || user.id == 42777L -> LocaleController.getString(R.string.ServiceNotifications)
                        MessagesController.isSupportUser(user) -> LocaleController.getString(R.string.SupportStatus)
                        user.bot -> LocaleController.getString(R.string.Bot)
                        else -> {
                            val isOnline = BooleanArray(1)
                            val s = LocaleController.formatUserStatus(currentAccount, user, isOnline, null) ?: ""
                            useOnlineColor = isOnline[0]
                            s
                        }
                    }
                } else if (chat != null) {
                    var onlineCount = 0
                    val info = mc.getChatFull(chat.id)
                    if (info != null) {
                        val currentTime = ConnectionsManager.getInstance(currentAccount).currentTime
                        val selfId = UserConfig.getInstance(currentAccount).clientUserId
                        val participantsList = info.participants?.participants
                        if (info is TLRPC.TL_channelFull && info.participants_count > 200) {
                            onlineCount = info.online_count
                        } else if (participantsList != null) {
                            for (p in participantsList) {
                                if (p == null) continue
                                val pUser = mc.getUser(p.user_id) ?: continue
                                val status = pUser.status ?: continue
                                if ((status.expires > currentTime || pUser.id == selfId) && status.expires > 10000) {
                                    onlineCount++
                                }
                            }
                        }
                    }
                    subtitleText = ChatAvatarContainer.getChatSubtitle(chat, info, onlineCount) ?: ""
                } else {
                    subtitleText = ""
                }
            }

            subtitleTextView?.text = subtitleText
            subtitleTextView?.setTextColor(Theme.getColor(if (useOnlineColor) Theme.key_chat_status else Theme.key_windowBackgroundWhiteGrayText))
            subtitleTextView?.visibility = if (TextUtils.isEmpty(subtitleText)) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            subtitleTextView?.visibility = View.GONE
        }
    }

    private fun loadMessages() {
        // load_type/mode all 0, no markDialogAsRead -> fetch only, never sends the "read" receipt.
        MessagesController.getInstance(currentAccount).loadMessages(
            dialogId, 0, true, COUNT_TO_LOAD, 0, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 0, false
        )
    }

    private inner class PreviewAdapter : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean = false
        override fun getItemCount(): Int = messages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListView.Holder {
            val cell = ChatMessageCell(context, currentAccount)
            cell.setDelegate(object : ChatMessageCell.ChatMessageCellDelegate {
                override fun canPerformActions(): Boolean = true

                override fun didPressImage(cell: ChatMessageCell?, x: Float, y: Float, fullPreview: Boolean) {
                    val message = cell?.messageObject ?: return
                    if (!message.isPhoto && !message.isVideo) return
                    PhotoViewer.getInstance().setParentActivity(activity)
                    PhotoViewer.getInstance().openPhoto(
                        ArrayList(messages), messages.indexOf(message), 0, 0, 0,
                        object : PhotoViewer.EmptyPhotoViewerProvider() {
                            override fun getPlaceForPhoto(
                                messageObject: MessageObject?,
                                fileLocation: TLRPC.FileLocation?,
                                index: Int,
                                needPreview: Boolean,
                                closing: Boolean
                            ): PhotoViewer.PlaceProviderObject? {
                                for (i in 0 until listView.childCount) {
                                    val view = listView.getChildAt(i)
                                    if (view is ChatMessageCell && view.messageObject?.id == messageObject?.id) {
                                        val imageReceiver = view.photoImage
                                        val location = IntArray(2)
                                        view.getLocationInWindow(location)
                                        val info = PhotoViewer.PlaceProviderObject()
                                        info.viewX = location[0] + imageReceiver.imageX.toInt()
                                        info.viewY = location[1] + imageReceiver.imageY.toInt()
                                        info.parentView = listView
                                        info.imageReceiver = imageReceiver
                                        info.radius = intArrayOf(
                                            AndroidUtilities.dp(4f), AndroidUtilities.dp(4f),
                                            AndroidUtilities.dp(4f), AndroidUtilities.dp(4f)
                                        )
                                        info.scale = view.scaleX
                                        info.thumb = imageReceiver.bitmapSafe ?: imageReceiver.thumbBitmapSafe
                                        return info
                                    }
                                }
                                return null
                            }
                        }
                    )
                }

                override fun needPlayMessage(cell: ChatMessageCell, messageObject: MessageObject, muted: Boolean): Boolean {
                    // playMessage would normally fire content-read for unread voice/round video, but the
                    // ChatPreviewState hook in markMessageContentAsRead suppresses it for this dialog.
                    MediaController.getInstance().playMessage(messageObject)
                    return true
                }

                override fun didPressUrl(cell: ChatMessageCell?, url: CharacterStyle?, longPress: Boolean) {
                    val urlString = when (url) {
                        is URLSpanNoUnderline -> url.url
                        is URLSpan -> url.url
                        else -> null
                    } ?: return
                    if (longPress) AndroidUtilities.addToClipboard(urlString)
                    else Browser.openUrl(activity, urlString)
                }
            })
            return RecyclerListView.Holder(cell)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val cell = holder.itemView as ChatMessageCell
            val messageObject = messages[position]
            val playing = MediaController.getInstance().playingMessageObject
            if (playing != null && playing.id == messageObject.id) {
                messageObject.audioProgress = playing.audioProgress
                messageObject.audioProgressSec = playing.audioProgressSec
            }
            cell.setMessageObject(messageObject, null, false, false, true)
        }
    }

    override fun canDismissWithSwipe(): Boolean = listView.computeVerticalScrollOffset() <= 0

    override fun dismiss() {
        super.dismiss()
        ChatPreviewState.clear()
        val nc = NotificationCenter.getInstance(currentAccount)
        nc.removeObserver(audioDelegate, NotificationCenter.messagePlayingProgressDidChanged)
        nc.removeObserver(audioDelegate, NotificationCenter.messagePlayingPlayStateChanged)
        nc.removeObserver(audioDelegate, NotificationCenter.messagePlayingDidSeek)
        nc.removeObserver(messagesDelegate, NotificationCenter.messagesDidLoad)
        nc.removeObserver(messagesDelegate, NotificationCenter.didReceiveNewMessages)
        nc.removeObserver(messagesDelegate, NotificationCenter.replaceMessagesObjects)
        nc.removeObserver(messagesDelegate, NotificationCenter.updateInterfaces)
        nc.removeObserver(messagesDelegate, NotificationCenter.chatInfoDidLoad)
        nc.removeObserver(messagesDelegate, NotificationCenter.userInfoDidLoad)
        messages.clear()
        messageIds.clear()
    }

    private companion object {
        const val COUNT_TO_LOAD = 100
        const val PAGE_SIZE = 50
        const val LOAD_MORE_THRESHOLD = 5

        /** Alpha of the header scrim: 0xF0 = 94%, dense enough to read over any wallpaper. */
        const val HEADER_SCRIM_ALPHA = 0xF0000000.toInt()
    }
}
