package com.topo.core.domain.usecase

import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.ClimbingStatistics
import com.topo.core.domain.model.DifficultyBucket
import javax.inject.Inject

/**
 * Agrege une collection de blocs en statistiques de progression.
 *
 * Fonction PURE, sans acces aux donnees : elle recoit les blocs deja charges.
 * Consequence directe, elle se teste sur JVM en millisecondes, alors qu'un
 * calcul cable sur la base aurait exige un emulateur pour chaque cas limite.
 *
 * Le calcul se fait en Kotlin et non en SQL, contrairement aux compteurs de
 * seances : ici les regles sont metier (un flash compte parmi les reussites,
 * les deux echelles ne se melangent pas) et changeront plus souvent que le
 * schema. En SQL elles seraient dispersees dans des requetes intestables.
 */
class ComputeStatisticsUseCase @Inject constructor() {

    operator fun invoke(blocs: List<Bloc>, circuit: List<CircuitColor>): ClimbingStatistics {
        if (blocs.isEmpty()) return ClimbingStatistics.Empty

        val sent = blocs.filter { it.status.isSent }
        val colorsById = circuit.associateBy(CircuitColor::id)

        return ClimbingStatistics(
            blocCount = blocs.size,
            sentCount = sent.size,
            flashCount = blocs.count { it.status == BlocStatus.FLASHED },
            totalAttempts = blocs.sumOf(Bloc::attempts),
            // Le meilleur niveau se lit sur les blocs REUSSIS : avoir essaye un
            // 8A ne fait pas de soi un grimpeur de 8A.
            bestGrade = sent.maxByOrNull { it.gradeRank ?: -1 }?.takeIf { it.gradeRank != null }?.grade,
            bestColor = sent.mapNotNull { colorsById[it.colorId] }.maxByOrNull(CircuitColor::rank),
            gradeBuckets = gradeBuckets(blocs),
            colorBuckets = colorBuckets(blocs, circuit),
        )
    }

    /**
     * Une barre par cotation REPRESENTEE, pas par cotation existante : afficher
     * les 24 crans de l'echelle francaise pour trois blocs noierait le signal.
     */
    private fun gradeBuckets(blocs: List<Bloc>): List<DifficultyBucket> = blocs
        .filter { it.grade != null && it.gradeRank != null }
        .groupBy { it.grade!! }
        .map { (label, group) ->
            DifficultyBucket(
                label = label,
                rank = group.first().gradeRank!!,
                argb = null,
                total = group.size,
                sent = group.count { it.status.isSent },
            )
        }
        .sortedBy(DifficultyBucket::rank)

    /**
     * Le circuit entier est represente, y compris les couleurs a zero : sa liste
     * est courte et connue, et un trou dans la progression est une information.
     */
    private fun colorBuckets(blocs: List<Bloc>, circuit: List<CircuitColor>): List<DifficultyBucket> {
        if (circuit.isEmpty()) return emptyList()
        val byColor = blocs.filter { it.colorId != null }.groupBy { it.colorId }
        return circuit.sortedBy(CircuitColor::rank).map { color ->
            val group = byColor[color.id].orEmpty()
            DifficultyBucket(
                label = color.name,
                rank = color.rank,
                argb = color.argb,
                total = group.size,
                sent = group.count { it.status.isSent },
            )
        }
    }
}
