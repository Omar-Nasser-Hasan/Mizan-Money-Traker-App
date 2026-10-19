// Copyright (c) 2026 Mizan contributors
package app.mizan

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.mizan.data.repository.MizanRepository
import app.mizan.quick.EXTRA_QUICK_ACTION
import app.mizan.security.AppLock
import app.mizan.security.StartupSettings
import app.mizan.ui.mizanApp
import app.mizan.ui.theme.MizanTheme
import app.mizan.ui.v2.AppInterface
import app.mizan.ui.v2.LocalMzTokens
import app.mizan.ui.v2.MzSkeleton
import app.mizan.ui.v2.V2OnboardingFlow
import app.mizan.ui.v2.V2PrivacyGate
import app.mizan.ui.v2.appInterface
import app.mizan.ui.v2.v2MizanApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var repository: MizanRepository
    private lateinit var appLock: AppLock
    private val startup = MutableStateFlow<StartupSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLock = AppLock(this, mainExecutor)
        lifecycleScope.launch {
            repository
                .observeStartupSettings()
                .collect { settings ->
                    // Restore closes Room; retain the backup screen until its immediate process restart.
                    if (repository.restoreInProgress.value) return@collect
                    if (settings.secureScreens) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    appLock.configure(settings.lockEnabled, settings.timeout)
                    startup.value = settings
                }
        }
        setContent {
            val restoring by repository.restoreInProgress.collectAsStateWithLifecycle()
            val settings by startup.collectAsStateWithLifecycle()
            val lock by appLock.state.collectAsStateWithLifecycle()
            val message by appLock.message.collectAsStateWithLifecycle()
            val prompting by appLock.prompting.collectAsStateWithLifecycle()
            val savedContent = rememberSaveableStateHolder()
            app.mizan.ui.v2.MizanTheme {
                if (settings == null || !lock.loaded) {
                    Box(Modifier.fillMaxSize().background(LocalMzTokens.current.canvas)) { MzSkeleton() }
                } else {
                    V2PrivacyGate(
                        lock.blocksShell,
                        lock.timeoutSeconds,
                        { appLock.unlock() },
                        { appLock.unlock(true) },
                        message,
                        prompting,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            savedContent.SaveableStateProvider(if (settings!!.completed) "shell" else "setup") {
                                when {
                                    !settings!!.completed -> {
                                        V2OnboardingFlow(repository)
                                    }

                                    appInterface(settings!!.newInterface) == AppInterface.V2 -> {
                                        v2MizanApp(repository, intent.getStringExtra(EXTRA_QUICK_ACTION))
                                    }

                                    else -> {
                                        MizanTheme { mizanApp(repository, intent.getStringExtra(EXTRA_QUICK_ACTION)) }
                                    }
                                }
                            }
                            // Keep in-flight pickers alive during the grace period while Recents sees an opaque curtain.
                            if (!lock.foreground || restoring) Box(Modifier.fillMaxSize().background(LocalMzTokens.current.canvas))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appLock.onResume()
    }

    override fun onPause() {
        appLock.onBackground()
        super.onPause()
    }

    override fun onDestroy() {
        appLock.close()
        super.onDestroy()
    }
}
