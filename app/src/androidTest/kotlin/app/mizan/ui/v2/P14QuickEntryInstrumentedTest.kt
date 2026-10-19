// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.InboxDraftEntity
import app.mizan.domain.entry.DraftKind
import app.mizan.domain.entry.DraftRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class P14QuickEntryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun inbox_actions_are_review_only_and_possible_duplicates_are_visible() {
        var confirms = 0
        var edits = 0
        var dismissals = 0
        val ledgerRows = 0
        setScreen {
            InboxDraftCard(
                InboxDraftEntity(
                    "draft",
                    "SMS",
                    "BM",
                    15_900,
                    "EGP",
                    "Uber",
                    "4417",
                    occurredAt = 1_792_396_800_000L,
                    receivedAt = 1_792_396_800_000L,
                    rawMessage = "redacted",
                ),
                AccountEntity("cash", "Cash", "CASH", "#0785DB", "wallet", "EGP", "2026-10-01", true, false, 0),
                "Transport",
                possibleDuplicate = true,
                hideAmounts = false,
                onConfirm = { confirms++ },
                onEdit = { edits++ },
                onDismiss = { dismissals++ },
            )
        }
        compose.onNodeWithText("Possible duplicate").assertIsDisplayed()
        compose.onNodeWithText("Suggested category · Transport").assertIsDisplayed()
        capture("01-inbox-draft.png")
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, confirms)
        compose.onNodeWithText("Edit").performClick()
        assertEquals(1, edits)
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissals)
        assertEquals(0, ledgerRows)
    }

    @Test fun voice_review_sheet_posts_only_after_save_all_and_supports_arabic_transcript_state() {
        val draft = DraftRecord(DraftKind.EXPENSE, 12_500, "EGP", LocalDate.parse("2026-10-19"), "cash", "food", "Carrefour", 96, "125 EGP")
        var saves = 0
        var edits = 0
        setScreen {
            Column {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
                ) { androidx.compose.material3.Text("دفعت ١٢٥ جنيه في كارفور") }
                VoiceReviewSheet(
                    listOf(draft),
                    mapOf("cash" to "Cash"),
                    mapOf("food" to "Food"),
                    false,
                    false,
                    { _, replacement -> if (replacement.amountMinor != draft.amountMinor) edits++ },
                    {},
                    { saves++ },
                )
            }
        }
        compose.onNodeWithText("Review 1 records").assertIsDisplayed()
        compose.onNodeWithText("Carrefour").assertIsDisplayed()
        assertEquals(0, saves)
        capture("02-voice-review.png")
        compose.onNodeWithText("Save all").performClick()
        assertEquals(1, saves)
        assertEquals(0, edits)
    }

    @Test fun widget_amount_format_honors_hide_amounts_for_the_compact_and_wide_layouts() {
        assertEquals("EGP 1,234.56", app.mizan.quick.widgetAmountText(123_456, "EGP", false))
        assertEquals("EGP ••••••", app.mizan.quick.widgetAmountText(123_456, "EGP", true))
    }

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            view = LocalView.current
            MizanTheme {
                androidx.compose.material3.Surface(color = LocalMzTokens.current.canvas, contentColor = LocalMzTokens.current.text) {
                    Column(Modifier.fillMaxSize()) {
                        MzTopBar("P14 · Quick entry")
                        Column(Modifier.weight(1f).fillMaxSize().padding(MzSpacing.sideMargin)) { content() }
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle { view.invalidate() }
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = compose.onNode(isRoot()).captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MizanP14")
            }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(output)))
        }
    }
}
