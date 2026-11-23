package fr.mescourses.app.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

// Android garde le SQLite du système (aucun pilote configuré) : Room appelle la version SupportSQLite.
actual fun platformMigration(from: Int, to: Int, statements: List<String>): Migration = object : Migration(from, to) {
    override fun migrate(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }

    override fun migrate(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
    }
}
