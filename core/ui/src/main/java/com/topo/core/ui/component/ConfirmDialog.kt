package com.topo.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.topo.core.ui.R
import com.topo.core.ui.theme.TopoTheme

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
    TopoDialog(
        title = title,
        onDismissRequest = onDismiss,
        actions = {
            TopoButton(stringResource(R.string.action_cancel), onClick = onDismiss, style = ButtonStyle.Text)
            TopoButton(confirmLabel, onClick = onConfirm, style = ButtonStyle.Line, contentColor = TopoTheme.colors.error)
        },
    ) {
        TopoText(message, style = TopoTheme.type.body, color = TopoTheme.colors.ink2)
    }
}
