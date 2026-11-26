package fr.prisme.player.data.backup

import android.content.ContentValues
import android.database.Cursor
import fr.prisme.player.data.db.IptvDatabase
import fr.prisme.player.data.settings.LocalPlaylist
import fr.prisme.player.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Exports / restores everything the user configured, as one JSON file: the settings
 * (source and credentials included), the per-profile tables and, when the source is a
 * file sent from the phone, that playlist file. The catalogue itself is not saved — it is
 * rebuilt from the source after a restore.
 *
 * Tables are copied column by column through SQLite, so the format follows the Room
 * schema without per-entity code. A restore only writes columns that exist in the
 * current schema, which keeps older backups importable after a migration.
 */
class BackupManager(
    private val database: IptvDatabase,
    private val settings: SettingsStore,
) {

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val tables = buildJsonObject {
            val db = database.openHelper.readableDatabase
            for (table in USER_TABLES) {
                put(table, buildJsonArray {
                    db.query("SELECT * FROM `$table`").use { c -> while (c.moveToNext()) add(c.rowToJson()) }
                })
            }
        }
        val root = buildJsonObject {
            put("app", JsonPrimitive(APP_ID))
            put("format", JsonPrimitive(FORMAT))
            put("createdAt", JsonPrimitive(System.currentTimeMillis()))
            put("settings", settings.exportForBackup())
            put("tables", tables)
            LocalPlaylist.readText()?.let { put("localPlaylist", JsonPrimitive(it)) }
        }
        json.encodeToString(JsonObject.serializer(), root)
    }

    /** @throws IllegalArgumentException when the file is not a Prism backup. */
    suspend fun restore(raw: String) = withContext(Dispatchers.IO) {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw IllegalArgumentException("Fichier illisible.") }
        require(root["app"]?.jsonPrimitive?.content == APP_ID) { "Ce fichier n'est pas une sauvegarde Prism." }
        require((root["format"]?.jsonPrimitive?.int ?: 0) <= FORMAT) { "Sauvegarde créée par une version plus récente de Prism." }
        // Checked before anything is overwritten, so a damaged file cannot leave half a restore.
        val playlist = (root["localPlaylist"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        playlist?.let { LocalPlaylist.parse(it) }

        val tables = root["tables"] as? JsonObject
        if (tables != null) {
            database.runInTransaction {
                val db = database.openHelper.writableDatabase
                for (table in USER_TABLES) {
                    val rows = tables[table] as? JsonArray ?: continue
                    val columns = db.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toSet() }
                    db.execSQL("DELETE FROM `$table`")
                    for (row in rows) {
                        val values = ContentValues()
                        for ((key, value) in (row as? JsonObject ?: continue)) {
                            if (key in columns) values.putJson(key, value)
                        }
                        db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
                    }
                }
            }
        }
        playlist?.let { LocalPlaylist.restore(it) }
        (root["settings"] as? JsonObject)?.let { settings.restoreFromBackup(it) }
    }

    private fun Cursor.rowToJson(): JsonObject = buildJsonObject {
        for (i in 0 until columnCount) {
            val value: JsonElement = when (getType(i)) {
                Cursor.FIELD_TYPE_NULL -> JsonNull
                Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> continue
                else -> JsonPrimitive(getString(i))
            }
            put(getColumnName(i), value)
        }
    }

    private fun ContentValues.putJson(key: String, value: JsonElement) {
        if (value is JsonNull) {
            putNull(key)
            return
        }
        val p = value as? JsonPrimitive ?: return
        when {
            p.isString -> put(key, p.content)
            p.longOrNull != null -> put(key, p.longOrNull)
            p.doubleOrNull != null -> put(key, p.doubleOrNull)
            p.booleanOrNull != null -> put(key, if (p.booleanOrNull == true) 1L else 0L)
        }
    }

    companion object {
        private const val APP_ID = "prism"
        private const val FORMAT = 1
        private val json = Json { prettyPrint = true }

        /** User-owned tables; catalogue, EPG and caches are rebuilt from the source. */
        val USER_TABLES = listOf(
            "profiles",
            "favorites",
            "watchlist",
            "watch_progress",
            "watched_episodes",
            "reminders",
        )
    }
}
