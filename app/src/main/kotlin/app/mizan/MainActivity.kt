// Copyright (c) 2026 Mizan contributors
package app.mizan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.mizan.ui.MizanApp
import app.mizan.ui.theme.MizanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MizanTheme {
                MizanApp()
            }
        }
    }
}
