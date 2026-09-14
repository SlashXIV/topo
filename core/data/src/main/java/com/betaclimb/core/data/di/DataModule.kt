package com.betaclimb.core.data.di

import com.betaclimb.core.common.di.DefaultDispatcher
import com.betaclimb.core.common.di.IoDispatcher
import com.betaclimb.core.data.repository.BetaRepositoryImpl
import com.betaclimb.core.data.repository.BlocRepositoryImpl
import com.betaclimb.core.data.repository.SessionRepositoryImpl
import com.betaclimb.core.data.storage.InternalBlocImageStore
import com.betaclimb.core.domain.repository.BetaRepository
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.BlocRepository
import com.betaclimb.core.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Relie les interfaces du domaine a leurs implementations.
 *
 * C'est ici, et nulle part ailleurs, que le domaine apprend qu'il existe une base
 * SQLite. Les implementations sont `internal` : aucun module consommateur ne peut
 * les referencer, meme par accident — la seule porte d'entree est l'interface.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindBlocRepository(impl: BlocRepositoryImpl): BlocRepository

    @Binds
    @Singleton
    abstract fun bindBetaRepository(impl: BetaRepositoryImpl): BetaRepository

    @Binds
    @Singleton
    abstract fun bindBlocImageStore(impl: InternalBlocImageStore): BlocImageStore
}

@Module
@InstallIn(SingletonComponent::class)
internal object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
