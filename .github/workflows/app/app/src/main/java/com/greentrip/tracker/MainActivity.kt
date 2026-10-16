@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.greentrip.tracker

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ==========================================
// HỆ THỐNG 6 GIAO DIỆN HÃNG XE (THEMES)
// ==========================================
data class AppThemePalette(
    val id: Int,
    val name: String,
    val badgeText: String,
    val isLight: Boolean,
    val bg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val inputBg: Color,
    val headerTop: Color,
    val headerMid: Color,
    val accent: Color,
    val onAccent: Color,
    val highlight1: Color,
    val highlight2: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val danger: Color,
    val warningBg: Color,
    val warningBorder: Color
)

val ThemeList = listOf(
    AppThemePalette(
        id = 0,
        name = "Mặc định (Xanh Ngọc)",
        badgeText = "XANH SM",
        isLight = false,
        bg = Color(0xFF101315),
        cardBg = Color(0xFF1A1D20),
        cardBorder = Color(0xFF262B2F),
        inputBg = Color(0xFF131618),
        headerTop = Color(0xFF00382E),
        headerMid = Color(0xFF071F1A),
        accent = Color(0xFF00B074),
        onAccent = Color.White,
        highlight1 = Color(0xFF00E676),
        highlight2 = Color(0xFF00E5FF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8E959B),
        danger = Color(0xFFFF5252),
        warningBg = Color(0xFF2C1619),
        warningBorder = Color(0xFF4A1F24)
    ),
    AppThemePalette(
        id = 1,
        name = "Vàng Be (Tài xế Be)",
        badgeText = "BE DRIVER",
        isLight = false,
        bg = Color(0xFF121316),
        cardBg = Color(0xFF1C1E24),
        cardBorder = Color(0xFF2E323B),
        inputBg = Color(0xFF14161A),
        headerTop = Color(0xFF423200),
        headerMid = Color(0xFF241C05),
        accent = Color(0xFFFFB800),
        onAccent = Color(0xFF111315),
        highlight1 = Color(0xFFFFD54F),
        highlight2 = Color(0xFF4FC3F7),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF9EA4B0),
        danger = Color(0xFFFF5252),
        warningBg = Color(0xFF2E171A),
        warningBorder = Color(0xFF522329)
    ),
    AppThemePalette(
        id = 2,
        name = "Xanh Lá (Tài xế Grab)",
        badgeText = "GRAB DRIVER",
        isLight = false,
        bg = Color(0xFF0E1410),
        cardBg = Color(0xFF17201A),
        cardBorder = Color(0xFF253329),
        inputBg = Color(0xFF111813),
        headerTop = Color(0xFF003D1B),
        headerMid = Color(0xFF072112),
        accent = Color(0xFF00B14F),
        onAccent = Color.White,
        highlight1 = Color(0xFF00E676),
        highlight2 = Color(0xFF81C784),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8FA395),
        danger = Color(0xFFFF5252),
        warningBg = Color(0xFF2C1619),
        warningBorder = Color(0xFF4A1F24)
    ),
    AppThemePalette(
        id = 3,
        name = "Xanh Cyan (Green SM)",
        badgeText = "GREEN SM",
        isLight = false,
        bg = Color(0xFF0B1319),
        cardBg = Color(0xFF142029),
        cardBorder = Color(0xFF213542),
        inputBg = Color(0xFF0E1820),
        headerTop = Color(0xFF003B4D),
        headerMid = Color(0xFF05202B),
        accent = Color(0xFF00BCD4),
        onAccent = Color(0xFF06161C),
        highlight1 = Color(0xFF00E5FF),
        highlight2 = Color(0xFF1DE9B6),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8AA2B3),
        danger = Color(0xFFFF5252),
        warningBg = Color(0xFF2C1619),
        warningBorder = Color(0xFF4A1F24)
    ),
    AppThemePalette(
        id = 4,
        name = "Giao diện Sáng (Rõ nét)",
        badgeText = "LIGHT MODE",
        isLight = true,
        bg = Color(0xFFF2F5F8),
        cardBg = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFDCE1E7),
        inputBg = Color(0xFFF7F9FC),
        headerTop = Color(0xFF00695C),
        headerMid = Color(0xFF00897B),
        accent = Color(0xFF00897B),
        onAccent = Color.White,
        highlight1 = Color(0xFF00796B),
        highlight2 = Color(0xFF0277BD),
        textPrimary = Color(0xFF1A1D20),
        textSecondary = Color(0xFF5F6B76),
        danger = Color(0xFFD32F2F),
        warningBg = Color(0xFFFFEBEE),
        warningBorder = Color(0xFFFFCDD2)
    ),
    AppThemePalette(
        id = 5,
        name = "Giao diện Tối Sâu (AMOLED)",
        badgeText = "DARK MODE",
        isLight = false,
        bg = Color(0xFF000000),
        cardBg = Color(0xFF121212),
        cardBorder = Color(0xFF262626),
        inputBg = Color(0xFF0A0A0A),
        headerTop = Color(0xFF1F1F24),
        headerMid = Color(0xFF121215),
        accent = Color(0xFF10B981),
        onAccent = Color.White,
        highlight1 = Color(0xFF34D399),
        highlight2 = Color(0xFF38BDF8),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFF8E8E93),
        danger = Color(0xFFF87171),
        warningBg = Color(0xFF261013),
        warningBorder = Color(0xFF451A1F)
    )
)

val LocalAppTheme = compositionLocalOf { ThemeList[0] }

// ==========================================
// BỘ QUẢN LÝ AUTO SAVE & LOAD VÀO FILE MẶC ĐỊNH
// ==========================================
object DefaultBackupManager {
    private const val FOLDER = "GreenTripTracker"
    private const val FILE_NAME = "GreenTrip_Backup_Auto.json"

    fun save(context: Context, jsonStr: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(FILE_NAME, "%$FOLDER%")
                
                resolver.query(contentUri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        resolver.delete(ContentUris.withAppendedId(contentUri, id), null, null)
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER/")
                }
                val newUri = resolver.insert(contentUri, values) ?: return false
                resolver.openOutputStream(newUri, "wt")?.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, FILE_NAME)
                file.writeText(jsonStr, Charsets.UTF_8)
                true
            }
        } catch (e: Exception) {
            try {
                val file = File(context.getExternalFilesDir(null), FILE_NAME)
                file.writeText(jsonStr, Charsets.UTF_8)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun load(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(FILE_NAME, "%$FOLDER%")
                var targetUri: Uri? = null
                resolver.query(contentUri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        targetUri = ContentUris.withAppendedId(contentUri, id)
                    }
                }
                if (targetUri != null) {
                    resolver.openInputStream(targetUri!!)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                } else {
                    val f = File(context.getExternalFilesDir(null), FILE_NAME)
                    if (f.exists()) f.readText(Charsets.UTF_8) else null
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER)
                val file = File(dir, FILE_NAME)
                if (file.exists()) file.readText(Charsets.UTF_8) else {
                    val f = File(context.getExternalFilesDir(null), FILE_NAME)
                    if (f.exists()) f.readText(Charsets.UTF_8) else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

// ==========================================
// 1. DATA ENTITIES & ROOM DATABASE
// ==========================================
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val distanceKm: Double,
    val durationMinutes: Double,
    val source: String = "Đơn Xanh SM"
)

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val date: String,
    val startOdo: Double = 0.0,
    val endOdo: Double = 0.0,
    val revenue: Double = 0.0
)

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE date = :date ORDER BY id DESC")
    fun getTripsByDate(date: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY date ASC, id ASC")
    suspend fun getAllTrips(): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShift(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShifts(shifts: List<ShiftEntity>)

    @Query("SELECT * FROM shifts WHERE date = :date")
    fun getShiftByDate(date: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY date ASC")
    suspend fun getAllShifts(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE date < :date AND endOdo > 0 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestShiftBefore(date: String): ShiftEntity?

    @Query("SELECT * FROM trips WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, id DESC")
    fun getTripsBetween(startDate: String, endDate: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM shifts WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getShiftsBetween(startDate: String, endDate: String): Flow<List<ShiftEntity>>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)
}

@Database(entities = [TripEntity::class, ShiftEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "green_trip_db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}

// ==========================================
// 2. OCR BÓC TÁCH (XỬ LÝ MÉT & LỌC NHIỄU)
// ==========================================
data class ParsedTrip(val distanceKm: Double, val durationMinutes: Double, val source: String)

class OcrProcessor(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImageUri(uri: Uri): ParsedTrip? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { bitmap = BitmapFactory.decodeStream(it) }
            val currentBitmap = bitmap ?: return@withContext null
            val visionText = recognizer.process(InputImage.fromBitmap(currentBitmap, 0)).await()
            val text = visionText.text

            var distance = 0.0
            val kmMatch = Regex("""(?i)(\d+([.,]\d+)?)\s*km(?!/h)""").find(text)
            if (kmMatch != null) {
                distance = kmMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            } else {
                val mMatch = Regex("""(?i)(\d+)\s*m\b(?!/h|in)""").find(text)
                if (mMatch != null) {
                    val meters = mMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    distance = meters / 1000.0
                }
            }

            var totalMin = 0.0
            Regex("""(?i)(\d+)\s*giờ""").find(text)?.let {
                totalMin += (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60
            }

            val filteredLines = text.lines().filterNot {
                it.contains("chậm hơn", ignoreCase = true) || it.contains("nhanh hơn", ignoreCase = true)
            }.joinToString("\n")

            Regex("""(?i)(\d+([.,]\d+)?)\s*(?:phút|ph)""").find(filteredLines)?.let {
                totalMin += it.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            var src = "Đơn Xanh SM"
            if (text.contains("hướng", ignoreCase = true) || text.contains("sau đó", ignoreCase = true) || text.contains("về hướng", ignoreCase = true)) {
                src = "Google Maps"
            }

            ParsedTrip(distance, totalMin, src)
        } catch (e: Exception) {
            null
        } finally {
            bitmap?.recycle()
            bitmap = null
        }
    }
}

data class DuplicateCheckResult(
    val uniqueTrips: List<TripEntity>,
    val duplicateTrips: List<TripEntity>
)

// ==========================================
// 3. VIEWMODEL ĐIỀU HƯỚNG NGÀY / TUẦN / THÁNG
// ==========================================
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val prefs = appContext.getSharedPreferences("greentrip_prefs", Context.MODE_PRIVATE)
    private val dao = AppDatabase.getInstance(application).tripDao()
    private val ocrProcessor = OcrProcessor(application)

    var selectedThemeId by mutableStateOf(prefs.getInt("theme_id", 0))
        private set

    fun selectTheme(id: Int) {
        selectedThemeId = id
        prefs.edit().putInt("theme_id", id).apply()
    }

    val currentTheme: AppThemePalette
        get() = ThemeList.find { it.id == selectedThemeId } ?: ThemeList[0]

    val todayDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // --- ĐIỀU HƯỚNG TAB NGÀY ---
    val dayCal = MutableStateFlow(Calendar.getInstance())
    val selectedDate = MutableStateFlow(todayDate)

    fun previousDay() {
        val c = dayCal.value.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -1)
        dayCal.value = c
        selectedDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
    }

    fun nextDay() {
        val c = dayCal.value.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, 1)
        dayCal.value = c
        selectedDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
    }

    fun goToToday() {
        val c = Calendar.getInstance()
        dayCal.value = c
        selectedDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
    }

    fun isTodaySelected(): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(dayCal.value.time) == todayDate
    }

    fun getSelectedDayTitle(): String {
        val c = dayCal.value
        val dayOfWeek = when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Thứ Hai"
            Calendar.TUESDAY -> "Thứ Ba"
            Calendar.WEDNESDAY -> "Thứ Tư"
            Calendar.THURSDAY -> "Thứ Năm"
            Calendar.FRIDAY -> "Thứ Sáu"
            Calendar.SATURDAY -> "Thứ Bảy"
            Calendar.SUNDAY -> "Chủ Nhật"
            else -> ""
        }
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return "$dayOfWeek, ${df.format(c.time)}"
    }

    // --- ĐIỀU HƯỚNG TAB TUẦN ---
    val weekCal = MutableStateFlow(Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY })

    fun previousWeek() {
        val c = weekCal.value.clone() as Calendar
        c.add(Calendar.WEEK_OF_YEAR, -1)
        weekCal.value = c
    }

    fun nextWeek() {
        val c = weekCal.value.clone() as Calendar
        c.add(Calendar.WEEK_OF_YEAR, 1)
        weekCal.value = c
    }

    fun goToCurrentWeek() {
        weekCal.value = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
    }

    fun isCurrentWeekSelected(): Boolean {
        val now = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
        return weekCal.value.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR) &&
               weekCal.value.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    }

    fun getSelectedWeekTitle(): String {
        val c = weekCal.value.clone() as Calendar
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val start = sdf.format(c.time)
        c.add(Calendar.DAY_OF_WEEK, 6)
        val end = sdf.format(c.time)
        val year = c.get(Calendar.YEAR)
        val weekNum = c.get(Calendar.WEEK_OF_YEAR)
        return "Tuần $weekNum ($start - $end/$year)"
    }

    private val currentWeekBounds = weekCal.map { cal ->
        val c = cal.clone() as Calendar
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val s = sdf.format(c.time)
        c.add(Calendar.DAY_OF_WEEK, 6)
        val e = sdf.format(c.time)
        Pair(s, e)
    }

    val selectedWeekTrips = currentWeekBounds.flatMapLatest { (s, e) ->
        dao.getTripsBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedWeekShifts = currentWeekBounds.flatMapLatest { (s, e) ->
        dao.getShiftsBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- ĐIỀU HƯỚNG TAB THÁNG ---
    val monthCal = MutableStateFlow(Calendar.getInstance())

    fun previousMonth() {
        val c = monthCal.value.clone() as Calendar
        c.add(Calendar.MONTH, -1)
        monthCal.value = c
    }

    fun nextMonth() {
        val c = monthCal.value.clone() as Calendar
        c.add(Calendar.MONTH, 1)
        monthCal.value = c
    }

    fun goToCurrentMonth() {
        monthCal.value = Calendar.getInstance()
    }

    fun isCurrentMonthSelected(): Boolean {
        val now = Calendar.getInstance()
        return monthCal.value.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
               monthCal.value.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    }

    fun getSelectedMonthTitle(): String {
        val c = monthCal.value
        return "Tháng ${c.get(Calendar.MONTH) + 1} / ${c.get(Calendar.YEAR)}"
    }

    private val currentMonthBounds = monthCal.map { cal ->
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val s = sdf.format(c.time)
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        val e = sdf.format(c.time)
        Pair(s, e)
    }

    val selectedMonthTrips = currentMonthBounds.flatMapLatest { (s, e) ->
        dao.getTripsBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedMonthShifts = currentMonthBounds.flatMapLatest { (s, e) ->
        dao.getShiftsBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // DỮ LIỆU NGÀY
    val currentTrips = selectedDate.flatMapLatest { dao.getTripsByDate(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentShift = selectedDate.flatMapLatest { dao.getShiftByDate(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var isScanning by mutableStateOf(false)
    var duplicatePrompt by mutableStateOf<DuplicateCheckResult?>(null)

    init {
        checkAndAutofillStartOdo()
    }

    private fun checkAndAutofillStartOdo() {
        viewModelScope.launch {
            val cur = dao.getShiftByDate(todayDate).firstOrNull()
            if (cur == null || cur.startOdo <= 0.0) {
                val lastShift = dao.getLatestShiftBefore(todayDate)
                if (lastShift != null && lastShift.endOdo > 0) {
                    dao.saveShift(
                        ShiftEntity(
                            date = todayDate,
                            startOdo = lastShift.endOdo,
                            endOdo = cur?.endOdo ?: 0.0,
                            revenue = cur?.revenue ?: 0.0
                        )
                    )
                }
            }
        }
    }

    // TỰ ĐỘNG SAO LƯU NGẦM VÀO FILE MẶC ĐỊNH
    private fun autoSaveSilently() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trips = dao.getAllTrips()
                val shifts = dao.getAllShifts()
                val json = buildJsonString(trips, shifts)
                DefaultBackupManager.save(appContext, json)
            } catch (_: Exception) {}
        }
    }

    fun manualBackupDefault() {
        viewModelScope.launch {
            try {
                val trips = withContext(Dispatchers.IO) { dao.getAllTrips() }
                val shifts = withContext(Dispatchers.IO) { dao.getAllShifts() }
                val json = buildJsonString(trips, shifts)
                val success = withContext(Dispatchers.IO) { DefaultBackupManager.save(appContext, json) }
                if (success) {
                    Toast.makeText(appContext, "✅ Đã lưu ${trips.size} cuốc & ${shifts.size} ngày vào file mặc định!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(appContext, "❌ Lưu file thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(appContext, "❌ Lỗi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun manualRestoreDefault() {
        viewModelScope.launch {
            try {
                val jsonStr = withContext(Dispatchers.IO) { DefaultBackupManager.load(appContext) }
                if (jsonStr.isNullOrBlank()) {
                    Toast.makeText(appContext, "❌ Chưa tìm thấy file sao lưu mặc định!", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val root = JSONObject(jsonStr)
                val shiftsToInsert = mutableListOf<ShiftEntity>()
                val tripsToInsert = mutableListOf<TripEntity>()

                if (root.has("shifts")) {
                    val shiftsArr = root.getJSONArray("shifts")
                    for (i in 0 until shiftsArr.length()) {
                        val o = shiftsArr.getJSONObject(i)
                        shiftsToInsert.add(
                            ShiftEntity(
                                date = o.getString("date"),
                                startOdo = o.optDouble("startOdo", 0.0),
                                endOdo = o.optDouble("endOdo", 0.0),
                                revenue = o.optDouble("revenue", 0.0)
                            )
                        )
                    }
                }

                val existingTrips = withContext(Dispatchers.IO) { dao.getAllTrips() }
                if (root.has("trips")) {
                    val tripsArr = root.getJSONArray("trips")
                    for (i in 0 until tripsArr.length()) {
                        val o = tripsArr.getJSONObject(i)
                        val d = o.getString("date")
                        val dist = o.optDouble("distanceKm", 0.0)
                        val min = o.optDouble("durationMinutes", 0.0)
                        val src = o.optString("source", "Đơn Xanh SM")

                        val alreadyExists = existingTrips.any {
                            it.date == d && abs(it.distanceKm - dist) < 0.01 && abs(it.durationMinutes - min) < 0.1 && it.source == src
                        }
                        if (!alreadyExists) {
                            tripsToInsert.add(TripEntity(date = d, distanceKm = dist, durationMinutes = min, source = src))
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    if (shiftsToInsert.isNotEmpty()) dao.insertShifts(shiftsToInsert)
                    if (tripsToInsert.isNotEmpty()) dao.insertTrips(tripsToInsert)
                }

                Toast.makeText(appContext, "✅ Đã khôi phục ${shiftsToInsert.size} ngày & ${tripsToInsert.size} cuốc xe từ file mặc định!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(appContext, "❌ Lỗi đọc file sao lưu!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildJsonString(trips: List<TripEntity>, shifts: List<ShiftEntity>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportDate", todayDate)

        val shiftsArr = JSONArray()
        for (s in shifts) {
            val obj = JSONObject()
            obj.put("date", s.date)
            obj.put("startOdo", s.startOdo)
            obj.put("endOdo", s.endOdo)
            obj.put("revenue", s.revenue)
            shiftsArr.put(obj)
        }
        root.put("shifts", shiftsArr)

        val tripsArr = JSONArray()
        for (t in trips) {
            val obj = JSONObject()
            obj.put("date", t.date)
            obj.put("distanceKm", t.distanceKm)
            obj.put("durationMinutes", t.durationMinutes)
            obj.put("source", t.source)
            tripsArr.put(obj)
        }
        root.put("trips", tripsArr)

        return root.toString(2)
    }

    fun processImages(uris: List<Uri>, forDate: String) {
        viewModelScope.launch {
            isScanning = true
            val parsedList = uris.mapNotNull { ocrProcessor.processImageUri(it) }
                .filter { it.distanceKm > 0 || it.durationMinutes > 0 }

            val currentExisting = dao.getTripsByDate(forDate).first()
            val uniqueList = mutableListOf<TripEntity>()
            val duplicateList = mutableListOf<TripEntity>()

            for (p in parsedList) {
                val isDupWithExisting = currentExisting.any {
                    abs(it.distanceKm - p.distanceKm) < 0.05 && abs(it.durationMinutes - p.durationMinutes) < 0.2
                }
                val isDupWithUnique = uniqueList.any {
                    abs(it.distanceKm - p.distanceKm) < 0.05 && abs(it.durationMinutes - p.durationMinutes) < 0.2
                }

                val entity = TripEntity(date = forDate, distanceKm = p.distanceKm, durationMinutes = p.durationMinutes, source = p.source)

                if (isDupWithExisting || isDupWithUnique) {
                    duplicateList.add(entity)
                } else {
                    uniqueList.add(entity)
                }
            }

            if (duplicateList.isNotEmpty()) {
                duplicatePrompt = DuplicateCheckResult(uniqueList, duplicateList)
            } else if (uniqueList.isNotEmpty()) {
                dao.insertTrips(uniqueList)
                autoSaveSilently()
            }
            isScanning = false
        }
    }

    fun confirmSaveOnlyUnique() {
        viewModelScope.launch {
            duplicatePrompt?.uniqueTrips?.let {
                if (it.isNotEmpty()) {
                    dao.insertTrips(it)
                    autoSaveSilently()
                }
            }
            duplicatePrompt = null
        }
    }

    fun confirmSaveAll() {
        viewModelScope.launch {
            duplicatePrompt?.let { prompt ->
                val all = prompt.uniqueTrips + prompt.duplicateTrips
                if (all.isNotEmpty()) {
                    dao.insertTrips(all)
                    autoSaveSilently()
                }
            }
            duplicatePrompt = null
        }
    }

    fun dismissDuplicatePrompt() {
        duplicatePrompt = null
    }

    fun saveOrUpdateTrip(trip: TripEntity) {
        viewModelScope.launch {
            dao.insertOrUpdateTrip(trip)
            autoSaveSilently()
        }
    }

    fun updateShift(date: String, start: Double?, end: Double?, revenue: Double?) {
        viewModelScope.launch {
            val cur = dao.getShiftByDate(date).firstOrNull() ?: ShiftEntity(date = date)
            dao.saveShift(
                cur.copy(
                    startOdo = start ?: cur.startOdo,
                    endOdo = end ?: cur.endOdo,
                    revenue = revenue ?: cur.revenue
                )
            )
            autoSaveSilently()
        }
    }

    fun deleteTrip(id: Long) = viewModelScope.launch {
        dao.deleteTrip(id)
        autoSaveSilently()
    }
}

// ==========================================
// 4. MAIN ACTIVITY & CẤU TRÚC GIAO DIỆN MỚI
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val theme = vm.currentTheme

            CompositionLocalProvider(LocalAppTheme provides theme) {
                MaterialTheme(colorScheme = if (theme.isLight) lightColorScheme() else darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = theme.bg) {
                        var isLoading by remember { mutableStateOf(true) }

                        LaunchedEffect(Unit) {
                            delay(1400)
                            isLoading = false
                        }

                        if (isLoading) {
                            AppLoadingScreen()
                        } else {
                            MainDrawerScaffold(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppLoadingScreen() {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(theme.headerTop, theme.headerMid, theme.bg))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(theme.cardBg)
                    .border(2.dp, theme.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 44.sp)
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GreenTrip Tracker", color = if (theme.isLight) Color.White else theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 23.sp)
                Surface(shape = RoundedCornerShape(6.dp), color = theme.accent.copy(alpha = 0.25f)) {
                    Text(theme.badgeText, color = if (theme.isLight) Color.White else theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("Hệ thống quản lý chuyến đi & ODO", color = if (theme.isLight) Color(0xFFE0F2F1) else theme.textSecondary, fontSize = 13.sp)

            Spacer(Modifier.height(34.dp))
            CircularProgressIndicator(color = theme.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text("Đang đồng bộ dữ liệu ngày chạy...", color = if (theme.isLight) Color(0xFFB2DFDB) else theme.textSecondary, fontSize = 12.sp)
        }
    }
}

// KHUNG GIAO DIỆN CHÍNH: MENU TRƯỢT TRÁI (DRAWER) + BOTTOM BAR 4 TAB
@Composable
fun MainDrawerScaffold(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Hôm nay, 1: Ngày, 2: Tuần, 3: Tháng

    var showTripDialog by remember { mutableStateOf(false) }

    // HỘP THOẠI ĐƠN TRÙNG
    vm.duplicatePrompt?.let { promptData ->
        AlertDialog(
            onDismissRequest = { vm.dismissDuplicatePrompt() },
            containerColor = theme.cardBg,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 20.sp)
                    Text("Phát hiện đơn trùng lặp", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Có ${promptData.duplicateTrips.size} đơn trùng số km & thời gian với dữ liệu đã có:", color = theme.textSecondary, fontSize = 13.sp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.inputBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        promptData.duplicateTrips.take(4).forEach { d ->
                            Text("• ${d.distanceKm} km - ${d.durationMinutes} ph (${d.source})", color = theme.highlight2, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.confirmSaveOnlyUnique() },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Bỏ qua trùng (${promptData.uniqueTrips.size} mới)", color = theme.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmSaveAll() }) {
                    Text("Lưu tất cả", color = theme.textSecondary)
                }
            }
        )
    }

    if (showTripDialog) {
        TripEditDialog(
            trip = null,
            currentDate = vm.todayDate,
            onDismiss = { showTripDialog = false },
            onSave = { savedTrip ->
                vm.saveOrUpdateTrip(savedTrip)
                showTripDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = theme.cardBg,
                drawerContentColor = theme.textPrimary,
                modifier = Modifier.width(310.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(theme.accent)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚡", fontSize = 22.sp)
                            }
                            Column {
                                Text("GreenTrip Tracker", fontWeight = FontWeight.Black, color = theme.textPrimary, fontSize = 17.sp)
                                Text("Quản lý hiệu quả ngày chạy", color = theme.textSecondary, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = theme.cardBorder)
                    }

                    item {
                        Text("DỮ LIỆU & SAO LƯU TỰ ĐỘNG", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = theme.inputBg,
                            border = BorderStroke(1.dp, theme.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.manualBackupDefault()
                                    scope.launch { drawerState.close() }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("💾", fontSize = 18.sp)
                                Column {
                                    Text("Sao lưu vào file mặc định", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Lưu an toàn vào Documents/GreenTripTracker", color = theme.textSecondary, fontSize = 10.5.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = theme.inputBg,
                            border = BorderStroke(1.dp, theme.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.manualRestoreDefault()
                                    scope.launch { drawerState.close() }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("📂", fontSize = 18.sp)
                                Column {
                                    Text("Khôi phục từ file mặc định", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Đọc lại toàn bộ cuốc xe & doanh thu", color = theme.textSecondary, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("CHỌN GIAO DIỆN CHỦ ĐẠO (6 THEMES)", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeList.forEach { item ->
                                val isSelected = item.id == theme.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) item.accent.copy(alpha = 0.2f) else theme.inputBg,
                                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) item.accent else theme.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.selectTheme(item.id)
                                            scope.launch { drawerState.close() }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(item.accent))
                                            Text(item.name, color = theme.textPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.5.sp)
                                        }
                                        if (isSelected) {
                                            Text("✓", color = item.accent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("Phiên bản 1.3 • Chạy Offline 100%", color = theme.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(theme.headerTop, theme.headerMid)))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.clickable { scope.launch { drawerState.open() } }
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text("☰", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("GreenTrip", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.35f)) {
                                        Text(theme.badgeText, color = if (theme.isLight) Color(0xFFFFE082) else theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                                Text("Hôm nay: ${vm.todayDate}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { showTripDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("+ Thêm tay", color = theme.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            },
            bottomBar = {
                // THANH ĐIỀU HƯỚNG 4 TAB DƯỚI ĐÁY MÀN HÌNH
                Surface(
                    color = theme.cardBg,
                    border = BorderStroke(1.dp, theme.cardBorder),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomBarItem("⚡ Hôm nay", selectedTab == 0, theme) { selectedTab = 0; vm.goToToday() }
                        BottomBarItem("📅 Ngày", selectedTab == 1, theme) { selectedTab = 1 }
                        BottomBarItem("📆 Tuần", selectedTab == 2, theme) { selectedTab = 2 }
                        BottomBarItem("📊 Tháng", selectedTab == 3, theme) { selectedTab = 3 }
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(theme.bg)) {
                when (selectedTab) {
                    0 -> TodayScreen(vm)
                    1 -> DayScreen(vm)
                    2 -> WeekScreen(vm)
                    3 -> MonthScreen(vm)
                }
            }
        }
    }
}

@Composable
fun BottomBarItem(label: String, isSelected: Boolean, theme: AppThemePalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) theme.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) theme.accent else theme.textSecondary,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

// ==========================================
// TAB 0: HÔM NAY (ĐÃ ĐỔI THÀNH NGÀY CHẠY)
// ==========================================
@Composable
fun TodayScreen(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val trips by vm.currentTrips.collectAsState()
    val shift by vm.currentShift.collectAsState()
    var startInput by remember { mutableStateOf("") }
    var endInput by remember { mutableStateOf("") }

    var showTripDialog by remember { mutableStateOf(false) }
    var editingTrip by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(shift) {
        shift?.let {
            if (it.startOdo > 0) startInput = it.startOdo.toString()
            if (it.endOdo > 0) endInput = it.endOdo.toString()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.processImages(uris, vm.todayDate)
    }

    val totalOrderDist = trips.sumOf { it.distanceKm }
    val totalOrderMin = trips.sumOf { it.durationMinutes }
    val sOdo = shift?.startOdo ?: 0.0
    val eOdo = shift?.endOdo ?: 0.0
    val actualDist = if (eOdo >= sOdo && sOdo > 0) eOdo - sOdo else 0.0
    val emptyDist = if (actualDist > totalOrderDist) actualDist - totalOrderDist else 0.0
    val pickupRate = if (actualDist > 0) (totalOrderDist / actualDist * 100).toInt() else 0

    if (showTripDialog) {
        TripEditDialog(
            trip = editingTrip,
            currentDate = vm.todayDate,
            onDismiss = { showTripDialog = false; editingTrip = null },
            onSave = { savedTrip ->
                vm.saveOrUpdateTrip(savedTrip)
                showTripDialog = false
                editingTrip = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ODO ĐẦU NGÀY / CUỐI NGÀY
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = theme.accent.copy(alpha = 0.2f)) {
                                Text("ODO", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Text("Đồng hồ ODO xe", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text("Lăn bánh: ${String.format("%.1f", actualDist)} km", color = theme.highlight1, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedOdoBox("ODO Đầu ngày (km)", startInput, { startInput = it; vm.updateShift(vm.todayDate, it.toDoubleOrNull(), null, null) }, Modifier.weight(1f))
                        ThemedOdoBox("ODO Cuối ngày (km)", endInput, { endInput = it; vm.updateShift(vm.todayDate, null, it.toDoubleOrNull(), null) }, Modifier.weight(1f))
                    }
                }
            }
        }

        // BẢNG HIỆU QUẢ NGÀY CHẠY
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(theme.accent))
                            Text("BẢNG HIỆU QUẢ NGÀY CHẠY", color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = theme.inputBg) {
                            Text("${trips.size} cuốc", color = theme.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedStatBox("Thời gian khách", String.format("%.1f", totalOrderMin), "phút", theme.textPrimary, Modifier.weight(1f))
                        ThemedStatBox("Km có đơn chở", String.format("%.1f", totalOrderDist), "km", theme.highlight2, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedStatBox("Km ODO thực tế", String.format("%.1f", actualDist), "km", theme.textPrimary, Modifier.weight(1f))
                        ThemedStatBox("Tỷ lệ đón khách", "$pickupRate", "%", theme.highlight1, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(12.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.warningBg),
                        modifier = Modifier.fillMaxWidth().border(1.dp, theme.warningBorder, RoundedCornerShape(12.dp))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🛵", fontSize = 22.sp)
                                Column {
                                    Text("Km chạy rỗng", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Không chở khách", color = theme.textSecondary, fontSize = 11.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${String.format("%.1f", emptyDist)} km", fontSize = 19.sp, fontWeight = FontWeight.Black, color = if (emptyDist > 15) theme.danger else theme.highlight1)
                                if (emptyDist > 15) {
                                    Text("Chạy rỗng nhiều!", color = theme.danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                enabled = !vm.isScanning
            ) {
                if (vm.isScanning) {
                    CircularProgressIndicator(color = theme.onAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Đang quét...", color = theme.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("📸 QUÉT ẢNH ĐƠN / MAPS", color = theme.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Danh sách đơn hôm nay (${trips.size})", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text("Chạm để sửa", color = theme.textSecondary, fontSize = 11.5.sp)
            }
        }

        items(trips) { t ->
            ThemedTripRow(t, onClick = { editingTrip = t; showTripDialog = true }, onDelete = { vm.deleteTrip(t.id) })
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==========================================
// TAB 1: THEO NGÀY (CHUYỂN NGÀY BẰNG 1 CHẠM)
// ==========================================
@Composable
fun DayScreen(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val selectedDate by vm.selectedDate.collectAsState()
    val trips by vm.currentTrips.collectAsState()
    val shift by vm.currentShift.collectAsState()

    var revenueInput by remember { mutableStateOf("") }
    var startInput by remember { mutableStateOf("") }
    var endInput by remember { mutableStateOf("") }

    var showTripDialog by remember { mutableStateOf(false) }
    var editingTrip by remember { mutableStateOf<TripEntity?>(null) }

    val formatter = DecimalFormat("#,###")

    LaunchedEffect(shift, selectedDate) {
        revenueInput = if ((shift?.revenue ?: 0.0) > 0) shift!!.revenue.toInt().toString() else ""
        startInput = if ((shift?.startOdo ?: 0.0) > 0) shift!!.startOdo.toString() else ""
        endInput = if ((shift?.endOdo ?: 0.0) > 0) shift!!.endOdo.toString() else ""
    }

    val totalOrderDist = trips.sumOf { it.distanceKm }
    val sOdo = shift?.startOdo ?: 0.0
    val eOdo = shift?.endOdo ?: 0.0
    val actualDist = if (eOdo >= sOdo && sOdo > 0) eOdo - sOdo else 0.0
    val emptyDist = if (actualDist > totalOrderDist) actualDist - totalOrderDist else 0.0
    val rev = shift?.revenue ?: 0.0

    val pricePerOrderKm = if (totalOrderDist > 0) rev / totalOrderDist else 0.0
    val pricePerActualKm = if (actualDist > 0) rev / actualDist else 0.0

    if (showTripDialog) {
        TripEditDialog(
            trip = editingTrip,
            currentDate = selectedDate,
            onDismiss = { showTripDialog = false; editingTrip = null },
            onSave = { savedTrip ->
                vm.saveOrUpdateTrip(savedTrip)
                showTripDialog = false
                editingTrip = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // THANH ĐIỀU HƯỚNG NGÀY 1 CHẠM
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.previousDay() }) {
                        Text("◀", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.getSelectedDayTitle(), color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        if (vm.isTodaySelected()) {
                            Text("(Hôm nay)", color = theme.highlight1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("⟲ Về hôm nay", color = theme.accent, fontSize = 11.sp, modifier = Modifier.clickable { vm.goToToday() })
                        }
                    }

                    IconButton(onClick = { vm.nextDay() }) {
                        Text("▶", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DOANH SỐ RÒNG & ODO NGÀY NÀY
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Doanh Số Ròng Thực Nhận (VNĐ)", color = theme.highlight1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = revenueInput,
                        onValueChange = {
                            revenueInput = it
                            vm.updateShift(selectedDate, null, null, it.toDoubleOrNull())
                        },
                        placeholder = { Text("VD: 650000", color = theme.textSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Black, color = theme.textPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.cardBorder
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedOdoBox("ODO Đầu ngày", startInput, { startInput = it; vm.updateShift(selectedDate, it.toDoubleOrNull(), null, null) }, Modifier.weight(1f))
                        ThemedOdoBox("ODO Cuối ngày", endInput, { endInput = it; vm.updateShift(selectedDate, null, it.toDoubleOrNull(), null) }, Modifier.weight(1f))
                    }
                }
            }
        }

        // ĐƠN GIÁ / KM
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("HIỆU QUẢ DOANH THU TRÊN KM", fontWeight = FontWeight.Black, color = theme.accent, fontSize = 12.5.sp)
                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedStatBox("Đơn giá / Km khách", "${formatter.format(pricePerOrderKm.toLong())} đ", "", theme.highlight1, Modifier.weight(1f))
                        ThemedStatBox("Đơn giá / Km ODO", "${formatter.format(pricePerActualKm.toLong())} đ", "", theme.highlight2, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = theme.cardBorder)
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Số cuốc: ${trips.size}", color = theme.textPrimary, fontSize = 12.5.sp)
                        Text("Km khách: ${String.format("%.1f", totalOrderDist)} km", color = theme.highlight2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Km ODO: ${String.format("%.1f", actualDist)} km", color = theme.textPrimary, fontSize = 12.5.sp)
                        Text("Km rỗng: ${String.format("%.1f", emptyDist)} km", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cuốc ngày $selectedDate (${trips.size})", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                TextButton(onClick = { editingTrip = null; showTripDialog = true }) {
                    Text("+ Thêm cuốc", color = theme.accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(trips) { t ->
            ThemedTripRow(t, onClick = { editingTrip = t; showTripDialog = true }, onDelete = { vm.deleteTrip(t.id) })
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==========================================
// TAB 2: THEO TUẦN (TUẦN TRƯỚC / TUẦN KẾ TIẾP)
// ==========================================
@Composable
fun WeekScreen(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val weekTrips by vm.selectedWeekTrips.collectAsState()
    val weekShifts by vm.selectedWeekShifts.collectAsState()

    val totalDist = weekTrips.sumOf { it.distanceKm }
    val totalRev = weekShifts.sumOf { it.revenue }
    val totalActualDist = weekShifts.sumOf { if (it.endOdo >= it.startOdo && it.startOdo > 0) it.endOdo - it.startOdo else 0.0 }
    val totalEmptyDist = if (totalActualDist > totalDist) totalActualDist - totalDist else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ĐIỀU HƯỚNG TUẦN
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.previousWeek() }) {
                        Text("◀", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.getSelectedWeekTitle(), color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                        if (vm.isCurrentWeekSelected()) {
                            Text("(Tuần hiện tại)", color = theme.highlight1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("⟲ Về tuần này", color = theme.accent, fontSize = 11.sp, modifier = Modifier.clickable { vm.goToCurrentWeek() })
                        }
                    }

                    IconButton(onClick = { vm.nextWeek() }) {
                        Text("▶", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            PeriodSummaryCard(
                title = "TỔNG KẾT TUẦN",
                tripCount = weekTrips.size,
                revenue = totalRev,
                orderDist = totalDist,
                actualDist = totalActualDist,
                emptyDist = totalEmptyDist,
                headerColor = theme.highlight2
            )
        }

        item {
            Text("Danh sách chuyến trong tuần (${weekTrips.size})", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }

        items(weekTrips) { t ->
            ThemedTripRow(t, onClick = {}, onDelete = { vm.deleteTrip(t.id) })
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==========================================
// TAB 3: THEO THÁNG (THÁNG TRƯỚC / THÁNG TIẾP)
// ==========================================
@Composable
fun MonthScreen(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val monthTrips by vm.selectedMonthTrips.collectAsState()
    val monthShifts by vm.selectedMonthShifts.collectAsState()

    val totalDist = monthTrips.sumOf { it.distanceKm }
    val totalRev = monthShifts.sumOf { it.revenue }
    val totalActualDist = monthShifts.sumOf { if (it.endOdo >= it.startOdo && it.startOdo > 0) it.endOdo - it.startOdo else 0.0 }
    val totalEmptyDist = if (totalActualDist > totalDist) totalActualDist - totalDist else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ĐIỀU HƯỚNG THÁNG
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.previousMonth() }) {
                        Text("◀", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.getSelectedMonthTitle(), color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 15.5.sp)
                        if (vm.isCurrentMonthSelected()) {
                            Text("(Tháng hiện tại)", color = theme.highlight1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("⟲ Về tháng này", color = theme.accent, fontSize = 11.sp, modifier = Modifier.clickable { vm.goToCurrentMonth() })
                        }
                    }

                    IconButton(onClick = { vm.nextMonth() }) {
                        Text("▶", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            PeriodSummaryCard(
                title = "TỔNG KẾT THÁNG",
                tripCount = monthTrips.size,
                revenue = totalRev,
                orderDist = totalDist,
                actualDist = totalActualDist,
                emptyDist = totalEmptyDist,
                headerColor = theme.accent
            )
        }

        item {
            Text("Danh sách chuyến trong tháng (${monthTrips.size})", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }

        items(monthTrips) { t ->
            ThemedTripRow(t, onClick = {}, onDelete = { vm.deleteTrip(t.id) })
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==========================================
// THÀNH PHẦN GIAO DIỆN PHỤ TRỢ
// ==========================================
@Composable
fun PeriodSummaryCard(
    title: String,
    tripCount: Int,
    revenue: Double,
    orderDist: Double,
    actualDist: Double,
    emptyDist: Double,
    headerColor: Color
) {
    val theme = LocalAppTheme.current
    val formatter = DecimalFormat("#,###")
    val pricePerOrderKm = if (orderDist > 0) revenue / orderDist else 0.0
    val pricePerActualKm = if (actualDist > 0) revenue / actualDist else 0.0
    val pickupRate = if (actualDist > 0) (orderDist / actualDist * 100).toInt() else 0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        modifier = Modifier.fillMaxWidth().border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = headerColor, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
                Surface(shape = RoundedCornerShape(10.dp), color = theme.inputBg) {
                    Text("$tripCount cuốc", color = theme.textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = theme.inputBg),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tổng Doanh Thu:", color = theme.textSecondary, fontSize = 12.5.sp)
                    Text("${formatter.format(revenue.toLong())} đ", color = theme.highlight1, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemedStatBox("Đơn giá / Km khách", "${formatter.format(pricePerOrderKm.toLong())} đ", "", theme.highlight1, Modifier.weight(1f))
                ThemedStatBox("Đơn giá / Km ODO", "${formatter.format(pricePerActualKm.toLong())} đ", "", theme.highlight2, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemedStatBox("Km khách tích lũy", String.format("%.1f", orderDist), "km", theme.highlight2, Modifier.weight(1f))
                ThemedStatBox("Tổng ODO lăn bánh", String.format("%.1f", actualDist), "km", theme.textPrimary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = theme.cardBorder)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tỷ lệ đón khách: $pickupRate%", color = theme.highlight1, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text("Km rỗng: ${String.format("%.1f", emptyDist)} km", color = if (emptyDist > 50) theme.danger else theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
fun TripEditDialog(
    trip: TripEntity?,
    currentDate: String,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit
) {
    val theme = LocalAppTheme.current
    var distInput by remember { mutableStateOf(trip?.distanceKm?.toString() ?: "") }
    var minInput by remember { mutableStateOf(trip?.durationMinutes?.toString() ?: "") }
    var srcInput by remember { mutableStateOf(trip?.source ?: "Đơn Xanh SM") }

    val sources = listOf("Đơn Xanh SM", "Google Maps", "Đơn Be", "Đơn Grab")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardBg,
        title = {
            Text(if (trip == null) "Thêm Cuốc Xe Mới" else "Sửa Thông Tin Cuốc", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = distInput,
                    onValueChange = { distInput = it },
                    label = { Text("Quãng đường (km)") },
                    placeholder = { Text("VD: 3.5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = theme.textPrimary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it },
                    label = { Text("Thời gian (phút)") },
                    placeholder = { Text("VD: 12.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = theme.textPrimary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Nguồn đơn:", color = theme.textSecondary, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    sources.take(2).forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (srcInput == s) theme.accent else theme.inputBg,
                            border = BorderStroke(1.dp, if (srcInput == s) theme.accent else theme.cardBorder),
                            modifier = Modifier.weight(1f).clickable { srcInput = s }
                        ) {
                            Text(s, color = if (srcInput == s) theme.onAccent else theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    sources.drop(2).forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (srcInput == s) theme.accent else theme.inputBg,
                            border = BorderStroke(1.dp, if (srcInput == s) theme.accent else theme.cardBorder),
                            modifier = Modifier.weight(1f).clickable { srcInput = s }
                        ) {
                            Text(s, color = if (srcInput == s) theme.onAccent else theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = distInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val m = minInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val updated = trip?.copy(distanceKm = d, durationMinutes = m, source = srcInput)
                        ?: TripEntity(date = currentDate, distanceKm = d, durationMinutes = m, source = srcInput)
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Lưu lại", color = theme.onAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun ThemedOdoBox(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier) {
        Text(label, color = theme.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("0.0", color = theme.textSecondary.copy(alpha = 0.5f), fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = theme.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Black),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = theme.inputBg,
                unfocusedContainerColor = theme.inputBg,
                focusedBorderColor = theme.accent,
                unfocusedBorderColor = theme.cardBorder
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ThemedStatBox(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.inputBg),
        modifier = modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(label, color = theme.textSecondary, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1, softWrap = false)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, color = theme.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 1.dp))
                }
            }
        }
    }
}

@Composable
fun ThemedTripRow(trip: TripEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val theme = LocalAppTheme.current
    val isMaps = trip.source == "Google Maps"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${trip.distanceKm} km", color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("•", color = theme.textSecondary)
                    Text("${trip.durationMinutes} phút", color = theme.textSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMaps) theme.highlight2.copy(alpha = 0.15f) else theme.accent.copy(alpha = 0.15f)
                    ) {
                        Text(trip.source, color = if (isMaps) theme.highlight2 else theme.accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text(trip.date, color = theme.textSecondary, fontSize = 11.5.sp)
                }
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = theme.danger)
            ) {
                Text("Xóa", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
