package org.fenixuz.ui.secret_chat

import android.content.Context
import com.google.gson.Gson
import org.fenixuz.ui.lock.LockCredential
import org.fenixuz.ui.lock.LockEditor
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig

/**
 * Storage + folder logic for the secret (passcode-locked) chat folder.
 *
 * Secret chats are regular dialogs moved into [SECRET_FOLDER_ID]. They are revealed only after the
 * passcode is entered on the lock screen. The passcode and the set of secret dialog ids are kept
 * on-device in the shared "db" preferences — no network, no Firebase.
 *
 * The server does NOT persist folder id 100, so after a re-login every dialog comes back as folder 0.
 * To survive that, we keep our own id list locally and re-assert folder 100 on every [MessagesController.sortDialogs]
 * via [getSecretIdsArray].
 */
object SecretPassword {

    const val SECRET_FOLDER_ID = 100
    private const val SECRET_PASSWORD_LOCATE = "secret_password"
    private const val SECRET_IDS_LOCATE = "secret_dialog_ids"

    private val sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val secretIds: MutableSet<Long> = loadSecretIds()

    /** Immutable snapshot iterated on the (hot) sortDialogs path — replaced atomically, never mutated in place. */
    @Volatile
    private var secretIdsArray: LongArray = secretIds.toLongArray()

    // region passcode

    fun changePassword(password: String, type: Int, fingerPrint: Boolean) {
        val json = gson.toJson(SecretPasswordModule(password, type, fingerPrint))
        sharedPreferences.edit().putString(SECRET_PASSWORD_LOCATE, json).apply()
    }

    fun getPassword(): SecretPasswordModule? {
        val json = sharedPreferences.getString(SECRET_PASSWORD_LOCATE, null)
        if (json.isNullOrEmpty()) {
            return null
        }
        return try {
            gson.fromJson(json, SecretPasswordModule::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun checkPassword(password: String): Boolean {
        return getPassword()?.password == password
    }

    fun hasPassword(): Boolean {
        return getPassword() != null
    }

    /** Disables the feature: drop the passcode, move every secret chat back to the main list. */
    fun removePassword() {
        sharedPreferences.edit().remove(SECRET_PASSWORD_LOCATE).apply()
        resetAllSecretDialogs()
    }

    /** Adapts this store to the shared lock screen. */
    fun credential(): LockCredential = object : LockCredential {
        override val type: Int? get() = getPassword()?.type
        // Biometric unlock is automatic, same as the per-chat lock: offered whenever the device supports
        // strong biometrics (the passcode always remains as the fallback). Previously this read the stored
        // per-setup flag, which stayed false, so the secret folder never showed fingerprint at all.
        override val fingerPrint: Boolean get() = org.fenixuz.utils.Password.isFingerprintHardwareAvailable()
        override fun check(input: String): Boolean = checkPassword(input)
    }

    /** Adapts this store to the shared passcode-creation screen. */
    fun editor(): LockEditor = object : LockEditor {
        override fun hasPassword(): Boolean = getPassword() != null
        override fun currentType(): Int? = getPassword()?.type
        override fun currentFingerPrint(): Boolean = getPassword()?.fingerPrint ?: false
        override fun check(input: String): Boolean = checkPassword(input)
        override fun save(password: String, type: Int, fingerPrint: Boolean) = changePassword(password, type, fingerPrint)
        override fun remove() = removePassword()
    }

    // endregion

    // region secret dialog ids

    private fun loadSecretIds(): MutableSet<Long> {
        val json = sharedPreferences.getString(SECRET_IDS_LOCATE, null)
        if (json.isNullOrEmpty()) {
            return mutableSetOf()
        }
        return try {
            (gson.fromJson(json, LongArray::class.java) ?: LongArray(0)).toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun persist() {
        secretIdsArray = secretIds.toLongArray()
        sharedPreferences.edit().putString(SECRET_IDS_LOCATE, gson.toJson(secretIdsArray)).apply()
        // Novagram: a chat just moved into / out of the secret folder — rebuild the account's Contacts
        // sections (which now skip secret contacts) so the change shows immediately, not only on next sync.
        // Posted to the main thread by the controller, so calling it from these synchronized methods is safe.
        ContactsController.getInstance(UserConfig.selectedAccount).rebuildSecretFilteredSections()
    }

    fun isSecret(dialogId: Long): Boolean = secretIdsArray.contains(dialogId)

    /** Used by sortDialogs to re-apply folder 100 after the server resets it. */
    fun getSecretIdsArray(): LongArray = secretIdsArray

    @Synchronized
    fun addSecret(dialogId: Long) {
        if (secretIds.add(dialogId)) {
            persist()
        }
    }

    @Synchronized
    fun removeSecret(dialogId: Long) {
        if (secretIds.remove(dialogId)) {
            persist()
        }
    }

    /** Moves every tracked secret dialog back to the main list (folder 0) and forgets them. */
    @Synchronized
    fun resetAllSecretDialogs() {
        val ids = ArrayList(secretIds)
        secretIds.clear()
        persist()
        if (ids.isNotEmpty()) {
            MessagesController.getInstance(UserConfig.selectedAccount)
                .addDialogToFolder(ids, 0, -1, null, 0)
        }
    }

    // endregion
}

data class SecretPasswordModule(var password: String, var type: Int, var fingerPrint: Boolean)
