package com.example.bluetoothcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.bluetooth.BluetoothDevice
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter

@Composable
fun CarControlScreen(
    discoveredDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    onScanDevices: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onSendCommand: (String) -> Unit,
    onStop: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFE0F7FA) // Màu nền
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tiêu đề
                Text(
                    text = "Điều khiển xe",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Nút quét thiết bị
                Button(onClick = onScanDevices) {
                    Text(if (isScanning) "Đang quét..." else "Quét thiết bị Bluetooth")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Danh sách thiết bị
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    items(discoveredDevices) { device ->
                        Button(
                            onClick = { onConnect(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(device.name ?: "Thiết bị không tên (${device.address})")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Giao diện điều khiển
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ControlButton("Tiến", "F", onSendCommand, onStop, Icons.Default.KeyboardArrowUp)
                        Spacer(modifier = Modifier.height(16.dp))
                        ControlButton("Lùi", "B", onSendCommand, onStop, Icons.Default.KeyboardArrowDown)
                    }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB0BEC5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🚗",
                            fontSize = 40.sp
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ControlButton("Trái", "L", onSendCommand, onStop, Icons.Default.ArrowBack)
                        Spacer(modifier = Modifier.height(16.dp))
                        ControlButton("Phải", "R", onSendCommand, onStop, Icons.Default.ArrowForward)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControlButton(
    label: String,
    command: String,
    sendCommand: (String) -> Unit,
    onStop: () -> Unit,
    icon: ImageVector
) {
    Button(
        onClick = { },
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sendCommand(command)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        onStop()
                        true
                    }
                    else -> false
                }
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(48.dp),
            tint = Color.White
        )
    }
}
