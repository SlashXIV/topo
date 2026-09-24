package com.topo.core.data.di

import com.topo.core.common.di.DefaultDispatcher
import com.topo.core.common.di.IoDispatcher
import com.topo.core.data.repository.BetaRepositoryImpl
import com.topo.core.data.repository.BlocRepositoryImpl
import com.topo.core.data.repository.SessionRepositoryImpl
import com.topo.core.data.storage.InternalBlocImageStore
import com.topo.core.domain.repository.BetaRepository
import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.repository.SessionRepository
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
