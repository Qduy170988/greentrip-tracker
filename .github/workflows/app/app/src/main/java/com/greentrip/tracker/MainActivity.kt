@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.greentrip.tracker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
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
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ==========================================
// HỆ THỐNG 6 GIAO DIỆN TUỲ BIẾN (THEMES)
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
    // 0: Mặc định (Xanh SM Dark)
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
    // 1: Màu Vàng (Tài xế Be)
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
    // 2: Màu Xanh Lá (Tài xế Grab)
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
    // 3: Màu Xanh Cyan (Green SM)
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
    // 4: Giao diện Sáng (Light Mode)
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
    // 5: Giao diện Tối Sâu (Pure Dark AMOLED)
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

// 1. DATA ENTITIES & ROOM DATABASE
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

    @Query("SELECT * FROM trips WHERE date BETWEEN :startDate AND :endDate")
    fun getTripsBetween(startDate: String, endDate: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM shifts WHERE date BETWEEN :startDate AND :endDate")
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

// 2. OCR BÓC TÁCH (XỬ LÝ MÉT & CHẶN BIỂN BÁO AH17)
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

// 3. VIEWMODEL QUẢN LÝ DỮ LIỆU, GIAO DIỆN & BACKUP/RESTORE
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
    val selectedDate = MutableStateFlow(todayDate)

    val currentTrips = selectedDate.flatMapLatest { dao.getTripsByDate(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentShift = selectedDate.flatMapLatest { dao.getShiftByDate(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var isScanning by mutableStateOf(false)
    var duplicatePrompt by mutableStateOf<DuplicateCheckResult?>(null)

    private val weekRange = getWeekRange(todayDate)
    private val monthRange = getMonthRange(todayDate)

    val weekTrips = dao.getTripsBetween(weekRange.first, weekRange.second)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val weekShifts = dao.getShiftsBetween(weekRange.first, weekRange.second)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val monthTrips = dao.getTripsBetween(monthRange.first, monthRange.second)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val monthShifts = dao.getShiftsBetween(monthRange.first, monthRange.second)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    fun setDate(date: String) { selectedDate.value = date }

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

                val entity = TripEntity(
                    date = forDate,
                    distanceKm = p.distanceKm,
                    durationMinutes = p.durationMinutes,
                    source = p.source
                )

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
            }
            isScanning = false
        }
    }

    fun confirmSaveOnlyUnique() {
        viewModelScope.launch {
            duplicatePrompt?.uniqueTrips?.let {
                if (it.isNotEmpty()) dao.insertTrips(it)
            }
            duplicatePrompt = null
        }
    }

    fun confirmSaveAll() {
        viewModelScope.launch {
            duplicatePrompt?.let { prompt ->
                val all = prompt.uniqueTrips + prompt.duplicateTrips
                if (all.isNotEmpty()) dao.insertTrips(all)
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
        }
    }

    fun deleteTrip(id: Long) = viewModelScope.launch { dao.deleteTrip(id) }

    // CHỨC NĂNG 1: SAVE DỮ LIỆU RA FILE JSON TRONG DOCUMENTS
    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val trips = withContext(Dispatchers.IO) { dao.getAllTrips() }
                val shifts = withContext(Dispatchers.IO) { dao.getAllShifts() }

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

                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(appContext, "✅ Đã Save ${trips.size} cuốc & ${shifts.size} ca chạy thành công!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(appContext, "❌ Lỗi khi lưu dữ liệu: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // CHỨC NĂNG 2: LOAD DỮ LIỆU TỪ FILE JSON KHÔI PHỤC VÀO APP
    fun importDataFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonStr = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                }
                if (jsonStr.isBlank()) {
                    Toast.makeText(appContext, "❌ File trống hoặc không hợp lệ!", Toast.LENGTH_SHORT).show()
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
                            tripsToInsert.add(
                                TripEntity(
                                    date = d,
                                    distanceKm = dist,
                                    durationMinutes = min,
                                    source = src
                                )
                            )
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    if (shiftsToInsert.isNotEmpty()) dao.insertShifts(shiftsToInsert)
                    if (tripsToInsert.isNotEmpty()) dao.insertTrips(tripsToInsert)
                }

                Toast.makeText(
                    appContext,
                    "✅ Đã khôi phục ${shiftsToInsert.size} ca chạy & ${tripsToInsert.size} cuốc xe mới!",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(appContext, "❌ File không đúng định dạng sao lưu!", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        fun getWeekRange(dateStr: String): Pair<String, String> {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(dateStr) ?: Date()
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val start = sdf.format(cal.time)
            cal.add(Calendar.DAY_OF_WEEK, 6)
            val end = sdf.format(cal.time)
            return Pair(start, end)
        }

        fun getMonthRange(dateStr: String): Pair<String, String> {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(dateStr) ?: Date()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val start = sdf.format(cal.time)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val end = sdf.format(cal.time)
            return Pair(start, end)
        }
    }
}

// 4. MAIN ACTIVITY VỚI MÀN HÌNH LOADING
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val theme = vm.currentTheme

            CompositionLocalProvider(LocalAppTheme provides theme) {
                MaterialTheme(
                    colorScheme = if (theme.isLight) lightColorScheme() else darkColorScheme()
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = theme.bg) {
                        var isLoading by remember { mutableStateOf(true) }

                        LaunchedEffect(Unit) {
                            delay(1400)
                            isLoading = false
                        }

                        if (isLoading) {
                            AppLoadingScreen()
                        } else {
                            MainThemedScaffold(vm)
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(theme.headerTop, theme.headerMid, theme.bg)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "GreenTrip Tracker",
                    color = if (theme.isLight) Color.White else theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 23.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = theme.accent.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = theme.badgeText,
                        color = if (theme.isLight) Color.White else theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Hệ thống quản lý chuyến đi & ODO",
                color = if (theme.isLight) Color(0xFFE0F2F1) else theme.textSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(34.dp))

            CircularProgressIndicator(
                color = theme.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Đang đồng bộ dữ liệu ca chạy...",
                color = if (theme.isLight) Color(0xFFB2DFDB) else theme.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

// KHUNG GIAO DIỆN CHÍNH TÍCH HỢP CHỌN THEME & SAVE/LOAD DỮ LIỆU
@Composable
fun MainThemedScaffold(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    var selectedTab by remember { mutableStateOf(0) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Bộ chọn vị trí lưu file JSON (Save dữ liệu)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            vm.exportDataToUri(uri)
        }
    }

    // Bộ mở file JSON từ bộ nhớ máy (Load dữ liệu)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.importDataFromUri(uri)
        }
    }

    // Hộp thoại chọn 6 Giao diện
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = theme.cardBg,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    "🎨 Chọn Giao Diện Ứng Dụng",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeList.forEach { item ->
                        val isSelected = item.id == theme.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) item.accent.copy(alpha = 0.2f) else theme.inputBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) item.accent else theme.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.selectTheme(item.id)
                                    showThemeDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(item.accent)
                                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    )
                                    Text(
                                        text = item.name,
                                        color = theme.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Text("✓ Đang dùng", color = item.accent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Đóng", color = theme.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Hộp thoại cảnh báo đơn trùng lặp
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
                    Text(
                        "Có ${promptData.duplicateTrips.size} đơn trùng số km & thời gian với dữ liệu đã có:",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
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
                        if (promptData.duplicateTrips.size > 4) {
                            Text("và ${promptData.duplicateTrips.size - 4} đơn khác...", color = theme.textSecondary, fontSize = 11.sp)
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
                    Text(
                        text = if (promptData.uniqueTrips.isNotEmpty()) "Bỏ qua trùng (${promptData.uniqueTrips.size} mới)" else "Bỏ qua tất cả",
                        color = theme.onAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmSaveAll() }) {
                    Text("Lưu tất cả", color = theme.textSecondary)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        // PHẦN HEADER + THANH CÔNG CỤ SAO LƯU & ĐỔI GIAO DIỆN
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(theme.headerTop, theme.headerMid, theme.bg)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                // Hàng 1: Logo + Tên App + Nút chọn Giao diện
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.25f))
                                .border(1.dp, theme.accent.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 18.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "GreenTrip",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.35f)
                                ) {
                                    Text(
                                        text = theme.badgeText,
                                        color = if (theme.isLight) Color(0xFFFFE082) else theme.accent,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Ngày: ${vm.todayDate}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Nút Đổi Giao Diện
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.clickable { showThemeDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🎨", fontSize = 13.sp)
                            Text(
                                text = "Giao diện",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Hàng 2: 2 Nút SAVE DỮ LIỆU & LOAD DỮ LIỆU (Chống mất dữ liệu khi xoá/update app)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.cardBg.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, theme.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                exportLauncher.launch("GreenTrip_Backup_${vm.todayDate}.json")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💾", fontSize = 13.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Save dữ liệu",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.cardBg.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, theme.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📂", fontSize = 13.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Load dữ liệu",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Hàng 3: Thanh chuyển 3 Tab chia đều chống tràn chữ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (theme.isLight) Color.Black.copy(alpha = 0.15f) else Color(0xFF161A1D))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabPillItem("Hôm nay", selectedTab == 0, Modifier.weight(1f)) {
                        selectedTab = 0
                        vm.setDate(vm.todayDate)
                    }
                    TabPillItem("Theo ngày", selectedTab == 1, Modifier.weight(1f)) {
                        selectedTab = 1
                    }
                    TabPillItem("Tuần / Tháng", selectedTab == 2, Modifier.weight(1f)) {
                        selectedTab = 2
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> TodayScreen(vm)
                1 -> HistoryScreen(vm)
                2 -> PeriodReportScreen(vm)
            }
        }
    }
}

@Composable
fun TabPillItem(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) theme.accent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) theme.onAccent else Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// TAB 1: CA CHẠY HÔM NAY
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
        item { Spacer(Modifier.height(2.dp)) }

        // CARD ODO XE
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = theme.accent.copy(alpha = 0.2f)) {
                                Text(
                                    text = "ODO",
                                    color = theme.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Đồng hồ ODO xe",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Lăn bánh: ${String.format("%.1f", actualDist)} km",
                            color = theme.highlight1,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedOdoBox(
                            label = "ODO Đầu ca (km)",
                            value = startInput,
                            onValueChange = { startInput = it; vm.updateShift(vm.todayDate, it.toDoubleOrNull(), null, null) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemedOdoBox(
                            label = "ODO Cuối ca (km)",
                            value = endInput,
                            onValueChange = { endInput = it; vm.updateShift(vm.todayDate, null, it.toDoubleOrNull(), null) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // BẢNG HIỆU QUẢ CA CHẠY
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(theme.accent))
                            Text(
                                text = "BẢNG HIỆU QUẢ CA CHẠY",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = theme.inputBg) {
                            Text(
                                text = "${trips.size} cuốc",
                                color = theme.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
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

                    // THẺ KM CHẠY RỖNG
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.warningBg),
                        modifier = Modifier.fillMaxWidth().border(1.dp, theme.warningBorder, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🛵", fontSize = 22.sp)
                                Column {
                                    Text(
                                        text = "Km chạy rỗng",
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "ODO - Km có khách",
                                        color = theme.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format("%.1f", emptyDist)} km",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (emptyDist > 15) theme.danger else theme.highlight1,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (emptyDist > 15) {
                                    Text(
                                        text = "Chạy rỗng nhiều!",
                                        color = theme.danger,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2 NÚT QUÉT ẢNH & THÊM TAY
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1.4f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    enabled = !vm.isScanning
                ) {
                    if (vm.isScanning) {
                        CircularProgressIndicator(color = theme.onAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Đang quét...", color = theme.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    } else {
                        Text("📸 QUÉT ẢNH ĐƠN", color = theme.onAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                    }
                }

                Button(
                    onClick = { editingTrip = null; showTripDialog = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.cardBg),
                    border = BorderStroke(1.dp, theme.cardBorder)
                ) {
                    Text("+ Thêm tay", color = theme.highlight2, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Danh sách đơn (chạm để sửa)",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1
                )
                Text("${trips.size} đơn", color = theme.textSecondary, fontSize = 12.sp)
            }
        }

        items(trips) { t ->
            ThemedTripRow(
                trip = t,
                onClick = { editingTrip = t; showTripDialog = true },
                onDelete = { vm.deleteTrip(t.id) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==========================================
// TAB 2: THEO NGÀY & DOANH THU
// ==========================================
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val selectedDate by vm.selectedDate.collectAsState()
    val trips by vm.currentTrips.collectAsState()
    val shift by vm.currentShift.collectAsState()

    var dateInput by remember { mutableStateOf(selectedDate) }
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
        item { Spacer(Modifier.height(2.dp)) }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBg),
                modifier = Modifier.border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Chọn ngày xem dữ liệu (yyyy-MM-dd):", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { dateInput = it; vm.setDate(it) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.inputBg,
                                unfocusedContainerColor = theme.inputBg,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.cardBorder
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { dateInput = vm.todayDate; vm.setDate(vm.todayDate) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Hôm nay", fontWeight = FontWeight.Bold, color = theme.onAccent, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

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
                        ThemedOdoBox("ODO Đầu (km)", startInput, { startInput = it; vm.updateShift(selectedDate, it.toDoubleOrNull(), null, null) }, Modifier.weight(1f))
                        ThemedOdoBox("ODO Cuối (km)", endInput, { endInput = it; vm.updateShift(selectedDate, null, it.toDoubleOrNull(), null) }, Modifier.weight(1f))
                    }
                }
            }
        }

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
            ThemedTripRow(
                trip = t,
                onClick = { editingTrip = t; showTripDialog = true },
                onDelete = { vm.deleteTrip(t.id) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==========================================
// TAB 3: TỔNG KẾT TUẦN & THÁNG
// ==========================================
@Composable
fun PeriodReportScreen(vm: MainViewModel) {
    val theme = LocalAppTheme.current
    val weekTrips by vm.weekTrips.collectAsState()
    val weekShifts by vm.weekShifts.collectAsState()

    val monthTrips by vm.monthTrips.collectAsState()
    val monthShifts by vm.monthShifts.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }

        item {
            val weekDistOrder = weekTrips.sumOf { it.distanceKm }
            val weekRev = weekShifts.sumOf { it.revenue }
            val weekActualDist = weekShifts.sumOf { if (it.endOdo >= it.startOdo && it.startOdo > 0) it.endOdo - it.startOdo else 0.0 }
            val weekEmptyDist = if (weekActualDist > weekDistOrder) weekActualDist - weekDistOrder else 0.0

            PeriodSummaryCard(
                title = "TỔNG KẾT TUẦN NÀY (Thứ 2 - CN)",
                tripCount = weekTrips.size,
                revenue = weekRev,
                orderDist = weekDistOrder,
                actualDist = weekActualDist,
                emptyDist = weekEmptyDist,
                headerColor = theme.highlight2
            )
        }

        item {
            val monthDistOrder = monthTrips.sumOf { it.distanceKm }
            val monthRev = monthShifts.sumOf { it.revenue }
            val monthActualDist = monthShifts.sumOf { if (it.endOdo >= it.startOdo && it.startOdo > 0) it.endOdo - it.startOdo else 0.0 }
            val monthEmptyDist = if (monthActualDist > monthDistOrder) monthActualDist - monthDistOrder else 0.0

            PeriodSummaryCard(
                title = "TỔNG KẾT THÁNG HIỆN TẠI",
                tripCount = monthTrips.size,
                revenue = monthRev,
                orderDist = monthDistOrder,
                actualDist = monthActualDist,
                emptyDist = monthEmptyDist,
                headerColor = theme.accent
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

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
                Text(
                    text = title,
                    color = headerColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(10.dp), color = theme.inputBg) {
                    Text(
                        text = "$tripCount cuốc",
                        color = theme.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = theme.inputBg),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tổng Doanh Thu:", color = theme.textSecondary, fontSize = 12.5.sp, maxLines = 1)
                    Text(
                        text = "${formatter.format(revenue.toLong())} đ",
                        color = theme.highlight1,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
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
                Text(
                    text = "Km rỗng: ${String.format("%.1f", emptyDist)} km",
                    color = if (emptyDist > 50) theme.danger else theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// HỘP THOẠI THÊM / SỬA CUỐC XE
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
            Text(
                text = if (trip == null) "Thêm Cuốc Xe Mới" else "Sửa Thông Tin Cuốc",
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
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

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        sources.take(2).forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (srcInput == s) theme.accent else theme.inputBg,
                                border = BorderStroke(1.dp, if (srcInput == s) theme.accent else theme.cardBorder),
                                modifier = Modifier.weight(1f).clickable { srcInput = s }
                            ) {
                                Text(
                                    text = s,
                                    color = if (srcInput == s) theme.onAccent else theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
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
                                Text(
                                    text = s,
                                    color = if (srcInput == s) theme.onAccent else theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
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

// ==========================================
// THÀNH PHẦN UI ĐÃ TỐI ƯU CHỐNG TRÀN CHỮ
// ==========================================
@Composable
fun ThemedOdoBox(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier) {
        Text(
            text = label,
            color = theme.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("0.0", color = theme.textSecondary.copy(alpha = 0.5f), fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Black
            ),
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
            Text(
                text = label,
                color = theme.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = valueColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = unit,
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${trip.distanceKm} km",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("•", color = theme.textSecondary)
                    Text(
                        text = "${trip.durationMinutes} phút",
                        color = theme.textSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMaps) theme.highlight2.copy(alpha = 0.15f) else theme.accent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = trip.source,
                            color = if (isMaps) theme.highlight2 else theme.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = trip.date,
                        color = theme.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = theme.danger)
            ) {
                Text("Xóa", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
