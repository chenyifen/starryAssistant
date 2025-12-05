package com.ai.voice.test

import android.content.Context
import android.util.Log
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.AutoTestLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

class AutoTestHttpServer(private val context: Context, private val port: Int = 8765) {
    companion object {
        private const val TAG = "AutoTest"
        private const val NOTIFY_SERVER_URL = "http://192.168.2.8:8080/api/notify_state"
    }
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun notifyStateChange(event: String, data: Map<String, Any> = emptyMap()) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("event", event)
                    put("timestamp", System.currentTimeMillis())
                    data.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Int -> put(key, value)
                            is Long -> put(key, value)
                            is Boolean -> put(key, value)
                            is Float -> put(key, value.toDouble())
                            is Double -> put(key, value)
                            else -> put(key, value.toString())
                        }
                    }
                }
                
                val url = URL(NOTIFY_SERVER_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                
                connection.outputStream.use { output ->
                    output.write(json.toString().toByteArray(Charsets.UTF_8))
                }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    Log.d(TAG, "[NOTIFY] State change notified: $event")
                } else {
                    Log.w(TAG, "[NOTIFY] Failed to notify state change: $event, response code: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "[NOTIFY] Error notifying state change: ${e.message}")
            }
        }
    }
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "AutoTest HTTP Server started on port $port")
                
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }
    
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "AutoTest HTTP Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server: ${e.message}")
        }
    }
    
    private fun handleClient(client: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val outputStream = client.getOutputStream()
                
                val requestLine = reader.readLine() ?: return@launch
                Log.d(TAG, "Request: $requestLine")
                
                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                
                var body = ""
                if (contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    reader.read(bodyChars, 0, contentLength)
                    body = String(bodyChars)
                }
                
                val response = processRequest(requestLine, body)
                val responseBytes = response.toByteArray(Charsets.UTF_8)
                
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Content-Length: ${responseBytes.size}\r\n")
                    append("\r\n")
                }
                
                outputStream.write(headers.toByteArray(Charsets.UTF_8))
                outputStream.write(responseBytes)
                outputStream.flush()
                
                client.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client: ${e.message}", e)
            }
        }
    }
    
    private fun processRequest(requestLine: String, body: String): String {
        val parts = requestLine.split(" ")
        if (parts.size < 2) return """{"error":"Invalid request"}"""
        
        val method = parts[0]
        val path = parts[1]
        
        Log.d(TAG, "Processing: $method $path body=$body")
        
        return when {
            path == "/wake" || path.startsWith("/wake?") -> {
                handleWake()
            }
            path == "/asr" || path.startsWith("/asr?") -> {
                val text = extractParam(path, "text") ?: body.takeIf { it.isNotBlank() } ?: ""
                handleAsr(text)
            }
            path == "/status" -> {
                handleStatus()
            }
            path == "/state" -> {
                handleState()
            }
            path == "/log" || path.startsWith("/log?") -> {
                val message = extractParam(path, "message") ?: body.takeIf { it.isNotBlank() } ?: ""
                handleLog(message)
            }
            else -> {
                """{"error":"Unknown command","available":["/wake","/asr?text=xxx","/status","/state","/log?message=xxx"]}"""
            }
        }
    }
    
    private fun extractParam(path: String, param: String): String? {
        val queryStart = path.indexOf('?')
        if (queryStart < 0) return null
        val query = path.substring(queryStart + 1)
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .find { it[0] == param }
            ?.getOrNull(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }
    
    private fun handleWake(): String {
        val ts = System.currentTimeMillis()
        Log.i(TAG, "[CMD] wake ts=$ts")
        WakeWordCallbackManager.notifyWakeWordDetected(0.95f, "http_test")
        Log.i(TAG, "[WAKE] triggered ts=$ts")
        return """{"status":"ok","command":"wake","ts":$ts}"""
    }
    
    private fun handleAsr(text: String): String {
        if (text.isBlank()) {
            Log.w(TAG, "[CMD] asr failed: empty text")
            return """{"error":"text parameter required"}"""
        }
        val ts = System.currentTimeMillis()
        Log.i(TAG, "[CMD] asr text=\"$text\" ts=$ts")
//        AsrHandler.simulateFinalResult(text)
        Log.i(TAG, "[ASR] simulated text=\"$text\" ts=$ts")
        return """{"status":"ok","command":"asr","text":"$text","ts":$ts}"""
    }
    
    private fun handleStatus(): String {
        val asrRunning = AsrHandler.isStarted()
        val ts = System.currentTimeMillis()
        Log.i(TAG, "[STATUS] asr_running=$asrRunning ts=$ts")
        return """{"status":"ok","asr_running":$asrRunning,"ts":$ts}"""
    }
    
    private fun handleLog(message: String): String {
        val ts = System.currentTimeMillis()
        if (message.isNotBlank()) {
            Log.i(TAG, "[TEST_INFO] $message ts=$ts")
        } else {
            Log.w(TAG, "[TEST_INFO] empty message ts=$ts")
        }
        return """{"status":"ok","command":"log","message":"$message","ts":$ts}"""
    }
    
    private fun handleState(): String {
        return try {
            val stateProvider = VoiceAssistantStateProvider.getInstance()
            val currentState = stateProvider.getCurrentState()
            val ts = System.currentTimeMillis()
            val uiStateName = currentState.uiState.name
            Log.d(TAG, "[STATE] Current state: $uiStateName, asrRunning: ${AsrHandler.isStarted()}")
            val json = JSONObject().apply {
                put("status", "ok")
                put("uiState", uiStateName)
                put("displayText", currentState.displayText)
                put("asrText", currentState.asrText)
                put("ttsText", currentState.ttsText)
                put("confidence", currentState.confidence)
                put("asrRunning", AsrHandler.isStarted())
                put("timestamp", ts)
            }
            json.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting state: ${e.message}", e)
            """{"status":"error","error":"${e.message}","ts":${System.currentTimeMillis()}}"""
        }
    }
}
