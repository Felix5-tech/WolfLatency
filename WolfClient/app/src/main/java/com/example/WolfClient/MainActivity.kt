// Author: Luiz Felipe Cantanhede Cristino
// Institution: GTA, COPPE, UFRJ

package com.example.wolfclient

// Necessary imports
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.ActivityCompat
import com.example.wolfclient.ui.theme.WolfClientTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.opencsv.bean.CsvBindByName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    // Declaration of state variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val resultFromRequestState = mutableStateOf<String?>(null)
    private val latitudeState = mutableStateOf<Double?>(null)
    private val longitudeState = mutableStateOf<Double?>(null)
    private val urlState = mutableStateOf(TextFieldValue())
    private val nsa = mutableStateOf(false)
    private val transportation = mutableStateOf(1)
    private val timeStampValue = mutableStateOf(" ")
    private val cellIdState = mutableStateOf<Any>(0)
    private val sizeResponseState = mutableStateOf(0)
    private var toastBalloon = 0

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Inicia a detecção de 5G
        start5GDetection(this)


        setContent {
            val coroutineScope = rememberCoroutineScope()

            WolfClientTheme {
                val gpsMessage by resultFromRequestState
                val latitude by latitudeState
                val longitude by longitudeState
                val timestamp by timeStampValue
                val cellId by cellIdState

                val showToast = Toast.makeText(
                    this@MainActivity,
                    "Successful Request!",
                    Toast.LENGTH_SHORT
                )
                var selectedItem by remember { mutableStateOf("On foot") }
                val items = listOf(
                    "On foot",
                    "Bicycle",
                    "Motorcycle",
                    "Car",
                    "Bus",
                    "Train",
                    "VLT",
                    "Subway",
                    "Barca"
                )
                var expanded by remember { mutableStateOf(false) }
                val tipoTransporteMap = mapOf(
                    "On foot" to 1,
                    "Bicycle" to 2,
                    "Motorcycle" to 3,
                    "Car" to 4,
                    "Bus" to 5,
                    "Train" to 6,
                    "VLT" to 7,
                    "Subway" to 8,
                    "Barca" to 9
                )
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {
                        Card(
                            modifier = Modifier.clickable { expanded = !expanded },
                            shape = RoundedCornerShape(4.dp)

                        ) {
                            Text(
                                text = "Means of transportation: $selectedItem",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        if (expanded) {

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier,
                                offset = DpOffset(
                                    120.dp,
                                    (-40).dp
                                ), // Adjust the displacement as required
                                properties = PopupProperties(focusable = true) // Adjust the properties as necessary
                            ) {
                                items.forEach { label ->
                                    DropdownMenuItem(
                                        text = { Text(text = label) },
                                        onClick = {
                                            selectedItem = label
                                            expanded =
                                                false // Closes the menu when an item is selected
                                            val valueInteger = tipoTransporteMap[selectedItem]
                                            if (valueInteger != null) {
                                                transportation.value = valueInteger
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        // Input field for the URL

                        BasicTextField(
                            value = urlState.value,
                            onValueChange = {
                                // Updates the status of the URL when the user types it in
                                urlState.value = it
                            },
                            textStyle = TextStyle(color = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(color = Color.White)
                                .border(1.dp, Color.Black)
                        )


                        Spacer(modifier = Modifier.height(8.dp))

                        // Botão para pegar IP do Ntfy
                        Button(
                            onClick = {
                                fetchIpFromNtfy { ip ->
                                    if (ip != null) {
                                        // Atualiza a caixa de texto na Thread Principal
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
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text("Get Server IP (Ntfy)")
                        }

                        // Botão para iniciar requisições
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    while (true) {
                                        val url = urlState.value.text
                                        if (url.isNotEmpty()) {
                                            requestLocationAndFetchData(url)
                                        }
                                        showToast.cancel()
                                        toastBalloon = 0
                                        delay(1000)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text("Start Continuous Request")
                        }

                        // O BOTÃO DE DELETAR FOI REMOVIDO DAQUI

                        // scrolling component to display information
                        ScrollableContent(
                            latitude,
                            longitude,
                            gpsMessage ?: "\nAwaiting response to request",
                            timestamp,
                            cellId
                        )
                        Log.d("toast", "toast: $toastBalloon")

                        if (toastBalloon == 2) {
                            showToast.show()
                            toastBalloon = 0
                        } else {
                            showToast.cancel()

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
        // Para a detecção de 5G para evitar leaks
        stop5GDetection(this)
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
                                // Add other cell types as required
                                else -> {
                                    // If you need to deal with other types of cells, do so here
                                    // Specific treatment may be required for other cell types
                                    // Depending on the case, return or continue the loop
                                }
                            }
                        }
                    }
                }
            } else {
                // If permissions have not been granted, ask the user to

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
                                // Conexão 5G NSA detectada
                                Log.d("ATLAS", "Tipo de rede: NSA")
                                on5GNSAConnectionDetected()

                            }

                            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                                // Conexão 5G+ detectada (A implementar)
                                Log.d("ATLAS", "Tipo de rede: PLUS")

                            }

                            else -> {
                                // Não está conectado a uma rede 5G
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
        // Coloque aqui o código que será executado quando a conexão 5G NSA for detectada
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

            // ADICIONADO "Hops" no cabeçalho
            if (!csvFile.exists() || csvFile.readLines().none { it.startsWith("Sequence") }) {
                val columnNames =
                    "Sequence, Transport, Timestamp, Latency, TTL, Hops, Latitude, Longitude, Signal_dbm, Signal_level, MCC, MNC, CellId, Tac/Lac, Mobile_Network, RSRQ, RSSNR, NRARFCN"

                // REMOVIDO: sendDataToServer(columnNames, true)

                val writer = BufferedWriter(FileWriter(csvFile, true))
                writer.write("$columnNames\n")
                writer.close()
                currentCount = 0
            }

            val existingCount = currentCount
            val newCount = existingCount + 1
            sharedPreferences.edit().putInt("count", newCount).apply()

            if (isValidData(data)) {
                // ADICIONADO data.hops na linha
                val csvLine =
                    "$newCount, ${data.transport}, ${data.timestamp}, ${data.latency}, ${data.ttl}, ${data.hops}, ${data.latitude}, ${data.longitude}," +
                            dataCellId.joinToString(",")

                Log.d("CurrentCount4", "Line: $csvLine")

                val writer = BufferedWriter(FileWriter(csvFile, true))
                writer.write(csvLine)
                writer.newLine()
                writer.close()

                // REMOVIDO: sendDataToServer(csvLine, false)

                toastBalloon += 1
            } else {
                Log.d("CurrentCount40000", "ERRROOOORR$data")
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    // Function to check if the required fields of the DataModel are filled in
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


    // Main function responsible for obtaining data
    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestLocationAndFetchData(url: String) {

        val permissionGranted = (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) &&

                (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)

        if (!permissionGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSION_REQUEST_CODE
            )
        } else {
            val locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000) // Intervalo de 5 segundos para as atualizações
                .setFastestInterval(1000) // Intervalo mais rápido de 1 segundo

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val lastLocation = locationResult.lastLocation
                    val latitude = lastLocation?.latitude
                    val longitude = lastLocation?.longitude
                    latitudeState.value = latitude
                    longitudeState.value = longitude
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

            CoroutineScope(Dispatchers.IO).launch {
                while (latitudeState.value == null || longitudeState.value == null) {
                    delay(10000)
                }

                val url = urlState.value.text

                // 1. Pega Latência e TTL (Ping normal)
                val (latency, ttl) = pingHostIPv6(url)

                // 2. Calcula Hops de Ida (Traceroute Manual) - ISSO PODE DEMORAR
                val hops = calculateHops(url)

                val resultString = if (latency >= 0) "Ping: ${latency}ms (TTL=$ttl, Hops=$hops)" else "Falha no Ping"

                runOnUiThread {
                    resultFromRequestState.value = resultString
                }

                val timeStamp: String = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Date())
                runOnUiThread {
                    timeStampValue.value = timeStamp
                }

                val cellId = getConnectedCellId(this@MainActivity)

                cellId?.let {
                    runOnUiThread {
                        cellIdState.value = it[2]
                    }

                    val dataModel = DataModel(
                        transportation.value,
                        timeStampValue.value,
                        cellIdState.value,
                        latency,
                        ttl,
                        hops, // <--- Passando o novo valor de Hops
                        latitudeState.value ?: 0.0,
                        longitudeState.value ?: 0.0,
                    )
                    saveDataToCSV(dataModel, it)
                }
            }
        }
    }



    // Function that shows Hello, World on the screen
    @Composable
    fun Greeting(name: String, result: String?) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "Hello, $name!",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))
            if (result != null) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Function that receives the data results for scrolling
    @Composable
    fun ScrollableContent(
        latitude: Double?,
        longitude: Double?,
        result: String,
        tempo: String,
        cid: Any
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                latitude?.let { lat ->
                    longitude?.let { lon ->
                        Text(
                            text = "Latitude: $lat\nLongitude: $lon\nTimestamp: $tempo\nCellId: $cid",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Greeting("world", result)
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        WolfClientTheme {
            Greeting("World", "Sample Result")
        }
    }


    // Função nova para Ping IPv6 com captura de TTL
    private fun pingHostIPv6(url: String): Pair<Long, Int> {
        var delay = -1L
        var ttl = -1
        try {
            // Limpeza da URL para extrair apenas o host
            var host = url.replace("http://", "").replace("https://", "").split("/")[0]
            host = host.replace("[", "").replace("]", "") // Remove colchetes de IPv6 se houver

            // Executa: ping6 -c 1 (1 pacote) -w 2 (timeout 2s)
            val process = Runtime.getRuntime().exec("ping6 -c 1 -w 2 $host")
            val exitValue = process.waitFor()

            if (exitValue == 0) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    // Exemplo de saída: "64 bytes from ... ttl=118 time=22.5 ms"

                    // Extrair Latência
                    if (line!!.contains("time=")) {
                        val timePart = line!!.split("time=")[1].split(" ")[0]
                        delay = timePart.toFloat().toLong()
                    }
                    // Extrair TTL
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

    // Função para calcular Hops de Ida (Traceroute Manual)
    private fun calculateHops(url: String): Int {
        var host = url.replace("http://", "").replace("https://", "").split("/")[0]
        host = host.replace("[", "").replace("]", "")

        // Tenta TTL de 1 até 30
        for (ttl in 1..30) {
            try {
                // -c 1 (1 pacote), -w 1 (wait 1s), -t (TTL/HopLimit)
                // Nota: Alguns Androids usam -t, outros -h. O padrão costuma ser -t.
                val cmd = "ping6 -c 1 -w 1 -t $ttl $host"
                val process = Runtime.getRuntime().exec(cmd)
                val exitValue = process.waitFor()

                // Se exitValue for 0, significa que chegou no destino (Echo Reply)
                // Se for diferente (ex: Time Exceeded), ainda não chegou
                if (exitValue == 0) {
                    return ttl // Encontrou o número de saltos para chegar
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return -1 // Não alcançou o destino em 30 saltos ou erro
    }

    // Função para pegar IP do Ntfy (Histórico Recente)
    private fun fetchIpFromNtfy(callback: (String?) -> Unit) {
        val client = OkHttpClient()
        // Use BuildConfig.NTFY_TOPIC se tiver configurado, ou a string direta
        val topic = ""

        // MUDANÇA 1: Adicionado parâmetros para pegar apenas a ÚLTIMA mensagem do histórico
        val request = Request.Builder()
            .url("https://ntfy.sh/$topic/raw?since=all&limit=1")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                // Falha na rede
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(null)
                    } else {
                        // MUDANÇA 2: Tratamento de String robusto
                        // Pega o corpo, remove espaços em branco nas pontas
                        val fullBody = it.body?.string()?.trim()

                        // Se por acaso vierem múltiplas linhas, pega a última que não esteja vazia
                        // Isso protege caso o ntfy mande "IP_VELHO\nIP_NOVO"
                        val lastLine = fullBody?.lines()?.lastOrNull { line -> line.isNotBlank() }?.trim()

                        // Validação: Só aceita se tiver ":" (característica do IPv6) e não for erro
                        if (lastLine != null && lastLine.contains(":")) {
                            Log.d("WolfClient", "IP Recebido: $lastLine")
                            callback(lastLine)
                        } else {
                            Log.d("WolfClient", "Resposta inválida ou vazia: $fullBody")
                            callback(null)
                        }
                    }
                }
            }
        })
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
