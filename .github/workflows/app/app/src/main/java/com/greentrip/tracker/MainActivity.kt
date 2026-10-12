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

// BẢNG MÀU CHUẨN XANH SM (DARK THEME)
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

    @Query("SELECT * FROM trips WHERE date = :date ORDER BY id DESC")
    fun getTripsByDate(date: String): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShift(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE date = :date")
    fun getShiftByDate(date: String): Flow<ShiftEntity?>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)
}

@Database(entities = [TripEntity::class, ShiftEntity::class], version = 2, exportSchema = false)
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

// BỘ BÓC TÁCH NÂNG CẤP (XỬ LÝ MÉT & LỌC NHIỄU BIỂN BÁO)
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

            // 1. Tìm km trước (loại trừ km/h)
            val kmMatch = Regex("""(?i)(\d+([.,]\d+)?)\s*km(?!/h)""").find(text)
            if (kmMatch != null) {
                distance = kmMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            } else {
                // 2. Nếu không có km, tìm đơn vị mét (m) và chia cho 1000
                val mMatch = Regex("""(?i)(\d+)\s*m\b(?!/h|in)""").find(text)
                if (mMatch != null) {
                    val meters = mMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    distance = meters / 1000.0
                }
            }

            // 3. Xử lý thời gian (Bỏ qua "chậm hơn X phút", không bắt chữ 'h' đơn lẻ để tránh AH17)
            var totalMin = 0.0

            // Chỉ bắt chữ "giờ" rõ ràng (ví dụ: 1 giờ 10 phút)
            Regex("""(?i)(\d+)\s*giờ""").find(text)?.let {
                totalMin += (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60
            }

            // Lấy số phút (loại bỏ các dòng chứa 'chậm hơn' hoặc 'nhanh hơn')
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

    fun setDate(date: String) { selectedDate.value = date }

    fun processImages(uris: List<Uri>, forDate: String) {
        viewModelScope.launch {
            isScanning = true
            val trips = uris.mapNotNull { ocrProcessor.processImageUri(it) }
                .filter { it.distanceKm > 0 || it.durationMinutes > 0 }
                .map { TripEntity(date = forDate, distanceKm = it.distanceKm, durationMinutes = it.durationMinutes, source = it.source) }
            if (trips.isNotEmpty()) dao.insertTrips(trips)
            isScanning = false
        }
    }

    fun updateShift(date: String, start: Double?, end: Double?, revenue: Double?) {
        viewModelScope.launch {
            val cur = currentShift.value ?: ShiftEntity(date = date)
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
}

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
                            Text("Ngày: ${vm.todayDate}", color = TextGray, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // THANH TAB CHỐNG RỚT DÒNG
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF161A1D))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTab == 0) EmeraldAccent else Color.Transparent)
                            .clickable { selectedTab = 0; vm.setDate(vm.todayDate) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Hôm nay", color = if (selectedTab == 0) TextWhite else TextGray, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTab == 1) EmeraldAccent else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Theo ngày & Doanh thu", color = if (selectedTab == 1) TextWhite else TextGray, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) TodayDarkScreen(vm) else HistoryDarkScreen(vm)
        }
    }
}

@Composable
fun TodayDarkScreen(vm: MainViewModel) {
    val trips by vm.currentTrips.collectAsState()
    val shift by vm.currentShift.collectAsState()
    var startInput by remember { mutableStateOf("") }
    var endInput by remember { mutableStateOf("") }

    LaunchedEffect(shift) {
        shift?.let {
            if (it.startOdo > 0 && startInput.isEmpty()) startInput = it.startOdo.toString()
            if (it.endOdo > 0 && endInput.isEmpty()) endInput = it.endOdo.toString()
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                modifier = Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E3A2F)) {
                                Text("ODO", color = EmeraldAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Text("Đồng hồ ODO xe (km)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text("Lăn bánh: ${String.format("%.1f", actualDist)} km", color = TextGray, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DarkOdoBox("ODO Đầu ca", startInput, { startInput = it; vm.updateShift(vm.todayDate, it.toDoubleOrNull(), null, null) }, Modifier.weight(1f))
                        DarkOdoBox("ODO Cuối ca", endInput, { endInput = it; vm.updateShift(vm.todayDate, null, it.toDoubleOrNull(), null) }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                modifier = Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldAccent))
                            Text("BẢNG HIỆU QUẢ CA CHẠY", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF242A2E)) {
                            Text("${trips.size} cuốc", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DarkStatBox("Thời gian có khách", String.format("%.1f", totalOrderMin), "phút", TextWhite, Modifier.weight(1f))
                        DarkStatBox("Km có đơn chở", String.format("%.1f", totalOrderDist), "km", NeonCyan, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DarkStatBox("Km ODO thực tế", String.format("%.1f", actualDist), "km", TextWhite, Modifier.weight(1f))
                        DarkStatBox("Tỷ lệ đón khách", "$pickupRate", "%", NeonGreen, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(14.dp))

                    // THẺ KM CHẠY RỖNG CHỐNG TRÀN VÀ GÃY CHỮ
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningRedBg),
                        modifier = Modifier.fillMaxWidth().border(1.dp, WarningRedBorder, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                Text("🛵", fontSize = 24.sp)
                                Column {
                                    Text("Km chạy rỗng", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Không chở khách", color = TextGray, fontSize = 11.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format("%.1f", emptyDist)} km",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (emptyDist > 15) DangerRed else NeonGreen,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (emptyDist > 15) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF4A1E24)) {
                                        Text("Chạy rỗng nhiều!", color = DangerRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
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
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                elevation = ButtonDefaults.buttonElevation(4.dp),
                enabled = !vm.isScanning
            ) {
                if (vm.isScanning) {
                    CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Đang bóc tách số liệu...", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("📸   CHỌN ẢNH ĐƠN / MAPS ĐỂ QUÉT", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Danh sách đơn đã quét", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${trips.size} đơn", color = TextGray, fontSize = 12.sp)
            }
        }

        items(trips) { t ->
            DarkTripRow(t) { vm.deleteTrip(t.id) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun HistoryDarkScreen(vm: MainViewModel) {
    val selectedDate by vm.selectedDate.collectAsState()
    val trips by vm.currentTrips.collectAsState()
    val shift by vm.currentShift.collectAsState()

    var dateInput by remember { mutableStateOf(selectedDate) }
    var revenueInput by remember { mutableStateOf("") }
    var startInput by remember { mutableStateOf("") }
    var endInput by remember { mutableStateOf("") }

    val formatter = DecimalFormat("#,###")

    LaunchedEffect(shift) {
        shift?.let {
            if (it.revenue > 0 && revenueInput.isEmpty()) revenueInput = it.revenue.toInt().toString()
            if (it.startOdo > 0 && startInput.isEmpty()) startInput = it.startOdo.toString()
            if (it.endOdo > 0 && endInput.isEmpty()) endInput = it.endOdo.toString()
        }
    }

    val totalOrderDist = trips.sumOf { it.distanceKm }
    val sOdo = shift?.startOdo ?: 0.0
    val eOdo = shift?.endOdo ?: 0.0
    val actualDist = if (eOdo >= sOdo && sOdo > 0) eOdo - sOdo else 0.0
    val emptyDist = if (actualDist > totalOrderDist) actualDist - totalOrderDist else 0.0
    val rev = shift?.revenue ?: 0.0

    val pricePerOrderKm = if (totalOrderDist > 0) rev / totalOrderDist else 0.0
    val pricePerActualKm = if (actualDist > 0) rev / actualDist else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                modifier = Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nhập ngày xem dữ liệu (yyyy-MM-dd):", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { dateInput = it; vm.setDate(it) },
                            placeholder = { Text("2026-10-11", color = TextGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkInputBg,
                                unfocusedContainerColor = DarkInputBg,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { dateInput = vm.todayDate; vm.setDate(vm.todayDate) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                        ) {
                            Text("Hôm nay", fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                modifier = Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Doanh Số Ròng Thực Nhận (VNĐ)", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = revenueInput,
                        onValueChange = {
                            revenueInput = it
                            vm.updateShift(selectedDate, null, null, it.toDoubleOrNull())
                        },
                        placeholder = { Text("VD: 750000", color = TextGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkInputBg,
                            unfocusedContainerColor = DarkInputBg,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DarkOdoBox("ODO Đầu", startInput, { startInput = it; vm.updateShift(selectedDate, it.toDoubleOrNull(), null, null) }, Modifier.weight(1f))
                        DarkOdoBox("ODO Cuối", endInput, { endInput = it; vm.updateShift(selectedDate, null, it.toDoubleOrNull(), null) }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00382E)),
                modifier = Modifier.border(1.dp, Color(0xFF005A4A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("HIỆU QUẢ DOANH THU TRÊN TỪNG KM", fontWeight = FontWeight.Black, color = Color(0xFFA7FFEB), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF07241E), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Đơn giá / Km khách", color = TextGray, fontSize = 11.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${formatter.format(pricePerOrderKm.toLong())} đ", fontSize = 19.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                                Text("Doanh số ÷ Km khách", color = Color(0xFF6E857F), fontSize = 9.sp)
                            }
                        }

                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF07241E), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Đơn giá / Km lăn bánh", color = TextGray, fontSize = 11.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${formatter.format(pricePerActualKm.toLong())} đ", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD54F))
                                Text("Doanh số ÷ Tổng ODO", color = Color(0xFF6E857F), fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF0B463A))
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Số cuốc: ${trips.size}", color = TextWhite, fontSize = 13.sp)
                        Text("Km khách: ${String.format("%.1f", totalOrderDist)} km", color = NeonCyan, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Km lăn bánh: ${String.format("%.1f", actualDist)} km", color = TextWhite, fontSize = 13.sp)
                        Text("Km rỗng: ${String.format("%.1f", emptyDist)} km", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Text("Danh sách cuốc ngày $selectedDate (${trips.size})", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(trips) { t ->
            DarkTripRow(t) { vm.deleteTrip(t.id) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun DarkOdoBox(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkInputBg),
        modifier = modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = TextGray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("0.0", color = Color(0xFF555B60)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text("km", color = TextGray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DarkStatBox(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkInputBg),
        modifier = modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = color)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
fun DarkTripRow(trip: TripEntity, onDelete: () -> Unit) {
    val isMaps = trip.source == "Google Maps"
    val badgeBg = if (isMaps) Color(0xFF162736) else Color(0xFF142B22)
    val badgeColor = if (isMaps) Color(0xFF64B5F6) else EmeraldAccent

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${trip.distanceKm} km", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("•", color = TextGray)
                    Text("${trip.durationMinutes} phút", color = Color(0xFFCFD8DC), fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                        Text(
                            trip.source,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(trip.date, color = TextGray, fontSize = 12.sp)
                }
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
            ) {
                Text("Xóa", fontWeight = FontWeight.Bold)
            }
        }
    }
}
