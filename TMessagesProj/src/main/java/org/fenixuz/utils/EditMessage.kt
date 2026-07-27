package org.fenixuz.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.tgnet.TLRPC
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.fenixuz.ui.message_history.MessageHistoryModule
import java.text.SimpleDateFormat
import java.util.*

object EditMessage {

    var editMode = false
    val gson = Gson()

    // Rolling-window cap for the "edited_messages" store: keep at most the newest MAX_ENTRIES entries
    // across all chats. It NEVER resets to empty — once the cap is exceeded only the single oldest
    // entry rolls off per save. Bounds prefs growth so the storage queue never does O(history) work
    // per edit. Just a constant — raising it later needs no migration (the JSON format is unchanged).
    // Kept lower than mark_delete's cap (20000) on purpose: each entry carries the FULL pre-edit message
    // text (up to ~4 KB), so this store's blob lives in the shared "db" prefs and must stay modest.
    private const val MAX_ENTRIES = 10000

    private var sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private var editor = sharedPreferences.edit()

    var messages = ArrayList<MessageObject>()

    // In-memory cache of the parsed "edited_messages" store, mirroring DeletedMsg. It is written ONLY
    // through saveAll() (the single write funnel), so it stays authoritative and can never go stale.
    // Reads (getHistory) no longer parse the whole JSON blob on every call. Volatile + double-checked
    // load keeps the first access lock-free once warmed.
    @Volatile
    private var cachedEdits: ArrayList<MessageHistoryModule>? = null

    init {
        editMode = sharedPreferences.getBoolean("edit", false)
    }

    private fun ensureLoaded() {
        if (cachedEdits != null) {
            return
        }
        synchronized(this) {
            if (cachedEdits != null) {
                return
            }
            cachedEdits = parseFromPrefs()
        }
    }

    private fun parseFromPrefs(): ArrayList<MessageHistoryModule> {
        val out = ArrayList<MessageHistoryModule>()
        val str = sharedPreferences.getString("edited_messages", "")
        if (!str.isNullOrEmpty()) {
            try {
                val type: TypeToken<*> = object : TypeToken<List<MessageHistoryModule?>?>() {}
                val fromJson = gson.fromJson<ArrayList<MessageHistoryModule>>(str, type.type)
                if (fromJson != null) {
                    for (item in fromJson) {
                        if (item != null) {
                            out.add(item)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    // Preload the edited-messages cache OFF the UI thread (called once at startup on a dedicated
    // low-priority thread — never the shared globalQueue). It ONLY parses (no write): the disk blob
    // shrinks on the next real edit-save via the cap in saveAll, which runs on the storage queue — so
    // warm-up adds zero cross-thread writes and cannot race the edit path.
    fun warmUp() {
        ensureLoaded()
    }

    fun getEditModeFromCache(): Boolean {
        return sharedPreferences.getBoolean("edit", false)
    }

    fun changeEditMode(mode: Boolean) {
        editMode = mode
        editor.putBoolean("edit", editMode)
        // editMode is served from the in-memory field, so the async write is safe.
        editor.apply()
    }

    fun getAllEditedMessages(): ArrayList<MessageHistoryModule> {
        ensureLoaded()
        // Independent copy: callers (saveEditedMsg) mutate then pass back, so the cache must stay untouched.
        return ArrayList(cachedEdits ?: ArrayList())
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
    }

    fun getHistory(dialogId: Long, messageId: Int): ArrayList<MessageHistoryModule> {
        val allEditedMessages = ArrayList<MessageHistoryModule>()
        allEditedMessages.addAll((getAllEditedMessages().filter { it.dialogId == dialogId && it.msgId == messageId }
            .sortedByDescending { it.editedTime ?: 0 }))
        return allEditedMessages
    }

    fun saveEditedMsg(oldMessage: TLRPC.Message?, dialogId: Long) {
        val allEditedMessages = getAllEditedMessages()
        var oldText = oldMessage?.message ?: ""
        for (message in messages) {
            val str = message.previousMessage
            if (message.id == (oldMessage?.id ?: 0) && str != null) {
                oldText = str
                break
            }
        }
        val editedTime: Long
        val editDate = oldMessage?.edit_date ?: 0
        val firstDate = oldMessage?.date ?: 0
        editedTime = if (editDate != 0) {
            editDate.toLong()
        } else if (firstDate != 0) {
            firstDate.toLong()
        } else {
            Date().time / 1000
        }
        allEditedMessages.add(
            MessageHistoryModule(
                dialogId,
                oldMessage?.id,
                oldText,
                editedTime * 1000
            )
        )
        saveAll(allEditedMessages)
    }

    private fun saveAll(all: ArrayList<MessageHistoryModule>) {
        // Rolling-window cap: entries are appended newest-last, so keep only the last MAX_ENTRIES and
        // drop from the front (oldest). This never empties the store; only the oldest overflow rolls off.
        val capped: ArrayList<MessageHistoryModule> =
            if (all.size > MAX_ENTRIES) {
                ArrayList(all.subList(all.size - MAX_ENTRIES, all.size))
            } else {
                all
            }
        val str = gson.toJson(capped)
        editor.putString("edited_messages", str)
        // apply() (async) not commit() (sync): this runs on the storage queue when an edit arrives, and
        // the authoritative view is the in-memory cache refreshed below, so the async disk write is safe.
        editor.apply()
        synchronized(this) {
            cachedEdits = ArrayList(capped)
        }
    }
}
