package com.example.bluetoothcar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bluetoothcar.ui.theme.BluetoothCarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private var bluetoothSocket: BluetoothSocket? = null
    private val isConnected = mutableStateOf(false) // Trạng thái kết nối
    private var outputStream: OutputStream? = null // OutputStream để gửi dữ liệu
    private var deviceAddress: String? = null // Lưu địa chỉ thiết bị để khôi phục kết nối

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Khôi phục trạng thái nếu Activity tái tạo
        if (savedInstanceState != null) {
            isConnected.value = savedInstanceState.getBoolean("isConnected", false)
            deviceAddress = savedInstanceState.getString("deviceAddress")
            if (isConnected.value && deviceAddress != null) {
                // Thử khôi phục kết nối
                lifecycleScope.launch(Dispatchers.IO) {
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    if (device != null) {
                        try {
                            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                            val socket = device.createRfcommSocketToServiceRecord(uuid)
                            socket.connect()
                            bluetoothSocket = socket
                            outputStream = socket.outputStream
                            withContext(Dispatchers.Main) {
                                startConnectionMonitor()
                            }
                        } catch (e: IOException) {
                            withContext(Dispatchers.Main) {
                                isConnected.value = false
                                Toast.makeText(this@MainActivity, "Không thể khôi phục kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        checkBluetoothPermissions()

        setContent {
            BluetoothCarTheme {
                // Thiết lập điều hướng
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "device_list") {
                    composable("device_list") {
                        BluetoothDeviceList(
                            context = this@MainActivity,
                            devices = getPairedDevices(),
                            onDeviceSelected = { device ->
                                connectToDevice(device) {
                                    // Khi kết nối thành công, chuyển sang ControlScreen
                                    navController.navigate("control")
                                }
                            }
                        )
                    }
                    composable("control") {
                        ControlScreen(
                            onSendCommand = { command -> sendCommand(command) },
                            onDisconnect = {
                                disconnectDevice()
                                // Quay lại màn hình danh sách thiết bị
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isConnected", isConnected.value)
        outState.putString("deviceAddress", deviceAddress)
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    1
                )
            }
        } else { // Android 11 trở xuống
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1
                )
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        return pairedDevices?.toList() ?: emptyList()
    }

    private fun connectToDevice(device: BluetoothDevice, onSuccess: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID chuẩn cho HC-06
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                bluetoothSocket = socket
                outputStream = socket.outputStream
                deviceAddress = device.address // Lưu địa chỉ thiết bị

                withContext(Dispatchers.Main) {
                    isConnected.value = true
                    Toast.makeText(this@MainActivity, "Kết nối thành công!", Toast.LENGTH_SHORT).show()
                    startConnectionMonitor()
                    onSuccess() // Gọi callback để chuyển màn hình
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    isConnected.value = false
                    Toast.makeText(this@MainActivity, "Kết nối thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendCommand(command: String) {
        try {
            if (bluetoothSocket == null || outputStream == null) {
                Toast.makeText(this, "Không có kết nối Bluetooth", Toast.LENGTH_SHORT).show()
                return
            }

            outputStream?.write(command.toByteArray())
            outputStream?.flush()
            Log.d("Bluetooth", "Đã gửi lệnh: $command")

        } catch (e: IOException) {
            Log.e("Bluetooth", "Lỗi khi gửi lệnh: ${e.message}")
            Toast.makeText(this, "Lỗi gửi lệnh, kiểm tra kết nối", Toast.LENGTH_SHORT).show()
            disconnectDevice()
        }
    }

    private fun disconnectDevice() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
            outputStream = null
            deviceAddress = null

            isConnected.value = false

            Toast.makeText(this, "Đã ngắt kết nối", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("Bluetooth", "Lỗi khi ngắt kết nối: ${e.message}")
        }
    }

    private fun startConnectionMonitor() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Đợi 3 giây trước khi bắt đầu kiểm tra để đảm bảo kết nối ổn định
            delay(3000)
            while (isConnected.value) {
                delay(1000)
                val isConnectedNow = try {
                    bluetoothSocket?.isConnected ?: false
                } catch (e: Exception) {
                    Log.e("Bluetooth", "Lỗi kiểm tra kết nối: ${e.message}")
                    false
                }

                Log.d("Bluetooth", "Trạng thái kết nối: $isConnectedNow")

                if (isConnected.value && !isConnectedNow) {
                    withContext(Dispatchers.Main) {
                        isConnected.value = false
                        Toast.makeText(this@MainActivity, "Mất kết nối Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
    }
}

@Composable
fun BluetoothDeviceList(
    context: Context,
    devices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    // Đảm bảo màn hình ở chế độ dọc
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            // Không cần khôi phục vì đây là màn hình mặc định
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Danh sách thiết bị Bluetooth",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onDeviceSelected(device) },
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) device.name ?: "Unknown Device" else "Permission required",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlScreen(
    onSendCommand: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    // Thay đổi hướng màn hình thành chế độ ngang khi vào ControlScreen
    val context = LocalContext.current
    DisposableEffect(Unit) {
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            // Khôi phục chế độ dọc khi thoát ControlScreen
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Trạng thái cho từng nút
    var isForwardPressed by remember { mutableStateOf(false) }
    var isBackwardPressed by remember { mutableStateOf(false) }
    var isLeftPressed by remember { mutableStateOf(false) }
    var isRightPressed by remember { mutableStateOf(false) }
    var isHornPressed by remember { mutableStateOf(false) }
    var isLightOn by remember { mutableStateOf(false) }
    var isSwitchOn by remember { mutableStateOf(false) }
    // Xử lý gửi lệnh dựa trên trạng thái
    LaunchedEffect(isForwardPressed) {
        if (isForwardPressed) {
            onSendCommand("F")
        } else if (!isBackwardPressed) {
            // Chỉ gửi "S" nếu cả Tiến và Lùi đều không được nhấn
            onSendCommand("S")
        }
    }

    LaunchedEffect(isBackwardPressed) {
        if (isBackwardPressed) {
            onSendCommand("B")
        } else if (!isForwardPressed) {
            // Chỉ gửi "S" nếu cả Tiến và Lùi đều không được nhấn
            onSendCommand("S")
        }
    }

    LaunchedEffect(isLeftPressed) {
        if (isLeftPressed) {
            onSendCommand("L")
        } else {
            // Khi thả nút Trái, kiểm tra trạng thái Tiến/Lùi
            when {
                isForwardPressed -> onSendCommand("F") // Tiếp tục Tiến nếu nút Tiến đang được nhấn
                isBackwardPressed -> onSendCommand("B") // Tiếp tục Lùi nếu nút Lùi đang được nhấn
                else -> onSendCommand("S") // Dừng nếu không có nút nào được nhấn
            }
        }
    }

    LaunchedEffect(isRightPressed) {
        if (isRightPressed) {
            onSendCommand("R")
        } else {
            // Khi thả nút Phải, kiểm tra trạng thái Tiến/Lùi
            when {
                isForwardPressed -> onSendCommand("F") // Tiếp tục Tiến nếu nút Tiến đang được nhấn
                isBackwardPressed -> onSendCommand("B") // Tiếp tục Lùi nếu nút Lùi đang được nhấn
                else -> onSendCommand("S") // Dừng nếu không có nút nào được nhấn
            }
        }
    }

    LaunchedEffect(isHornPressed) {
        if (isHornPressed) {
            onSendCommand("X")
        }
        else{
            onSendCommand("x")
            }
    }

    LaunchedEffect(isLightOn) {
        if (isLightOn) {
            onSendCommand("Y")
            Log.d("Bluetooth", "Bật đèn, gửi lệnh Y")
        } else {
            onSendCommand("y")
            Log.d("Bluetooth", "Tắt đèn, gửi lệnh y")
        }
    }
    LaunchedEffect(isSwitchOn) {
        if (isSwitchOn) {
            onSendCommand("A")
            Log.d("Bluetooth", "Switch bật, gửi lệnh A")
        } else {
            onSendCommand("N")
            Log.d("Bluetooth", "Switch tắt, gửi lệnh N")
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0F7FA))
            .padding(16.dp)
    ) {
        // Bố cục chính: Chia thành 3 phần với tỷ lệ 4/11, 3/11, 4/11
        Row(
            modifier = Modifier
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Phần 1: Nút Tiến và Lùi (chiếm 4/11 không gian)
            Column(
                modifier = Modifier
                    .weight(4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    onClickReleased = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(width = 270.dp, height = 130.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        event.changes.any { it.pressed && !isForwardPressed } -> {
                                            isForwardPressed = true
                                            Log.d("Bluetooth", "Nhấn nút Tiến")
                                        }
                                        event.changes.any { !it.pressed && isForwardPressed } -> {
                                            isForwardPressed = false
                                            Log.d("Bluetooth", "Thả nút Tiến, gửi lệnh dừng")
                                        }
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Tiến",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }

                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    onClickReleased = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(width = 270.dp, height = 130.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        event.changes.any { it.pressed && !isBackwardPressed } -> {
                                            isBackwardPressed = true
                                            Log.d("Bluetooth", "Nhấn nút Lùi")
                                        }
                                        event.changes.any { !it.pressed && isBackwardPressed } -> {
                                            isBackwardPressed = false
                                            Log.d("Bluetooth", "Thả nút Lùi, gửi lệnh dừng")
                                        }
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Lùi",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            // Phần 2: Nút Ngắt kết nối, biểu tượng xe và 2 nút Còi/Đèn (chiếm 3/11 không gian)
            Column(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Nút Ngắt kết nối (đặt ở trên cùng, căn giữa)
                Button(
                    onClick = onDisconnect,
                    onClickReleased = { /* Không cần gửi lệnh khi thả */ },
                    modifier = Modifier
                        .size(width = 200.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(Color.Gray)
                ) {
                    Text("Ngắt kết nối", fontSize = 16.sp)
                }

                // Biểu tượng xe
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Auto: ",
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Switch(
                        checked = isSwitchOn,
                        onCheckedChange = { isSwitchOn = it },
                        modifier = Modifier
                            .scale(1.2f) // Tăng kích thước Switch nếu cần
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Nút Còi và Đèn (nằm ngang)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { /* Xử lý qua pointerInput */ },
                        onClickReleased = { /* Xử lý qua pointerInput */ },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when {
                                            event.changes.any { it.pressed && !isHornPressed } -> {
                                                isHornPressed = true
                                                Log.d("Bluetooth", "Nhấn nút Còi")
                                            }
                                            event.changes.any { !it.pressed && isHornPressed } -> {
                                                isHornPressed = false
                                                Log.d("Bluetooth", "Thả nút Còi")
                                            }
                                        }
                                    }
                                }
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                    ) {
                        Text(
                            text = "📢",
                            fontSize = 16.sp
                        )
                    }

                    Button(
                        onClick = { /* Xử lý qua pointerInput */ },
                        onClickReleased = { /* Xử lý qua pointerInput */ },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when {
                                            event.changes.any { it.pressed && !isLightOn } -> {
                                                isLightOn = true
                                                Log.d("Bluetooth", "Nhấn nút Đèn")
                                            }
                                            event.changes.any { !it.pressed && isLightOn } -> {
                                                isLightOn = false
                                                Log.d("Bluetooth", "Thả nút Đèn")
                                            }
                                        }
                                    }
                                }
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLightOn) Color.Yellow else Color(0xFFB0BEC5)
                        )
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Phần 3: Nút Trái và Phải (chiếm 4/11 không gian)
            Row(
                modifier = Modifier
                    .weight(4f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    onClickReleased = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(width = 130.dp, height = 270.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        event.changes.any { it.pressed && !isLeftPressed } -> {
                                            isLeftPressed = true
                                            Log.d("Bluetooth", "Nhấn nút Trái")
                                        }
                                        event.changes.any { !it.pressed && isLeftPressed } -> {
                                            isLeftPressed = false
                                            Log.d("Bluetooth", "Thả nút Trái, kiểm tra Tiến/Lùi")
                                        }
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Trái",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }

                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    onClickReleased = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(width = 130.dp, height = 270.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        event.changes.any { it.pressed && !isRightPressed } -> {
                                            isRightPressed = true

                                            Log.d("Bluetooth", "Nhấn nút Phải")
                                        }
                                        event.changes.any { !it.pressed && isRightPressed } -> {
                                            isRightPressed = false
                                            Log.d("Bluetooth", "Thả nút Phải, kiểm tra Tiến/Lùi")
                                        }
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Phải",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    onClickReleased: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Button(
        onClick = { /* Không dùng onClick của Button, xử lý qua pointerInput */ },
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when {
                            event.changes.any { it.pressed && !isPressed } -> {
                                isPressed = true
                                onClick()
                                Log.d("Bluetooth", "Nhấn nút")
                            }
                            event.changes.any { !it.pressed && isPressed } -> {
                                isPressed = false
                                onClickReleased()
                                Log.d("Bluetooth", "Thả nút")
                            }
                        }
                    }
                }
            },
        colors = colors
    ) {
        content()
    }
}

@Preview(
    name = "Control Screen Preview",
    widthDp = 800,
    heightDp = 400
)
@Composable
fun ControlScreenPreview() {
    BluetoothCarTheme {
        ControlScreen(
            onSendCommand = { command -> println("Command: $command") },
            onDisconnect = { println("Disconnected") }
        )
    }
}