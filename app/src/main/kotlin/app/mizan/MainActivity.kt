// Copyright (c) 2026 Mizan contributors
package app.mizan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.mizan.data.repository.MizanRepository
import app.mizan.ui.mizanApp
import app.mizan.ui.theme.MizanTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: MizanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MizanTheme {
                mizanApp(repository)
            }
        }
    }
}
