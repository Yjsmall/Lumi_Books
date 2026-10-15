package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContextNoteMigrationTest {
    @Test fun oldNotesKeepTheirIdentityAnchorsAndTagsWithNullableContextMetadata() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, note TEXT NOT NULL, syncId TEXT NOT NULL, startPosition INTEGER NOT NULL, startLocatorJson TEXT, tagsJson TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO notes VALUES (7,'Existing note','stable-sync',42,'{\"position\":42}','[\"Review\"]')")
            DatabaseMigrations.MIGRATION_18_19.migrate(db)

            db.query("SELECT id,note,syncId,startPosition,startLocatorJson,tagsJson,contextJson FROM notes").use {
                assertTrue(it.moveToFirst())
                assertEquals(7L, it.getLong(0))
                assertEquals("Existing note", it.getString(1))
                assertEquals("stable-sync", it.getString(2))
                assertEquals(42, it.getInt(3))
                assertEquals("{\"position\":42}", it.getString(4))
                assertEquals("[\"Review\"]", it.getString(5))
                assertTrue(it.isNull(6))
            }
            db.execSQL("UPDATE notes SET contextJson = ? WHERE id = 7", arrayOf("{\"meaning\":\"saved\"}"))
            db.query("SELECT contextJson FROM notes WHERE id=7").use {
                assertTrue(it.moveToFirst())
                assertEquals("{\"meaning\":\"saved\"}", it.getString(0))
            }
        }
    }
}
