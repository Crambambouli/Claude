package com.puzzle.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.puzzle.android.data.db.ExampleEntity
import com.puzzle.android.ui.screens.StartScreen
import com.puzzle.android.ui.screens.StartScreenTags
import com.puzzle.android.ui.theme.PuzzleAndroidTheme
import com.puzzle.android.viewmodel.HealthStatus
import com.puzzle.android.viewmodel.MainUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [StartScreen] / [StartScreenContent].
 *
 * These tests drive the stateless [StartScreenContent] overload directly so
 * they don't need a real ViewModel or repository.
 */
@RunWith(AndroidJUnit4::class)
class StartScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper – renders the screen inside our theme ──────────────────────────

    private fun setContent(
        uiState         : MainUiState      = MainUiState(),
        examples        : List<ExampleEntity> = emptyList(),
        onCheckHealth   : () -> Unit        = {},
        onAddExample    : () -> Unit        = {},
        onClearExamples : () -> Unit        = {}
    ) {
        composeTestRule.setContent {
            PuzzleAndroidTheme(dynamicColor = false) {
                com.puzzle.android.ui.screens.StartScreenContent(
                    uiState         = uiState,
                    examples        = examples,
                    onCheckHealth   = onCheckHealth,
                    onAddExample    = onAddExample,
                    onClearExamples = onClearExamples
                )
            }
        }
    }

    // ── Idle state ────────────────────────────────────────────────────────────

    @Test
    fun healthButton_isVisibleAndEnabled_whenIdle() {
        setContent(uiState = MainUiState(HealthStatus.Idle))

        composeTestRule
            .onNodeWithTag(StartScreenTags.HEALTH_BUTTON)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun noStatusCard_isShown_whenIdle() {
        setContent(uiState = MainUiState(HealthStatus.Idle))

        composeTestRule
            .onNodeWithTag(StartScreenTags.STATUS_CARD)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(StartScreenTags.ERROR_TEXT)
            .assertDoesNotExist()
    }

    // ── Loading state ─────────────────────────────────────────────────────────

    @Test
    fun spinner_isVisible_whenLoading() {
        setContent(uiState = MainUiState(HealthStatus.Loading))

        composeTestRule
            .onNodeWithTag(StartScreenTags.LOADING_SPINNER)
            .assertIsDisplayed()
    }

    @Test
    fun healthButton_isDisabled_whenLoading() {
        setContent(uiState = MainUiState(HealthStatus.Loading))

        composeTestRule
            .onNodeWithTag(StartScreenTags.HEALTH_BUTTON)
            .assertIsNotEnabled()
    }

    // ── Success state ─────────────────────────────────────────────────────────

    @Test
    fun statusCard_isVisible_whenHealthCheckSucceeds() {
        setContent(uiState = MainUiState(HealthStatus.Success("ok", "0.1.0")))

        composeTestRule
            .onNodeWithTag(StartScreenTags.STATUS_CARD)
            .assertIsDisplayed()
    }

    @Test
    fun statusText_isDisplayed_whenHealthCheckSucceeds() {
        setContent(uiState = MainUiState(HealthStatus.Success("ok", null)))

        composeTestRule
            .onNodeWithText("ok")
            .assertIsDisplayed()
    }

    // ── Error state ───────────────────────────────────────────────────────────

    @Test
    fun errorCard_isVisible_whenHealthCheckFails() {
        setContent(uiState = MainUiState(HealthStatus.Error("Connection refused")))

        composeTestRule
            .onNodeWithTag(StartScreenTags.ERROR_TEXT)
            .assertIsDisplayed()
    }

    @Test
    fun errorMessage_isDisplayed_whenHealthCheckFails() {
        setContent(uiState = MainUiState(HealthStatus.Error("Connection refused")))

        composeTestRule
            .onNodeWithText("Connection refused")
            .assertIsDisplayed()
    }

    // ── Examples list ─────────────────────────────────────────────────────────

    @Test
    fun emptyMessage_isShown_whenNoExamples() {
        setContent(examples = emptyList())

        composeTestRule
            .onNodeWithText("No examples saved yet.")
            .assertIsDisplayed()
    }

    @Test
    fun exampleRow_isDisplayed_whenExamplesExist() {
        val examples = listOf(ExampleEntity(id = "test-uuid-001"))
        setContent(examples = examples)

        composeTestRule
            .onNodeWithText("test-uuid-001")
            .assertIsDisplayed()
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Test
    fun clickingHealthButton_invokesOnCheckHealth() {
        var clicked = false
        setContent(
            uiState       = MainUiState(HealthStatus.Idle),
            onCheckHealth = { clicked = true }
        )

        composeTestRule
            .onNodeWithTag(StartScreenTags.HEALTH_BUTTON)
            .performClick()

        assert(clicked) { "onCheckHealth was not called after button click" }
    }
}
