package cn.edu.xyc.campus.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredCredential(
    val account: String,
    val password: String,
)

/**
 * 加密凭证存储：Android Keystore 主密钥 + EncryptedSharedPreferences。
 * 解决"清理后台后需重新输入账号密码"的问题——启动时静默重登。
 */
object CredStore {

    private const val FILE = "xyc_secure_prefs"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_PASSWORD = "password"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(account: String, password: String) {
        prefs.edit()
            .putString(KEY_ACCOUNT, account)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun load(): StoredCredential? {
        val acc = prefs.getString(KEY_ACCOUNT, null) ?: return null
        val pwd = prefs.getString(KEY_PASSWORD, null) ?: return null
        if (acc.isBlank() || pwd.isBlank()) return null
        return StoredCredential(acc, pwd)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
