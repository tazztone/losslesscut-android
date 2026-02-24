package com.tazztone.losslesscut.domain.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class DefaultDispatcher
