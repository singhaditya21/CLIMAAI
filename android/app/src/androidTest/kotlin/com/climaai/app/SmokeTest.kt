package com.climaai.app

import android.Manifest
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.climaai.app.ui.screens.onboardingPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two things no unit test in this project can tell you: that the app starts
 * at all, and that what it wrote to disk on one launch is still in effect on the
 * next.
 *
 * Both failures are silent. A crash in onCreate is a green build and a launcher
 * icon that bounces once. Onboarding replaying forever is a green build and an
 * app that never reaches its own home screen — the flag is written correctly,
 * nothing reads it back at startup, and no layer reports an error.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    // Empty rather than createAndroidComposeRule: these tests launch the
    // activity themselves, twice, which is the whole point of the second one.
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Before
    fun startFromAFreshInstall() {
        // MainActivity asks for location in onCreate. Left ungranted, the system
        // dialog takes the foreground and every assertion below races it.
        grant(Manifest.permission.ACCESS_FINE_LOCATION)
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Mirrors AuthViewModel's PREFS_NAME. Wiping it is what makes "first
        // launch" mean first launch on a device that has run this suite before.
        context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun survivesColdStart() {
        launchApp().use { scenario ->
            compose.awaitContent()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse("MainActivity finished itself during startup", activity.isFinishing)
            }
        }
    }

    @Test
    fun onboardingDoesNotReplayOnASecondLaunch() {
        val firstPage = onboardingPages.first().title

        launchApp().use {
            compose.awaitShowing(firstPage)
            compose.onNodeWithText(firstPage).assertIsDisplayed()

            // Go through the app's own completion path rather than writing the
            // flag from the test — a flag the test wrote proves nothing about
            // the flag the app writes.
            compose.onNodeWithText(SKIP_LABEL).performClick()
            compose.awaitGone(firstPage)
        }

        launchApp().use {
            compose.awaitContent()
            assertFalse(
                "Onboarding replayed on the second launch. The completion flag is " +
                    "persisted but nothing reads it back at startup, so every launch " +
                    "looks like a first install.",
                compose.isShowing(firstPage)
            )
        }
    }

    // ---------------------------------------------------------------------

    private fun launchApp(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        // Home's background, weather glyphs and glass borders are all
        // rememberInfiniteTransition, so the Compose tree is never idle once it
        // is on screen. On the auto-advancing clock every assertion below would
        // spin until the harness timeout and report a hang instead of a result.
        // With the clock parked nothing advances on its own, so the helpers
        // below step it a frame at a time.
        compose.mainClock.autoAdvance = false
        return scenario
    }

    private fun ComposeTestRule.isShowing(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Steps the parked clock until the activity has drawn anything at all. */
    private fun ComposeTestRule.awaitContent() =
        step("MainActivity drew no content") {
            onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeTestRule.awaitShowing(text: String) =
        step("\"$text\" never appeared") { isShowing(text) }

    /** Steps the parked clock through the NavHost transition away from [text]. */
    private fun ComposeTestRule.awaitGone(text: String) =
        step("\"$text\" was still on screen") { !isShowing(text) }

    private fun ComposeTestRule.step(failure: String, done: () -> Boolean) {
        repeat(STEPS) {
            if (done()) return
            mainClock.advanceTimeBy(STEP_MS)
        }
        throw AssertionError("$failure after ${STEPS * STEP_MS}ms of frames")
    }

    private fun grant(permission: String) {
        // executeShellCommand rather than GrantPermissionRule: that lives in
        // androidx.test:rules, which is not on this module's androidTest
        // classpath, and adding it is not this file's business.
        val pfd = instrumentation.uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private companion object {
        const val AUTH_PREFS = "climaai_auth"

        /** The onboarding skip button's label in OnboardingScreen. */
        const val SKIP_LABEL = "Skip"

        const val STEP_MS = 250L
        const val STEPS = 60
    }
}
