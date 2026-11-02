package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v50 → v51:
 *   - invites.childId (nullable TEXT) — инвайт родителя сразу с ребёнком
 *   - parent_child_links.inviteCode (nullable TEXT) — код инвайта, по которому создана связь
 *   - индекс на parent_child_links(parentId), (childId) — для observeByParent/observeByChild
 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invites ADD COLUMN childId TEXT")
        db.execSQL("ALTER TABLE parent_child_links ADD COLUMN inviteCode TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_parent_child_links_parentId ON parent_child_links(parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_parent_child_links_childId ON parent_child_links(childId)")
    }
}
