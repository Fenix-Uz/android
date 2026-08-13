package org.fenixuz.ui

import android.content.Context
import android.media.RingtoneManager
import android.view.View
import android.widget.FrameLayout
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import org.telegram.ui.ActionBar.Theme
import org.fenixuz.ui.auto_answer.AutoAnswer
import org.fenixuz.ui.auto_answer.AutoAnswerMenu
import org.fenixuz.ui.create_folder_dialog.FolderIcons
import org.fenixuz.ui.onboarding.FenixTour
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBar
import org.fenixuz.utils.ApkShield
import org.fenixuz.utils.AutoAcceptJoin
import org.fenixuz.utils.CameraSituation
import org.fenixuz.utils.ConfirmDialogsPref
import org.fenixuz.utils.GhostStory
import org.fenixuz.utils.HideTabs
import org.fenixuz.utils.LanguageCode
import org.fenixuz.utils.MessageReminder
import org.fenixuz.utils.StoryDownload
import org.fenixuz.utils.StoryUtil
import org.fenixuz.utils.StrangerShield
import org.fenixuz.utils.VoiceDictation
import org.fenixuz.utils.EditMessage
import org.fenixuz.utils.DeletedMsg
import org.fenixuz.utils.GhostVariable
import org.fenixuz.utils.Password
import org.fenixuz.ui.secret_chat.SecretPassword
import org.fenixuz.ui.secret_chat.SecretPasscodeScreen
import org.fenixuz.ui.secret_chat.SecretPasswordType
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DownloadController
import org.telegram.messenger.MessagesController
import android.os.Bundle
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.DialogsActivity
import org.telegram.ui.Cells.NotificationsCheckCell
import org.telegram.ui.Components.NumberPicker
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalFragment
import org.telegram.ui.Components.ShareAlert
import org.telegram.ui.Components.BulletinFactory
import org.telegram.messenger.MessageObject

/**
 * Fenix Settings — Novagram feature hub, rendered in native Telegram style
 * (rounded card sections via [UniversalFragment]).
 *
 * Stories section — two independent toggles:
 *  - view stories anonymously ([GhostStory], hooked in PeerStoriesView)
 *  - hide the stories tray in the chat list ([StoryUtil], hooked in StoriesController)
 * Add one row per ported feature.
 */
class FenixSettings @JvmOverloads constructor(private val targetUrl: String? = null) : UniversalFragment() {

    private val STORY_GHOST = 1
    private val STORY_HIDE = 2
    private val DOWNLOAD_STOP = 5
    private val AUTO_ANSWER_ACTIVE = 6
    private val AUTO_ANSWER_MSG = 7
    private val CONFIRM_STICKER = 8
    private val CONFIRM_VOICE = 9
    private val CONFIRM_GIF = 10
    private val FOLDER_ICONS = 11
    private val HIDE_TABS = 12
    private val AUTO_ACCEPT_JOIN = 13
    private val REMINDER_ENABLED = 14
    private val REMINDER_DELAY = 15
    private val REMINDER_SOUND = 16
    private val STORY_DOWNLOAD = 17
    private val STRANGER_SHIELD = 18
    private val STRANGER_INBOX = 19

    // Previously hidden behind a 5-tap secret gesture — now surfaced directly in this list.
    private val EDIT_SAVE = 20
    private val DELETE_SAVE = 21
    private val GHOST_MODE = 22
    private val SECRET_CHAT = 23
    private val CHANGE_COMMON_PASSWORD = 24
    private val GHOST_ACTIONBAR_BTN = 26
    private val ROUND_CAMERA_FRONT = 27
    private val APK_SHIELD = 28
    private val VOICE_MIC = 29

    // Onboarding: a toolbar "?" replays the full feature tour; the short tour auto-runs once on first open.
    private val HELP_BUTTON = 1001
    private val TOUR_SHOWN_KEY = "fenix_tour_shown"

    // Shareable settings deep-links: long-press a row to copy/send a tg://novagram_settings/<key> link;
    // opening that link (handled in LaunchActivity) reopens this screen scrolled to + highlighting the row.
    // Only feature rows are linkable; action/sub-picker rows (inbox, pickers, passcode) are intentionally left out.
    private val LINK_SCHEME = "tg://novagram_settings"
    private val linkKeys: Map<Int, String> = linkedMapOf(
        STORY_GHOST to "story_ghost",
        STORY_HIDE to "hide_stories",
        STORY_DOWNLOAD to "story_download",
        EDIT_SAVE to "save_edited_messages",
        DELETE_SAVE to "save_deleted_messages",
        DOWNLOAD_STOP to "stop_automatic_downloads",
        AUTO_ANSWER_ACTIVE to "auto_answer",
        GHOST_MODE to "ghost",
        GHOST_ACTIONBAR_BTN to "ghost_button",
        SECRET_CHAT to "secret_chat",
        CONFIRM_STICKER to "confirm_sticker",
        CONFIRM_VOICE to "confirm_voice",
        CONFIRM_GIF to "confirm_gif",
        FOLDER_ICONS to "folder_icons",
        HIDE_TABS to "hide_tabs",
        AUTO_ACCEPT_JOIN to "auto_accept_join",
        REMINDER_ENABLED to "reminder",
        STRANGER_SHIELD to "protection_from_strangers",
        APK_SHIELD to "block_apk",
        ROUND_CAMERA_FRONT to "round_video_camera"
    )
    private var targetConsumed = false
    private var flashAnimator: ValueAnimator? = null

    /** True when auto-download is fully stopped (all network presets disabled). */
    private fun isAutoDownloadStopped(): Boolean {
        val dc = DownloadController.getInstance(currentAccount)
        return !dc.mobilePreset.enabled && !dc.wifiPreset.enabled && !dc.roamingPreset.enabled
    }

    private fun setAutoDownloadStopped(stopped: Boolean) {
        val dc = DownloadController.getInstance(currentAccount)
        val enabled = !stopped
        dc.mobilePreset.enabled = enabled
        dc.wifiPreset.enabled = enabled
        dc.roamingPreset.enabled = enabled
        val editor = MessagesController.getMainSettings(currentAccount).edit()
        editor.putString("mobilePreset", dc.mobilePreset.toString())
        editor.putString("wifiPreset", dc.wifiPreset.toString())
        editor.putString("roamingPreset", dc.roamingPreset.toString())
        editor.putInt("currentMobilePreset", 3)
        editor.putInt("currentWifiPreset", 3)
        editor.putInt("currentRoamingPreset", 3)
        editor.commit()
        dc.checkAutodownloadSettings()
        dc.savePresetToServer(0)
        dc.savePresetToServer(1)
        dc.savePresetToServer(2)
    }

    override fun getTitle(): CharSequence = LanguageCode.getMyTitles(236)

    override fun createView(context: Context): View {
        val view = super.createView(context)
        // Native Telegram rounded "card" sections (like the main Settings screen).
        listView.setSections()
        listView.adapter.setApplyBackground(false)
        // Transparent/adaptive toolbar that blends with the list, like other settings menus.
        actionBar.setAdaptiveBackground(listView)

        // Toolbar "?" → replay the guided tour on demand (for anyone who skipped or wants a refresher).
        actionBar.createMenu().addItem(HELP_BUTTON, R.drawable.outline_question_mark)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
                else if (id == HELP_BUTTON) startTour()
            }
        })

        // First-ever open: auto-play the full tour once, after the list has settled. It covers every feature;
        // the Skip button lets anyone bail out instantly, and the toolbar "?" lets them replay it later.
        if (!tourAlreadyShown()) {
            markTourShown()
            AndroidUtilities.runOnUIThread({ startTour() }, 350)
        }

        return view
    }

    private fun tourAlreadyShown(): Boolean =
        parentActivity?.getSharedPreferences("db", Context.MODE_PRIVATE)?.getBoolean(TOUR_SHOWN_KEY, false) ?: true

    private fun markTourShown() {
        parentActivity?.getSharedPreferences("db", Context.MODE_PRIVATE)?.edit()?.putBoolean(TOUR_SHOWN_KEY, true)?.apply()
    }

    /** Build a tour stop from a row id, its title string and the explanation (reusing each feature's subtitle). */
    private fun step(itemId: Int, titleCode: Int, bodyCode: Int): FenixTour.Step =
        FenixTour.Step(itemId, LanguageCode.getMyTitles(titleCode), LanguageCode.getMyTitles(bodyCode))

    /** The full walkthrough: every Novagram feature on this screen, in order. Used both on first open and replay. */
    private fun fullSteps(): List<FenixTour.Step> = listOf(
        step(STORY_GHOST, 232, 233),
        step(STORY_HIDE, 136, 137),
        step(STORY_DOWNLOAD, 297, 298),
        step(EDIT_SAVE, 23, 72),
        step(DELETE_SAVE, 31, 235),
        step(DOWNLOAD_STOP, 183, 196),
        step(AUTO_ANSWER_ACTIVE, 228, 239),
        step(GHOST_MODE, 32, 245),
        step(SECRET_CHAT, 213, 217),
        step(CONFIRM_STICKER, 190, 191),
        step(FOLDER_ICONS, 240, 241),
        step(HIDE_TABS, 260, 261),
        step(AUTO_ACCEPT_JOIN, 263, 264),
        step(REMINDER_ENABLED, 270, 271),
        step(STRANGER_SHIELD, 319, 320),
        step(ROUND_CAMERA_FRONT, 353, 354)
    )

    private fun startTour() {
        val container = fragmentView as? FrameLayout ?: return
        FenixTour(container.context, container, listView, fullSteps()).start()
    }

    // Draw the grey page behind a TRANSPARENT system navigation bar (Telegram's own settings screens do this
    // via edge-to-edge) instead of painting a solid grey bar at the bottom. With edge-to-edge on, BaseFragment
    // skips forcing a nav-bar colour, so the bar goes transparent and the list background scrolls underneath.
    override fun isSupportEdgeToEdge(): Boolean = true

    // Keep the last row clear of the nav buttons (pad the bottom by the nav inset) while letting the background
    // and section cards bleed under the transparent bar (clipToPadding=false). Top/side padding is preserved so
    // the adaptive toolbar inset stays intact.
    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        listView.setPadding(listView.paddingLeft, listView.paddingTop, listView.paddingRight, bottom)
        listView.clipToPadding = false
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(LanguageCode.getMyTitles(135)))
        items.add(
            UItem.asButtonCheck(STORY_GHOST, LanguageCode.getMyTitles(232), LanguageCode.getMyTitles(233))
                .setChecked(GhostStory.ghostMode)
        )
        items.add(
            UItem.asButtonCheck(STORY_HIDE, LanguageCode.getMyTitles(136), LanguageCode.getMyTitles(137))
                .setChecked(StoryUtil.hideStoryMode)
        )
        items.add(
            UItem.asButtonCheck(STORY_DOWNLOAD, LanguageCode.getMyTitles(297), LanguageCode.getMyTitles(298))
                .setChecked(StoryDownload.isEnabled())
        )
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(234)))
        items.add(
            UItem.asButtonCheck(EDIT_SAVE, LanguageCode.getMyTitles(23), LanguageCode.getMyTitles(72))
                .setChecked(EditMessage.editMode)
        )
        items.add(
            UItem.asButtonCheck(DELETE_SAVE, LanguageCode.getMyTitles(31), LanguageCode.getMyTitles(235))
                .setChecked(DeletedMsg.getCheckType() != DeletedMsg.SIMPLE)
        )
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(237)))
        items.add(
            UItem.asButtonCheck(DOWNLOAD_STOP, LanguageCode.getMyTitles(183), LanguageCode.getMyTitles(196))
                .setChecked(isAutoDownloadStopped())
        )
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(228)))
        items.add(
            UItem.asButtonCheck(AUTO_ANSWER_ACTIVE, LanguageCode.getMyTitles(228), LanguageCode.getMyTitles(239))
                .setChecked(AutoAnswer.autoAnswerIsActive())
        )
        items.add(UItem.asButton(AUTO_ANSWER_MSG, LanguageCode.getMyTitles(238)))
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(244)))
        items.add(
            UItem.asButtonCheck(GHOST_MODE, LanguageCode.getMyTitles(32), LanguageCode.getMyTitles(245))
                .setChecked(GhostVariable.ghostMode)
        )
        items.add(
            UItem.asButtonCheck(GHOST_ACTIONBAR_BTN, LanguageCode.getMyTitles(342), LanguageCode.getMyTitles(343))
                .setChecked(GhostVariable.ghostMenuVisibilityOnActionBar)
        )
        items.add(
            UItem.asButtonCheck(SECRET_CHAT, LanguageCode.getMyTitles(213), LanguageCode.getMyTitles(217))
                .setChecked(SecretPassword.hasPassword())
        )
        items.add(UItem.asShadow(null))

        // Chat-lock management — just the "change common password" row. Fingerprint unlock is now
        // automatic on any biometric device (no toggle needed), so it no longer appears here.
        if (Password.hasCommonPassword()) {
            items.add(UItem.asHeader(LanguageCode.getMyTitles(252)))
            items.add(UItem.asButton(CHANGE_COMMON_PASSWORD, LanguageCode.getMyTitles(251)))
            items.add(UItem.asShadow(null))
        }

        items.add(UItem.asHeader(LanguageCode.getMyTitles(190)))
        items.add(
            UItem.asButtonCheck(CONFIRM_STICKER, LanguageCode.getMyTitles(210), LanguageCode.getMyTitles(191))
                .setChecked(ConfirmDialogsPref.confirmSticker)
        )
        items.add(
            UItem.asButtonCheck(CONFIRM_VOICE, LanguageCode.getMyTitles(211), LanguageCode.getMyTitles(192))
                .setChecked(ConfirmDialogsPref.confirmVoice)
        )
        items.add(
            UItem.asButtonCheck(CONFIRM_GIF, LanguageCode.getMyTitles(212), LanguageCode.getMyTitles(193))
                .setChecked(ConfirmDialogsPref.confirmGif)
        )
        items.add(UItem.asShadow(null))

        // Round video note camera — a persistent choice (front by default) used every time a round
        // video is recorded, instead of the old always-ask popup. Title reuses "Front camera" (113).
        items.add(UItem.asHeader(LanguageCode.getMyTitles(353)))
        items.add(
            UItem.asButtonCheck(ROUND_CAMERA_FRONT, LanguageCode.getMyTitles(113), LanguageCode.getMyTitles(354))
                .setChecked(CameraSituation.isFront)
        )
        items.add(UItem.asShadow(null))

        // Composer voice-input mic. Only offered where it can actually work: on a device with no speech
        // recogniser the mic is hidden anyway, so a switch for it would be a control that does nothing.
        if (VoiceDictation.hasRecognizer(ApplicationLoader.applicationContext)) {
            items.add(UItem.asHeader(LanguageCode.getMyTitles(380)))
            items.add(
                UItem.asButtonCheck(VOICE_MIC, LanguageCode.getMyTitles(381), LanguageCode.getMyTitles(382))
                    .setChecked(VoiceDictation.isMicEnabled(ApplicationLoader.applicationContext))
            )
            items.add(UItem.asShadow(null))
        }

        items.add(UItem.asHeader(LanguageCode.getMyTitles(240)))
        items.add(
            UItem.asButtonCheck(FOLDER_ICONS, LanguageCode.getMyTitles(240), LanguageCode.getMyTitles(241))
                .setChecked(FolderIcons.isIconMode())
        )
        items.add(
            UItem.asButtonCheck(HIDE_TABS, LanguageCode.getMyTitles(260), LanguageCode.getMyTitles(261))
                .setChecked(HideTabs.isEnabled())
        )
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(262)))
        items.add(
            UItem.asButtonCheck(AUTO_ACCEPT_JOIN, LanguageCode.getMyTitles(263), LanguageCode.getMyTitles(264))
                .setChecked(AutoAcceptJoin.isEnabled())
        )
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(269)))
        items.add(
            UItem.asButtonCheck(REMINDER_ENABLED, LanguageCode.getMyTitles(270), LanguageCode.getMyTitles(271))
                .setChecked(MessageReminder.isEnabled())
        )
        items.add(UItem.asButton(REMINDER_DELAY, reminderDelayLabel()))
        items.add(UItem.asButton(REMINDER_SOUND, reminderSoundLabel()))
        items.add(UItem.asShadow(null))

        items.add(UItem.asHeader(LanguageCode.getMyTitles(318)))
        items.add(
            UItem.asButtonCheck(STRANGER_SHIELD, LanguageCode.getMyTitles(319), LanguageCode.getMyTitles(320))
                .setChecked(StrangerShield.isEnabled())
        )
        // A "Stranger chats" inbox — opens a Chats-like screen listing the hidden non-contact chats,
        // so nothing is lost: you can read and reply from there. Reachable even when the toggle is off.
        // Badge the entry with the unread count so an important message is never missed unnoticed.
        val inboxUnread = StrangerShield.countInboxUnread(currentAccount)
        if (inboxUnread > 0) {
            items.add(UItem.asButton(STRANGER_INBOX, LanguageCode.getMyTitles(321), inboxUnread.toString()))
        } else {
            items.add(UItem.asButton(STRANGER_INBOX, LanguageCode.getMyTitles(321)))
        }
        // Block incoming .apk attachments — same Privacy section, it is the same "protect me" intent.
        items.add(
            UItem.asButtonCheck(APK_SHIELD, LanguageCode.getMyTitles(378), LanguageCode.getMyTitles(379))
                .setChecked(ApkShield.isEnabled())
        )
        items.add(UItem.asShadow(null))

        resolveTargetPosition(items)
    }

    private fun reminderDelayLabel(): String =
        LanguageCode.getMyTitles(272) + ": " + MessageReminder.getDelayMin() + " " + LanguageCode.getMyTitles(273)

    private fun reminderSoundLabel(): String {
        val tone = if (MessageReminder.getSound() == 1) LanguageCode.getMyTitles(276) else LanguageCode.getMyTitles(275)
        return LanguageCode.getMyTitles(274) + ": " + tone
    }

    /**
     * Switch the shield off, optionally emptying the inbox back into the main list. Order matters:
     * [StrangerShield.setEnabled] prunes and recomputes the badge first, then the release clears the set
     * and recomputes again — so the final numbers are the released ones.
     */
    private fun disableStrangerShield(item: UItem, view: View, release: Boolean) {
        StrangerShield.setEnabled(false)
        if (release) {
            StrangerShield.releaseCaptured(currentAccount)
        }
        item.checked = false
        // Animate the switch itself, then rebuild so the "Stranger chats" unread badge follows too.
        (view as? NotificationsCheckCell)?.setChecked(false)
        listView?.adapter?.update(true)
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload)
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            EDIT_SAVE -> {
                EditMessage.changeEditMode(!EditMessage.editMode)
                (view as NotificationsCheckCell).setChecked(EditMessage.editMode)
            }
            DELETE_SAVE -> {
                val enabled = DeletedMsg.getCheckType() != DeletedMsg.SIMPLE
                DeletedMsg.saveCheckType(if (enabled) DeletedMsg.SIMPLE else DeletedMsg.SECOND)
                (view as NotificationsCheckCell).setChecked(DeletedMsg.getCheckType() != DeletedMsg.SIMPLE)
            }
            GHOST_MODE -> {
                // Toggles GhostVariable.ghostMode → re-asserts offline via MyStatus; backend hooks already wired.
                GhostVariable.changeGhostMode()
                (view as NotificationsCheckCell).setChecked(GhostVariable.ghostMode)
            }
            GHOST_ACTIONBAR_BTN -> {
                // Show/hide the quick ghost toggle on the main dialogs action bar (DialogsActivity reads
                // this flag in onResume → updateGhostButton). Default OFF; opt-in here.
                GhostVariable.changeGhostModeVisibilityOnActionBar()
                (view as NotificationsCheckCell).setChecked(GhostVariable.ghostMenuVisibilityOnActionBar)
            }
            SECRET_CHAT -> {
                // Secure switch: ON asks to create a passcode, OFF asks to confirm before removing.
                // Checked state follows SecretPassword.hasPassword(), refreshed in onResume after the flow.
                if (SecretPassword.hasPassword()) {
                    presentFragment(SecretPasscodeScreen(SecretPassword.editor(), SecretPasswordType.CHANGE))
                } else {
                    presentFragment(SecretPasscodeScreen(SecretPassword.editor(), SecretPasswordType.SET_NEW))
                }
            }
            CHANGE_COMMON_PASSWORD -> {
                presentFragment(SecretPasscodeScreen(Password.editorForCommonPassword(), SecretPasswordType.SET_NEW))
            }
            STORY_GHOST -> {
                GhostStory.changeGhostMode(!GhostStory.ghostMode)
                (view as NotificationsCheckCell).setChecked(GhostStory.ghostMode)
            }
            STORY_HIDE -> {
                StoryUtil.changeHideStoryMode()
                // Ask DialogsActivity to refresh the stories tray when we return (see markStoryVisibilityDirty).
                StoryUtil.markStoryVisibilityDirty()
                (view as NotificationsCheckCell).setChecked(StoryUtil.hideStoryMode)
            }
            STORY_DOWNLOAD -> {
                StoryDownload.toggle()
                (view as NotificationsCheckCell).setChecked(StoryDownload.isEnabled())
            }
            DOWNLOAD_STOP -> {
                setAutoDownloadStopped(!isAutoDownloadStopped())
                (view as NotificationsCheckCell).setChecked(isAutoDownloadStopped())
            }
            AUTO_ANSWER_ACTIVE -> {
                AutoAnswer.saveAutoAnswerActive(!AutoAnswer.autoAnswerIsActive())
                (view as NotificationsCheckCell).setChecked(AutoAnswer.autoAnswerIsActive())
            }
            AUTO_ANSWER_MSG -> {
                presentFragment(AutoAnswerMenu())
            }
            CONFIRM_STICKER -> {
                ConfirmDialogsPref.changeConfirmStickerMode()
                (view as NotificationsCheckCell).setChecked(ConfirmDialogsPref.confirmSticker)
            }
            CONFIRM_VOICE -> {
                ConfirmDialogsPref.changeConfirmVoiceMode()
                (view as NotificationsCheckCell).setChecked(ConfirmDialogsPref.confirmVoice)
            }
            CONFIRM_GIF -> {
                ConfirmDialogsPref.changeConfirmGifMode()
                (view as NotificationsCheckCell).setChecked(ConfirmDialogsPref.confirmGif)
            }
            ROUND_CAMERA_FRONT -> {
                // ON = front camera, OFF = rear. Persisted in CameraSituation; InstantCameraView reads it
                // on open. No popup — the saved choice applies to every round video from now on.
                CameraSituation.isFront = !CameraSituation.isFront
                (view as NotificationsCheckCell).setChecked(CameraSituation.isFront)
            }
            FOLDER_ICONS -> {
                FolderIcons.setIconMode(!FolderIcons.isIconMode())
                (view as NotificationsCheckCell).setChecked(FolderIcons.isIconMode())
                // Rebuild folder tabs so the change applies immediately.
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogFiltersUpdated)
            }
            HIDE_TABS -> {
                // Persist + mark dirty; DialogsActivity.onResume re-applies it when we navigate back.
                HideTabs.toggle()
                (view as NotificationsCheckCell).setChecked(HideTabs.isEnabled())
            }
            AUTO_ACCEPT_JOIN -> {
                AutoAcceptJoin.toggle()
                (view as NotificationsCheckCell).setChecked(AutoAcceptJoin.isEnabled())
            }
            REMINDER_ENABLED -> {
                MessageReminder.setEnabled(!MessageReminder.isEnabled())
                (view as NotificationsCheckCell).setChecked(MessageReminder.isEnabled())
            }
            REMINDER_DELAY -> showReminderDelayPicker()
            REMINDER_SOUND -> showReminderSoundPicker()
            STRANGER_SHIELD -> {
                if (!StrangerShield.isEnabled()) {
                    // Turning ON: explain exactly what happens, then ask for consent — so the user is
                    // never surprised that chats "disappeared" or that a message was silenced.
                    val context = parentActivity ?: return
                    AlertDialog.Builder(context)
                        .setTitle(LanguageCode.getMyTitles(319))
                        .setMessage(LanguageCode.getMyTitles(322))
                        .setPositiveButton(LanguageCode.getMyTitles(323)) { _, _ ->
                            StrangerShield.setEnabled(true)
                            item.checked = true
                            (view as? NotificationsCheckCell)?.setChecked(true)
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload)
                        }
                        .setNegativeButton(LanguageCode.getMyTitles(80), null)
                        .show()
                } else {
                    // Turning OFF is NOT the mirror of turning ON: chats captured while the shield was on
                    // stay in the inbox. Never do that silently — ask, or the user turns the switch off,
                    // sees nothing come back and reports it as a bug.
                    val pending = StrangerShield.countCaptured(currentAccount)
                    val context = parentActivity
                    if (pending > 0 && context != null) {
                        // Back / tap-outside must still honour the tap on the switch, or the toggle looks
                        // dead. It falls through to the safe branch — shield off, inbox untouched — which is
                        // fully reversible: re-arming re-files exactly the same chats.
                        var handled = false
                        AlertDialog.Builder(context)
                            .setTitle(LanguageCode.getMyTitles(319))
                            .setMessage(LanguageCode.getMyTitles(383).replace("%d", pending.toString()))
                            .setPositiveButton(LanguageCode.getMyTitles(384)) { _, _ ->
                                handled = true
                                disableStrangerShield(item, view, release = true)
                            }
                            .setNegativeButton(LanguageCode.getMyTitles(385)) { _, _ ->
                                handled = true
                                disableStrangerShield(item, view, release = false)
                            }
                            .setOnDismissListener { _ ->
                                if (!handled) disableStrangerShield(item, view, release = false)
                            }
                            .show()
                    } else {
                        disableStrangerShield(item, view, release = false)
                    }
                }
            }
            STRANGER_INBOX -> {
                val args = Bundle()
                args.putBoolean("fenixStrangerInbox", true)
                presentFragment(DialogsActivity(args))
            }
            APK_SHIELD -> {
                ApkShield.toggle()
                (view as NotificationsCheckCell).setChecked(ApkShield.isEnabled())
            }
            VOICE_MIC -> {
                val ctx = ApplicationLoader.applicationContext
                VoiceDictation.setMicEnabled(ctx, !VoiceDictation.isMicEnabled(ctx))
                (view as NotificationsCheckCell).setChecked(VoiceDictation.isMicEnabled(ctx))
            }
        }
        // Novagram: keep the cached UItem model in sync with the toggled cell. setChecked() only updates the
        // live view; the RecyclerView rebinds from the UItem on scroll, so without this the row snaps back to
        // its old (model) value when recycled. Only check cells have a checked state — button rows are skipped.
        // (STRANGER_SHIELD turns on from an async dialog callback, which sets item.checked itself.)
        if (view is NotificationsCheckCell) {
            item.checked = view.isChecked
        }
    }

    private fun showReminderDelayPicker() {
        val context = parentActivity ?: return
        val picker = NumberPicker(context)
        picker.setMinValue(MessageReminder.MIN_DELAY_MIN)
        picker.setMaxValue(MessageReminder.MAX_DELAY_MIN)
        picker.setValue(MessageReminder.getDelayMin())
        AlertDialog.Builder(context)
            .setTitle(LanguageCode.getMyTitles(272))
            .setView(picker)
            .setPositiveButton(LanguageCode.getMyTitles(4)) { _, _ ->
                MessageReminder.setDelayMin(picker.value)
                listView.adapter.update(true)
            }
            .setNegativeButton(LanguageCode.getMyTitles(80), null)
            .show()
    }

    private fun showReminderSoundPicker() {
        val context = parentActivity ?: return
        val items = arrayOf<CharSequence>(LanguageCode.getMyTitles(275), LanguageCode.getMyTitles(276))
        AlertDialog.Builder(context)
            .setTitle(LanguageCode.getMyTitles(274))
            .setItems(items) { _, which ->
                MessageReminder.setSound(which)
                previewReminderSound(context, which)
                listView.adapter.update(true)
            }
            .show()
    }

    private fun previewReminderSound(context: Context, index: Int) {
        try {
            val uri = MessageReminder.soundUri(index) ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.play()
            // Bound the preview: alarm tones loop, so stop it after a short listen.
            AndroidUtilities.runOnUIThread({
                try {
                    ringtone.stop()
                } catch (ignore: Exception) {
                }
            }, 2500)
        } catch (ignore: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        // Reflect secret-chat / fingerprint state after returning from a passcode screen.
        if (listView != null) {
            listView.adapter.update(true)
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        // Long-press a linkable row → offer to copy or send its deep-link.
        val key = linkKeys[item.id] ?: return false
        val ctx = parentActivity ?: return false
        val url = "$LINK_SCHEME/$key"
        AlertDialog.Builder(ctx)
            .setItems(
                arrayOf<CharSequence>(LanguageCode.getMyTitles(351), LanguageCode.getMyTitles(352))
            ) { _, which ->
                if (which == 0) {
                    AndroidUtilities.addToClipboard(url)
                    BulletinFactory.of(this).createCopyLinkBulletin().show()
                } else {
                    showDialog(ShareAlert(ctx, null as ArrayList<MessageObject>?, url, false, url, false))
                }
            }
            .show()
        return true
    }

    /**
     * Resolve the deep-linked row (tg://novagram_settings/<key>) and — once — scroll to + flash-highlight it.
     * Mirrors Telegram's own working pattern (LiteModeSettingsActivity.highlightRow): the scroll lives INSIDE
     * the highlight callback, so if the row isn't laid out yet the highlight is deferred and the callback
     * re-runs (re-scrolling) when the row appears. Doing the scroll outside would skip that self-healing.
     */
    private fun resolveTargetPosition(items: ArrayList<UItem>) {
        if (targetUrl == null || targetConsumed) return
        val key = targetUrl.substringAfterLast('/').substringBefore('?')
        val targetId = linkKeys.entries.firstOrNull { it.value == key }?.key
        if (targetId == null) {
            targetConsumed = true
            return
        }
        val pos = items.indexOfFirst { it.id == targetId }
        if (pos < 0) {
            targetConsumed = true
            return
        }
        targetConsumed = true
        // Let the list finish its first layout pass, then scroll to + flash the row.
        AndroidUtilities.runOnUIThread({ scrollAndFlash(pos, 0) }, 250)
    }

    /** Scroll the deep-linked row into view, then flash it. Retries a few times until the row is laid out. */
    private fun scrollAndFlash(pos: Int, attempt: Int) {
        try {
            listView.layoutManager.scrollToPositionWithOffset(pos, AndroidUtilities.dp(80f))
        } catch (ignore: Exception) {
        }
        AndroidUtilities.runOnUIThread({
            val row = listView.findViewHolderForAdapterPosition(pos)?.itemView
            if (row != null && row.width > 0 && row.height > 0) {
                flashRow(row, pos)
            } else if (attempt < 4) {
                scrollAndFlash(pos, attempt + 1)
            }
        }, 140)
    }

    /**
     * The card-style section list doesn't paint the native row selector, so highlightRow is invisible here.
     * Instead, draw a fading accent overlay on top of the row (double blink) — clearly visible in both themes,
     * without touching the row's real background.
     */
    private fun flashRow(row: View, pos: Int) {
        flashAnimator?.cancel()
        val overlay = ColorDrawable(Theme.getColor(Theme.key_featuredStickers_addButton))
        overlay.setBounds(0, 0, row.width, row.height)
        overlay.alpha = 0
        row.overlay.add(overlay)
        // Three clear blinks over ~2.1s so it's easy to spot which row the link points to.
        val anim = ValueAnimator.ofInt(0, 95, 0, 95, 0, 95, 0)
        anim.duration = 2100
        anim.addUpdateListener {
            // Guard: if the row got recycled/detached (e.g. the user scrolled during the flash), stop —
            // so the blink can never land on the wrong row. onAnimationEnd (fired on cancel too) cleans up.
            if (row.parent == null || listView.getChildAdapterPosition(row) != pos) {
                it.cancel()
                return@addUpdateListener
            }
            overlay.alpha = it.animatedValue as Int
            row.invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                row.overlay.remove(overlay)
                if (flashAnimator === anim) flashAnimator = null
            }
        })
        flashAnimator = anim
        anim.start()
    }

    override fun onPause() {
        super.onPause()
        // Don't let a flash keep running (touching views) after we leave the screen.
        flashAnimator?.cancel()
    }
}
