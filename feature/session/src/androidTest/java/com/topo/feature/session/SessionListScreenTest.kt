package com.topo.feature.session

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.topo.core.domain.model.Session
import com.topo.core.domain.model.SessionSummary
import com.topo.core.domain.repository.SessionRepository
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test

/**
 * Comportement de la liste des seances.
 *
 * Le ViewModel est construit avec un dépôt en memoire plutot qu'injecte par Hilt :
 * ces tests portent sur l'ecran et sa reaction aux emissions, pas sur le cablage
 * d'injection, qui est verifie par le test de navigation de `:app`.
 */
class SessionListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeSessionRepository()

    private fun string(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun an_empty_list_shows_the_empty_state_and_not_a_blank_screen() {
        composeRule.setContent {
            SessionListScreen(onSessionClick = {}, viewModel = SessionListViewModel(repository))
        }

        composeRule.onNodeWithText(string(R.string.session_list_empty_title)).assertIsDisplayed()
    }

    @Test
    fun creating_a_session_makes_it_appear_in_the_list() {
        composeRule.setContent {
            SessionListScreen(onSessionClick = {}, viewModel = SessionListViewModel(repository))
        }

        composeRule.onNodeWithTag(AddSessionTestTag).performClick()
        composeRule.onNodeWithText(string(R.string.session_create_location)).performTextInput("Arkose Nation")
        composeRule.onNodeWithText(string(R.string.session_create_confirm)).performClick()

        // Aucune notification manuelle n'est emise : c'est l'ecriture dans le depot
        // qui fait reemettre le Flow, et l'ecran suit.
        composeRule.onNodeWithText("Arkose Nation").assertIsDisplayed()
    }

    @Test
    fun a_blank_location_cannot_be_submitted() {
        composeRule.setContent {
            SessionListScreen(onSessionClick = {}, viewModel = SessionListViewModel(repository))
        }

        composeRule.onNodeWithTag(AddSessionTestTag).performClick()
        composeRule.onNodeWithText(string(R.string.session_create_location)).performTextInput("   ")

        // Le domaine refuse un lieu vide : desactiver le bouton vaut mieux que
        // laisser l'utilisateur declencher un echec silencieux.
        composeRule.onNodeWithText(string(R.string.session_create_confirm)).assertIsNotEnabled()
    }

    @Test
    fun clicking_a_session_reports_its_identifier() {
        var clicked: String? = null
        repository.add(locationName = "Blocbuster")

        composeRule.setContent {
            SessionListScreen(
                onSessionClick = { clicked = it },
                viewModel = SessionListViewModel(repository),
            )
        }

        composeRule.onNodeWithText("Blocbuster").performClick()

        assertThat(clicked).isEqualTo(repository.sessions.value.single().id)
    }

    @Test
    fun the_card_summarises_progress() {
        repository.add(locationName = "Climb Up", blocCount = 5, completedCount = 2)

        composeRule.setContent {
            SessionListScreen(onSessionClick = {}, viewModel = SessionListViewModel(repository))
        }

        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.resources
                .getQuantityString(R.plurals.session_card_sent_count, 2, 2, 5),
        ).assertIsDisplayed()
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = MutableStateFlow<List<Session>>(emptyList())
        private val counts = mutableMapOf<String, Pair<Int, Int>>()

        fun add(locationName: String, blocCount: Int = 0, completedCount: Int = 0) {
            val session = Session(
                id = UUID.randomUUID().toString(),
                date = 0L,
                locationName = locationName,
                notes = null,
            )
            counts[session.id] = blocCount to completedCount
            sessions.value = sessions.value + session
        }

        override fun observeSessions(): Flow<List<SessionSummary>> = sessions.map { list ->
            list.map { session ->
                val (blocs, completed) = counts[session.id] ?: (0 to 0)
                SessionSummary(session, blocs, completed)
            }
        }

        override fun observeSession(sessionId: String): Flow<Session?> =
            sessions.map { list -> list.firstOrNull { it.id == sessionId } }

        override suspend fun createSession(
            locationName: String,
            date: Long,
            notes: String?,
        ): String {
            add(locationName)
            return sessions.value.last().id
        }

        override suspend fun updateSession(session: Session) = Unit

        override suspend fun deleteSession(sessionId: String) {
            sessions.value = sessions.value.filterNot { it.id == sessionId }
        }
    }
}
