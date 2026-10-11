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
            if (text.contains("hướng", ignoreCase = true) || text.contains("sau đó", ignoreCase = true)) {
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

// 4. MAIN ACTIVITY & UI
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6F8)) {
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
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0; vm.setDate(vm.todayDate) },
                    label = { Text("Hôm nay", fontWeight = FontWeight.Bold) },
                    icon = { Text("⚡", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    label = { Text("Theo ngày & Doanh thu", fontWeight = FontWeight.Bold) },
                    icon = { Text("📊", fontSize = 18.sp) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (currentTab == 0) TodayShiftScreen(vm) else HistoryDateScreen(vm)
        }
    }
}

// TAB 1: HÔM NAY
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
        item {
            Text("Ca Chạy Hôm Nay", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
            Text("Ngày: ${vm.todayDate}", fontSize = 13.sp, color = Color.Gray)
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Đồng hồ ODO xe (Hôm nay)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startInput,
                            onValueChange = { startInput = it; vm.updateShift(vm.todayDate, it.toDoubleOrNull(), null, null) },
                            label = { Text("ODO Đầu ca") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endInput,
                            onValueChange = { endInput = it; vm.updateShift(vm.todayDate, null, it.toDoubleOrNull(), null) },
                            label = { Text("ODO Cuối ca") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Tổng Kết Hiệu Quả", fontWeight = FontWeight.Bold, color = Color(0xFF004D40))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tổng cuốc: ${trips.size}")
                        Text("Thời gian: ${String.format("%.1f", totalOrderMin)} phút")
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Km có khách: ${String.format("%.1f", totalOrderDist)} km")
                        Text("Km lăn bánh: ${String.format("%.1f", actualDist)} km")
                    }
                    Divider(Modifier.padding(vertical = 10.dp), color = Color(0xFF80CBC4))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Km chạy rỗng:", fontWeight = FontWeight.Bold)
                        Text(
                            "${String.format("%.1f", emptyDist)} km",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (emptyDist > 15) Color(0xFFD32F2F) else Color(0xFF00796B)
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                enabled = !vm.isScanning
            ) {
                if (vm.isScanning) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đang quét ảnh...")
                } else {
                    Text("CHỌN ẢNH ĐƠN / MAPS ĐỂ QUÉT", fontWeight = FontWeight.Bold)
                }
            }
        }
        items(trips) { t ->
            TripRowCard(t) { vm.deleteTrip(t.id) }
        }
    }
}

// TAB 2: THEO NGÀY & DOANH THU & ĐƠN GIÁ / KM
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
            Text("Báo Cáo & Đơn Giá Km", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
        }
        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Nhập ngày cần xem (yyyy-MM-dd):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { dateInput = it; vm.setDate(it) },
                            placeholder = { Text("2026-10-11") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { dateInput = vm.todayDate; vm.setDate(vm.todayDate) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                        ) {
                            Text("Hôm nay")
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Doanh Số Ròng Thực Nhận (VNĐ)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = revenueInput,
                        onValueChange = {
                            revenueInput = it
                            vm.updateShift(selectedDate, null, null, it.toDoubleOrNull())
                        },
                        placeholder = { Text("VD: 750000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startInput,
                            onValueChange = { startInput = it; vm.updateShift(selectedDate, it.toDoubleOrNull(), null, null) },
                            label = { Text("ODO Đầu") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endInput,
                            onValueChange = { endInput = it; vm.updateShift(selectedDate, null, it.toDoubleOrNull(), null) },
                            label = { Text("ODO Cuối") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF004D40))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("HIỆU QUẢ DOANH THU TRÊN KM", fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Đơn giá / Km có khách", color = Color(0xFFA7FFEB), fontSize = 12.sp)
                            Text(
                                "${formatter.format(pricePerOrderKm.toLong())} đ/km",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E676)
                            )
                            Text("(Doanh số ÷ Km vận doanh)", color = Color.LightGray, fontSize = 10.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Đơn giá / Km lăn bánh", color = Color(0xFFFFE082), fontSize = 12.sp)
                            Text(
                                "${formatter.format(pricePerActualKm.toLong())} đ/km",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD54F)
                            )
                            Text("(Doanh số ÷ Tổng ODO)", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color(0xFF00695C))
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
            Text("Danh sách cuốc ngày $selectedDate (${trips.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        items(trips) { t ->
            TripRowCard(t) { vm.deleteTrip(t.id) }
        }
    }
}

@Composable
fun TripRowCard(trip: TripEntity, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${trip.distanceKm} km", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("•", color = Color.Gray)
                    Text("${trip.durationMinutes} phút", color = Color.DarkGray, fontSize = 14.sp)
                }
                Text("${trip.source} - ${trip.date}", fontSize = 12.sp, color = Color.Gray)
            }
            TextButton(onClick = onDelete) {
                Text("Xóa", color = Color.Red)
            }
        }
    }
}
