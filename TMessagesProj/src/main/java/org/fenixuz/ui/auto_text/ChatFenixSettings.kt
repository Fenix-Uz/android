package org.fenixuz.ui.auto_text

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.fenixuz.ui.onboarding.FenixTour
import org.fenixuz.ui.secret_chat.SecretPasscodeScreen
import org.fenixuz.ui.secret_chat.SecretPasswordType
import org.fenixuz.utils.AutoTextAppender
import org.fenixuz.utils.LanguageCode
import org.fenixuz.utils.OneTimeVoice
import org.fenixuz.utils.Password
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ChatActivity
import org.telegram.ui.Cells.NotificationsCheckCell
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalFragment

class
ChatFenixSettings(
    private val dialogId: Long,
    private val parentChat: ChatActivity?
) : UniversalFragment() {

    private val ONE_TIME = 1
    private val AUTO_TEXT = 2
    private val AUTO_TEXT_EDIT = 3
    private val LOCK = 4

    private val TOUR_SHOWN_KEY = "fenix_chat_settings_tour_shown"
    private val HELP_BUTTON = 1001

    override fun getTitle(): CharSequence = LanguageCode.getMyTitles(305)

    override fun createView(context: Context): View {
        val view = super.createView(context)
        listView.setSections()
        listView.adapter.setApplyBackground(false)
        actionBar.setAdaptiveBackground(listView)

        // Toolbar "?" → replay the tour on demand (for anyone who skipped or wants a refresher).
        actionBar.createMenu().addItem(HELP_BUTTON, R.drawable.outline_question_mark)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
                else if (id == HELP_BUTTON) startTour()
            }
        })

        // First-ever open of any chat's Novagram tools: a one-time coach-mark guide to the per-chat tools.
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

    private fun step(itemId: Int, titleCode: Int, bodyCode: Int): FenixTour.Step =
        FenixTour.Step(itemId, LanguageCode.getMyTitles(titleCode), LanguageCode.getMyTitles(bodyCode))

    private fun tourSteps(): List<FenixTour.Step> = listOf(
        step(ONE_TIME, 306, 307),
        step(AUTO_TEXT, 300, 301),
        step(LOCK, 246, 341)
    )

    private fun startTour() {
        val container = fragmentView as? FrameLayout ?: return
        FenixTour(container.context, container, listView, tourSteps()).start()
    }

    override fun onResume() {
        super.onResume()
        // Returning from the composer / passcode screen may have changed state — re-read it.
        listView?.adapter?.update(true)
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(LanguageCode.getMyTitles(305)))
        items.add(
            UItem.asButtonCheck(ONE_TIME, LanguageCode.getMyTitles(306), LanguageCode.getMyTitles(307))
                .setChecked(OneTimeVoice.isEnabled(dialogId))
        )
        items.add(UItem.asShadow(null))

        items.add(
            UItem.asButtonCheck(AUTO_TEXT, LanguageCode.getMyTitles(300), LanguageCode.getMyTitles(301))
                .setChecked(AutoTextAppender.isActive(dialogId))
        )
        items.add(UItem.asButton(AUTO_TEXT_EDIT, LanguageCode.getMyTitles(302)))
        items.add(UItem.asShadow(null))

        items.add(UItem.asButton(LOCK, LanguageCode.getMyTitles(if (Password.isLocked(dialogId)) 247 else 246)))
        items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            ONE_TIME -> {
                OneTimeVoice.toggle(dialogId)
                (view as NotificationsCheckCell).isChecked = OneTimeVoice.isEnabled(dialogId)
            }
            AUTO_TEXT -> {
                AutoTextAppender.setActive(dialogId, !AutoTextAppender.isActive(dialogId))
                (view as NotificationsCheckCell).isChecked = AutoTextAppender.isActive(dialogId)
            }
            AUTO_TEXT_EDIT -> presentFragment(AutoTextEditor(dialogId))
            LOCK -> lockFlow()
        }
    }

    private fun lockFlow() {
        parentChat?.markChatLockPassed()
        if (Password.isLocked(dialogId)) {
            Password.unlock(dialogId)
            listView.adapter.update(true)
            return
        }
        val activity = parentActivity ?: return
        AlertDialog.Builder(activity)
            .setTitle(LanguageCode.getMyTitles(248))
            .setItems(
                arrayOf<CharSequence>(LanguageCode.getMyTitles(249), LanguageCode.getMyTitles(250))
            ) { _, which ->
                if (which == 0) {
                    if (Password.hasCommonPassword()) {
                        Password.addCommonLock(dialogId)
                        listView.adapter.update(true)
                    } else {
                        presentFragment(SecretPasscodeScreen(Password.editorForCommonLock(dialogId), SecretPasswordType.SET_NEW))
                    }
                } else {
                    presentFragment(SecretPasscodeScreen(Password.editorForIndividualLock(dialogId), SecretPasswordType.SET_NEW))
                }
            }
            .show()
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean = false
}
