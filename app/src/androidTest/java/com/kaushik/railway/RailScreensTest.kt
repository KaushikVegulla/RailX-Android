package com.kaushik.railway

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaushik.railway.data.Train
import com.kaushik.railway.data.TrainClassAvail
import com.kaushik.railway.ui.screens.HomeScreen
import com.kaushik.railway.ui.screens.TrainListScreen
import com.kaushik.railway.ui.theme.RailwayTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Screen interactions use local fixtures, without submitting an enquiry or payment. */
@RunWith(AndroidJUnit4::class)
class RailScreensTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    private lateinit var vm: AppViewModel
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    @Before fun prepareViewModel() {
        compose.runOnUiThread {
            vm = ViewModelProvider(viewModels, ViewModelProvider.NewInstanceFactory())[AppViewModel::class.java]
        }
        compose.waitUntil(60_000) { vm.gateReady }
        compose.runOnIdle {
            vm.userName = "Demo Traveller"
            vm.profileComplete = true
            vm.fromCode = "NDLS"
            vm.toCode = "MMCT"
            vm.journeyDate = LocalDate.now().plusDays(1).format(dateFormat)
            vm.lastJourney = null
            vm.bookings.clear()
        }
    }

    @After fun clearViewModel() {
        compose.runOnUiThread { viewModels.clear() }
    }

    @Test fun homeRejectsSameStationAndPastDateWithoutSearching() {
        var searched = false
        compose.runOnIdle { vm.toCode = vm.fromCode }
        compose.setContent { TestTheme { Home(onSearch = { searched = true }) } }
        compose.onNodeWithText("Search trains").performScrollTo().performClick()
        compose.onNodeWithText("Choose different departure and arrival stations.").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(searched)
            assertFalse(vm.searchLoading)
            vm.toCode = "MMCT"
            vm.journeyDate = LocalDate.now().minusDays(1).format(dateFormat)
        }
        compose.onNodeWithText("Search trains").performScrollTo().performClick()
        compose.onNodeWithText("Choose today or a future journey date.").assertIsDisplayed()
        compose.runOnIdle { assertFalse(searched); assertFalse(vm.searchLoading) }
    }

    @Test fun calendarPreservesSelectionAndHomeRendersInBothThemes() {
        var dark by mutableStateOf(false)
        compose.setContent { TestTheme(dark) { Home() } }
        compose.onNodeWithText("Where to next?").assertIsDisplayed()
        screenshot("home-light")
        val selectedDate = vm.journeyDate
        compose.onNodeWithText("Journey date").performScrollTo().performClick()
        compose.onNodeWithText("Choose date").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose date").assertDoesNotExist()
        compose.runOnIdle { assertEquals(selectedDate, vm.journeyDate); dark = true }
        compose.onNodeWithText("Find your train").performScrollTo().assertIsDisplayed()
        screenshot("home-dark")
    }

    @Test fun trainFiltersResetAndDepartureSortChangeVisibleResults() {
        val travelClass = TrainClassAvail("SL", "Sleeper", 500, "AVAILABLE 12", 12)
        val late = Train("10001", "Late train", "NDLS", "MMCT", "23:00", "08:00", "9h", "Daily", classes = listOf(travelClass))
        val morning = Train("10002", "Morning train", "NDLS", "MMCT", "08:00", "18:00", "10h", "Daily", classes = listOf(travelClass))
        compose.runOnIdle { vm.trains.addAll(listOf(late, morning)); vm.isOfflineHint = true }
        compose.setContent { TestTheme { TrainListScreen(vm, onBack = {}, onSelect = {}) } }
        compose.onNodeWithText("2 trains").assertIsDisplayed()
        screenshot("train-results")
        compose.onNodeWithText("Sort & filter").performClick()
        compose.onNodeWithText("Departure time").performScrollTo()
        compose.onNodeWithText("06:00–12:00").performScrollTo().performClick()
        compose.onNodeWithText("Show 1 train").performScrollTo().performClick()
        compose.onNodeWithText("1 train").assertIsDisplayed()
        compose.onNodeWithText("Morning train").assertIsDisplayed()
        compose.onNodeWithText("Late train").assertDoesNotExist()
        compose.onNodeWithText("Filters · 1").performClick()
        compose.onNodeWithText("Reset").performScrollTo().performClick()
        compose.onNodeWithText("Earliest departure").performScrollTo().performClick()
        compose.onNodeWithText("Show 2 trains").performScrollTo().performClick()
        compose.onNodeWithText("2 trains").assertIsDisplayed()
        val firstTrain = compose.onAllNodes(hasText("Morning train") or hasText("Late train"))
            .fetchSemanticsNodes().first()
        assertTrue("Earlier departure should be listed first",
            firstTrain.config[SemanticsProperties.Text].any { it.text == "Morning train" })
    }

    @Composable private fun Home(onSearch: () -> Unit = {}) {
        HomeScreen(vm, onSearch, onPnr = {}, onRunning = {}, onBookings = {}, onNeedProfile = {}, onNeedAadhaar = {})
    }

    @Composable private fun TestTheme(dark: Boolean = false, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            RailwayTheme { Surface(content = content) }
        }
    }

    private fun screenshot(name: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
