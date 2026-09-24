package com.topo.core.data.mapper

import com.topo.core.data.database.entity.BlocEntity
import com.topo.core.data.database.entity.HoldEntity
import com.topo.core.data.database.entity.MovementEntity
import com.topo.core.data.database.entity.SessionEntity
import com.topo.core.data.database.relation.BlocWithBeta
import com.topo.core.data.database.relation.SessionWithCounts
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.domain.model.Session
import com.topo.core.domain.model.SessionSummary

/**
 * Conversion entites Room <-> modeles du domaine.
 *
 * Cette frontiere n'est pas de la ceremonie : elle empeche les annotations Room et
 * la forme des tables de remonter dans la logique metier. Le jour ou une colonne
 * change de nom ou de type, seul ce fichier bouge.
 *
 * Les enumerations traversent en `Int` (leur ordinal). La conversion est faite ici,
 * explicitement, avec un repli sur la premiere valeur : un ordinal inconnu, venu
 * d'une version plus recente de l'application, ne doit pas faire planter la lecture.
 */

// --- Session ---------------------------------------------------------------

internal fun SessionEntity.toDomain(): Session = Session(
    id = id,
    date = date,
    locationName = locationName,
    notes = notes,
)

internal fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    date = date,
    locationName = locationName,
    notes = notes,
)

internal fun SessionWithCounts.toDomain(): SessionSummary = SessionSummary(
    session = session.toDomain(),
    blocCount = blocCount,
    completedCount = completedCount,
)

// --- Bloc ------------------------------------------------------------------

internal fun BlocEntity.toDomain(): Bloc = Bloc(
    id = id,
    sessionId = sessionId,
    imagePath = imagePath,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    grade = grade,
    status = BlocStatus.entries.getOrElse(status) { BlocStatus.PROJECT },
    attempts = attempts,
    sentAt = sentAt,
    feeling = feeling,
    createdAt = createdAt,
)

internal fun Bloc.toEntity(): BlocEntity = BlocEntity(
    id = id,
    sessionId = sessionId,
    imagePath = imagePath,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    grade = grade,
    status = status.ordinal,
    attempts = attempts,
    sentAt = sentAt,
    feeling = feeling,
    createdAt = createdAt,
)

internal fun BlocWithBeta.toDomain(): BlocDetail = BlocDetail(
    bloc = bloc.toDomain(),
    holds = holds.map(HoldEntity::toDomain),
    movements = movements.map(MovementEntity::toDomain),
)

// --- Hold ------------------------------------------------------------------

internal fun HoldEntity.toDomain(): Hold = Hold(
    id = id,
    blocId = blocId,
    box = BoundingBox(
        centerX = bboxXCenter,
        centerY = bboxYCenter,
        width = bboxWidth,
        height = bboxHeight,
    ),
    confidence = confidenceScore,
    origin = HoldOrigin.entries.getOrElse(origin) { HoldOrigin.MANUAL },
)

internal fun Hold.toEntity(): HoldEntity = HoldEntity(
    id = id,
    blocId = blocId,
    bboxXCenter = box.centerX,
    bboxYCenter = box.centerY,
    bboxWidth = box.width,
    bboxHeight = box.height,
    confidenceScore = confidence,
    origin = origin.ordinal,
)

// --- Movement --------------------------------------------------------------

internal fun MovementEntity.toDomain(): Movement = Movement(
    id = id,
    blocId = blocId,
    sequenceOrder = sequenceOrder,
    limb = Limb.entries.getOrElse(limbType) { Limb.RIGHT_HAND },
    sourceHoldId = sourceHoldId,
    targetHoldId = targetHoldId,
)

internal fun Movement.toEntity(): MovementEntity = MovementEntity(
    id = id,
    blocId = blocId,
    sequenceOrder = sequenceOrder,
    limbType = limb.ordinal,
    sourceHoldId = sourceHoldId,
    targetHoldId = targetHoldId,
)
