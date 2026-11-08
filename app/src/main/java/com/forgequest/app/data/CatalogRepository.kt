package com.forgequest.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.forgequest.app.domain.Barcodes
import com.forgequest.app.domain.Catalog
import com.forgequest.app.domain.CatalogFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The read-only food list shipped as assets/catalog.db. It is not user data: it lives in no-backup storage, is copied from
 * the asset on first use and again after an app update, and is not part of Room or backups.
 */
class CatalogRepository(private val context: Context) {
    private val lock = Mutex()
    private var db: SQLiteDatabase? = null
    private var all: List<CatalogFood>? = null

    private fun open(): SQLiteDatabase? {
        db?.let { return it }
        return try {
            val file = File(context.noBackupFilesDir, "catalog.db")
            val stamp = File(context.noBackupFilesDir, "catalog.stamp")
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString() }.getOrDefault("0")
            if (!file.exists() || !stamp.exists() || stamp.readText() != version) {
                val tmp = File(context.noBackupFilesDir, "catalog.tmp")
                context.assets.open("catalog.db").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                file.delete()
                tmp.renameTo(file)
                stamp.writeText(version)
            }
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
        } catch (_: Exception) {
            null // no list on this build: search just shows nothing from it
        }
    }

    private fun Cursor.food() = CatalogFood(
        id = getLong(0), kind = getString(1).orEmpty(), barcode = getString(2).orEmpty(), nameEn = getString(3).orEmpty(),
        nameAr = getString(4).orEmpty(), brand = getString(5).orEmpty(), brandAr = getString(6).orEmpty(), kcal = getFloat(7), protein = getFloat(8),
        carbs = getFloat(9), servingG = if (isNull(10)) null else getFloat(10), units = Catalog.parseUnits(getString(11)), scans = getInt(12), liquid = getInt(13) == 1,
    )

    private val columns = "id,kind,barcode,name_en,name_ar,brand,brand_ar,kcal,protein,carbs,serving_g,units,scans,liquid"

    /** The product with this barcode, trying every written form of it (UPC-A with or without its leading 0). */
    suspend fun byBarcode(code: String): CatalogFood? = withContext(Dispatchers.IO) {
        lock.withLock {
            val d = open() ?: return@withLock null
            Barcodes.variants(code).firstNotNullOfOrNull { c ->
                d.rawQuery("SELECT $columns FROM food WHERE barcode = ? LIMIT 1", arrayOf(c)).use { if (it.moveToFirst()) it.food() else null }
            }
        }
    }

    /** Loaded once, off the main thread; later searches reuse it. */
    private fun names(): List<CatalogFood> = all ?: run {
        val d = open()
        val list = d?.rawQuery("SELECT $columns FROM food", null)?.use { c -> buildList { while (c.moveToNext()) add(c.food()) } }.orEmpty()
        list.also { all = it }
    }

    suspend fun search(query: String, limit: Int = 20): List<CatalogFood> = withContext(Dispatchers.Default) {
        val list = lock.withLock { names() }
        Catalog.search(list, query, limit)
    }
}
