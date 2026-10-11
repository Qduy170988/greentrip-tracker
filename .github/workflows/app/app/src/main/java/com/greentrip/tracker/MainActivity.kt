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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

// --- BẢNG MÀU CHUẨN XANH SM ---
val GreenPrimary = Color(0xFF00897B)
val GreenDark = Color(0xFF004D40)
val GreenLightBg = Color(0xFFE0F2F1)
val GreenAccent = Color(0xFF00C853)
val DangerRed = Color(0xFFD32F2F)
val ScreenBg = Color(0xFFF4F6F8)

// 1. DATA DATABASE
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

// 2. OCR BÓC TÁCH VÀ TỰ XÓA RAM
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

            val distMatch = Regex("""(\d+([.,]\d+)?)\s*km(?!/h)""", RegexOption.IGNORE_CASE).find(text)
            val distance = distMatch?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

            var totalMin = 0.0
            Regex("""(\d+)\s*(?:giờ|h)""", RegexOption.IGNORE_CASE).find(text)?.let {
                totalMin += (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60
            }
            Regex("""(\d+([.,]\d+)?)\s*(?:phút|ph|min)""", RegexOption.IGNORE_CASE).find(text)?.let {
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

// 4. MAIN ACTIVITY & UI NÂNG CẤP
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = ScreenBg) {
                    MainAppScaffold()
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(vm: MainViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0; vm.setDate(vm.todayDate) },
                    label = { Text("Hôm nay", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Text("⚡", fontSize = 20.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GreenDark,
                        selectedTextColor = GreenDark,
                        indicatorColor = GreenLightBg,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    label = { Text("Theo ngày & Doanh thu", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Text("📊", fontSize = 20.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GreenDark,
                        selectedTextColor = GreenDark,
                        indicatorColor = GreenLightBg,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (currentTab == 0) TodayShiftScreen(vm) else HistoryDateScreen(vm)
        }
    }
}

// TAB 1: CA CHẠY HÔM NAY (CHUẨN GIAO DIỆN DEMO)
@Composable
fun TodayShiftScreen(vm: MainViewModel) {
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // TIÊU ĐỀ ĐẦU TRANG
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Ca Chạy Hôm Nay", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GreenDark)
                    Text("Ngày: ${vm.todayDate}", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = GreenLightBg) {
                    Text(
                        text = "● Đang ghi nhận",
                        color = GreenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // CARD NHẬP ODO
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Đồng hồ ODO xe (Hôm nay)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF212121))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = startInput,
                            onValueChange = { startInput = it; vm.updateShift(vm.todayDate, it.toDoubleOrNull(), null, null) },
                            label = { Text("ODO Đầu ca") },
                            placeholder = { Text("1245.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endInput,
                            onValueChange = { endInput = it; vm.updateShift(vm.todayDate, null, it.toDoubleOrNull(), null) },
                            label = { Text("ODO Cuối ca") },
                            placeholder = { Text("1311.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // CARD TỔNG KẾT HIỆU QUẢ DẠNG GRID (KHÔNG BỊ TRÀN CHỮ)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tổng Kết Hiệu Quả", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GreenDark)
                        Text("${trips.size} chuyến", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(12.dp))

                    // 4 Ô CHỈ SỐ GỌN GÀNG (2 HÀNG x 2 CỘT)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBox(label = "Km có khách", value = "${String.format("%.1f", totalOrderDist)} km", color = GreenDark, modifier = Modifier.weight(1f))
                        MetricBox(label = "Km lăn bánh", value = "${String.format("%.1f", actualDist)} km", color = Color(0xFF1E88E5), modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBox(label = "Tổng thời gian", value = "${String.format("%.1f", totalOrderMin)} ph", color = Color(0xFF5E35B1), modifier = Modifier.weight(1f))
                        MetricBox(label = "Số đơn nhận", value = "${trips.size} đơn", color = Color(0xFF00796B), modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(14.dp))

                    // KM CHẠY RỖNG NỔI BẬT DƯỚI CÙNG
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Km chạy rỗng:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF424242))
                            Text("(Lăn bánh - Km có khách)", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(
                            text = "${String.format("%.1f", emptyDist)} km",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (emptyDist > 15) DangerRed else GreenAccent
                        )
                    }
                }
            }
        }

        // NÚT CHỌN ẢNH ĐỂ QUÉT
        item {
            Button(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                elevation = ButtonDefaults.buttonElevation(3.dp),
                enabled = !vm.isScanning
            ) {
                if (vm.isScanning) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Đang nhận diện số liệu...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("📸  CHỌN ẢNH ĐƠN / MAPS ĐỂ QUÉT", fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // TIÊU ĐỀ DANH SÁCH ĐƠN
        item {
            Text("Danh sách đơn đã quét (${trips.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF37474F))
        }

        items(trips) { t ->
            TripRowCard(t) { vm.deleteTrip(t.id) }
        }
    }
}

// TAB 2: QUẢN LÝ THEO NGÀY & DOANH THU & ĐƠN GIÁ / KM
@Composable
fun HistoryDateScreen(vm: MainViewModel) {
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Báo Cáo & Đơn Giá Km", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GreenDark)
        }

        // Ô CHỌN NGÀY
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nhập ngày cần xem (yyyy-MM-dd):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { dateInput = it; vm.setDate(it) },
                            placeholder = { Text("2026-10-11") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { dateInput = vm.todayDate; vm.setDate(vm.todayDate) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text("Hôm nay", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Ô NHẬP DOANH SỐ RÒNG & ODO
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Doanh Số Ròng Thực Nhận (VNĐ)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GreenDark)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = revenueInput,
                        onValueChange = {
                            revenueInput = it
                            vm.updateShift(selectedDate, null, null, it.toDoubleOrNull())
                        },
                        placeholder = { Text("VD: 750000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startInput,
                            onValueChange = { startInput = it; vm.updateShift(selectedDate, it.toDoubleOrNull(), null, null) },
                            label = { Text("ODO Đầu") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endInput,
                            onValueChange = { endInput = it; vm.updateShift(selectedDate, null, it.toDoubleOrNull(), null) },
                            label = { Text("ODO Cuối") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // THẺ HIỆU QUẢ DOANH THU ĐẬM CHẤT PREMIUM
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GreenDark),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("HIỆU QUẢ DOANH THU TRÊN TỪNG KM", fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF00382E), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Đơn giá / Km khách", color = Color(0xFFA7FFEB), fontSize = 11.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${formatter.format(pricePerOrderKm.toLong())} đ",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E676)
                                )
                                Text("Doanh số ÷ Km khách", color = Color.Gray, fontSize = 9.sp)
                            }
                        }

                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF00382E), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Đơn giá / Km lăn bánh", color = Color(0xFFFFE082), fontSize = 11.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${formatter.format(pricePerActualKm.toLong())} đ",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFFD54F)
                                )
                                Text("Doanh số ÷ Tổng ODO", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF00695C))
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Số cuốc: ${trips.size}", color = Color.White, fontSize = 13.sp)
                        Text("Km khách: ${String.format("%.1f", totalOrderDist)} km", color = Color(0xFFA7FFEB), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Km lăn bánh: ${String.format("%.1f", actualDist)} km", color = Color.White, fontSize = 13.sp)
                        Text("Km rỗng: ${String.format("%.1f", emptyDist)} km", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Text("Danh sách cuốc ngày $selectedDate (${trips.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF37474F))
        }

        items(trips) { t ->
            TripRowCard(t) { vm.deleteTrip(t.id) }
        }
    }
}

// THẺ HIỂN THỊ CHỈ SỐ GỌN GÀNG (METRIC BOX)
@Composable
fun MetricBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF7F9FA),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

// THẺ TỪNG CUỐC XE CÓ GẮN NHÃN MÀU (BADGE)
@Composable
fun TripRowCard(trip: TripEntity, onDelete: () -> Unit) {
    val isMaps = trip.source == "Google Maps"
    val badgeBg = if (isMaps) Color(0xFFE3F2FD) else Color(0xFFE8F5E9)
    val badgeColor = if (isMaps) Color(0xFF1976D2) else Color(0xFF2E7D32)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${trip.distanceKm} km", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color(0xFF212121))
                    Text("•", color = Color.LightGray)
                    Text("${trip.durationMinutes} phút", color = Color(0xFF616161), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                        Text(
                            text = trip.source,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(trip.date, fontSize = 12.sp, color = Color.Gray)
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
