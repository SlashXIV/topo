package com.topo.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.domain.model.ClimbingStatistics
import com.topo.core.domain.model.DifficultyBucket
import com.topo.core.ui.component.EmptyState
import com.topo.core.ui.component.LoadingState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Progression") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))

            uiState.statistics.isEmpty -> EmptyState(
                title = "Rien a mesurer",
                message = "Enregistre des blocs et note leur difficulte : " +
                    "la progression apparaitra ici.",
                modifier = Modifier.padding(padding),
            )

            else -> StatsContent(uiState.statistics, Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatsContent(stats: ClimbingStatistics, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Blocs", "${stats.blocCount}", Modifier.weight(1f))
                Metric("Reussis", "${stats.sentCount}", Modifier.weight(1f))
                Metric("Flashs", "${stats.flashCount}", Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Reussite", stats.successRate.asPercent(), Modifier.weight(1f))
                // Rapporte aux reussites et non a tous les blocs : sinon ouvrir
                // un projet difficile ferait chuter le taux.
                Metric("Flash", stats.flashRate.asPercent(), Modifier.weight(1f))
                Metric(
                    label = "Essais / reussite",
                    value = stats.attemptsPerSend?.let { "%.1f".format(it) } ?: "-",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (stats.bestGrade != null || stats.bestColor != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats.bestGrade?.let {
                        Metric("Meilleure cotation", it, Modifier.weight(1f))
                    }
                    stats.bestColor?.let {
                        Metric("Meilleure couleur", it.name, Modifier.weight(1f))
                    }
                }
            }
        }

        if (stats.colorBuckets.isNotEmpty()) {
            item { SectionTitle("Par couleur de circuit", "Du plus facile au plus dur") }
            items(stats.colorBuckets) { bucket -> BucketRow(bucket, stats.maxBucketTotal()) }
        }

        if (stats.gradeBuckets.isNotEmpty()) {
            item { SectionTitle("Par cotation", "Seules les cotations utilisees apparaissent") }
            items(stats.gradeBuckets) { bucket -> BucketRow(bucket, stats.maxBucketTotal()) }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Une barre par niveau : longueur proportionnelle au nombre de blocs, part pleine
 * pour les reussites.
 *
 * Barres dessinees a la main plutot qu'une bibliotheque de graphiques : deux
 * rectangles proportionnels n'en justifient pas une, et la dependance pesterait
 * plus lourd que le code qu'elle remplace.
 */
@Composable
private fun BucketRow(bucket: DifficultyBucket, maxTotal: Int) {
    val accent = bucket.argb?.let(::Color) ?: MaterialTheme.colorScheme.primary
    val fraction = if (maxTotal == 0) 0f else bucket.total.toFloat() / maxTotal
    val sentFraction = if (bucket.total == 0) 0f else bucket.sent.toFloat() / bucket.total

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = bucket.label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bucket.total > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(20.dp)
                        .background(accent.copy(alpha = 0.35f)),
                ) {
                    // La part pleine marque les reussites : la barre entiere dit
                    // ce qui a ete tente, la partie opaque ce qui a ete fait.
                    Box(
                        Modifier
                            .fillMaxWidth(sentFraction)
                            .height(20.dp)
                            .background(accent),
                    )
                }
            }
        }
        Text(
            text = "${bucket.sent}/${bucket.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
    }
}

private fun ClimbingStatistics.maxBucketTotal(): Int =
    ((colorBuckets + gradeBuckets).maxOfOrNull(DifficultyBucket::total) ?: 0).coerceAtLeast(1)

private fun Float.asPercent(): String = "${(this * 100).roundToInt()} %"
