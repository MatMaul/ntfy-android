package io.heckel.ntfy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.preference.*
import androidx.preference.Preference.OnPreferenceClickListener
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.data.Repository
import io.heckel.ntfy.util.formatDateShort
import io.heckel.ntfy.util.toPriorityString

class SettingsActivity : AppCompatActivity() {
    private val repository by lazy { (application as Application).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Log.d(TAG, "Create $this")

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_layout, SettingsFragment(repository, supportFragmentManager))
                .commit()
        }

        // Action bar
        title = getString(R.string.settings_title)

        // Show 'Back' button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment(val repository: Repository, private val supportFragmentManager: FragmentManager) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.main_preferences, rootKey)

            // Important note: We do not use the default shared prefs to store settings. Every
            // preferenceDataStore is overridden to use the repository. This is convenient, because
            // everybody has access to the repository.

            // Notifications muted until (global)
            val mutedUntilPrefId = context?.getString(R.string.settings_notifications_muted_until_key) ?: return
            val mutedUntilSummary = { s: Long ->
                when (s) {
                    0L -> getString(R.string.settings_notifications_muted_until_enabled)
                    1L -> getString(R.string.settings_notifications_muted_until_disabled_forever)
                    else -> {
                        val formattedDate = formatDateShort(s)
                        getString(R.string.settings_notifications_muted_until_disabled_until, formattedDate)
                    }
                }
            }
            val mutedUntil: Preference? = findPreference(mutedUntilPrefId)
            mutedUntil?.preferenceDataStore = object : PreferenceDataStore() { } // Dummy store to protect from accidentally overwriting
            mutedUntil?.summary = mutedUntilSummary(repository.getGlobalMutedUntil())
            mutedUntil?.onPreferenceClickListener = OnPreferenceClickListener {
                if (repository.getGlobalMutedUntil() > 0) {
                    repository.setGlobalMutedUntil(0)
                    mutedUntil?.summary = mutedUntilSummary(0)
                } else {
                    val notificationFragment = NotificationFragment()
                    notificationFragment.settingsListener = object : NotificationFragment.NotificationSettingsListener {
                        override fun onNotificationMutedUntilChanged(mutedUntilTimestamp: Long) {
                            repository.setGlobalMutedUntil(mutedUntilTimestamp)
                            mutedUntil?.summary = mutedUntilSummary(mutedUntilTimestamp)
                        }
                    }
                    notificationFragment.show(supportFragmentManager, NotificationFragment.TAG)
                }
                true
            }

            // Minimum priority
            val minPriorityPrefId = context?.getString(R.string.settings_notifications_min_priority_key) ?: return
            val minPriority: ListPreference? = findPreference(minPriorityPrefId)
            minPriority?.value = repository.getMinPriority().toString()
            minPriority?.preferenceDataStore = object : PreferenceDataStore() {
                override fun putString(key: String?, value: String?) {
                    val minPriorityValue = value?.toIntOrNull() ?:return
                    repository.setMinPriority(minPriorityValue)
                }
                override fun getString(key: String?, defValue: String?): String {
                    return repository.getMinPriority().toString()
                }
            }
            minPriority?.summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                val minPriorityValue = pref.value.toIntOrNull() ?: 1 // 1/low means all priorities
                when (minPriorityValue) {
                    1 -> getString(R.string.settings_notifications_min_priority_summary_any)
                    5 -> getString(R.string.settings_notifications_min_priority_summary_max)
                    else -> {
                        val minPriorityString = toPriorityString(minPriorityValue)
                        getString(R.string.settings_notifications_min_priority_summary_x_or_higher, minPriorityValue, minPriorityString)
                    }
                }
            }

            // Broadcast enabled
            val broadcastEnabledPrefId = context?.getString(R.string.settings_advanced_broadcast_key) ?: return
            val broadcastEnabled: SwitchPreference? = findPreference(broadcastEnabledPrefId)
            broadcastEnabled?.isChecked = repository.getBroadcastEnabled()
            broadcastEnabled?.preferenceDataStore = object : PreferenceDataStore() {
                override fun putBoolean(key: String?, value: Boolean) {
                    repository.setBroadcastEnabled(value)
                }
                override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                    return repository.getBroadcastEnabled()
                }
            }
            broadcastEnabled?.summaryProvider = Preference.SummaryProvider<SwitchPreference> { pref ->
                if (pref.isChecked) {
                    getString(R.string.settings_advanced_broadcast_summary_enabled)
                } else {
                    getString(R.string.settings_advanced_broadcast_summary_disabled)
                }
            }

            // UnifiedPush enabled
            val upEnabledPrefId = context?.getString(R.string.settings_unified_push_enabled_key) ?: return
            val upEnabled: SwitchPreference? = findPreference(upEnabledPrefId)
            upEnabled?.isChecked = repository.getUnifiedPushEnabled()
            upEnabled?.preferenceDataStore = object : PreferenceDataStore() {
                override fun putBoolean(key: String?, value: Boolean) {
                    repository.setUnifiedPushEnabled(value)
                }
                override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                    return repository.getUnifiedPushEnabled()
                }
            }
            upEnabled?.summaryProvider = Preference.SummaryProvider<SwitchPreference> { pref ->
                if (pref.isChecked) {
                    getString(R.string.settings_unified_push_enabled_summary_on)
                } else {
                    getString(R.string.settings_unified_push_enabled_summary_off)
                }
            }

            // UnifiedPush Base URL
            val appBaseUrl = context?.getString(R.string.app_base_url) ?: return
            val upBaseUrlPrefId = context?.getString(R.string.settings_unified_push_base_url_key) ?: return
            val upBaseUrl: EditTextPreference? = findPreference(upBaseUrlPrefId)
            upBaseUrl?.text = repository.getUnifiedPushBaseUrl() ?: ""
            upBaseUrl?.preferenceDataStore = object : PreferenceDataStore() {
                override fun putString(key: String, value: String?) {
                    val baseUrl = value ?: return
                    repository.setUnifiedPushBaseUrl(baseUrl)
                }
                override fun getString(key: String, defValue: String?): String? {
                    return repository.getUnifiedPushBaseUrl()
                }
            }
            upBaseUrl?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                if (TextUtils.isEmpty(pref.text)) {
                    getString(R.string.settings_unified_push_base_url_default_summary, appBaseUrl)
                } else {
                    pref.text
                }
            }

            // Version
            val versionPrefId = context?.getString(R.string.settings_about_version_key) ?: return
            val versionPref: Preference? = findPreference(versionPrefId)
            val version = getString(R.string.settings_about_version_format, BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)
            versionPref?.summary = version
            versionPref?.onPreferenceClickListener = OnPreferenceClickListener {
                val context = context ?: return@OnPreferenceClickListener false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("app version", version)
                clipboard.setPrimaryClip(clip)
                Toast
                    .makeText(context, getString(R.string.settings_about_version_copied_to_clipboard_message), Toast.LENGTH_LONG)
                    .show()
                true
            }
        }
    }

    companion object {
        const val TAG = "NtfySettingsActivity"
    }
}
