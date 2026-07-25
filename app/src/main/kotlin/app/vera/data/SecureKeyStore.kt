package app.vera.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.vera.core.llm.CloudCatalog
import app.vera.core.llm.CloudConfig
import app.vera.core.llm.CloudProvider

/**
 * Stores the user's own API keys, encrypted at rest with a hardware-backed master key
 * (AES-256-GCM via Jetpack Security). Keys are never logged, never sent anywhere except the
 * provider they belong to, and can be deleted from Settings at any time.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vera_secure",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun keyName(p: CloudProvider) = "api_key_${p.name.lowercase()}"

    fun apiKey(p: CloudProvider): String = prefs.getString(keyName(p), "").orEmpty()

    fun setApiKey(p: CloudProvider, value: String) {
        prefs.edit().apply {
            if (value.isBlank()) remove(keyName(p)) else putString(keyName(p), value.trim())
        }.apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    // ---- non-secret preferences (kept here so config is read in one place) ----

    var enabled: Boolean
        get() = prefs.getBoolean("cloud_enabled", false)
        set(v) = prefs.edit().putBoolean("cloud_enabled", v).apply()

    var provider: CloudProvider
        get() = runCatching {
            CloudProvider.valueOf(prefs.getString("cloud_provider", null) ?: "")
        }.getOrDefault(CloudProvider.ANTHROPIC)
        set(v) = prefs.edit().putString("cloud_provider", v.name).apply()

    var modelId: String
        get() = prefs.getString("cloud_model", null) ?: CloudCatalog.default(provider).id
        set(v) = prefs.edit().putString("cloud_model", v).apply()

    fun config(): CloudConfig {
        val p = provider
        return CloudConfig(
            enabled = enabled,
            provider = p,
            modelId = modelId.takeIf { CloudCatalog.byId(it)?.provider == p } ?: CloudCatalog.default(p).id,
            hasKey = apiKey(p).isNotBlank()
        )
    }
}
