package com.betaclimb.core.data.di

import android.content.Context
import androidx.room.Room
import com.betaclimb.core.data.dao.BlocDao
import com.betaclimb.core.data.dao.HoldDao
import com.betaclimb.core.data.dao.MovementDao
import com.betaclimb.core.data.dao.SessionDao
import com.betaclimb.core.data.database.BetaClimbDatabase
import com.betaclimb.core.data.database.applyBetaClimbConfiguration
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
     * `applyBetaClimbConfiguration` : les tests instrumentes appliquent la meme
     * fonction, donc ils valident la configuration reellement utilisee en
     * production et non une copie destinee a diverger.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BetaClimbDatabase =
        Room.databaseBuilder(context, BetaClimbDatabase::class.java, BetaClimbDatabase.NAME)
            .applyBetaClimbConfiguration()
            .build()

    @Provides
    fun provideSessionDao(database: BetaClimbDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideBlocDao(database: BetaClimbDatabase): BlocDao = database.blocDao()

    @Provides
    fun provideHoldDao(database: BetaClimbDatabase): HoldDao = database.holdDao()

    @Provides
    fun provideMovementDao(database: BetaClimbDatabase): MovementDao = database.movementDao()
}
