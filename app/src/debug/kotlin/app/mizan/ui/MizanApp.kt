// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mizan.domain.ports.GoldPriceSnapshot
import app.mizan.domain.ports.Quote
import app.mizan.spikes.DaleelakGoldPriceProvider
import app.mizan.spikes.EgiTickerQuoteProvider
import app.mizan.spikes.SpeechProbe
import app.mizan.spikes.SpeechProbeState
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class Tab(
    val label: String,
    val symbol: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun MizanApp() {
    val tabs =
        listOf(
            Tab("Home", "⌂"),
            Tab("Records", "≡"),
            Tab("Plan", "□"),
            Tab("Insights", "⌁"),
            Tab("More", "⋯"),
        )
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDevMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showDevMenu) "Dev probes" else tabs[selectedTab].label,
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { if (selectedTab == tabs.lastIndex) showDevMenu = true },
                            ),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index && !showDevMenu,
                        onClick = {
                            showDevMenu = false
                            selectedTab = index
                        },
                        icon = { Text(tab.symbol) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (showDevMenu) {
            devMenu(padding)
        } else {
            emptyTab(tabs[selectedTab].label, padding)
        }
    }
}

@Composable
private fun emptyTab(
    label: String,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Text("This tab is intentionally empty in M0.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun devMenu(padding: PaddingValues) {
    var screen by remember { mutableStateOf("menu") }
    when (screen) {
        "gold" -> {
            goldSpike(padding, onBack = { screen = "menu" })
        }

        "quotes" -> {
            quoteSpike(padding, onBack = { screen = "menu" })
        }

        "voice" -> {
            voiceSpike(padding, onBack = { screen = "menu" })
        }

        "models" -> {
            modelSpike(padding, onBack = { screen = "menu" })
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("M0 throwaway probes", style = MaterialTheme.typography.titleLarge) }
                item { Text("These screens are not part of the product navigation.") }
                item { devButton("SPIKE-G1 · Egyptian gold dealer feed") { screen = "gold" } }
                item { devButton("SPIKE-S1 · EGX quote source") { screen = "quotes" } }
                item { devButton("SPIKE-V1 · On-device speech") { screen = "voice" } }
                item { devButton("SPIKE-L1 · On-phone model benchmark log") { screen = "models" } }
            }
        }
    }
}

@Composable
private fun devButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) { Text(label) }
}

@Composable
private fun goldSpike(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val provider = remember { DaleelakGoldPriceProvider(OkHttpClient()) }
    var result by remember { mutableStateOf<GoldPriceSnapshot?>(null) }
    var status by remember { mutableStateOf("Not fetched") }
    probeColumn(padding, onBack) {
        Text("SPIKE-G1", style = MaterialTheme.typography.titleLarge)
        Text("Daleelak’s public feed lists dealer aggregation, including iSagha. Values are indicative.")
        devButton("Fetch latest dealer prices") {
            scope.launch {
                status = "Fetching…"
                result = provider.latest()
                status = if (result == null) "No data: network, schema, or source failure" else "Fetched"
            }
        }
        Text(status)
        result?.let { snapshot ->
            snapshot.karats.forEach { (karat, price) ->
                Text("${karat}K · buy ${price.buyEgp.formatEgp()} · sell ${price.sellEgp.formatEgp()}")
            }
            Text("Gold pound · buy ${snapshot.pound.buyEgp.formatEgp()} · sell ${snapshot.pound.sellEgp.formatEgp()}")
            Text("Observed ${DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("Africa/Cairo")).format(snapshot.asOf)}")
            Text(snapshot.source, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun quoteSpike(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val provider = remember { EgiTickerQuoteProvider(OkHttpClient()) }
    var quotes by remember { mutableStateOf<List<Quote>>(emptyList()) }
    var status by remember { mutableStateOf("Not fetched") }
    probeColumn(padding, onBack) {
        Text("SPIKE-S1", style = MaterialTheme.typography.titleLarge)
        Text("Probe: COMI, TMGH, and SWDY. This free delayed endpoint currently may require authorization.")
        devButton("Fetch three EGX quotes") {
            scope.launch {
                status = "Fetching…"
                quotes = provider.quotes(listOf("COMI", "TMGH", "SWDY"))
                status = if (quotes.isEmpty()) "No quotes: keep manual/last values" else "Fetched ${quotes.size} quote(s)"
            }
        }
        Text(status)
        quotes.forEach { quote -> Text("${quote.ticker} · ${quote.lastPriceMinor.formatEgp()}") }
    }
}

@Composable
private fun voiceSpike(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf("ar-EG") }
    var state by remember { mutableStateOf(SpeechProbeState(language, false)) }
    val probe = remember { SpeechProbe(context) { state = it } }
    probeColumn(padding, onBack) {
        Text("SPIKE-V1", style = MaterialTheme.typography.titleLarge)
        Text("The result and measured time are displayed for each spoken sentence. Do not use online audio.")
        devButton("Use ar-EG") { language = "ar-EG" }
        devButton("Use en-US") { language = "en-US" }
        devButton("Check support for $language") { probe.check(language) }
        devButton("Request offline model for $language") { probe.download(language) }
        devButton("Speak one sentence in $language") { probe.listen(language) }
        Text("Availability: ${state.onDeviceAvailable}")
        Text(state.support)
        if (state.transcript.isNotBlank()) Text("Transcript: ${state.transcript}")
        state.latencyMillis?.let { Text("Latency: $it ms") }
    }
}

@Composable
private fun modelSpike(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    probeColumn(padding, onBack) {
        Text("SPIKE-L1", style = MaterialTheme.typography.titleLarge)
        Text(
            "No model is bundled or integrated. This probe records the required methodology before a model is downloaded to the test phone.",
        )
        Text(
            "Benchmark each candidate against the same 40-category list: warm up 10 times, then record 20 picks; capture p50/p95 wall-clock latency and process PSS in Android Studio Profiler.",
        )
        Text("Candidate A: a small multilingual sentence embedding model. Candidate B: a LiteRT-LM model at 2B parameters or smaller.")
        Text(
            "Verdict gate: only recommend a candidate if p95 is below 3 seconds without UI jank. Record the handset and model artifact in docs/spikes.md.",
        )
    }
}

@Composable
private fun probeColumn(
    padding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Button(onClick = onBack) { Text("Back") } }
        item { content() }
        item { HorizontalDivider() }
        item { Text("Dev-only · no product data is stored", fontWeight = FontWeight.Medium) }
    }
}

private fun Long.formatEgp(): String {
    val whole = this / 100
    val fraction = (this % 100).toString().padStart(2, '0')
    return "$whole.$fraction EGP"
}
