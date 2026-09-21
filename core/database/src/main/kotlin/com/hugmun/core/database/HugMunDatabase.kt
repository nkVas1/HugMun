/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hugmun.core.database.dao.AnchorDao
import com.hugmun.core.database.dao.ProtocolDao
import com.hugmun.core.database.dao.SafetyDao
import com.hugmun.core.database.dao.VigilanceDao
import com.hugmun.core.database.entity.AdverseEventEntity
import com.hugmun.core.database.entity.AnchorItemEntity
import com.hugmun.core.database.entity.AnchorReviewEntity
import com.hugmun.core.database.entity.PracticeLockEntity
import com.hugmun.core.database.entity.ProtocolStateEntity
import com.hugmun.core.database.entity.SafetyStateEntity
import com.hugmun.core.database.entity.VigilanceSessionEntity
import com.hugmun.core.database.entity.VigilanceTrialEntity
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The local store. There is no remote counterpart and there never will be.
 *
 * Schemas are exported to `core/database/schemas` and committed: a migration that is not
 * backed by a visible schema diff in review is a migration nobody has checked.
 */
@Database(
    entities = [
        ProtocolStateEntity::class,
        VigilanceSessionEntity::class,
        VigilanceTrialEntity::class,
        AnchorItemEntity::class,
        AnchorReviewEntity::class,
        SafetyStateEntity::class,
        PracticeLockEntity::class,
        AdverseEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
public abstract class HugMunDatabase : RoomDatabase() {
    public abstract fun protocolDao(): ProtocolDao
    public abstract fun vigilanceDao(): VigilanceDao
    public abstract fun safetyDao(): SafetyDao
    public abstract fun anchorDao(): AnchorDao

    public companion object {
        public const val NAME: String = "hugmun.db"

        public fun create(context: Context, queryDispatcher: CoroutineDispatcher): HugMunDatabase = Room
            .databaseBuilder<HugMunDatabase>(
                context = context.applicationContext,
                name = context.applicationContext.getDatabasePath(NAME).absolutePath,
            )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(queryDispatcher)
            // No fallbackToDestructiveMigration. This database holds a person's family
            // photographs and medication routines; silently dropping it on a schema
            // mistake would be the worst possible failure mode.
            .build()
    }
}
