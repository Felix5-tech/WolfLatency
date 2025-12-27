// Created by: Luiz Felipe Cantanhede Cristino
// Modified by: Guilherme Oliveira Rolim Silva, Marco Antonio Tronco Felix
// Institution: GTA, COPPE, UFRJ
// Project: WolfServer - 5G/LTE Network Analysis (Server Side)

package com.example.wolfserver

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.example.wolfserver.ui.theme.WolfServerTheme
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.Inet6Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    // Variáveis de Estado (Para atualizar a UI)
    private val nsa = mutableStateOf(false)
    private val latitudeState = mutableStateOf(0.0)
    private val longitudeState = mutableStateOf(0.0)
    private val cellIdState = mutableStateOf("N/A")
    private val signalDbmState = mutableStateOf("N/A")
    private val signalLevelState = mutableStateOf("N/A")
    private val rsrqState = mutableStateOf("-")
    private val rssnrState = mutableStateOf("-") // SINR
    private val timestampState = mutableStateOf(" ")

    private val currentTopicState = mutableStateOf("")
    private val scope = CoroutineScope(Dispatchers.IO)

    // Listener de GPS para manter os dados frescos
    private var locationListener: LocationListener? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestAllPermissions()
        start5GDetection(this)

        // --- NOVO: Força o GPS a acordar ---
        startActiveLocationUpdates()

        startContinuousCollection()
        startIpBroadcaster()

        setContent {
            val context = LocalContext.current
            val sharedPreferences = remember { context.getSharedPreferences("WolfServerPrefs", Context.MODE_PRIVATE) }
            val savedTopic = remember { sharedPreferences.getString("ntfy_topic", "") ?: "" }
            val topicState = remember { mutableStateOf(TextFieldValue(savedTopic)) }
            currentTopicState.value = topicState.value.text

            var currentIp by remember { mutableStateOf("Fetching...") }
            LaunchedEffect(Unit) { currentIp = getDeviceIPv6() ?: "No IPv6 Found" }

            WolfServerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var isLocked by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxSize()) {

                        // --- CAMADA 1: CONTEÚDO ---
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize()
                                .alpha(if (isLocked) 0.3f else 1.0f),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // --- CARD DE CONFIGURAÇÃO (SERVER) ---
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0)) // Cinza claro padrão
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Server Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("Ntfy Topic ID:", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                    BasicTextField(
                                        value = topicState.value,
                                        onValueChange = {
                                            topicState.value = it
                                            currentTopicState.value = it.text
                                            sharedPreferences.edit().putString("ntfy_topic", it.text).apply()
                                        },
                                        textStyle = TextStyle(color = Color.Black),
                                        modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color.LightGray).padding(6.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Device IPv6:", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                            Text(text = currentIp, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF006400))
                                        }
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("IPv6 Address", currentIp)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "IP Copied!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = Color.Gray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                currentIp = getDeviceIPv6() ?: "No IPv6"
                                                sendIpToNtfy(currentTopicState.value, currentIp)
                                                withContext(Dispatchers.Main) { Toast.makeText(context, "IP Sent!", Toast.LENGTH_SHORT).show() }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("Force Send IPv6 to Ntfy", fontSize = 12.sp) }
                                }
                            }

                            // LazyColumn ocupando o resto da tela
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    // --- STATUS CARD (Igualzinho ao Cliente) ---
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Latitude: ${latitudeState.value}")
                                            Text("Longitude: ${longitudeState.value}")
                                            Text("Cell ID: ${cellIdState.value}")
                                            Text("RSRP (Signal): ${signalDbmState.value} dBm")
                                            Text("SINR (Noise) ${rssnrState.value} dB")
                                            Text("RSRQ: ${rsrqState.value} dB")
                                            Text("Timestamp: ${timestampState.value}")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // --- RESULT TEXT (No lugar do Ping, mostramos o Status da Rede) ---
                                    val resultText = if(nsa.value) "Network: 5G NSA (Detected)" else "Network: LTE / Legacy"

                                    Text(
                                        text = resultText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (nsa.value) Color(0xFF006400) else MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(top = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // --- CAMADA 2: BLOQUEIO ---
                        if (isLocked) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent).pointerInput(Unit) { detectTapGestures { Toast.makeText(context, "Screen is Locked", Toast.LENGTH_SHORT).show() } }) {
                                Text(text = "LOCKED\nLong press button to unlock", color = Color.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }

                        // --- CAMADA 3: BOTÕES ---
                        var isHolding by remember { mutableStateOf(false) }
                        LaunchedEffect(isHolding) {
                            if (isHolding) {
                                val actionName = if (isLocked) "UNLOCK" else "LOCK"
                                Toast.makeText(context, "Hold 3 seconds to $actionName", Toast.LENGTH_SHORT).show()
                                delay(3000)
                                isLocked = !isLocked
                                val finalMsg = if (isLocked) "LOCKED" else "UNLOCKED"
                                Toast.makeText(context, finalMsg, Toast.LENGTH_SHORT).show()
                                isHolding = false
                            }
                        }

                        if (!isLocked) {
                            FloatingActionButton(
                                onClick = { shareCsvFile() },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 90.dp, end = 24.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) { Icon(Icons.Default.Share, contentDescription = "Share CSV") }
                        }

                        FloatingActionButton(
                            onClick = { },
                            containerColor = when {
                                isHolding && !isLocked -> Color.Yellow
                                isHolding && isLocked -> Color.Yellow
                                isLocked -> Color.Red
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes[0].consume()
                                        isHolding = true
                                        val reason = waitForUpOrCancellation()
                                        isHolding = false
                                        if (reason != null) { }
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = if (isHolding) Color.Black else Color.White)                        }
                    }
                }
            }
        }
    }

    private fun startContinuousCollection() {
        scope.launch {
            while (true) {
                captureCellInfo(this@MainActivity)
                delay(1000)
            }
        }
    }

    // --- NOVO: Função para ativar o GPS de verdade ---
    private fun startActiveLocationUpdates() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // Atualiza o estado da UI imediatamente quando o GPS mudar
                    latitudeState.value = location.latitude
                    longitudeState.value = location.longitude
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Solicita atualizações a cada 1 segundo (1000ms) e 0 metros
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener!!
            )

            // Tenta ativar também via Rede para ter backup
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000L,
                0f,
                locationListener!!
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Para o GPS ao fechar o app para economizar bateria
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener?.let { locationManager.removeUpdates(it) }
        stop5GDetection(this)
    }

    private fun startIpBroadcaster() {
        scope.launch {
            while (true) {
                val topic = currentTopicState.value
                val ip = getDeviceIPv6()
                if (topic.isNotEmpty() && ip != null) sendIpToNtfy(topic, ip)
                delay(300000)
            }
        }
    }

    private fun getDeviceIPv6(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (inetAddress is Inet6Address && !inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress) {
                        val ip = inetAddress.hostAddress
                        val delimiter = ip.indexOf('%')
                        return if (delimiter < 0) ip else ip.substring(0, delimiter)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun sendIpToNtfy(topic: String, ip: String) {
        if (topic.isEmpty()) return
        val client = OkHttpClient()
        val requestBody = ip.toRequestBody("text/plain".toMediaType())
        val request = Request.Builder().url("https://ntfy.sh/$topic").post(requestBody).build()
        try {
            client.newCall(request).execute().close()
            Log.d("WolfServer", "IP sent to $topic: $ip")
        } catch (e: IOException) { Log.e("WolfServer", "Failed to send to Ntfy", e) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureCellInfo(context: Context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            timestampState.value = "Permission Denied"
            return
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val timeStampShort: String = SimpleDateFormat("HH:mm:ss").format(Date())
        val fullTimeStamp: String = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Date())
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            timestampState.value = "GPS OFF"
            return
        }

        val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo
        if (cellInfoList.isNullOrEmpty()) {
            cellIdState.value = "Searching..."
            return
        }

        cellInfoList.forEach { cellInfo ->
            if (cellInfo.isRegistered) {
                // Força a UI a atualizar na Thread Principal
                runOnUiThread {
                    // Tenta pegar a localização do Listener ativo (mais recente)
                    // Se não, tenta lastKnown
                    val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                    latitudeState.value = location?.latitude ?: latitudeState.value
                    longitudeState.value = location?.longitude ?: longitudeState.value

                    signalDbmState.value = cellInfo.cellSignalStrength.dbm.toString()
                    signalLevelState.value = cellInfo.cellSignalStrength.level.toString()
                    timestampState.value = timeStampShort
                }

                val data = mutableListOf<Any>(fullTimeStamp)
                data.add(latitudeState.value)
                data.add(longitudeState.value)
                data.add(signalDbmState.value)
                data.add(signalLevelState.value)

                // Usamos variáveis locais para preencher o CSV corretamente na thread de background
                var l_rssnr = "N/A"
                var l_rsrq = "N/A"
                var l_cellid = "N/A"

                when (cellInfo) {
                    is CellInfoGsm -> {
                        val id = cellInfo.cellIdentity
                        l_cellid = id.cid.toString()

                        id.mccString?.let { data.add(it) }
                        id.mncString?.let { data.add(it) }
                        data.add(id.cid); data.add(id.lac); data.add(5)
                    }
                    is CellInfoLte -> {
                        val id = cellInfo.cellIdentity
                        l_cellid = id.ci.toString()
                        l_rssnr = cellInfo.cellSignalStrength.rssnr.toString()
                        l_rsrq = cellInfo.cellSignalStrength.rsrq.toString()

                        id.mccString?.let { data.add(it) }
                        id.mncString?.let { data.add(it) }
                        data.add(id.ci); data.add(id.tac); data.add(if (nsa.value) 10 else 6)
                        data.add(cellInfo.cellSignalStrength.rsrq); data.add(cellInfo.cellSignalStrength.rssnr)
                    }
                    is CellInfoNr -> {
                        val id = cellInfo.cellIdentity as CellIdentityNr
                        val sig = cellInfo.cellSignalStrength as CellSignalStrengthNr
                        l_cellid = id.nci.toString()
                        l_rssnr = sig.ssSinr.toString()
                        l_rsrq = sig.ssRsrq.toString()

                        id.mccString?.let { data.add(it) }
                        id.mncString?.let { data.add(it) }
                        data.add(id.nci); data.add(id.tac); data.add(9)
                        data.add(sig.ssRsrq); data.add(sig.ssSinr); data.add(id.nrarfcn)
                    }
                    else -> {}
                }

                // Atualiza o resto da UI na thread principal
                runOnUiThread {
                    cellIdState.value = l_cellid
                    rssnrState.value = l_rssnr
                    rsrqState.value = l_rsrq
                }

                saveCellInfoToCSV(data)
                return
            }
        }
    }

    @Synchronized
    private fun saveCellInfoToCSV(cellInfoData: List<Any>) {
        val csvFileName = "serverdata.csv"
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val csvFile = File(baseDir, csvFileName)
        try {
            if (!baseDir.exists()) baseDir.mkdirs()
            val sharedPreferences = getSharedPreferences("WolfServerPrefs", Context.MODE_PRIVATE)
            var currentCount = sharedPreferences.getInt("count", 0)
            if (!csvFile.exists()) {
                val writer = BufferedWriter(FileWriter(csvFile, true))
                val columnNames = "Sequence, Timestamp, Latitude, Longitude, Signal_dbm, Signal_level, MCC, MNC, CellId, Tac/Lac, Mobile_Network, RSRQ, RSSNR, NRARFCN"
                writer.write("$columnNames\n")
                writer.close()
                currentCount = 0
            }
            val writer = BufferedWriter(FileWriter(csvFile, true))
            val newCount = currentCount + 1
            sharedPreferences.edit().putInt("count", newCount).apply()
            val csvLine = cellInfoData.joinToString(",") { it.toString() }
            writer.write("$newCount,$csvLine")
            writer.newLine()
            writer.close()
        } catch (e: IOException) { e.printStackTrace() }
    }

    private fun shareCsvFile() {
        val csvFileName = "serverdata.csv"
        val exportName = "serverdata_export.csv"
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val sourceFile = File(baseDir, csvFileName)
        val destFile = File(baseDir, exportName)
        if (sourceFile.exists()) {
            try {
                sourceFile.copyTo(destFile, overwrite = true)
                val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", destFile)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/csv"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, "Share Server Data"))
            } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
        } else { Toast.makeText(this, "No data yet!", Toast.LENGTH_SHORT).show() }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = android.net.Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivityForResult(intent, 2296)
                } catch (e: Exception) {
                    val intent = Intent().apply { action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION }
                    startActivityForResult(intent, 2296)
                }
                Toast.makeText(this, "Allow 'All Files Access'", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var telephonyCallback: TelephonyCallback? = null
    @RequiresApi(Build.VERSION_CODES.S)
    fun start5GDetection(context: Context) {
        if (telephonyCallback == null) {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                    when (displayInfo.overrideNetworkType) {
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> nsa.value = true
                        else -> nsa.value = false
                    }
                }
            }
            telephonyManager.registerTelephonyCallback(context.mainExecutor, telephonyCallback!!)
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    fun stop5GDetection(context: Context) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        telephonyCallback = null
    }
}