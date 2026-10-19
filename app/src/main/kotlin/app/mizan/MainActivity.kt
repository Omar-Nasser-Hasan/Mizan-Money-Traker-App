// Copyright (c) 2026 Mizan contributors
package app.mizan

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.mizan.data.repository.MizanRepository
import app.mizan.quick.EXTRA_QUICK_ACTION
import app.mizan.security.AppLock
import app.mizan.ui.mizanApp
import app.mizan.ui.theme.MizanTheme
import app.mizan.ui.v2.AppInterface
import app.mizan.ui.v2.appInterface
import app.mizan.ui.v2.v2MizanApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: MizanRepository
    private lateinit var appLock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLock = AppLock(this, mainExecutor)
        lifecycleScope.launch {
            repository.observeLockTimeoutSeconds().collectLatest { appLock.setTimeoutSeconds(it) }
        }
        lifecycleScope.launch {
            repository.observeSecureScreens().collectLatest { enabled ->
                if (enabled) {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            val newInterface by repository.observeNewInterface().collectAsStateWithLifecycle(false)
            val onboardingCompleted by produceState<Boolean?>(null) {
                repository.observeOnboardingCompleted().collect { value = it }
            }
            if (onboardingCompleted != true) {
                app.mizan.ui.v2.MizanTheme {
                    if (onboardingCompleted == false) {
                        app.mizan.ui.v2
                            .V2OnboardingFlow(repository)
                    } else {
                        app.mizan.ui.v2
                            .MzSkeleton()
                    }
                }
            } else if (appInterface(newInterface) == AppInterface.V2) {
                app.mizan.ui.v2.MizanTheme {
                    v2MizanApp(repository, intent.getStringExtra(EXTRA_QUICK_ACTION))
                }
            } else {
                MizanTheme {
                    mizanApp(repository, intent.getStringExtra(EXTRA_QUICK_ACTION))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appLock.onResume()
    }

    override fun onStop() {
        appLock.onStop()
        super.onStop()
    }
}
