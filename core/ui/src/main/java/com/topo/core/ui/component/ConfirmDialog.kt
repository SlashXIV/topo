package com.topo.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Confirmation avant une action irreversible.
 *
 * Utilisee pour les suppressions, qui emportent des donnees par cascade SQLite :
 * effacer une seance efface ses blocs, leurs prises et leurs mouvements, et les
 * fichiers image correspondants. Rien de tout cela n'est recuperable, d'ou
 * l'annonce explicite de la portee dans [message] plutot qu'un « Etes-vous sur ? ».
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
