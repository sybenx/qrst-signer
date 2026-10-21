package cc.aaro.qrst.vault

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cc.aaro.qrst.core.Json
import cc.aaro.qrst.core.Nip19
import cc.aaro.qrst.core.publicKeyHex
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One identity held on this device. Everything here but `ct`/`iv` is public. */
data class VaultEntry(
    val id: String,
    val pub: String,
    val label: String,
    /** Arrived by QRST, so this device is receive-only for it until the user allows a send (§14). */
    val viaTransfer: Boolean,
    val addedAt: Long,
    internal val ct: String,
    internal val iv: String,
) {
    val npub: String get() = Nip19.npub(pub)
}

/**
 * Nostr keys, encrypted under an AES-GCM key that lives in the Android Keystore and
 * never leaves it. Where the device has a lock screen, that key works only for ten
 * seconds after the user authenticates, so a key can be stored or read only right
 * after a fingerprint, face or screen-lock check (profiles/nostr-nsec.md, "Before
 * release"). The public half is kept in the clear, so listing keys needs no unlock.
 * App data is excluded from backups and device transfer.
 */
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    /** True when storing or reading a key needs the user to authenticate first. */
    val needsAuth: Boolean get() = keyguard.isDeviceSecure

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun aliasFor(auth: Boolean) = if (auth) "qrst-vault-auth-v1" else "qrst-vault-open-v1"

    private fun key(auth: Boolean): SecretKey {
        val alias = aliasFor(auth)
        (keystore().getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (auth) {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(10, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    setInvalidatedByBiometricEnrollment(false)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    fun list(): List<VaultEntry> {
        val raw = Json.parse(prefs.getString("entries", "[]") ?: "[]") as? List<*> ?: return emptyList()
        return raw.mapNotNull { m ->
            m as? Map<*, *> ?: return@mapNotNull null
            VaultEntry(
                m["id"] as String, m["pub"] as String, m["label"] as String,
                m["viaTransfer"] as Boolean, m["addedAt"] as Long, m["ct"] as String, m["iv"] as String,
            )
        }
    }

    private fun save(entries: List<VaultEntry>) {
        val raw = entries.map {
            mapOf(
                "id" to it.id, "pub" to it.pub, "label" to it.label, "viaTransfer" to it.viaTransfer,
                "addedAt" to it.addedAt, "ct" to it.ct, "iv" to it.iv,
            )
        }
        prefs.edit().putString("entries", Json.stringify(raw)).commit()
    }

    /** Stores a key. Call within ten seconds of a successful authentication when [needsAuth]. Returns null if already held. */
    fun add(secret: ByteArray, label: String, viaTransfer: Boolean): VaultEntry? {
        val pub = publicKeyHex(secret)
        val existing = list()
        if (existing.any { it.pub == pub }) return null
        val auth = needsAuth
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(auth)) }
        val ct = cipher.doFinal(secret)
        val id = pub.take(16)
        val entry = VaultEntry(
            id, pub, label.ifBlank { Nip19.npub(pub).take(12) + "…" }, viaTransfer, System.currentTimeMillis() / 1000,
            Base64.getEncoder().encodeToString(ct), Base64.getEncoder().encodeToString(cipher.iv),
        )
        prefs.edit().putBoolean("auth:$id", auth).commit()
        save(existing + entry)
        return entry
    }

    /** The secret key. Call within ten seconds of a successful authentication when the entry needs it. */
    fun reveal(entry: VaultEntry): ByteArray {
        val auth = prefs.getBoolean("auth:${entry.id}", true)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(auth), GCMParameterSpec(128, Base64.getDecoder().decode(entry.iv)))
        val secret = cipher.doFinal(Base64.getDecoder().decode(entry.ct))
        check(publicKeyHex(secret) == entry.pub) { "vault: stored key does not match its public key" }
        return secret
    }

    fun rename(entry: VaultEntry, label: String) = save(list().map { if (it.id == entry.id) it.copy(label = label) else it })

    /** Deletes this device's copy only. Nostr has no revocation; any other copy keeps working (§14). */
    fun delete(entry: VaultEntry) {
        save(list().filter { it.id != entry.id })
        prefs.edit().remove("auth:${entry.id}").commit()
    }
}
