package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v51 → v52:
 *   - parent_child_links: индекс на clubId (observeByClub в ManageParentsViewModel).
 *
 * На устройствах, где 50→51 уже применена, индексы parentId/childId
 * созданы, а clubId — нет. Room ждёт {parentId, childId, clubId} по @Entity,
 * поэтому без этого индекса — Migration didn't properly handle.
 */
val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_parent_child_links_clubId " +
            "ON parent_child_links(clubId)"
        )
    }
}
