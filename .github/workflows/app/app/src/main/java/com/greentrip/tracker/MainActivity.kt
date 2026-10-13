@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.greentrip.tracker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// BẢNG MÀU DARK THEME CHUẨN XANH SM
val DarkBg = Color(0xFF101315)
val DarkCardBg = Color(0xFF1A1D20)
val DarkCardBorder = Color(0xFF262B2F)
val DarkInputBg = Color(0xFF131618)
val EmeraldAccent = Color(0xFF00B074)
val NeonGreen = Color(0xFF00E676)
val NeonCyan = Color(0xFF00E5FF)
val DangerRed = Color(0xFFFF5252)
val WarningRedBg = Color(0xFF2C1619)
val WarningRedBorder = Color(0xFF4A1F24)
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFF8E959B)

// 1. DATA ENTITIES & ROOM DATABASE
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val distanceKm: Double,
    val durationMinutes: Double,
    val source: String = "App"
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShift(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE date = :date")
    fun getShiftByDate(date: String): Flow<ShiftEntity?>

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

// DỮ LIỆU ĐỐI SOÁT ĐƠN TRÙNG
data class DuplicateCheckResult(
    val uniqueTrips: List<TripEntity>,
    val duplicateTrips: List<TripEntity>
)

// 3. VIEWMODEL
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).tripDao()
    private val ocrProcessor = OcrProcessor(application)

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
                    dao.saveShift(ShiftEntity(date = todayDate, startOdo = lastShift.endOdo, endOdo = cur?.endOdo ?: 0.0, revenue = cur?.revenue ?: 0.0))
                }
            }
        }
    }

    fun setDate(date: String) { selectedDate.value = date }

    // PHÁT HIỆN ĐƠN TRÙNG LẶP KHI QUÉT ẢNH
    fun processImages(uris: List<Uri>, forDate: String) {
        viewModelScope.launch {
            isScanning = true
            val parsedList = uris.mapNotNull { ocrProcessor.processImageUri(it) }
                .filter { it.distanceKm > 0 || it.durationMinutes > 0 }

            val currentExisting = dao.getTripsByDate(forDate).first()
            val uniqueList = mutableListOf<TripEntity>()
            val duplicateList = mutableListOf<TripEntity>()

            for (p in parsedList) {
                // Kiểm tra trùng với dữ liệu đã lưu trong máy
                val isDupWithExisting = currentExisting.any {
                    abs(it.distanceKm - p.distanceKm) < 0.05 && abs(it.durationMinutes - p.durationMinutes) < 0.2
                }
                // Kiểm tra trùng ngay trong chính đợt ảnh vừa chọn
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

// 4. MAIN ACTIVITY & UI
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    DarkThemeAppScaffold()
                }
            }
        }
    }
}

@Composable
fun DarkThemeAppScaffold(vm: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }

    // HỘP THOẠI CẢNH BÁO PHÁT HIỆN ĐƠN TRÙNG
    vm.duplicatePrompt?.let { promptData ->
        AlertDialog(
            onDismissRequest = { vm.dismissDuplicatePrompt() },
            containerColor = DarkCardBg,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 22.sp)
                    Text("Phát hiện đơn trùng lặp", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Có ${promptData.duplicateTrips.size} đơn có số km và thời gian trùng với dữ liệu đã có:",
                        color = TextGray,
                        fontSize = 13.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkInputBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        promptData.duplicateTrips.take(4).forEach { d ->
                            Text("• ${d.distanceKm} km  -  ${d.durationMinutes} phút (${d.source})", color = NeonCyan, fontSize = 12.5.sp)
                        }
                        if (promptData.duplicateTrips.size > 4) {
                            Text("và ${promptData.duplicateTrips.size - 4} đơn khác...", color = TextGray, fontSize = 11.sp)
                        }
                    }
                    if (promptData.uniqueTrips.isNotEmpty()) {
                        Text("Có ${promptData.uniqueTrips.size} đơn mới hợp lệ sẽ được lưu.", color = NeonGreen, fontSize = 13.sp)
                    } else {
                        Text("Tất cả ảnh vừa quét đều bị trùng lặp.", color = DangerRed, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.confirmSaveOnlyUnique() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (promptData.uniqueTrips.isNotEmpty()) "Bỏ qua trùng (${promptData.uniqueTrips.size} đơn mới)" else "Bỏ qua tất cả",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmSaveAll() }) {
                    Text("Vẫn lưu tất cả", color = TextGray)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF00382E), Color(0xFF071F1A), DarkBg)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B2420))
                                .border(1.dp, Color(0xFF2D4B3F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 20.sp)
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("GreenTrip Tracker", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF004D40)) {
                                    Text("XANH SM", color = EmeraldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("Hôm nay: ${vm.todayDate}", color = TextGray, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF161A1D))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabPillItem("Hôm nay", selectedTab == 0, Modifier.weight(1f)) {
                        selectedTab = 0
                        vm.setDate(vm.todayDate)
                    }
                    TabPillItem("Theo ngày", selectedTab == 1, Modifier.weight(1f)) {
                        selectedTab = 1
                    }
                    TabPillItem("Tuần / Tháng", selectedTab == 2, Modifier.weight(1.2f)) {
                        selectedTab = 2
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> TodayDarkScreen(vm)
                1 -> HistoryDarkScreen(vm)
                2 -> PeriodReportScreen(vm)
            }
        }
    }
}

@Composable
fun TabPillItem(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) EmeraldAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) TextWhite else TextGray,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

// TAB 1: CA CHẠY HÔM NAY
@Composable
fun TodayDarkScreen(vm: MainViewModel) {
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
        TripEdit
