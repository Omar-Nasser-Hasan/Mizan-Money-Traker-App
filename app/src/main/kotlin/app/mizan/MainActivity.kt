// Copyright (c) 2026 Mizan contributors
package app.mizan

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.mizan.data.repository.MizanRepository
import app.mizan.security.AppLock
import app.mizan.ui.mizanApp
import app.mizan.ui.theme.MizanTheme
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
            MizanTheme {
                mizanApp(repository)
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
