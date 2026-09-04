package cn.edu.xyc.campus.data.remote

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 门户登录加密工具。
 * 完整复现 ehallmobile.xyc.edu.cn/mobile 首页 app.js 中 reEncrypt() 的逻辑：
 *
 * 1. 每次登录随机生成 24 位字母数字密钥 rk
 * 2. userName   = 3DES-ECB-PKCS7(学号, rk)      输出 hex
 * 3. userPassWord = 3DES-ECB-PKCS7(密码, rk)    输出 hex
 * 4. key        = 3DES-ECB-PKCS7(rk, MASTER_KEY) 输出 hex（主密钥写死于门户 JS）
 *
 * 服务端用 MASTER_KEY 解开 key 得到 rk，再解出学号与密码。
 */
data class EncryptedCredential(
    val userName: String,
    val userPassWord: String,
    val key: String,
)

object CryptoUtil {

    /** 门户 JS 写死的 3DES 主密钥（24 字节） */
    private const val MASTER_KEY = "dc651e062a92599aa1230153"

    private const val ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun randomKey24(): ByteArray =
        ByteArray(24) { ALNUM[Random.nextInt(ALNUM.length)].code.toByte() }

    /** 等价于 CryptoJS.TripleDES.encrypt(plain, Utf8Key, {ECB, Pkcs7}).ciphertext.toString() */
    fun des3Hex(plain: String, key: ByteArray): String {
        val cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DESede"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun reEncrypt(account: String, password: String): EncryptedCredential {
        val rk = randomKey24()
        return EncryptedCredential(
            userName = des3Hex(account, rk),
            userPassWord = des3Hex(password, rk),
            key = des3Hex(rk.decodeToString(), MASTER_KEY.toByteArray(Charsets.UTF_8)),
        )
    }
}
