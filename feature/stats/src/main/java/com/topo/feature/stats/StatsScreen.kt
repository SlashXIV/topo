package com.topo.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.domain.model.ClimbingStatistics
import com.topo.core.domain.model.DifficultyBucket
import com.topo.core.ui.component.ColorDot
import com.topo.core.ui.component.EmptyState
import com.topo.core.ui.component.Eyebrow
import com.topo.core.ui.component.FitText
import com.topo.core.ui.component.LoadingState
import com.topo.core.ui.component.PageHeader
import com.topo.core.ui.component.Rule
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.component.hatched
import com.topo.core.ui.motion.CountUp
import com.topo.core.ui.motion.LocalMotionLevel
import com.topo.core.ui.motion.Motion
import com.topo.core.ui.theme.TopoTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.statusBarsPadding
import com.topo.core.ui.component.bleed
import androidx.compose.foundation.layout.widthIn

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val blocCount = uiState.statistics.blocCount

    val header: @Composable () -> Unit = {
        PageHeader(
            title = stringResource(R.string.stats_title),
            eyebrow = pluralStringResource(R.plurals.stats_eyebrow, blocCount, blocCount).takeIf { blocCount > 0 },
            onBack = onBack,
            backLabel = stringResource(R.string.stats_back),
        )
    }

    // statusBarsPadding ici consomme l'encart : l'en-tete, qui defile avec les
    // chiffres, ne passe jamais sous la barre d'etat.
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        when {
            uiState.isLoading -> {
                header()
                LoadingState()
            }

            uiState.statistics.isEmpty -> {
                header()
                EmptyState(
                    title = stringResource(R.string.stats_empty_title),
                    message = stringResource(R.string.stats_empty_message),
                )
            }

            else -> StatsContent(uiState.statistics, header)
        }
    }
}

@Composable
private fun StatsContent(stats: ClimbingStatistics, header: @Composable () -> Unit) {
    val maxTotal = stats.maxBucketTotal()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // L'en-tete defile avec les chiffres : fixe, il prenait le tiers d'un
        // petit ecran.
        item { Box(Modifier.bleed(20.dp)) { header() } }

        item {
            MetricRow {
                Metric(stringResource(R.string.stats_metric_blocs), stats.blocCount.toDouble(), { "${it.roundToInt()}" }, first = true)
                Metric(stringResource(R.string.stats_metric_sent), stats.sentCount.toDouble(), { "${it.roundToInt()}" })
                Metric(stringResource(R.string.stats_metric_flashes), stats.flashCount.toDouble(), { "${it.roundToInt()}" })
            }
        }

        item {
            val resources = LocalResources.current
            val percent: (Double) -> String = { resources.getString(R.string.stats_percent, it.roundToInt()) }
            MetricRow {
                Metric(
                    stringResource(R.string.stats_metric_success_rate),
                    stats.successRate * 100.0,
                    percent,
                    first = true,
                )
                // Rapporte aux reussites et non a tous les blocs : sinon ouvrir
                // un projet difficile ferait chuter le taux.
                Metric(
                    stringResource(R.string.stats_metric_flash_rate),
                    stats.flashRate * 100.0,
                    percent,
                )
                Metric(
                    stringResource(R.string.stats_metric_attempts_per_send),
                    stats.attemptsPerSend?.toDouble() ?: 0.0,
                    { value -> stats.attemptsPerSend?.let { "%.1f".format(value) } ?: "-" },
                )
            }
        }

        if (stats.bestGrade != null || stats.bestColor != null) {
            item {
                MetricRow {
                    stats.bestGrade?.let { TextMetric(stringResource(R.string.stats_metric_best_grade), it, first = true) }
                    stats.bestColor?.let {
                        TextMetric(stringResource(R.string.stats_metric_best_color), it.name, first = stats.bestGrade == null)
                    }
                }
            }
        }

        if (stats.colorBuckets.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.stats_by_color_title), stringResource(R.string.stats_by_color_hint))
            }
            itemsIndexed(stats.colorBuckets) { i, bucket ->
                BucketRow(
                    bucket,
                    maxTotal,
                    i,
                    label = bucket.label.ifEmpty { stringResource(R.string.stats_uncolored) },
                    reserveDot = true,
                )
            }
            // La pastille « Reussis » prend les couleurs des barres qu'elle legende.
            item { Legend(stats.colorBuckets.map { it.argb }.distinct()) }
        }

        if (stats.gradeBuckets.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.stats_by_grade_title), stringResource(R.string.stats_by_grade_hint))
            }
            itemsIndexed(stats.gradeBuckets) { i, bucket -> BucketRow(bucket, maxTotal, i) }
            item { Legend(listOf(null)) }
        }
    }
}

/** Trois chiffres entre deux filets, separes par des filets doux. */
@Composable
private fun MetricRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column {
        Rule()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), content = content)
        Rule()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(
    label: String,
    value: Double,
    format: (Double) -> String,
    first: Boolean = false,
) {
    MetricCell(label, format(value), first) {
        CountUp(value = value, format = format, style = TopoTheme.type.figure)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TextMetric(label: String, value: String, first: Boolean = false) {
    MetricCell(label, value, first) {
        FitText(value, style = TopoTheme.type.figure.copy(fontSize = TopoTheme.type.figure.fontSize * 0.7f))
    }
}

/**
 * Une case de chiffre. Lue par TalkBack en une phrase, « Réussite, 71 % » : le
 * chiffre qui compte ne doit pas etre annonce a chaque pas.
 *
 * Un filet a gauche de chaque case sauf la premiere : des filets entre les
 * cases seulement, jamais un filet orphelin au bord droit.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MetricCell(
    label: String,
    finalValue: String,
    first: Boolean,
    figure: @Composable () -> Unit,
) {
    Row(Modifier.weight(1f).fillMaxHeight()) {
        if (!first) Spacer(Modifier.width(1.dp).fillMaxHeight().background(TopoTheme.colors.rule))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp, horizontal = 8.dp)
                .clearAndSetSemantics { contentDescription = "$label, $finalValue" },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            figure()
            // Sans plafond : coupe en syllabes, « Essais / reussite » prend trois lignes
            // dans une case etroite, et la deuxieme moitie ne doit pas disparaitre.
            Eyebrow(label, color = TopoTheme.colors.ink2, maxLines = Int.MAX_VALUE)
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column(Modifier.padding(top = 20.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TopoText(title, style = TopoTheme.type.heading)
        TopoText(hint, style = TopoTheme.type.bodySmall, color = TopoTheme.colors.ink2)
    }
}

/**
 * Une barre par niveau, a l'echelle du niveau le plus tente : la piste hachuree
 * dit ce qui a ete tente, la part pleine ce qui a ete reussi. Lisible sans la
 * couleur — le nom et la fraction exacte sont ecrits, et les essais hachures.
 *
 * Barres dessinees a la main plutot qu'une bibliotheque de graphiques : deux
 * rectangles proportionnels n'en justifient pas une.
 */
@Composable
private fun BucketRow(
    bucket: DifficultyBucket,
    maxTotal: Int,
    index: Int,
    label: String = bucket.label,
    // Section couleur : la place de la pastille est gardee meme sans couleur,
    // pour que « Sans couleur » s'aligne sur les autres noms.
    reserveDot: Boolean = false,
) {
    val colors = TopoTheme.colors
    val fill = bucket.argb?.let(::Color) ?: colors.ink
    val tried = if (maxTotal == 0) 0f else bucket.total.toFloat() / maxTotal
    val sent = if (maxTotal == 0) 0f else bucket.sent.toFloat() / maxTotal
    val growth = rememberGrowth(index)
    val description = pluralStringResource(R.plurals.stats_bucket_description, bucket.sent, label, bucket.sent, bucket.total)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 40 % de la place laissee par la fraction : une largeur fixe
        // ecrasait la barre sur un ecran de 320 dp, ou coupait « Sans couleur ».
        Row(Modifier.weight(0.4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bucket.argb != null) ColorDot(fill) else if (reserveDot) Spacer(Modifier.size(12.dp))
            TopoText(label, style = TopoTheme.type.label, maxLines = 2)
        }
        Box(Modifier.weight(0.6f).height(16.dp)) {
            if (bucket.total > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(tried * growth)
                        .fillMaxHeight()
                        .border(1.dp, colors.ink)
                        .hatched(colors.ink2, spacing = 4.dp),
                )
                if (bucket.sent > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth(sent * growth)
                            .fillMaxHeight()
                            .background(fill)
                            .border(1.dp, colors.ink),
                    )
                }
            }
        }
        // Jamais repliee : « 12/14 » se coupait en deux lignes dans 48 dp fixes
        // des que la police grandissait.
        TopoText(
            text = "${bucket.sent}/${bucket.total}",
            style = TopoTheme.type.cote,
            color = colors.ink2,
            modifier = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Les barres poussent l'une apres l'autre, avec un leger rebond. Expressif seulement. */
@Composable
private fun rememberGrowth(index: Int): Float {
    val decorative = LocalMotionLevel.current.decorative
    val growth = remember { Animatable(if (decorative) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!decorative) return@LaunchedEffect
        delay((index.coerceAtMost(Motion.MAX_STAGGERED_ITEMS) * BAR_STAGGER_MS).toLong())
        growth.animateTo(1f, tween(Motion.RELEASE_MS, easing = Motion.Release))
    }
    return growth.value.coerceAtMost(1f)
}

/** [sentFills] : une bande par couleur de barre, null pour l'encre. */
@Composable
private fun Legend(sentFills: List<Int?>) {
    val colors = TopoTheme.colors
    Row(
        Modifier.padding(top = 4.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.size(14.dp, 10.dp).border(1.dp, colors.ink)) {
                sentFills.forEach { argb ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(argb?.let(::Color) ?: colors.ink))
                }
            }
            Eyebrow(stringResource(R.string.stats_legend_sent), color = colors.ink2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp, 10.dp).border(1.dp, colors.ink).hatched(colors.ink2, spacing = 4.dp))
            Eyebrow(stringResource(R.string.stats_legend_tried), color = colors.ink2)
        }
    }
}

private fun ClimbingStatistics.maxBucketTotal(): Int =
    ((colorBuckets + gradeBuckets).maxOfOrNull(DifficultyBucket::total) ?: 0).coerceAtLeast(1)

private const val BAR_STAGGER_MS = 70
