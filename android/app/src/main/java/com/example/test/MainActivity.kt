package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RelayApp()
                }
            }
        }
    }
}

@Composable
fun RelayApp() {
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:3000") }
    var selectedRole by remember { mutableStateOf("source") }
    var status by remember { mutableStateOf("Disconnected") }
    var logText by remember { mutableStateOf("Ready to connect.\n") }
    var socket by remember { mutableStateOf<Socket?>(null) }

    fun appendLog(text: String) {
        logText += "$text\n"
    }

    fun connectSocket(role: String) {
        if (socket != null) {
            socket?.disconnect()
        }

        try {
            val ioOptions = IO.Options().apply {
                transports = arrayOf("websocket")
                reconnection = true
                timeout = 10000
            }

            socket = IO.socket(serverUrl, ioOptions)

            socket?.on(Socket.EVENT_CONNECT) {
                status = "Connected as $role"
                appendLog("Connected: ${socket?.id()}")
                if (role == "source") {
                    socket?.emit("registerSource")
                } else {
                    socket?.emit("registerClient")
                }
            }

            socket?.on("status") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val message = payload?.optString("message") ?: "No status message"
                status = message
                appendLog(message)
            }

            socket?.on("relayRequest") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val from = payload?.optString("from") ?: "unknown"
                appendLog("Relay request from $from")
                socket?.emit(
                    "acceptRelay",
                    JSONObject().apply {
                        put("sourceId", socket?.id())
                        put("clientId", from)
                    }
                )
            }

            socket?.on("data") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val from = payload?.optString("from") ?: "unknown"
                val data = payload?.optString("payload") ?: ""
                appendLog("Data from $from: $data")
            }

            socket?.on("connectedPeer") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val peerId = payload?.optString("peerId") ?: "unknown"
                val type = payload?.optString("type") ?: "unknown"
                appendLog("Connected peer is $peerId ($type)")
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                appendLog("Disconnected from server")
                status = "Disconnected"
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "Unknown error"
                appendLog("Connection error: $error")
                status = "Connection error"
            }

            socket?.connect()
        } catch (e: Exception) {
            status = "Failed to connect"
            appendLog("Error: ${e.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F5F9))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Test Relay App",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Prototype demo for source/client internet relay",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC7D2FE)
                )
            }
        }

        Text(text = "Server URL")
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Enter server URL") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    selectedRole = "source"
                    connectSocket("source")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Source")
            }

            Button(
                onClick = {
                    selectedRole = "client"
                    connectSocket("client")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Client")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Role: $selectedRole", fontWeight = FontWeight.SemiBold)
                Text(text = "Status: $status")
            }
        }

        Button(
            onClick = {
                val target = socket?.id()
                if (target != null) {
                    socket?.emit(
                        "sendData",
                        JSONObject().apply {
                            put("target", target)
                            put("data", "Test packet from $selectedRole")
                        }
                    )
                    appendLog("Sent test packet to self")
                } else {
                    appendLog("No active connection")
                }
            },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Send test packet")
        }

        Text(
            text = "Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .verticalScroll(rememberScrollState())
                .background(Color(0xFF101828))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = logText,
                color = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
fun TestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
