package com.topo.core.common.di

import javax.inject.Qualifier

/**
 * Qualificateurs de dispatchers de coroutines.
 *
 * Aucune classe de l'application ne reference Dispatchers.IO ou Dispatchers.Default
 * en dur : ils sont injectes. Sans cela, un test unitaire ne peut ni controler
 * l'ordonnancement ni substituer un dispatcher de test, et toute la couche data
 * devient dependante d'un vrai pool de threads.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
