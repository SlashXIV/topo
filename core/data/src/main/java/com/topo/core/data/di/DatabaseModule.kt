package com.topo.core.data.di

import android.content.Context
import androidx.room.Room
import com.topo.core.data.dao.BlocDao
import com.topo.core.data.dao.HoldDao
import com.topo.core.data.dao.MovementDao
import com.topo.core.data.dao.CircuitColorDao
import com.topo.core.data.dao.SessionDao
import com.topo.core.data.dao.SettingsDao
import com.topo.core.data.database.TopoDatabase
import com.topo.core.data.database.applyTopoConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    /**
     * La configuration (WAL, executeurs, cles etrangeres) vit dans
     * `applyTopoConfiguration` : les tests instrumentes appliquent la meme
     * fonction, donc ils valident la configuration reellement utilisee en
     * production et non une copie destinee a diverger.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TopoDatabase =
        Room.databaseBuilder(context, TopoDatabase::class.java, TopoDatabase.NAME)
            .applyTopoConfiguration()
            .build()

    @Provides
    fun provideSessionDao(database: TopoDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideBlocDao(database: TopoDatabase): BlocDao = database.blocDao()

    @Provides
    fun provideHoldDao(database: TopoDatabase): HoldDao = database.holdDao()

    @Provides
    fun provideMovementDao(database: TopoDatabase): MovementDao = database.movementDao()

    @Provides
    fun provideCircuitColorDao(database: TopoDatabase): CircuitColorDao = database.circuitColorDao()

    @Provides
    fun provideSettingsDao(database: TopoDatabase): SettingsDao = database.settingsDao()
}
