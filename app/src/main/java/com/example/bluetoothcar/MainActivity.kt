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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import kotlin.math.min

// Hàm ánh xạ khoảng cách thành màu sắc
fun getColorForDistanceFB(distance: Float?): Color {
    if (distance == null) return Color.Gray.copy(alpha = 0.8f)
    return when {
        distance >= 80 -> Color.Gray.copy(alpha = 0.2f)
        distance > 60f -> Color.Green.copy(alpha = 0.8f)
        distance > 40f -> Color.Yellow.copy(alpha = 0.8f)
         // Cam
        else -> Color.Red.copy(alpha = 0.8f)
    }
}

fun getColorForDistanceLR(distance: Float?): Color {
    if (distance == null) return Color.Gray.copy(alpha = 0.8f)
    return when {
        distance >= 60 -> Color.Gray.copy(alpha = 0.2f)
        distance > 40f -> Color.Green.copy(alpha = 0.8f)
        distance > 20f -> Color.Yellow.copy(alpha = 0.8f)
        // Cam
        else -> Color.Red.copy(alpha = 0.8f)
    }
}

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private var bluetoothSocket: BluetoothSocket? = null
    private val isConnected = mutableStateOf(false)
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var deviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        enableEdgeToEdge()

        if (savedInstanceState != null) {
            isConnected.value = savedInstanceState.getBoolean("isConnected", false)
            deviceAddress = savedInstanceState.getString("deviceAddress")
            if (isConnected.value && deviceAddress != null) {
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
                            inputStream = socket.inputStream
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
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "device_list") {
                    composable("device_list") {
                        BluetoothDeviceList(
                            context = this@MainActivity,
                            devices = getPairedDevices(),
                            onDeviceSelected = { device ->
                                connectToDevice(device) {
                                    navController.navigate("control")
                                }
                            }
                        )
                    }
                    composable("control") {
                        if (bluetoothSocket?.inputStream == null) {
                            Log.e("Bluetooth", "InputStream initialized: $inputStream")
                            Text("Vui lòng kết nối với thiết bị Bluetooth", modifier = Modifier.padding(16.dp))
                        } else {
                            ControlScreen(
                                onSendCommand = { command -> sendCommand(command) },
                                onDisconnect = {
                                    disconnectDevice()
                                    navController.navigate("device_list") { popUpTo("control") { inclusive = true } }
                                },
                                bluetoothInputStream = bluetoothSocket!!.inputStream
                            )
                        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        } else {
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
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                bluetoothSocket = socket
                outputStream = socket.outputStream
                inputStream = socket.inputStream
                Log.d("Bluetooth", "InputStream initialized: $inputStream")
                deviceAddress = device.address
                withContext(Dispatchers.Main) {
                    if(inputStream == null){
                        isConnected.value = false
                        Toast.makeText(this@MainActivity, "Không thể khởi tạo luồng dữ liệu", Toast.LENGTH_LONG).show()
                    }
                    else{
                        isConnected.value = true
                        Toast.makeText(this@MainActivity, "Kết nối thành công!", Toast.LENGTH_SHORT).show()
                        startConnectionMonitor()
                        onSuccess()
                    }

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
        } catch (e: IOException) {
            Toast.makeText(this, "Lỗi gửi lệnh, kiểm tra kết nối", Toast.LENGTH_SHORT).show()
            disconnectDevice()
        }
    }

    private fun disconnectDevice() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            inputStream = null
            outputStream = null
            bluetoothSocket = null
            deviceAddress = null
            isConnected.value = false
            Toast.makeText(this, "Đã ngắt kết nối", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
        }
    }

    private fun startConnectionMonitor() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(3000)
            while (isConnected.value) {
                delay(1000)
                val isConnectedNow = try {
                    bluetoothSocket?.isConnected ?: false
                } catch (e: Exception) {
                    false
                }
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
@SuppressLint("ContextCastToActivity")
@Composable
fun BluetoothDeviceList(
    context: Context,
    devices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
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

private fun processLine(
    line: String,
    frontDistance: MutableState<Float?>,
    rearDistance: MutableState<Float?>,
    leftDistance: MutableState<Float?>,
    rightDistance: MutableState<Float?>
) {
    try {
        val parts = line.split(",")
        if (parts.size == 4) {
            parts.forEach { part ->
                when {
                    part.startsWith("F:") -> frontDistance.value = part.removePrefix("F:").trim().toFloatOrNull()
                    part.startsWith("B:") -> rearDistance.value = part.removePrefix("B:").trim().toFloatOrNull()
                    part.startsWith("L:") -> leftDistance.value = part.removePrefix("L:").trim().toFloatOrNull()
                    part.startsWith("R:") -> rightDistance.value = part.removePrefix("R:").trim().toFloatOrNull()
                }
            }
        }
    } catch (e: Exception) {
    }
}

@Composable
fun ControlScreen(
    onSendCommand: (String) -> Unit,
    onDisconnect: () -> Unit,
    bluetoothInputStream: InputStream
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    var isForwardPressed by remember { mutableStateOf(false) }
    var isBackwardPressed by remember { mutableStateOf(false) }
    var isLeftPressed by remember { mutableStateOf(false) }
    var isRightPressed by remember { mutableStateOf(false) }
    var isHornPressed by remember { mutableStateOf(false) }
    var isFlagOn by remember{ mutableStateOf(false) }
    var isLightOn by remember { mutableStateOf(false) }
    var isSwitchOn by remember { mutableStateOf(false) }
    var targetRotationAngle by remember { mutableStateOf(0f) }
    var inputNumber by remember { mutableStateOf("0") }
    val carImage: Painter = painterResource(id = R.drawable.car_icon)
    val animatedRotationAngle by animateFloatAsState(
        targetValue = targetRotationAngle,
        animationSpec = tween(durationMillis = 200)
    )
    var targetOffsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(durationMillis = 200)
    )

    val frontDistance = remember { mutableStateOf<Float?>(null) }
    val rearDistance = remember { mutableStateOf<Float?>(null) }
    val leftDistance = remember { mutableStateOf<Float?>(null) }
    val rightDistance = remember { mutableStateOf<Float?>(null) }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAnimation = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )


    // Danh sách các resource ảnh lá cờ
    val flagImages = listOf(
        R.drawable.layer0,
        R.drawable.layer1,
        R.drawable.layer2,
        R.drawable.layer3,
        R.drawable.layer4,
        R.drawable.layer5,
        R.drawable.layer6
    )

    // Trạng thái để theo dõi frame hiện tại
    var currentFrame by remember { mutableStateOf(0) }

    // Hiệu ứng chuyển đổi frame khi isFlagOn = true
    LaunchedEffect(isFlagOn) {
        if (isFlagOn) {
            while (true) {
                delay(200) // Chuyển frame mỗi 200ms
                currentFrame = (currentFrame + 1) % (flagImages.size - 1) + 1 // Chỉ chuyển giữa frame 2, 3, 4
            }
        } else {
            currentFrame = 0 // Hiển thị frame tĩnh (vietnam_flag_1) khi tắt
        }
    }
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(bluetoothInputStream, Charsets.UTF_8))
                flow {
                    while (true) {
                        val line = reader.readLine() ?: break
                        emit(line)
                    }
                }.collect { line ->
                    withContext(Dispatchers.Main) {
                        processLine(line, frontDistance, rearDistance, leftDistance, rightDistance)
                    }
                }
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) { onDisconnect() }
        }
    }

    LaunchedEffect(isForwardPressed, isBackwardPressed, isLeftPressed, isRightPressed) {
        val command = when {
            isForwardPressed && isLeftPressed -> "FL"
            isForwardPressed && isRightPressed -> "FR"
            isBackwardPressed && isLeftPressed -> "BL"
            isBackwardPressed && isRightPressed -> "BR"
            isForwardPressed -> "F"
            isBackwardPressed -> "B"
            isLeftPressed -> "L"
            isRightPressed -> "R"
            else -> "S"
        }
        onSendCommand(command)
    }

    LaunchedEffect(isHornPressed) {
        onSendCommand(if (isHornPressed) "X" else "x")
    }

    LaunchedEffect(isFlagOn){
        onSendCommand(if (isFlagOn) "V" else "v")
    }
    LaunchedEffect(isLightOn) {
        onSendCommand(if (isLightOn) "Y" else "y")
    }

    LaunchedEffect(isSwitchOn) {
        onSendCommand(if (isSwitchOn) "A" else "N")
    }

    LaunchedEffect(isForwardPressed, isBackwardPressed, isLeftPressed, isRightPressed) {
        targetOffsetX = when {
            isLeftPressed -> 0f
            isRightPressed -> 0f
            isForwardPressed -> -30f
            isBackwardPressed -> 30f
            else -> 0f
        }
    }

    LaunchedEffect(isForwardPressed, frontDistance.value) {
        if (isForwardPressed && frontDistance.value != null && frontDistance.value!! <= 40.0f) {
            isForwardPressed = false
            Toast.makeText(context, "Phía trước có vật cản, xe tự động dừng", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isBackwardPressed, rearDistance.value) {
        if (isBackwardPressed && rearDistance.value != null && rearDistance.value!! <= 40.0f) {
            isBackwardPressed = false
            Toast.makeText(context, "Phía sau có vật cản, xe tự động dừng", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isLeftPressed, leftDistance.value) {
        if (isLeftPressed && leftDistance.value != null && leftDistance.value!! <= 20.0f) {
            isLeftPressed = false
            Toast.makeText(context, "Bên trái có vật cản, xe tự động dừng rẽ trái", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(isRightPressed, rightDistance.value) {
        if (isRightPressed && rightDistance.value != null && rightDistance.value!! <= 20.0f) {
            isRightPressed = false
            Toast.makeText(context, "Bên phải có vật cản, xe tự động dừng rẽ phải", Toast.LENGTH_SHORT).show()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
//            .background(Color(0xFFCFEDFB))
//            .padding(7.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.background2), // Thay 'background' bằng tên file ảnh
            contentDescription = null, // Không cần mô tả vì là ảnh nền
            modifier = Modifier
                .fillMaxSize(), // Chiếm toàn bộ kích thước của Box
            contentScale = ContentScale.Crop // Cắt ảnh để vừa màn hình
        )
        Spacer(Modifier.size(7.dp))
        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = 12.dp)
                .offset(x = -12.dp)
                .size(width = 140.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp)),
            colors = ButtonDefaults.buttonColors(Color.Gray)
        ) {

            Text("Ngắt kết nối", fontSize = 16.sp)
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.size(28.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Auto  ",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Switch(
                        checked = isSwitchOn,
                        onCheckedChange = { isSwitchOn = it },
                        modifier = Modifier.scale(1.2f)
                    )
                }
                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(width = 220.dp, height = 100.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        event.changes.any { it.pressed && !isForwardPressed } -> {
                                            if (frontDistance.value != null && frontDistance.value!! < 40.0f) {
                                                Toast.makeText(context, "Phía trước có vật cản, không thể đi thẳng", Toast.LENGTH_SHORT).show()
                                            } else {
                                                isForwardPressed = true
                                            }
                                        }
                                        event.changes.any { !it.pressed && isForwardPressed } -> {
                                            isForwardPressed = false
                                        }
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Tiến",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(width = 220.dp, height = 100.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        event.changes.any { it.pressed && !isBackwardPressed } -> {
                                            if (rearDistance.value != null && rearDistance.value!! < 40.0f) {
                                                Toast.makeText(context, "Phía sau có vật cản, không thể lùi", Toast.LENGTH_SHORT).show()
                                            } else {
                                                isBackwardPressed = true
                                            }
                                        }
                                        event.changes.any { !it.pressed && isBackwardPressed } -> {
                                            isBackwardPressed = false
                                        }
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40C4FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Lùi",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = carImage,
                        contentDescription = "Car Icon",
                        modifier = Modifier
                            .size(80.dp)
                            .rotate(animatedRotationAngle)
                            .offset(y = animatedOffsetX.dp)
                    )
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val radius = min(canvasWidth, canvasHeight) * 0.4f
                        val center = Offset(size.width / 2, size.height / 2)

                        drawArc(
                            color = getColorForDistanceFB(frontDistance.value),
                            startAngle = 270f - 30f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(
                                width = if (frontDistance.value != null && frontDistance.value!! < 20f) 12f else 10f,
                                cap = StrokeCap.Round
                            )
                        )

                        drawArc(
                            color = getColorForDistanceFB(rearDistance.value),
                            startAngle = 90f - 30f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(
                                width = if (rearDistance.value != null && rearDistance.value!! < 20f) 12f else 10f,
                                cap = StrokeCap.Round
                            )
                        )

                        drawArc(
                            color = getColorForDistanceLR(leftDistance.value),
                            startAngle = 180f - 30f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(
                                width = if (leftDistance.value != null && leftDistance.value!! < 20f) 12f else 10f,
                                cap = StrokeCap.Round
                            )
                        )

                        drawArc(
                            color = getColorForDistanceLR(rightDistance.value),
                            startAngle = 0f - 30f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(
                                width = if (rightDistance.value != null && rightDistance.value!! < 20f) 12f else 10f,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                Button(
                    onClick = { /* Xử lý qua pointerInput */ },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                        isFlagOn = !isFlagOn
                                    }
                                }
                            }
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFlagOn) Color(0xFF4FC3F7) else Color(0xFFB0BEC5)
                    )
                ) {
                    Image(
                        painter = painterResource(

                            id = flagImages[currentFrame]),
                        contentDescription = "Vietnam Flag",
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { /* Xử lý qua pointerInput */ },
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
                                            }
                                            event.changes.any { !it.pressed && isHornPressed } -> {
                                                isHornPressed = false
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
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.pressed }) {
                                            isLightOn = !isLightOn
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

            Column(
                modifier = Modifier
                    .weight(4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {

                }
                Spacer(modifier = Modifier.size(58.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Button(
                        onClick = { /* Xử lý qua pointerInput */ },
                        modifier = Modifier
                            .size(width = 100.dp, height = 220.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when {
                                            event.changes.any { it.pressed && !isLeftPressed } -> {
                                                if (leftDistance.value != null && leftDistance.value!! < 20.0f) {
                                                    Toast.makeText(context, "Bên trái có vật cản, không thể rẽ trái", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    isLeftPressed = true
                                                    targetRotationAngle = -90f
                                                }
                                            }
                                            event.changes.any { !it.pressed && isLeftPressed } -> {
                                                isLeftPressed = false
                                                targetRotationAngle = 0f
                                            }
                                        }
                                    }
                                }
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40C4FF))
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
                        modifier = Modifier
                            .size(width = 100.dp, height = 220.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when {
                                            event.changes.any { it.pressed && !isRightPressed } -> {
                                                if (rightDistance.value != null && rightDistance.value!! < 20.0f) {
                                                    Toast.makeText(context, "Bên phải có vật cản, không thể rẽ phải", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    isRightPressed = true
                                                    targetRotationAngle = 90f
                                                }
                                            }
                                            event.changes.any { !it.pressed && isRightPressed } -> {
                                                isRightPressed = false
                                                targetRotationAngle = 0f
                                            }
                                        }
                                    }
                                }
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40C4FF))
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
}


//@Composable
//fun Button(
//    onClick: () -> Unit,
//    onClickReleased: () -> Unit,
//    modifier: Modifier = Modifier,
//    colors: ButtonColors = ButtonDefaults.buttonColors(),
//    content: @Composable () -> Unit
//) {
//    var isPressed by remember { mutableStateOf(false) }
//    Button(
//        onClick = { /* Không dùng onClick của Button, xử lý qua pointerInput */ },
//        modifier = modifier
//            .pointerInput(Unit) {
//                awaitPointerEventScope {
//                    while (true) {
//                        val event = awaitPointerEvent()
//                        when {
//                            event.changes.any { it.pressed && !isPressed } -> {
//                                isPressed = true
//                                onClick()
//                            }
//                            event.changes.any { !it.pressed && isPressed } -> {
//                                isPressed = false
//                                onClickReleased()
//                            }
//                        }
//                    }
//                }
//            },
//        colors = colors
//    ) {
//        content()
//    }
//}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
            onDisconnect = { println("Disconnected") },
            bluetoothInputStream = InputStream.nullInputStream()
        )
    }
}