// Author: Luiz Felipe Cantanhede Cristino
// Institution: GTA, COPPE, UFRJ

package com.example.wolfserver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.telephony.*
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.wolfserver.ui.theme.WolfServerTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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

    private val nsa = mutableStateOf(false)
    private val currentIpState = mutableStateOf("Waiting for IP...")

    // Escopo para rodar as tarefas em background
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
        private const val NTFY_TOPIC = ""
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MANTÉM A TELA LIGADA (Essencial para o servidor não dormir)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        start5GDetection(this)

        // Inicia os Loops Eternos (Coleta e IPv6)
        startContinuousCollection()
        startIpBroadcaster()

        setContent {
            WolfServerTheme {
                Surface(
                    modifier = Modifier
                        .background(color = Color.White)
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Greeting(name = "")

                        // Mostra o IP atual na tela para facilitar debug visual
                        Text(
                            text = currentIpState.value,
                            modifier = Modifier.padding(16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Botão Manual para enviar IP
                        Button(
                            onClick = {
                                scope.launch {
                                    sendIpToNtfy()
                                }
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Force Send IP to Ntfy")
                        }

                        Button(
                            onClick = {
                                deleteCSVFile()
                                Toast.makeText(this@MainActivity, "CSV Deleted", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Delete Server Data CSV")
                        }
                    }
                }
            }
        }
    }

    // --- LOOP 1: Coleta de Dados a cada 1 segundo ---
    private fun startContinuousCollection() {
        scope.launch {
            while (true) {
                // Captura os dados (roda na thread IO para não travar, mas captureCellInfo é leve)
                captureCellInfo(this@MainActivity)
                delay(1000) // Espera 1 segundo
            }
        }
    }

    // --- LOOP 2: Envia IPv6 a cada 30 segundos ---
    private fun startIpBroadcaster() {
        scope.launch {
            while (true) {
                sendIpToNtfy()
                delay(300000) // 30 segundos
            }
        }
    }

    // Função para descobrir o IPv6 Global do dispositivo
    private fun getDeviceIPv6(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    // Procura por IPv6 que não seja loopback (::1) nem link-local (fe80::)
                    if (inetAddress is Inet6Address && !inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress) {
                        // Remove o sufixo de interface se houver (ex: %wlan0)
                        val ip = inetAddress.hostAddress
                        val delimiter = ip.indexOf('%')
                        return if (delimiter < 0) ip else ip.substring(0, delimiter)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Função que manda o IP para o Ntfy
    private fun sendIpToNtfy() {
        val ip = getDeviceIPv6()

        if (ip != null) {
            currentIpState.value = "Current IPv6: $ip" // Atualiza UI

            val client = OkHttpClient()
            val requestBody = ip.toRequestBody("text/plain".toMediaType())

            val request = Request.Builder()
                .url("https://ntfy.sh/$NTFY_TOPIC")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().close()
                Log.d("WolfServer", "IP sent to Ntfy: $ip")
            } catch (e: IOException) {
                Log.e("WolfServer", "Failed to send to Ntfy", e)
            }
        } else {
            currentIpState.value = "IPv6 not found. Check connection."
        }
    }

    private fun deleteCSVFile() {
        val serverDataFileName = "serverdata.csv"
        // Não deletamos mais o backupclientdata pois ele não é mais usado/gerado
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val serverDataFile = File(baseDir, serverDataFileName)

        if (serverDataFile.exists()) {
            serverDataFile.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
        stop5GDetection(this)
        // Remove flag de manter tela ligada
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @SuppressLint("SuspiciousIndentation")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureCellInfo(context: Context) {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val timeStamp: String = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Date())

            if (telephonyManager.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

                if (cellInfoList != null) {
                    for (cellInfo in cellInfoList) {
                        if (cellInfo.isRegistered) {
                            val data = mutableListOf<Any>(
                                timeStamp
                            )
                            val locationManager =
                                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                            val location: Location? =
                                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                            location?.let {
                                val latitude = it.latitude
                                val longitude = it.longitude
                                data.add(latitude)
                                data.add(longitude)
                            } ?: run {
                                // Se location for null, adiciona placeholders para não quebrar CSV
                                data.add(0.0)
                                data.add(0.0)
                            }

                            data.add(cellInfo.cellSignalStrength.dbm)
                            data.add(cellInfo.cellSignalStrength.level)

                            when (cellInfo) {
                                is CellInfoGsm -> {
                                    val cellIdentityGsm = cellInfo.cellIdentity
                                    cellIdentityGsm.mccString?.let { data.add(it) }
                                    cellIdentityGsm.mncString?.let { data.add(it) }
                                    data.add(cellIdentityGsm.cid)
                                    data.add(cellIdentityGsm.lac)
                                    data.add(5)
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
                                }

                                is CellInfoWcdma -> {
                                    val cellIdentityWcdma =
                                        cellInfo.cellIdentity
                                    cellIdentityWcdma.mccString?.let { data.add(it) }
                                    cellIdentityWcdma.mncString?.let { data.add(it) }
                                    data.add(cellIdentityWcdma.cid)
                                    data.add(cellIdentityWcdma.lac)
                                    data.add(7)
                                }

                                is CellInfoCdma -> {
                                    val cellIdentityCdma =
                                        cellInfo.cellIdentity
                                    data.add("")
                                    data.add("")
                                    data.add(cellIdentityCdma.basestationId)
                                    data.add("")
                                    data.add(8)
                                }

                                is CellInfoNr -> {
                                    val cellIdentityNr =
                                        cellInfo.cellIdentity as CellIdentityNr
                                    val cellSignalNr =
                                        cellInfo.cellSignalStrength as CellSignalStrengthNr
                                    cellIdentityNr.mccString?.let { data.add(it) }
                                    cellIdentityNr.mncString?.let { data.add(it) }
                                    data.add(cellIdentityNr.nci)
                                    data.add(cellIdentityNr.tac)
                                    data.add(9)
                                    data.add(cellSignalNr.ssRsrq)
                                    data.add(cellSignalNr.ssSinr)
                                    data.add(cellIdentityNr.nrarfcn)
                                }
                                else -> { }
                            }

                            // Save to CSV file
                            saveCellInfoToCSV(data)
                            return
                        }
                    }
                }
            }
        }
    }

    // Adicionado Synchronized para evitar problemas de concorrência com threads
    @Synchronized
    private fun saveCellInfoToCSV(cellInfoData: List<Any>) {
        val csvFileName = "serverdata.csv"
        val baseDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents")
        val csvFile = File(baseDir, csvFileName)

        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }

            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            var currentCount = sharedPreferences.getInt("count", 0)

            if (!csvFile.exists()) {
                val writer = BufferedWriter(FileWriter(csvFile, true))
                val columnNames =
                    "Sequence, Timestamp, Latitude, Longitude, Signal_dbm, Signal_level, MCC, MNC, CellId, Tac/Lac, Mobile_Network, RSRQ, RSSNR, NRARFCN"
                writer.write("$columnNames\n")
                writer.close()
                currentCount = 0
            }

            val writer = BufferedWriter(FileWriter(csvFile, true))
            val existingCount = currentCount
            val newCount = existingCount + 1
            sharedPreferences.edit().putInt("count", newCount).apply()

            val csvLine = cellInfoData.joinToString(",") { it.toString() }

            writer.write("$newCount,$csvLine")
            writer.newLine()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
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
                                nsa.value = true
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

    @Composable
    fun CenteredText(text: String) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = text)
        }
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        // Mostra o status do servidor
        Text(text = "WolfServer Running...\nCollecting Data: Every 1s\nSending IP: Every 30s",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp))
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        WolfServerTheme {
            Greeting("Android")
        }
    }
}
