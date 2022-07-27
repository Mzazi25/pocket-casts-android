package au.com.shiftyjelly.pocketcasts.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.StatFs
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.file.FileStorage
import au.com.shiftyjelly.pocketcasts.repositories.file.FolderLocation
import au.com.shiftyjelly.pocketcasts.repositories.file.StorageException
import au.com.shiftyjelly.pocketcasts.repositories.file.StorageOptions
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.utils.FileUtil
import au.com.shiftyjelly.pocketcasts.utils.StringUtil
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import au.com.shiftyjelly.pocketcasts.views.extensions.findToolbar
import au.com.shiftyjelly.pocketcasts.views.extensions.setup
import au.com.shiftyjelly.pocketcasts.views.helper.HasBackstack
import au.com.shiftyjelly.pocketcasts.views.helper.NavigationIcon.BackArrow
import au.com.shiftyjelly.pocketcasts.views.helper.UiUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

@AndroidEntryPoint
class StorageSettingsFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    Preference.OnPreferenceChangeListener,
    CoroutineScope,
    HasBackstack {

    @Inject lateinit var podcastManager: PodcastManager
    @Inject lateinit var episodeManager: EpisodeManager
    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var settings: Settings
    @Inject lateinit var fileStorage: FileStorage
    @Inject lateinit var theme: Theme

    private var storageChoicePreference: ListPreference? = null
    private var storageFolderPreference: EditTextPreference? = null
    private var useMobileDataPreference: SwitchPreference? = null

    private var foldersAvailable: List<FolderLocation>? = null
    private var permissionRequestedForPath: String? = null

    companion object {
        const val PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 241
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findToolbar().setup(title = getString(LR.string.settings_title_storage), navigationIcon = BackArrow, activity = activity, theme = theme)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_storage, rootKey)
        storageChoicePreference = preferenceManager.findPreference(Settings.PREFERENCE_STORAGE_CHOICE)
        storageFolderPreference = preferenceManager.findPreference(Settings.PREFERENCE_STORAGE_CUSTOM_FOLDER)

        findPreference<Preference>("manualCleanup")?.setOnPreferenceClickListener { _ ->
            showDownloadedFiles()
            true
        }

        findPreference<Preference>("clearDownloadCache")?.setOnPreferenceClickListener {
            val tempPath = fileStorage.tempPodcastDirectory
            FileUtil.deleteDirectoryContents(tempPath.absolutePath)
            Toast.makeText(context, LR.string.settings_storage_clear_cache, Toast.LENGTH_SHORT).show()
            true
        }

        updateUseMobileData()
    }

    private fun updateUseMobileData() {
        useMobileDataPreference = preferenceManager.findPreference<SwitchPreference>("useMobileData")?.apply {
            isChecked = settings.warnOnMeteredNetwork()
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val warnMobileData = newValue as Boolean
                settings.setWarnOnMeteredNetwork(warnMobileData)
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupStorage()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun showDownloadedFiles() {
        val fragment = ManualCleanupFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(UR.id.frameChildFragment, fragment)
            .addToBackStack("podcastSelect")
            .commit()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (Settings.PREFERENCE_STORAGE_CHOICE == key) {
            changeStorageLabels()
        } else if (Settings.PREFERENCE_STORAGE_CUSTOM_FOLDER == key) {
            changeStorageLabels()
        }
    }

    private fun changeStorageLabels() {
        val storageFolderPreference = storageFolderPreference ?: return
        val space = calcSpaceString()
        preferenceManager.findPreference<PreferenceCategory>("storageTitle")?.title = getString(LR.string.settings_storage) + " - $space"

        if (settings.usingCustomFolderStorage()) {
            storageFolderPreference.summary = settings.getStorageCustomFolder()
            storageChoicePreference?.summary = getString(LR.string.settings_storage_custom_folder)
        } else {
            storageFolderPreference.summary = getString(LR.string.settings_storage_using, settings.getStorageChoiceName())
            storageChoicePreference?.summary = settings.getStorageChoiceName()
        }

        // Custom Folder Location shown?
        findPreference<PreferenceCategory>("storageTitle")?.let {
            if (settings.usingCustomFolderStorage()) {
                it.addPreference(storageFolderPreference)
            } else {
                it.removePreference(storageFolderPreference)
            }
        }
    }

    private fun calcSpaceString(): String {
        try {
            val file = fileStorage.baseStorageDirectory
            val stat = StatFs(file.absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            return getString(LR.string.settings_storage_free_out_of, Util.formattedBytes(free, context = requireContext()), Util.formattedBytes(total, context = requireContext()))
        } catch (e: Exception) {
            Timber.e(e, "Unable to calculate free space.")
            return ""
        }
    }

    private fun setupStorage() {
        // find all the places the user might want to store their podcasts, but still give them a custom folder option
        val storageOptions = StorageOptions()
        val foldersAvailable = storageOptions.getFolderLocations(activity)
        var optionsCount = foldersAvailable.size
        if (android.os.Build.VERSION.SDK_INT < 29) {
            optionsCount++
        }

        this.foldersAvailable = foldersAvailable
        val entries = arrayOfNulls<String>(optionsCount)
        val entryValues = arrayOfNulls<String>(optionsCount)
        var i = 0
        for (folderLocation in foldersAvailable) {
            entries[i] = folderLocation.label + ", " + getStorageSpaceString(folderLocation.filePath)
            entryValues[i] = folderLocation.filePath

            i++
        }
        if (android.os.Build.VERSION.SDK_INT < 29) {
            entries[i] = getString(LR.string.settings_storage_custom_folder) + "…"
            entryValues[i] = Settings.STORAGE_ON_CUSTOM_FOLDER
        }

        storageChoicePreference?.let {
            it.entries = entries
            it.entryValues = entryValues
            it.value = settings.getStorageChoice()
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val folderPathChosen = newValue as String
                if (folderPathChosen == Settings.STORAGE_ON_CUSTOM_FOLDER) {
                    try {
                        val baseDirectory = fileStorage.baseStorageDirectory
                        baseDirectory?.absolutePath?.let { basePath ->
                            settings.setStorageCustomFolder(basePath)
                            storageFolderPreference?.text = basePath
                        }
                    } catch (e: StorageException) {
                        UiUtil.displayAlertError(activity, getString(LR.string.settings_storage_folder_change_failed) + " " + e.message, null)
                        return@OnPreferenceChangeListener false
                    }
                } else {
                    // store the old folder value, this is still available until we set it below
                    val oldFolderValue = if (settings.usingCustomFolderStorage()) settings.getStorageCustomFolder() else settings.getStorageChoice()

                    // set the name for this folder
                    for (folder in foldersAvailable) {
                        if (folder.filePath == folderPathChosen) {
                            settings.setStorageChoice(folderPathChosen, folder.label)
                            break
                        }
                    }

                    // if it's a new folder, ask the user if they want to move their files there
                    movePodcastStorage(oldFolderValue, folderPathChosen)
                }
                true
            }

            if (android.os.Build.VERSION.SDK_INT >= 29 && settings.usingCustomFolderStorage()) {
                it.value = entryValues.first()
                UiUtil.displayAlert(requireContext(), getString(LR.string.settings_storage_sorry), getString(LR.string.settings_storage_android_10_custom), null)
            }
        }

        // Custom Folder Location
        storageFolderPreference?.onPreferenceChangeListener = this

        changeStorageLabels()
    }

    private fun getStorageSpaceString(path: String): String {
        try {
            val stat = StatFs(path)
            val free = stat.availableBlocksLong * stat.blockSizeLong
            return getString(LR.string.settings_storage_size_free, Util.formattedBytes(free, context = requireContext()))
        } catch (e: Exception) {
            return ""
        }
    }

    private fun movePodcastStorage(oldDirectory: String?, newDirectory: String) {
        if (oldDirectory == null || newDirectory != oldDirectory) {
            val activity = activity ?: return
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(LR.string.settings_storage_move_are_you_sure)
                .setMessage(LR.string.settings_storage_move_message)
                .setCancelable(true)
                .setPositiveButton(LR.string.settings_storage_move) { dialog, _ ->
                    dialog.dismiss()
                    movePodcasts(oldDirectory, newDirectory)
                }
                .setNegativeButton(LR.string.settings_storage_move_cancel) { dialog, _ -> dialog.cancel() }
            val alert = builder.create()
            alert.setOnShowListener {
                alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.getThemeColor(UR.attr.primary_text_01))
                alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.getThemeColor(UR.attr.primary_text_01))
            }
            alert.show()
        }
    }

    @Suppress("NAME_SHADOWING", "DEPRECATION")
    @OptIn(DelicateCoroutinesApi::class)
    private fun movePodcasts(oldDirectory: String?, newDirectory: String?) {
        val oldDirectory = oldDirectory ?: return
        val newDirectory = newDirectory ?: return
        LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Moving storage from $oldDirectory to $newDirectory")
        GlobalScope.launch(Dispatchers.Main) {
            val progressDialog = android.app.ProgressDialog.show(activity, "", getString(LR.string.settings_storage_move_podcasts), true, false)
            progressDialog.show()
            withContext(Dispatchers.IO) {
                fileStorage.moveStorage(File(oldDirectory), File(newDirectory), podcastManager, episodeManager)
            }
            UiUtil.hideProgressDialog(progressDialog)
            setupStorage()
        }
    }

    fun onPermissionGrantedStorage() {
        val storageFolderPreference = storageFolderPreference ?: return
        val path = permissionRequestedForPath
        if (path != null && path.isNotBlank()) {
            if (onPreferenceChange(storageFolderPreference, path)) {
                storageFolderPreference.text = path
                settings.setStorageCustomFolder(path)
            }
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference.key == storageFolderPreference?.key) {
            val newPath = newValue as String

            if (StringUtil.isBlank(newPath)) {
                return false
            }
            var oldDirectory: File? = null
            try {
                oldDirectory = fileStorage.baseStorageDirectory
            } catch (e: StorageException) {
                // ignore error
            }

            // validate the path
            if (StringUtil.isBlank(newPath)) {
                UiUtil.displayAlertError(activity, getString(LR.string.settings_storage_folder_blank), null)
                return false
            }

            val activity = activity
            if (activity != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE)
                permissionRequestedForPath = newPath
                return false
            }

            val newDirectory = File(newPath)
            if (!newDirectory.exists()) {
                val success = newDirectory.mkdirs()
                if (!success && !newDirectory.exists()) {
                    UiUtil.displayAlertError(activity, getString(LR.string.settings_storage_folder_not_found), null)
                    return false
                }
            }

            if (!newDirectory.canWrite()) {
                UiUtil.displayAlertError(activity, getString(LR.string.settings_storage_folder_write_failed), null)
                return false
            }

            // move the podcasts if the user wants
            if (oldDirectory != null) {
                movePodcastStorage(oldDirectory.absolutePath, newDirectory.absolutePath)
            }

            return true
        }

        return false
    }

    override fun onBackPressed(): Boolean {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
            view?.findToolbar()?.title = getString(LR.string.settings_title_storage)
            return true
        }

        return false
    }

    override fun getBackstackCount(): Int {
        return childFragmentManager.backStackEntryCount
    }
}