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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
                                // Bỏ phần xoay màn hình
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
                // Đảm bảo UI recompose khi isConnected thay đổi
                val isConnectedState by isConnected
                if (isConnectedState) {
                    ControlScreen(
                        onSendCommand = { command -> sendCommand(command) },
                        onDisconnect = { disconnectDevice() }
                    )
                } else {
                    val pairedDevices = getPairedDevices()
                    BluetoothDeviceList(
                        context = this@MainActivity,
                        devices = pairedDevices,
                        onDeviceSelected = { device -> connectToDevice(device) }
                    )
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

    private fun connectToDevice(device: BluetoothDevice) {
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
                    // Bỏ phần xoay màn hình
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
            // Bỏ phần xoay màn hình
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
                        // Bỏ phần xoay màn hình
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
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

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
                            text = if (hasPermission) device.name ?: "Unknown Device" else "Permission required",
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0F7FA))
            .padding(16.dp)
    ) {
        // Bố cục chính: Chia thành 3 phần với tỷ lệ 2/5, 1/5, 2/5
        Row(
            modifier = Modifier
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Phần 1: Nút Tiến và Lùi (chiếm 2/5 không gian)
            Column(
                modifier = Modifier
                    .weight(4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { onSendCommand("F") },
                    onClickReleased = { onSendCommand("S") },
                    modifier = Modifier
                        .size(width = 270.dp, height = 130.dp)
                        ,
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
                    onClick = { onSendCommand("B") },
                    onClickReleased = { onSendCommand("S") },
                    modifier = Modifier
                        .size(width = 270.dp, height = 130.dp)
                        .clip(RoundedCornerShape(4.dp)),
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

            // Phần 2: Nút Ngắt kết nối, biểu tượng xe và 2 nút Còi/Đèn (chiếm 1/5 không gian)
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
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB0BEC5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Xe",
                        modifier = Modifier.size(40.dp),
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Nút Còi và Đèn (nằm ngang)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSendCommand("5") },
                        onClickReleased = { /* Không cần gửi lệnh khi thả */ },
                        modifier = Modifier
                            .size(80.dp) // Thu nhỏ để vừa không gian ngang
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                    ) {
                        Text(
                            text = "📢",
                            fontSize = 16.sp
                        )
                    }

                    Button(
                        onClick = { onSendCommand("6") },
                        onClickReleased = { /* Không cần gửi lệnh khi thả */ },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB0BEC5))
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Phần 3: Nút Trái và Phải (chiếm 2/5 không gian)
            Row(
                modifier = Modifier
                    .weight(4f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onSendCommand("L") },
                    onClickReleased = { onSendCommand("S") },
                    modifier = Modifier
                        .size(width = 130.dp, height = 270.dp)
                        .clip(RoundedCornerShape(4.dp)),
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
                    onClick = { onSendCommand("R") },
                    onClickReleased = { onSendCommand("S") },
                    modifier = Modifier
                        .size(width = 130.dp, height = 270.dp)
                        .clip(RoundedCornerShape(4.dp)),
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

