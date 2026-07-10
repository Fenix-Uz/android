package org.fenixuz.utils

import android.content.Context
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.Base64
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import java.util.concurrent.ConcurrentHashMap

/**
 * Novagram "Auto text" — per-chat signature appended to every outgoing message.
 *
 * Each dialog stores `{active, text, entities}` (text + its styling/custom-emoji entities). When active:
 *  - text messages get the styled signature concatenated at send time (clean, no edit);
 *  - media messages get it appended to the caption via an edit-after-send (see [editMedia]);
 *  - if the configured text is EMPTY, the chat's public link is appended instead (pro parity).
 *
 * Optimised over pro's `AutoPostEditorUtil`:
 *  - storage is a compact per-dialog [SerializedData] blob (Base64 in the "db" pref) — NO Gson/TLRPC.Message
 *    serialization, NO InstanceCreators;
 *  - an in-memory [ConcurrentHashMap] cache makes [getStyledText]/[isActive] O(1) on the send hot path;
 *  - the styled CharSequence is rebuilt with Telegram's OWN [MessageObject.addEntitiesToText] +
 *    [MessageObject.replaceAnimatedEmoji] helpers instead of a hand-rolled 140-line span switch.
 *
 * Device-only, no Firebase.
 */
object AutoTextAppender {

    private const val PREF = "db"
    private fun keyFor(dialogId: Long) = "auto_text_$dialogId"

    class Entry(
        @JvmField val active: Boolean,
        @JvmField val text: String,
        @JvmField val entities: ArrayList<TLRPC.MessageEntity>
    )

    private val EMPTY = Entry(false, "", ArrayList())
    private val cache = ConcurrentHashMap<Long, Entry>()

    private fun prefs() =
        ApplicationLoader.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun get(dialogId: Long): Entry {
        cache[dialogId]?.let { return it }
        val entry = load(dialogId)
        cache[dialogId] = entry
        return entry
    }

    fun isActive(dialogId: Long): Boolean = get(dialogId).active

    fun setActive(dialogId: Long, active: Boolean) {
        val e = get(dialogId)
        save(dialogId, active, e.text, e.entities)
    }

    private fun load(dialogId: Long): Entry {
        val raw = prefs().getString(keyFor(dialogId), null) ?: return EMPTY
        return try {
            val data = SerializedData(Base64.decode(raw, Base64.DEFAULT))
            val active = data.readInt32(false) == 1
            val text = data.readString(false) ?: ""
            val count = data.readInt32(false)
            val entities = ArrayList<TLRPC.MessageEntity>(maxOf(0, count))
            for (i in 0 until count) {
                val constructor = data.readInt32(false)
                val e = TLRPC.MessageEntity.TLdeserialize(data, constructor, false)
                if (e != null) entities.add(e)
            }
            data.cleanup()
            Entry(active, text, entities)
        } catch (e: Exception) {
            EMPTY
        }
    }

    fun save(dialogId: Long, active: Boolean, text: String, entities: ArrayList<TLRPC.MessageEntity>?) {
        val ents = entities ?: ArrayList()
        try {
            val calc = SerializedData(true)
            writeTo(calc, active, text, ents)
            val data = SerializedData(calc.length())
            writeTo(data, active, text, ents)
            val raw = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
            data.cleanup()
            calc.cleanup()
            prefs().edit().putString(keyFor(dialogId), raw).apply()
        } catch (e: Exception) {
        }
        cache[dialogId] = Entry(active, text, ents)
    }

    private fun writeTo(
        data: SerializedData, active: Boolean, text: String, entities: ArrayList<TLRPC.MessageEntity>
    ) {
        data.writeInt32(if (active) 1 else 0)
        data.writeString(text)
        data.writeInt32(entities.size)
        for (e in entities) e.serializeToStream(data)
    }

    // ------------------------------------------------------------------ append

    /**
     * The styled signature to append for [dialogId] — appended INLINE (no separator) right after the message,
     * matching pro; the user adds their own leading space/newline in the composer if they want one. Empty when
     * the chat has Auto text off. Empty configured text → the chat's public link.
     */
    fun getStyledText(dialogId: Long, fontMetrics: Paint.FontMetricsInt): CharSequence {
        val e = get(dialogId)
        if (!e.active) return ""
        val content: CharSequence = if (e.text.isEmpty()) getChatLink(dialogId) else buildStyled(e.text, e.entities, fontMetrics)
        if (TextUtils.isEmpty(content)) return ""
        return content
    }

    private fun buildStyled(
        text: String, entities: ArrayList<TLRPC.MessageEntity>, fontMetrics: Paint.FontMetricsInt
    ): CharSequence {
        return try {
            var m: CharSequence = SpannableStringBuilder(text)
            m = Emoji.replaceEmoji(m, fontMetrics, false)
            // out=false, photoViewer=false → mono/code runs render with key_chat_messageTextIn (theme-aware,
            // dark in light mode / light in dark mode). The old photoViewer=true forced type 2 = pure white
            // (URLSpanMono/CodeHighlighting), which is invisible on the light editor/composer background.
            MessageObject.addEntitiesToText(m, entities, false, false, false, false)
            m = MessageObject.replaceAnimatedEmoji(m, entities, fontMetrics)
            m
        } catch (e: Exception) {
            text
        }
    }

    /** The styled text to pre-fill the composer's edit field (no link default, no newline). */
    fun getEditPrefill(dialogId: Long, fontMetrics: Paint.FontMetricsInt): CharSequence {
        val e = get(dialogId)
        return if (e.text.isEmpty()) "" else buildStyled(e.text, e.entities, fontMetrics)
    }

    /** The chat link shown as the composer hint / used as the empty-text default. */
    fun chatLinkHint(dialogId: Long): CharSequence = getChatLink(dialogId)

    private fun getChatLink(dialogId: Long): CharSequence {
        if (DialogObject.isUserDialog(dialogId)) return ""
        val chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId) ?: return ""
        val username = ChatObject.getPublicUsername(chat)
        return if (username != null) "https://t.me/$username" else ""
    }

    // ------------------------------------------------------------------ media

    /**
     * Appends the signature to a just-sent MEDIA message's caption via an edit. Called from
     * ChatActivity's messageReceivedByServer. No-op for text/stickers/forwards or when inactive.
     * For albums, only caption-bearing items are touched (avoids a signature on every image).
     */
    fun editMedia(msg: MessageObject?, dialogId: Long) {
        if (msg == null) return
        val mo = msg.messageOwner ?: return
        if (!isActive(dialogId)) return
        val media = mo.media
        val hasMedia = media != null && (media.photo != null || media.document != null)
        if (!hasMedia) return
        if (media!!.document != null && media.document.mime_type == "application/x-tgsticker") return
        if (mo.fwd_from != null) return

        val fontMetrics = Theme.chat_msgTextPaint.fontMetricsInt
        val existing = mo.message
        val hasCaption = !existing.isNullOrEmpty()
        // Album item with no caption → skip (the album's caption-bearer gets the signature instead).
        if (!hasCaption && mo.grouped_id != 0L) return

        val newCaption: CharSequence = if (hasCaption) {
            val added = getStyledText(dialogId, fontMetrics)
            if (TextUtils.isEmpty(added)) return
            var m: CharSequence = SpannableStringBuilder(existing)
            m = Emoji.replaceEmoji(m, fontMetrics, false)
            MessageObject.addEntitiesToText(m, mo.entities, true, false, true, false)
            m = MessageObject.replaceAnimatedEmoji(m, mo.entities, fontMetrics)
            TextUtils.concat(m, added)
        } else {
            val raw = getStyledText(dialogId, fontMetrics)
            if (TextUtils.isEmpty(raw)) return
            raw
        }

        msg.editingMessage = newCaption
        try {
            SendMessagesHelper.getInstance(UserConfig.selectedAccount)
                .editMessage(msg, null, null, null, null, null, null, false, msg.hasMediaSpoilers(), msg)
        } catch (e: Exception) {
        }
    }
}
