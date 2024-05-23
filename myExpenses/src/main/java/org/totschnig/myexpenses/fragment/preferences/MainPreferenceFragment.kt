package org.totschnig.myexpenses.fragment.preferences

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.evernote.android.state.State
import com.evernote.android.state.StateSaver
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.form.SimpleFormDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.dialog.MoreInfoDialogFragment
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.AppDirHelper
import timber.log.Timber
import java.util.Locale

class MainPreferenceFragment : BasePreferenceFragment(),
    SimpleDialog.OnDialogResultListener, MultiSelectListPreferenceDialogFragment2.OnClickListener {

    lateinit var adapter: PreferenceGroupAdapter

    var isSlideable: Boolean = true
        set(value) {
            field = value
            val preferenceAdapterPosition = highlightedKey?.let {
                adapter.getPreferenceAdapterPosition(
                    it
                )
            }
            if (!value) {
                view?.setBackgroundColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.settings_two_pane_background_color,
                        null
                    )
                )

                if (preferenceAdapterPosition != null) {
                    adapter.notifyItemChanged(preferenceAdapterPosition)
                }
            }
        }

    @State
    var highlightedKey: String? = null

    fun onLoadPreference(key: String) {
        val oldPosition = highlightedKey?.let { adapter.getPreferenceAdapterPosition(it) }
        val newPosition = adapter.getPreferenceAdapterPosition(key)
        highlightedKey = key
        if (oldPosition != null) {
            adapter.notifyItemChanged(oldPosition)
        }
        adapter.notifyItemChanged(newPosition)
    }

    override fun overrideTheme(): Int {
        return R.style.MyPreferenceHeaderTheme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            StateSaver.restoreInstanceState(this, it)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAppDirInfo()
        viewModel.loadAppData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        StateSaver.saveInstanceState(this, outState)
    }

    override val preferencesResId = R.xml.preference_headers

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        requirePreference<Preference>(PrefKey.CATEGORY_IO).title = ioTitle
        requirePreference<Preference>(PrefKey.CATEGORY_BACKUP_RESTORE).title = backupRestoreTitle
        requirePreference<Preference>(PrefKey.CATEGORY_SECURITY).title = protectionTitle
        requirePreference<Preference>(PrefKey.BANKING_FINTS).summary =
            "FinTS (${Locale.GERMANY.displayCountry})"

        viewModel.appData.observe(this) {
            with(requirePreference<MultiSelectListPreference>(PrefKey.MANAGE_APP_DIR_FILES)) {
                if (it.isEmpty()) {
                    isVisible = false
                } else {
                    isVisible = true
                    entries = it.map {
                        "${it.first} (${
                            Formatter.formatFileSize(
                                requireContext(),
                                it.second
                            )
                        })"
                    }.toTypedArray()
                    entryValues = it.map { it.first }.toTypedArray()
                }
            }
        }
    }


    //MultiSelectListPreferenceDialogFragmentWithNeutralAction
    override fun onClick(preference: String, values: Set<String>, which: Int) {
        if (values.isNotEmpty() && preference == prefHandler.getKey(PrefKey.MANAGE_APP_DIR_FILES)) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                ConfirmationDialogFragment.newInstance(Bundle().apply {
                    putStringArray(
                        PreferencesBackupRestoreFragment.KEY_CHECKED_FILES,
                        values.toTypedArray()
                    )
                    putString(
                        ConfirmationDialogFragment.KEY_MESSAGE,
                        resources.getQuantityString(
                            R.plurals.delete_files_confirmation_message,
                            values.size,
                            values.size
                        )
                    )
                    putInt(
                        ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
                        R.id.DELETE_FILES_COMMAND
                    )
                    putInt(
                        ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL,
                        R.string.menu_delete
                    )
                })
                    .show(parentFragmentManager, "CONFIRM_DELETE")
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                val appDir = viewModel.appDirInfo.value?.getOrThrow()!!
                startActivity(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/plain"
                    val arrayList = ArrayList(
                        values.mapNotNull { file ->
                            appDir.documentFile.findFile(file)?.uri?.let {
                                AppDirHelper.ensureContentUri(it, requireContext())
                            }
                        })
                    Timber.d("ATTACHMENTS" + arrayList.joinToString())
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        arrayList
                    )
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                })
            }
        }
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen) =
        object : PreferenceGroupAdapter(preferenceScreen) {

            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                if (!isSlideable && getItem(position)?.key == highlightedKey) {
                    holder.itemView.setBackgroundColor(
                        ResourcesCompat.getColor(resources, R.color.activatedBackground, null)
                    )
                }
            }
        }.also {
            adapter = it
        }

    override fun onPreferenceTreeClick(preference: Preference) = when {
        matches(preference, PrefKey.CONTRIB_PURCHASE) && !licenceHandler.hasAnyLicence -> {
            preferenceActivity.dispatchCommand(R.id.CONTRIB_INFO_COMMAND, null)
            true
        }

        matches(preference, PrefKey.MORE_INFO_DIALOG) -> {
            MoreInfoDialogFragment().show(parentFragmentManager, "MORE_INFO")
            true
        }

        matches(preference, PrefKey.HACK_MODE) -> {
            if (!prefHandler.getBoolean(PrefKey.HACK_MODE, false)) {
                if (prefHandler.getBoolean(PrefKey.HACKED, false)) {
                    SimpleFormDialog.build()
                        .title(R.string.hack_mode_disable_title)
                        .msg(R.string.hack_mode_disable_message)
                        .pos(R.string.hack_mode_button_yes)
                        .neut()
                        .cancelable(false)
                        .show(this, KEY_HACKED_LICENCE_REMOVE)
                }
            }
            true
        }


        super.onPreferenceTreeClick(preference) -> true

        handleContrib(PrefKey.BANKING_FINTS, ContribFeature.BANKING, preference) -> true
        else -> false
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (dialogTag == KEY_HACKED_LICENCE_REMOVE) {
            if (which == SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            prefHandler.remove(PrefKey.NEW_LICENCE)
                            prefHandler.remove(PrefKey.LICENCE_EMAIL)
                            prefHandler.remove(PrefKey.HACKED)
                            licenceHandler.voidLicenceStatus(false)
                            true to getString(R.string.hack_licence_remove_success)
                        } catch (e: Exception) {
                            false to getString(R.string.hack_licence_remove_fail)
                        }
                    }
                }
            } else if (which == SimpleDialog.OnDialogResultListener.BUTTON_NEUTRAL) {
                prefHandler.putBoolean(PrefKey.HACK_MODE, true)
                val batteryControl = findPreference<SwitchPreferenceCompat>(PrefKey.HACK_MODE)
                if (batteryControl != null) {
                    batteryControl.isChecked = true
                }
            }
        }
        return true
    }

    companion object {
        const val KEY_HACKED_LICENCE_REMOVE = "removeHackLicence"
    }
}