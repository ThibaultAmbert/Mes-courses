package fr.mescourses.app.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

actual fun platformMigration(from: Int, to: Int, statements: List<String>): Migration = object : Migration(from, to) {
    override fun migrate(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
    }
}
