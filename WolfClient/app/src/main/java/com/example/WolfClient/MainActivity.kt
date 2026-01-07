// Created by: Luiz Felipe Cantanhede Cristino
// Modified by: Guilherme Oliveira Rolim Silva, Marco Antonio Tronco Felix
// Institution: GTA, COPPE, UFRJ

package com.example.wolfclient

// Imports do Android Sistema
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.content.Intent
import androidx.core.content.FileProvider
import android.net.Uri
import android.provider.Settings
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager

// --- NOVOS IMPORTS PARA TELA CHEIA (IMERSIVA) ---
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
// ------------------------------------------------

// Imports de Telefonia
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

// Imports de Rede e IO
import com.opencsv.bean.CsvBindByName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

// --- IMPORTS DE UI (JETPACK COMPOSE) ---
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.* import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.* import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext // Necessário para pegar a Activity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.wolfclient.ui.theme.WolfClientTheme

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    // Declaration of state variables
    private val resultFromRequestState = mutableStateOf<String?>(null)
    private val latitudeState = mutableStateOf(0.0)
    private val longitudeState = mutableStateOf(0.0)
    private val urlState = mutableStateOf(TextFieldValue())
    private val nsa = mutableStateOf(false)
    private val transportation = mutableStateOf(1)
    private val timeStampValue = mutableStateOf(" ")
    private val cellIdState = mutableStateOf<Any>(0)

    // Listener de GPS Nativo
    private var locationListener: LocationListener? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkStoragePermission()
        start5GDetection(this)

        setContent {
            val context = LocalContext.current // Pega o contexto atual
            val sharedPreferences = remember { context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE) }
            val savedTopic = remember { sharedPreferences.getString("ntfy_topic", "") ?: "" }
            val topicState = remember { mutableStateOf(TextFieldValue(savedTopic)) }
            val coroutineScope = rememberCoroutineScope()

            WolfClientTheme {
                val gpsMessage by resultFromRequestState
                val latitude by latitudeState
                val longitude by longitudeState
                val timestamp by timeStampValue
                val cellId by cellIdState
                val isRunning = remember { mutableStateOf(false) }

                var selectedItem by remember { mutableStateOf("On foot") }
                var expanded by remember { mutableStateOf(false) }
                val items = listOf("On foot", "Bicycle", "Motorcycle", "Car", "Bus", "Train", "VLT", "Subway", "Barca")
                val tipoTransporteMap = mapOf("On foot" to 1, "Bicycle" to 2, "Motorcycle" to 3, "Car" to 4, "Bus" to 5, "Train" to 6, "VLT" to 7, "Subway" to 8, "Barca" to 9)

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var isLocked by remember { mutableStateOf(false) }

                    // --- LÓGICA NOVA: ESCONDER BARRAS DO SISTEMA ---
                    // Pega a janela da Activity atual
                    val window = (context as? Activity)?.window
                    // Cria o controlador de UI
                    val insetsController = remember(window) {
                        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                    }

                    // Reage quando a variável isLocked muda
                    LaunchedEffect(isLocked) {
                        if (isLocked) {
                            // Se bloqueado: Esconde barras de sistema (Status e Navegação)
                            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                            // Define que elas só aparecem se o usuário arrastar a borda (Swipe)
                            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            // Se desbloqueado: Mostra tudo normal
                            insetsController?.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                    // ----------------------------------------------

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize()
                                .alpha(if (isLocked) 0.3f else 1.0f),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // --- CARD DE CONFIGURAÇÃO ---
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Server Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("Ntfy Topic ID:", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                    BasicTextField(
                                        value = topicState.value,
                                        onValueChange = {
                                            topicState.value = it
                                            sharedPreferences.edit().putString("ntfy_topic", it.text).apply()
                                        },
                                        textStyle = TextStyle(color = Color.Black),
                                        modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color.LightGray).padding(6.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            fetchIpFromNtfy(topicState.value.text) { ip ->
                                                if (ip != null) {
                                                    runOnUiThread {
                                                        urlState.value = TextFieldValue(ip)
                                                        Toast.makeText(this@MainActivity, "IP Updated: $ip", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    runOnUiThread { Toast.makeText(this@MainActivity, "Failed to fetch IP", Toast.LENGTH_SHORT).show() }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("Fetch IP from Ntfy", fontSize = 12.sp) }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("Target URL (IPv6):", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                    BasicTextField(
                                        value = urlState.value,
                                        onValueChange = { urlState.value = it },
                                        textStyle = TextStyle(color = Color.Black),
                                        modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color.LightGray).padding(6.dp)
                                    )
                                }
                            }

                            // --- BLOCO B: TRANSPORTE ---
                            Card(
                                modifier = Modifier
                                    .clickable { if (!isLocked) expanded = !expanded }
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Transport Mode", fontSize = 12.sp, color = Color.DarkGray)
                                    Text(text = selectedItem, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                }
                            }

                            if (expanded) {
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    offset = DpOffset(0.dp, 0.dp),
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    items.forEach { label ->
                                        DropdownMenuItem(
                                            text = { Text(text = label) },
                                            onClick = {
                                                selectedItem = label
                                                expanded = false
                                                val valueInteger = tipoTransporteMap[selectedItem]
                                                if (valueInteger != null) transportation.value = valueInteger
                                            }
                                        )
                                    }
                                }
                            }

                            // --- BLOCO C: BOTÃO START ---
                            Button(
                                onClick = {
                                    if (!isRunning.value) {
                                        startActiveLocationUpdates()
                                        isRunning.value = true
                                        coroutineScope.launch {
                                            while (true) {
                                                val url = urlState.value.text
                                                if (url.isNotEmpty()) {
                                                    requestLocationAndFetchData(url)
                                                }
                                                delay(1000)
                                            }
                                        }
                                    }
                                },
                                enabled = !isRunning.value,
                                modifier = Modifier.fillMaxWidth().height(50.dp).padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning.value) Color.Gray else MaterialTheme.colorScheme.primary)
                            ) {
                                Text(text = if (isRunning.value) "RUNNING... (Collecting Data)" else "START CONTINUOUS REQUEST", fontWeight = FontWeight.Bold)
                            }

                            // --- BLOCO D: MONITORAMENTO ---
                            ScrollableContent(latitude, longitude, gpsMessage ?: "Ready to start.", timestamp, cellId)
                        }

                        // --- CAMADA 2: ESCUDO DE BLOQUEIO ---
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
                                Toast.makeText(context, "Hold for 3 seconds to $actionName", Toast.LENGTH_SHORT).show()
                                delay(3000)
                                isLocked = !isLocked
                                val finalMsg = if (isLocked) "SCREEN LOCKED" else "UNLOCKED"
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
                            Icon(imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock Button", tint = if (isHolding) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener?.let { locationManager.removeUpdates(it) }
        stop5GDetection(this)
    }

    private fun startActiveLocationUpdates() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    latitudeState.value = location.latitude
                    longitudeState.value = location.longitude
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener!!)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener!!)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getConnectedCellId(context: Context): MutableList<Any>? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo
            if (cellInfoList != null) {
                for (cellInfo in cellInfoList) {
                    if (cellInfo.isRegistered) {
                        val data = mutableListOf<Any>(cellInfo.cellSignalStrength.dbm, cellInfo.cellSignalStrength.level)
                        when (cellInfo) {
                            is CellInfoGsm -> {
                                val id = cellInfo.cellIdentity
                                id.mccString?.let { data.add(it) }; id.mncString?.let { data.add(it) }
                                data.add(id.cid); data.add(id.lac); data.add(5); return data
                            }
                            is CellInfoLte -> {
                                val id = cellInfo.cellIdentity
                                id.mccString?.let { data.add(it) }; id.mncString?.let { data.add(it) }
                                data.add(id.ci); data.add(id.tac); data.add(if (nsa.value) 10 else 6)
                                data.add(cellInfo.cellSignalStrength.rsrq); data.add(cellInfo.cellSignalStrength.rssnr); return data
                            }
                            is CellInfoWcdma -> {
                                val id = cellInfo.cellIdentity
                                id.mccString?.let { data.add(it) }; id.mncString?.let { data.add(it) }
                                data.add(id.cid); data.add(id.lac); data.add(7); return data
                            }
                            is CellInfoCdma -> {
                                val id = cellInfo.cellIdentity
                                data.add(""); data.add(""); data.add(id.basestationId); data.add(""); data.add(8); return data
                            }
                            is CellInfoNr -> {
                                val id = cellInfo.cellIdentity as CellIdentityNr
                                val sig = cellInfo.cellSignalStrength as CellSignalStrengthNr
                                id.mccString?.let { data.add(it) }; id.mncString?.let { data.add(it) }
                                data.add(id.nci); data.add(id.tac); data.add(9)
                                data.add(sig.ssRsrq); data.add(sig.ssSinr); data.add(id.nrarfcn); return data
                            }
                            else -> { }
                        }
                    }
                }
            }
        }
        return null
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

    @Synchronized
    private fun saveDataToCSV(data: DataModel, dataCellId: MutableList<Any>) {
        val csvFileName = "clientdata.csv"
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val csvFile = File(baseDir, csvFileName)
        try {
            if (!baseDir.exists()) baseDir.mkdirs()
            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            var currentCount = sharedPreferences.getInt("count", 0)
            if (!csvFile.exists() || csvFile.readLines().none { it.startsWith("Sequence") }) {
                val columnNames = "Sequence,Transport,Timestamp,Latency,Http_Latency,TTL,Hops,Latitude,Longitude,Signal_dbm,Signal_level,MCC,MNC,CellId,Tac/Lac,Mobile_Network,RSRQ,RSSNR,NRARFCN"
                val writer = BufferedWriter(FileWriter(csvFile, true))
                writer.write("$columnNames\n")
                writer.close()
                currentCount = 0
            }
            val newCount = currentCount + 1
            sharedPreferences.edit().putInt("count", newCount).apply()
            if (isValidData(data)) {
                val csvLine = "$newCount, ${data.transport}, ${data.timestamp}, ${data.latency}, ${data.httpLatency}, ${data.ttl}, ${data.hops}, ${data.latitude}, ${data.longitude}," + dataCellId.joinToString(",")
                val writer = BufferedWriter(FileWriter(csvFile, true))
                writer.write(csvLine); writer.newLine(); writer.close()
            }
        } catch (e: IOException) { e.printStackTrace() }
    }

    private fun isValidData(data: DataModel): Boolean {
        return (data.transport > 0 && data.timestamp.isNotBlank() && data.cellId != 0 && data.latitude != 0.0 && data.longitude != 0.0)
    }

    // --- LOOP PRINCIPAL ATUALIZADO ---
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestLocationAndFetchData(targetUrl: String) {
        if (latitudeState.value == 0.0) {
            resultFromRequestState.value = "Waiting for GPS Fix..."
            return
        }

        withContext(Dispatchers.IO) {
            val cellIdInfoList = getConnectedCellId(this@MainActivity)
            if (cellIdInfoList != null) {
                withContext(Dispatchers.Main) { cellIdState.value = cellIdInfoList }
            }

            val timeStamp: String = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Date())
            withContext(Dispatchers.Main) { timeStampValue.value = timeStamp }

            // 1. Ping IPv6 (ICMP)
            val (latency, ttl) = pingHostIPv6(targetUrl)

            // 2. HTTP Request (Aplicação)
            val httpLatency = measureHttpLatency(targetUrl)

            // 3. Hops
            val hops = calculateHops(targetUrl)

            // Atualiza UI com ambos os resultados
            val resultString = if (latency >= 0) {
                "Ping: ${latency}ms | HTTP: ${httpLatency}ms\n(TTL=$ttl, Hops=$hops)"
            } else {
                "Ping Failed | HTTP: ${httpLatency}ms"
            }

            withContext(Dispatchers.Main) { resultFromRequestState.value = resultString }

            cellIdInfoList?.let { infoList ->
                val cellIdReal = if (infoList.size > 4) infoList[4] else 0
                val currentLat = latitudeState.value ?: 0.0
                val currentLon = longitudeState.value ?: 0.0

                val dataModel = DataModel(
                    transportation.value, timeStamp, cellIdReal,
                    latency, httpLatency, ttl, hops,
                    currentLat, currentLon
                )
                saveDataToCSV(dataModel, infoList)
            }
        }
    }

    // --- NOVA FUNÇÃO DE MEDIÇÃO HTTP ---
    private fun measureHttpLatency(rawUrl: String): Long {
        var cleanUrl = rawUrl.trim()

        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            if (cleanUrl.contains(":") && !cleanUrl.startsWith("[")) {
                cleanUrl = "http://[$cleanUrl]:8080"
            } else {
                cleanUrl = "http://$cleanUrl:8080"
            }
        } else if (!cleanUrl.contains(":8080")) {
            cleanUrl = "$cleanUrl:8080"
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(cleanUrl).build()
        var start = 0L
        var end = 0L

        return try {
            start = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            end = System.currentTimeMillis()
            response.close()
            end - start
        } catch (e: Exception) {
            -1
        }
    }

    @Composable
    fun ScrollableContent(lat: Double?, lon: Double?, res: String, time: String, cid: Any) {
        val list = cid as? List<*>
        val signalDbm = list?.getOrNull(0) ?: "N/A"
        val cellId = list?.getOrNull(4) ?: "N/A"
        val rsrq = list?.getOrNull(7) ?: "-"
        val sinr = list?.getOrNull(8) ?: "-"

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Latitude: $lat"); Text("Longitude: $lon"); Text("Cell ID: $cellId")
                        Text("RSRP (Signal): $signalDbm dBm"); Text("SINR (Noise) $sinr dB"); Text("RSRQ: $rsrq dB")
                        Text("Timestamp: $time")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = res, style = MaterialTheme.typography.bodyLarge, color = if (res.contains("Failed") || res.contains("-1ms")) Color.Red else MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold)
            }
        }
    }

    // --- FUNÇÃO AUXILIAR PARA LIMPAR URL (Compatível com IPv4 e IPv6) ---
    private fun extractHostFromUrl(url: String): String {
        var tempUrl = url.trim()
            .replace("http://", "")
            .replace("https://", "")

        // Remove caminhos (ex: /index.html)
        if (tempUrl.contains("/")) {
            tempUrl = tempUrl.split("/")[0]
        }

        return if (tempUrl.startsWith("[")) {
            // Caso IPv6 com colchetes: [2001:db8::1]:8080 -> 2001:db8::1
            tempUrl.substringAfter("[").substringBefore("]")
        } else {
            // Caso sem colchetes (IPv4:Porta ou Domínio:Porta)
            // Se tiver APENAS UM ":", é porta. Se tiver vários, é IPv6 puro.
            if (tempUrl.count { it == ':' } == 1) {
                tempUrl.split(":")[0]
            } else {
                tempUrl // IPv6 puro ou domínio simples
            }
        }
    }

    private fun pingHostIPv6(url: String): Pair<Long, Int> {
        var delay = -1L
        var ttl = -1
        try {
            // USANDO A NOVA LÓGICA DE EXTRAÇÃO
            val host = extractHostFromUrl(url)

            // Log para debug
            Log.d("WolfClient", "Ping Host: $host")

            val process = Runtime.getRuntime().exec("ping6 -c 1 -w 2 $host")
            val exitValue = process.waitFor()

            if (exitValue == 0) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains("time=")) {
                        // Lógica mais robusta para pegar o tempo
                        val timePart = line!!.substringAfter("time=").split(" ")[0]
                        delay = timePart.toFloat().toLong()
                    }
                    if (line!!.contains("ttl=")) {
                        val ttlPart = line!!.substringAfter("ttl=").split(" ")[0]
                        ttl = ttlPart.toInt()
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(delay, ttl)
    }

    private fun calculateHops(url: String): Int {
        val host = extractHostFromUrl(url)

        for (ttl in 1..30) {
            try {
                val cmd = "ping6 -c 1 -w 1 -t $ttl $host"
                val process = Runtime.getRuntime().exec(cmd)
                val exitValue = process.waitFor()

                if (exitValue == 0) {
                    return ttl
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return -1
    }

    private fun fetchIpFromNtfy(topicRaw: String, callback: (String?) -> Unit) {
        val topic = topicRaw.trim()
        if (topic.isEmpty()) { callback(null); return }
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("https://ntfy.sh/$topic/raw?since=all&limit=5&poll=1").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { callback(null); return }
                    try {
                        val rawBody = it.body?.string() ?: ""
                        val ip = rawBody.lines().filter { l -> l.contains(":") }.lastOrNull { l -> l.isNotBlank() }?.trim()
                        callback(ip)
                    } catch (e: Exception) { callback(null) }
                }
            }
        })
    }

    private fun shareCsvFile() {
        val csvFileName = "clientdata.csv"
        val exportName = "clientdata_export.csv"
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val sourceFile = File(baseDir, csvFileName); val destFile = File(baseDir, exportName)
        if (sourceFile.exists()) {
            try {
                sourceFile.copyTo(destFile, overwrite = true)
                val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", destFile)
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                startActivity(Intent.createChooser(intent, "Share Data"))
            } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
        } else { Toast.makeText(this, "No data yet!", Toast.LENGTH_SHORT).show() }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        addCategory("android.intent.category.DEFAULT")
                        data = Uri.parse("package:${applicationContext.packageName}")
                    }
                    startActivityForResult(intent, 2296)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, 2296)
                }
                Toast.makeText(this, "Allow 'All Files Access'", Toast.LENGTH_LONG).show()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
        }
    }

    data class DataModel(
        @CsvBindByName(column = "Transportation") val transport: Int,
        @CsvBindByName(column = "Timestamp") val timestamp: String,
        @CsvBindByName(column = "Cellid") val cellId: Any,
        @CsvBindByName(column = "Latência") val latency: Long,
        // Novo campo para latência HTTP
        @CsvBindByName(column = "Http_Latency") val httpLatency: Long,
        @CsvBindByName(column = "TTL") val ttl: Int,
        @CsvBindByName(column = "Hops") val hops: Int,
        @CsvBindByName(column = "Latitude") val latitude: Double,
        @CsvBindByName(column = "Longitude") val longitude: Double
    )
}