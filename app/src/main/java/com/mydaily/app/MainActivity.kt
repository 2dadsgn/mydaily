package com.mydaily.app

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.mydaily.app.data.DeviceCalendarReader
import com.mydaily.app.notification.CollisionNotificationManager
import com.mydaily.app.ui.MyDailyAppScreen
import com.mydaily.app.ui.MyDailyViewModel
import com.mydaily.app.ui.theme.MyDailyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: MyDailyViewModel by viewModels()
    private var pendingGoogleAccount: Account? = null

    private val googleConsentLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val account = pendingGoogleAccount
            if (result.resultCode == Activity.RESULT_OK && account != null) {
                requestGoogleOAuthToken(account, allowConsentLaunch = false)
            } else if (account != null) {
                viewModel.onGoogleAccountAuthError(account.name, "Consent cancelled")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntentTargetDate(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // Calendar + Notification runtime permissions launcher
            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val calGranted = permissions[Manifest.permission.READ_CALENDAR] == true ||
                    DeviceCalendarReader.hasCalendarPermission(this@MainActivity)
                viewModel.onCalendarPermissionResult(calGranted)
            }

            // Native Android Google Account Picker for Google Tasks & Calendar
            val googleAccountPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    val accountType = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE) ?: "com.google"
                    if (!accountName.isNullOrBlank()) {
                        val account = Account(accountName, accountType)
                        pendingGoogleAccount = account
                        requestGoogleOAuthToken(account, allowConsentLaunch = true)
                    }
                }
            }

            // Automatically prompt for Calendar & Notification permissions on launch if needed
            LaunchedEffect(Unit) {
                val needed = mutableListOf<String>()
                if (!DeviceCalendarReader.hasCalendarPermission(this@MainActivity)) {
                    needed.add(Manifest.permission.READ_CALENDAR)
                    needed.add(Manifest.permission.WRITE_CALENDAR)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !CollisionNotificationManager.hasNotificationPermission(this@MainActivity)
                ) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (needed.isNotEmpty()) {
                    permissionsLauncher.launch(needed.toTypedArray())
                }
            }

            MyDailyTheme {
                MyDailyAppScreen(
                    uiState = uiState,
                    onSelectDate = viewModel::selectDate,
                    onSelectFilter = viewModel::selectFilter,
                    onSelectTab = viewModel::selectTab,
                    onToggleTaskCompleted = viewModel::toggleTaskCompleted,
                    onAutoResolveCollision = viewModel::autoResolveCollision,
                    onResetRescheduledTimes = viewModel::resetRescheduledTimes,
                    onAddScheduleItem = viewModel::addScheduleItem,
                    onDeleteCustomItem = viewModel::deleteCustomItem,
                    onRequestCalendarPermission = {
                        val req = mutableListOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            req.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionsLauncher.launch(req.toTypedArray())
                    },
                    onConnectGoogleTasksAccount = {
                        val intent = AccountManager.newChooseAccountIntent(
                            null,
                            null,
                            arrayOf("com.google"),
                            null,
                            null,
                            null,
                            null
                        )
                        googleAccountPickerLauncher.launch(intent)
                    },
                    onOpenConnectDialog = viewModel::openConnectPromptDialog,
                    onDismissConnectDialog = viewModel::dismissConnectPromptDialog,
                    onToggleAccount = viewModel::toggleAccountEnabled,
                    onAddIcsFeed = viewModel::addIcsFeed,
                    onRemoveIcsFeed = viewModel::removeIcsFeed,
                    onSaveGoogleTasksToken = viewModel::saveGoogleTasksToken,
                    onSyncNow = viewModel::syncAllSources,
                    onDismissBanner = viewModel::dismissStatusBanner
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentTargetDate(intent)
    }

    private fun handleIntentTargetDate(intent: Intent?) {
        val epochDay = intent?.getLongExtra(
            CollisionNotificationManager.EXTRA_TARGET_DATE_EPOCH_DAY,
            Long.MIN_VALUE
        ) ?: Long.MIN_VALUE
        if (epochDay != Long.MIN_VALUE) {
            viewModel.selectDate(LocalDate.ofEpochDay(epochDay))
        }
    }

    private fun requestGoogleOAuthToken(account: Account, allowConsentLaunch: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val accountManager = AccountManager.get(this@MainActivity)
                val scope = "oauth2:https://www.googleapis.com/auth/tasks.readonly https://www.googleapis.com/auth/calendar.readonly"
                val future = accountManager.getAuthToken(
                    account,
                    scope,
                    Bundle(),
                    false,
                    null,
                    null
                )
                val bundle = future.result
                val consentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable(AccountManager.KEY_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    bundle.getParcelable(AccountManager.KEY_INTENT)
                }

                if (consentIntent != null && allowConsentLaunch) {
                    withContext(Dispatchers.Main) {
                        googleConsentLauncher.launch(consentIntent)
                    }
                    return@launch
                }

                val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
                withContext(Dispatchers.Main) {
                    viewModel.onGoogleAccountAuthorized(account.name, token)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewModel.onGoogleAccountAuthError(
                        account.name,
                        e.localizedMessage ?: "OAuth token unavailable"
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshScheduleData()
    }
}
