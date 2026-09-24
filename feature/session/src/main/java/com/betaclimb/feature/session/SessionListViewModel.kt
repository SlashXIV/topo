package com.betaclimb.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betaclimb.core.domain.model.SessionSummary
import com.betaclimb.core.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Etat de la liste des seances.
 *
 * [Loading] est un etat a part entiere et non une liste vide : au premier
 * affichage, la base n'a pas encore emis, et montrer « aucune seance » a ce
 * moment-la ferait clignoter un faux etat vide devant l'utilisateur.
 */
sealed interface SessionListUiState {
    data object Loading : SessionListUiState
    data object Empty : SessionListUiState
    data class Content(val sessions: List<SessionSummary>) : SessionListUiState
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<SessionListUiState> = sessionRepository.observeSessions()
        .map { sessions ->
            if (sessions.isEmpty()) {
                SessionListUiState.Empty
            } else {
                SessionListUiState.Content(sessions)
            }
        }
        .stateIn(
            scope = viewModelScope,
            // WhileSubscribed(5 s) : l'abonnement survit a une rotation d'ecran mais
            // s'arrete si l'utilisateur quitte reellement l'ecran. A zero, chaque
            // changement de configuration relancerait la requete.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionListUiState.Loading,
        )

    fun createSession(locationName: String, notes: String?) {
        val trimmedLocation = locationName.trim()
        if (trimmedLocation.isEmpty()) return

        viewModelScope.launch {
            sessionRepository.createSession(
                locationName = trimmedLocation,
                date = System.currentTimeMillis(),
                notes = notes?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }

    /**
     * Supprime la seance, ses blocs, leur annotation et leurs fichiers image.
     *
     * La portee est volontairement large et irreversible : elle est annoncee a
     * l'utilisateur par la boite de confirmation, pas dissimulee.
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch { sessionRepository.deleteSession(sessionId) }
    }
}
