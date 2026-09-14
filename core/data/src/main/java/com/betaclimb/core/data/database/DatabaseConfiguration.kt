package com.betaclimb.core.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

/**
 * Configuration de la base, definie UNE fois.
 *
 * Extraite du module Hilt a dessein : les tests instrumentes appliquent exactement
 * cette fonction. Sans cela, ils valideraient une configuration recopiee, et le
 * jour ou la production divergerait (un WAL desactive, un executeur multi-thread
 * sur les transactions), les tests resteraient verts pour rien.
 */
internal fun RoomDatabase.Builder<BetaClimbDatabase>.applyBetaClimbConfiguration():
    RoomDatabase.Builder<BetaClimbDatabase> = this
    // -----------------------------------------------------------------------
    //  Write-Ahead Logging
    //
    //  En mode "rollback journal" (l'historique de SQLite), chaque ecriture prend
    //  un verrou EXCLUSIF sur tout le fichier : aucune lecture ne peut s'executer
    //  pendant ce temps. Avec des ecrans abonnes a des Flow, cela se traduit
    //  directement par une interface qui se fige pendant un enregistrement.
    //
    //  En WAL, les ecritures vont dans un journal annexe (.db-wal) et les lecteurs
    //  continuent de lire le dernier etat valide sans etre bloques. Room active
    //  WAL par defaut sur les API recentes, mais l'invocation reste EXPLICITE :
    //  c'est une propriete dont depend la fluidite de l'application, elle ne doit
    //  pas tenir a un defaut implicite susceptible de changer.
    // -----------------------------------------------------------------------
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

    // -----------------------------------------------------------------------
    //  Pool de connexions
    //
    //  Ecritures SERIALISEES sur un fil unique : en WAL, SQLite n'admet qu'un seul
    //  ecrivain a la fois, et deux transactions concurrentes se solderaient par un
    //  SQLITE_BUSY. Un executeur mono-thread transforme cette contrainte en file
    //  d'attente au lieu d'une exception.
    //
    //  Lectures sur un pool : elles sont massivement paralleles (un Flow par ecran
    //  visible) et, grace au WAL, reellement concurrentes.
    // -----------------------------------------------------------------------
    .setTransactionExecutor(Executors.newSingleThreadExecutor())
    .setQueryExecutor(Executors.newFixedThreadPool(QUERY_THREADS))

    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Ceinture et bretelles : Room active deja les cles etrangeres, mais
            // tout le modele repose sur les cascades ON DELETE. Si la contrainte
            // n'etait pas appliquee, supprimer un bloc laisserait silencieusement
            // des prises orphelines.
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    })

/** Quatre lecteurs : au-dela, on paie des changements de contexte pour rien. */
private const val QUERY_THREADS = 4
