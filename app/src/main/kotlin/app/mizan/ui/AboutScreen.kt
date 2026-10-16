// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun aboutScreen(modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("About Mizan")
        Text("Mizan is local-first personal finance software. It has no account, advertising, analytics, telemetry, or paywall.")
        Text("Your records stay on this device unless you explicitly share an encrypted backup or export.")
        Text(
            "Rates By Exchange Rate API",
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "ExchangeRate-API open rates are daily mid-market references. They are cacheable, can be rate limited, and are not bank buy or sell prices.",
        )
        Text(
            "Open provider terms and attribution",
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 4.dp),
        )
        androidx.compose.material3.TextButton(onClick = { uriHandler.openUri("https://www.exchangerate-api.com/docs/free") }) {
            Text("Exchange Rate API documentation")
        }
    }
}
