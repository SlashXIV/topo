package com.topo.vision

import com.topo.core.domain.repository.HoldDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Sortie du module `:vision` vers le graphe d'injection : l'interface du
 * domaine, jamais le moteur natif. Un ecran recoit un [HoldDetector] et ignore
 * qu'ONNX Runtime existe.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class VisionModule {

    @Binds
    abstract fun bindHoldDetector(detector: BundledModelHoldDetector): HoldDetector
}
