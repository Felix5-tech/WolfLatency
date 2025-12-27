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
import androidx.compose.material.icons.filled.LockOpen // <--- MUDANÇA: Ícone de cadeado aberto
import androidx.compose.material3.* import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    // REMOVIDO: fusedLocationClient

    private val resultFromRequestState = mutableStateOf<String?>(null)
    private val latitudeState = mutableStateOf(0.0) // Inicializa com 0.0
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
        // Mantém a tela ligada
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkStoragePermission()

        start5GDetection(this)

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val sharedPreferences = remember {
                context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            }

            val savedTopic = remember {
                sharedPreferences.getString("ntfy_topic", "") ?: ""
            }

            val topicState = remember { mutableStateOf(TextFieldValue(savedTopic)) }
            val coroutineScope = rememberCoroutineScope()

            WolfClientTheme {
                // Variáveis de estado da UI
                val gpsMessage by resultFromRequestState
                val latitude by latitudeState
                val longitude by longitudeState
                val timestamp by timeStampValue
                val cellId by cellIdState

                val isRunning = remember { mutableStateOf(false) }

                // Dropdown setup
                var selectedItem by remember { mutableStateOf("On foot") }
                var expanded by remember { mutableStateOf(false) }
                val items = listOf("On foot", "Bicycle", "Motorcycle", "Car", "Bus", "Train", "VLT", "Subway", "Barca")
                val tipoTransporteMap = mapOf(
                    "On foot" to 1, "Bicycle" to 2, "Motorcycle" to 3, "Car" to 4,
                    "Bus" to 5, "Train" to 6, "VLT" to 7, "Subway" to 8, "Barca" to 9
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Estado do Bloqueio de Tela
                    var isLocked by remember { mutableStateOf(false) }

                    // Box principal permite sobrepor elementos (Conteúdo + Bloqueio + Botão)
                    Box(modifier = Modifier.fillMaxSize()) {

                        // --- CAMADA 1: O CONTEÚDO DO APP ---
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize()
                                // Se bloqueado, deixa o fundo meio transparente (efeito visual)
                                .alpha(if (isLocked) 0.3f else 1.0f),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            // --- BLOCO A: CONFIGURAÇÃO DE SERVIDOR ---
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Server Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Tópico Ntfy
                                    Text("Ntfy Topic ID:", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                    BasicTextField(
                                        value = topicState.value,
                                        onValueChange = {
                                            topicState.value = it
                                            sharedPreferences.edit().putString("ntfy_topic", it.text).apply()
                                        },
                                        textStyle = TextStyle(color = Color.Black),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White)
                                            .border(1.dp, Color.LightGray)
                                            .padding(6.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Botão Buscar IP
                                    Button(
                                        onClick = {
                                            fetchIpFromNtfy(topicState.value.text) { ip ->
                                                if (ip != null) {
                                                    runOnUiThread {
                                                        urlState.value = TextFieldValue(ip)
                                                        Toast.makeText(this@MainActivity, "IP Updated: $ip", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    runOnUiThread {
                                                        Toast.makeText(this@MainActivity, "Failed to fetch IP", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Fetch IP from Ntfy", fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // URL Alvo
                                    Text("Target URL (IPv6):", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                    BasicTextField(
                                        value = urlState.value,
                                        onValueChange = { urlState.value = it },
                                        textStyle = TextStyle(color = Color.Black),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White)
                                            .border(1.dp, Color.LightGray)
                                            .padding(6.dp)
                                    )
                                }
                            }

                            // --- BLOCO B: TRANSPORTE ---
                            Card(
                                modifier = Modifier
                                    .clickable { if (!isLocked) expanded = !expanded } // Só abre se não estiver bloqueado
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

                                        // 1. Inicia o GPS Nativo (Força Bruta - Igual Servidor)
                                        startActiveLocationUpdates()

                                        isRunning.value = true

                                        // 2. Inicia o loop
                                        coroutineScope.launch {
                                            while (true) {
                                                val url = urlState.value.text
                                                if (url.isNotEmpty()) {
                                                    // Roda a coleta usando a localização atualizada em background
                                                    requestLocationAndFetchData(url)
                                                }
                                                // Loop limpo: sem toasts aqui
                                                delay(1000)
                                            }
                                        }
                                    }
                                },
                                enabled = !isRunning.value, // Desabilita visualmente se já roda
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunning.value) Color.Gray else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (isRunning.value) "RUNNING... (Collecting Data)" else "START CONTINUOUS REQUEST",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // --- BLOCO D: MONITORAMENTO ---
                            ScrollableContent(
                                latitude,
                                longitude,
                                gpsMessage ?: "Ready to start.",
                                timestamp,
                                cellId
                            )
                        }

                        // --- CAMADA 2: ESCUDO DE BLOQUEIO ---
                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                                    // Captura todos os toques para nada passar para baixo
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            // Feedback opcional se tocar na tela bloqueada
                                            Toast.makeText(context, "Screen is Locked", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                // Aviso visual no centro da tela
                                Text(
                                    text = "LOCKED\nLong press button to unlock",
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.align(Alignment.Center),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        // Estado para mudar a cor do botão enquanto segura
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

                        // --- BOTÃO DE COMPARTILHAR ---
                        if (!isLocked) {
                            FloatingActionButton(
                                onClick = { shareCsvFile() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 90.dp, end = 24.dp), // Fica acima do outro botão
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share CSV")
                            }
                        }

                        FloatingActionButton(
                            onClick = { /* Vazio, controlado pelo pointerInput abaixo */ },
                            containerColor = when {
                                isHolding && !isLocked -> Color.Yellow // Amarelo enquanto segura para travar
                                isHolding && isLocked -> Color.Yellow // Amarelo enquanto segura para destravar
                                isLocked -> Color.Red     // Vermelho se bloqueado
                                else -> MaterialTheme.colorScheme.primary // Azul se livre
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            // A. Espera o dedo tocar (DOWN)
                                            awaitPointerEvent().changes[0].consume() // Consome o toque inicial
                                            isHolding = true // Ativa o LaunchedEffect acima

                                            // B. Espera o dedo levantar (UP) ou sair do botão
                                            val reason = waitForUpOrCancellation()

                                            // C. Dedo levantou: Reseta o estado
                                            isHolding = false // Isso cancela o LaunchedEffect imediatamente (se não tiver acabado os 3s)

                                            if (reason != null) {
                                                // Se soltou antes de completar a ação, o LaunchedEffect foi cancelado automaticamente.
                                            }
                                        }
                                    }
                                }
                        ) {
                            // MUDANÇA: Ícone muda para LockOpen (Cadeado Aberto)
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock Button",
                                tint = if (isHolding) Color.Black else Color.White
                            )
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

        // Limpa o GPS ao sair
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener?.let { locationManager.removeUpdates(it) }

        stop5GDetection(this)
    }

    // --- NOVA FUNÇÃO DE GPS (FORÇA BRUTA) ---
    // Isso garante atualizações a cada 1 segundo, mesmo parado
    private fun startActiveLocationUpdates() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // Atualiza as variáveis globais
                    latitudeState.value = location.latitude
                    longitudeState.value = location.longitude
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Solicita via GPS
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener!!)
            // Solicita via Rede (backup)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener!!)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getConnectedCellId(context: Context): MutableList<Any>? {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            if (telephonyManager.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

                if (cellInfoList != null) {
                    for (cellInfo in cellInfoList) {

                        // Checks if the cell is registered (connected)
                        if (cellInfo.isRegistered) {

                            // Obtains the Cell ID of different cell types
                            val data = mutableListOf<Any>(
                                cellInfo.cellSignalStrength.dbm,
                                cellInfo.cellSignalStrength.level
                            )
                            when (cellInfo) {
                                is CellInfoGsm -> {
                                    val cellIdentityGsm = cellInfo.cellIdentity
                                    cellIdentityGsm.mccString?.let { data.add(it) }
                                    cellIdentityGsm.mncString?.let { data.add(it) }
                                    data.add(cellIdentityGsm.cid)
                                    data.add(cellIdentityGsm.lac)
                                    data.add(5)
                                    return data
                                }

                                is CellInfoLte -> {
                                    val cellIdentityLte = cellInfo.cellIdentity
                                    cellIdentityLte.mccString?.let { data.add(it) }
                                    cellIdentityLte.mncString?.let { data.add(it) }
                                    data.add(cellIdentityLte.ci)
                                    data.add(cellIdentityLte.tac)
                                    if (nsa.value) {
                                        data.add(10)
                                    } else {
                                        data.add(6)
                                    }
                                    data.add(cellInfo.cellSignalStrength.rsrq)
                                    data.add(cellInfo.cellSignalStrength.rssnr)
                                    return data
                                }

                                is CellInfoWcdma -> {
                                    val cellIdentityWcdma = cellInfo.cellIdentity
                                    cellIdentityWcdma.mccString?.let { data.add(it) }
                                    cellIdentityWcdma.mncString?.let { data.add(it) }
                                    data.add(cellIdentityWcdma.cid)
                                    data.add(cellIdentityWcdma.lac)
                                    data.add(7)
                                    return data
                                }

                                is CellInfoCdma -> {
                                    val cellIdentityCdma = cellInfo.cellIdentity
                                    data.add("")
                                    data.add("")
                                    data.add(cellIdentityCdma.basestationId)
                                    data.add("")
                                    data.add(8)
                                    return data
                                }

                                is CellInfoNr -> {
                                    val cellIdentityNr = cellInfo.cellIdentity as CellIdentityNr
                                    val cellsignalnr =
                                        cellInfo.cellSignalStrength as CellSignalStrengthNr
                                    cellIdentityNr.mccString?.let { data.add(it) }
                                    cellIdentityNr.mncString?.let { data.add(it) }
                                    data.add(cellIdentityNr.nci)
                                    data.add(cellIdentityNr.tac)
                                    data.add(9)
                                    data.add(cellsignalnr.ssRsrq)
                                    data.add(cellsignalnr.ssSinr)
                                    data.add(cellIdentityNr.nrarfcn)
                                }
                                else -> { }
                            }
                        }
                    }
                }
            } else {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        return null
    }
    private var telephonyCallback: TelephonyCallback? = null

    @RequiresApi(Build.VERSION_CODES.S)
    fun start5GDetection(context: Context) {
        if (telephonyCallback == null) {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            telephonyCallback =
                object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                        when (displayInfo.overrideNetworkType) {
                            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> {
                                Log.d("ATLAS", "Tipo de rede: NSA")
                                on5GNSAConnectionDetected()
                            }
                            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                                Log.d("ATLAS", "Tipo de rede: PLUS")
                            }
                            else -> {
                                nsa.value = false
                                Log.d("ATLAS", "Não conectado.")
                            }
                        }
                    }
                }
            telephonyManager.registerTelephonyCallback(context.mainExecutor, telephonyCallback!!)
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    fun stop5GDetection(context: Context) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyCallback?.let {
            telephonyManager.unregisterTelephonyCallback(it)
        }
        telephonyCallback = null
    }

    fun on5GNSAConnectionDetected(): MutableState<Boolean> {
        nsa.value = true
        return nsa
    }

    @Synchronized
    private fun saveDataToCSV(data: DataModel, dataCellId: MutableList<Any>) {
        val csvFileName = "clientdata.csv"
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val csvFile = File(baseDir, csvFileName)

        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }

            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            var currentCount = sharedPreferences.getInt("count", 0)

            if (!csvFile.exists() || csvFile.readLines().none { it.startsWith("Sequence") }) {
                val columnNames =
                    "Sequence,Transport,Timestamp,Latency,TTL,Hops,Latitude,Longitude,Signal_dbm,Signal_level,MCC,MNC,CellId,Tac/Lac,Mobile_Network,RSRQ,RSSNR,NRARFCN"

                val writer = BufferedWriter(FileWriter(csvFile, true))
                writer.write("$columnNames\n")
                writer.close()
                currentCount = 0
            }

            val existingCount = currentCount
            val newCount = existingCount + 1
            sharedPreferences.edit().putInt("count", newCount).apply()

            if (isValidData(data)) {
                val csvLine =
                    "$newCount, ${data.transport}, ${data.timestamp}, ${data.latency}, ${data.ttl}, ${data.hops}, ${data.latitude}, ${data.longitude}," +
                            dataCellId.joinToString(",")

                val writer = BufferedWriter(FileWriter(csvFile, true))
                writer.write(csvLine)
                writer.newLine()
                writer.close()

            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun isValidData(data: DataModel): Boolean {
        if (data.transport > 0 &&
            data.timestamp.isNotBlank() &&
            data.latency > 0.toLong() &&
            data.cellId != 0 &&
            data.latitude != 0.toDouble() &&
            data.longitude != 0.toDouble()

        ) {
            return true
        }
        return false
    }

    // --- LOOP PRINCIPAL (MODIFICADO PARA USAR GPS NATIVO) ---
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestLocationAndFetchData(targetUrl: String) {

        // Verificação rápida de UI (pode ficar na thread principal)
        if (latitudeState.value == 0.0) {
            resultFromRequestState.value = "Waiting for GPS Fix..."
            return
        }

        // --- MUDANÇA CRUCIAL: Movemos todo o trabalho pesado para Dispatchers.IO ---
        withContext(Dispatchers.IO) {

            // 1. Pega dados da Torre (Pesado - leitura de hardware)
            val cellIdInfoList = getConnectedCellId(this@MainActivity)

            // Atualiza UI (Volta rápido pra Main só pra setar valor)
            if (cellIdInfoList != null) {
                withContext(Dispatchers.Main) {
                    cellIdState.value = cellIdInfoList
                }
            }

            val timeStamp: String = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Date())
            withContext(Dispatchers.Main) {
                timeStampValue.value = timeStamp
            }

            // 2. Testes de Rede (Ping/Hops) - EXTREMAMENTE BLOQUEANTE
            // Agora isso roda em background e deixa o Dropdown funcionar
            val (latency, ttl) = pingHostIPv6(targetUrl)
            val hops = calculateHops(targetUrl)

            val resultString = if (latency >= 0) "Ping: ${latency}ms (TTL=$ttl, Hops=$hops)" else "Ping Failed"

            // Atualiza o texto do resultado na UI
            withContext(Dispatchers.Main) {
                resultFromRequestState.value = resultString
            }

            // 3. Salva no CSV (Operação de Arquivo - IO)
            cellIdInfoList?.let { infoList ->
                val cellIdReal = if (infoList.size > 4) infoList[4] else 0

                // Captura valores de latitude/longitude da UI de forma segura antes de salvar
                // (Precisamos ler state values na main thread ou de variaveis locais,
                // aqui acessamos via getters thread-safe do state, mas o ideal é ler snapshot)
                val currentLat = latitudeState.value ?: 0.0
                val currentLon = longitudeState.value ?: 0.0

                val dataModel = DataModel(
                    transportation.value,
                    timeStamp, // Usa a variavel local, não o state
                    cellIdReal,
                    latency,
                    ttl,
                    hops,
                    currentLat,
                    currentLon,
                )
                saveDataToCSV(dataModel, infoList)
            }
        }
    }

    @Composable
    fun ScrollableContent(
        latitude: Double?,
        longitude: Double?,
        result: String,
        tempo: String,
        cid: Any
    ) {
        val cellInfoList = cid as? List<*>
        val signalDbm = cellInfoList?.getOrNull(0) ?: "N/A"
        val cellIdentity = cellInfoList?.getOrNull(4) ?: "N/A"
        val rsrq = cellInfoList?.getOrNull(7) ?: "-"
        val sinr = cellInfoList?.getOrNull(8) ?: "-"

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Latitude: $latitude")
                        Text("Longitude: $longitude")
                        Text("Cell ID: $cellIdentity")
                        Text("RSRP (Signal): $signalDbm dBm")
                        Text("SINR (Noise) $sinr dB")
                        Text("RSRQ: $rsrq dB")
                        Text("Timestamp: $tempo")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (result.contains("Failed") || result.contains("timeout")) Color.Red else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    private fun pingHostIPv6(url: String): Pair<Long, Int> {
        var delay = -1L
        var ttl = -1
        try {
            var host = url.replace("http://", "").replace("https://", "").split("/")[0]
            host = host.replace("[", "").replace("]", "")

            val process = Runtime.getRuntime().exec("ping6 -c 1 -w 2 $host")
            val exitValue = process.waitFor()

            if (exitValue == 0) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains("time=")) {
                        val timePart = line!!.split("time=")[1].split(" ")[0]
                        delay = timePart.toFloat().toLong()
                    }
                    if (line!!.contains("ttl=")) {
                        val ttlPart = line!!.split("ttl=")[1].split(" ")[0]
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
        var host = url.replace("http://", "").replace("https://", "").split("/")[0]
        host = host.replace("[", "").replace("]", "")

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

        if (topic.isEmpty()) {
            Log.e("WolfDebug", "ERRO: O tópico está vazio!")
            callback(null)
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val url = "https://ntfy.sh/$topic/raw?since=all&limit=5&poll=1"

        Log.d("WolfDebug", "Conectando em: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WolfDebug", "FALHA TOTAL: ${e.message}")
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("WolfDebug", "Erro do Servidor: ${it.code}")
                        callback(null)
                        return
                    }

                    try {
                        val rawBody = it.body?.string() ?: ""
                        Log.d("WolfDebug", "Resposta recebida: $rawBody")

                        val lastValidIp = rawBody.lines()
                            .filter { line -> line.contains(":") }
                            .lastOrNull { line -> line.isNotBlank() }
                            ?.trim()

                        if (lastValidIp != null) {
                            Log.d("WolfDebug", "IP VALIDADO: $lastValidIp")
                            callback(lastValidIp)
                        } else {
                            Log.e("WolfDebug", "Nenhum IP encontrado no texto.")
                            callback(null)
                        }

                    } catch (e: Exception) {
                        Log.e("WolfDebug", "Erro ao processar texto: ${e.message}")
                        e.printStackTrace()
                        callback(null)
                    }
                }
            }
        })
    }

    private fun shareCsvFile() {
        val csvFileName = "clientdata.csv"
        val exportName = "clientdata_export.csv"

        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val sourceFile = File(baseDir, csvFileName)
        val destFile = File(baseDir, exportName)

        if (sourceFile.exists()) {
            try {
                sourceFile.copyTo(destFile, overwrite = true)
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    destFile
                )
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/csv"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, "Share Data"))
            } catch (e: Exception) {
                Toast.makeText(this, "Error preparing file: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "No data collected yet!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivityForResult(intent, 2296)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivityForResult(intent, 2296)
                }
                Toast.makeText(this, "Please allow 'All Files Access' to save CSV", Toast.LENGTH_LONG).show()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    data class DataModel(
        @CsvBindByName(column = "Transportation")
        val transport: Int,
        @CsvBindByName(column = "Timestamp")
        val timestamp: String,
        @CsvBindByName(column = "Cellid")
        val cellId: Any,
        @CsvBindByName(column = "Latência")
        val latency: Long,
        @CsvBindByName(column = "TTL")
        val ttl: Int,
        @CsvBindByName(column = "Hops")
        val hops: Int,
        @CsvBindByName(column = "Latitude")
        val latitude: Double,
        @CsvBindByName(column = "Longitude")
        val longitude: Double
    )
}