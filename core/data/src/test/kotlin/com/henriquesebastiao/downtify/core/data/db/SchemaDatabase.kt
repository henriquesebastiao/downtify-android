package com.henriquesebastiao.downtify.core.data.db

import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Writes [file] as an installed app left it: the tables of exported schema [schema] (but [without]),
 * the rows [fill] adds, and [version].
 */
internal fun createFromSchema(
    file: File,
    schema: Int,
    version: Int = schema,
    without: Set<String> = emptySet(),
    fill: (SQLiteDatabase) -> Unit = {},
) {
    val resource =
        File(
            System.getProperty("downtify.schemas"),
            "com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase/$schema.json",
        )
    val database = Json.parseToJsonElement(resource.readText())
        .jsonObject.getValue("database").jsonObject
    SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
        for (entry in database.getValue("entities").jsonArray) {
            val entity = entry.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            if (table in without) continue
            old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            for (index in entity["indices"]?.jsonArray.orEmpty()) {
                old.execSQL(
                    index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table),
                )
            }
        }
        for (query in database.getValue("setupQueries").jsonArray) old.execSQL(query.jsonPrimitive.content)
        fill(old)
        old.version = version
    }
}
