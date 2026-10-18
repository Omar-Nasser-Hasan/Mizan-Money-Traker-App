// Copyright (c) 2026 Mizan contributors
package app.mizan.di

import app.mizan.data.db.AppDatabase
import app.mizan.data.repository.MizanRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Access contract used by instrumentation tests and non-Hilt Android entry points. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun repository(): MizanRepository

    fun database(): AppDatabase
}
