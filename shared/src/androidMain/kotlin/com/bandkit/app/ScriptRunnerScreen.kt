// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bandkit.app.core.NativeDevice
import com.bandkit.app.core.currentTimeMillis
import com.bandkit.app.models.DeviceSession
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ConsoleEntry(
    val id: Long,
    val message: String,
    val level: String = "log",
    val timestamp: Long = currentTimeMillis(),
)

private val consoleIdCounter = java.util.concurrent.atomic.AtomicLong(0)

/**
 * Android → JS 桥接类。
 *
 * 所有 @JavascriptInterface 方法在 WebView 后台线程执行，
 * 通过 mainHandler.post {} 确保 Compose 状态修改在主线程。
 */
class ScriptBridge(
    private val session: () -> DeviceSession?,
    private val onConsole: (String, String) -> Unit,
    private val onShowGui: () -> Unit = {},
    private val appContext: android.content.Context? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 执行脚本的 WebView 引用，用于推送事件 */
    var scriptWebView: WebView? = null

    @JavascriptInterface
    fun log(message: String) = mainHandler.post { onConsole(message, "log") }

    @JavascriptInterface
    fun warn(message: String) = mainHandler.post { onConsole(message, "warn") }

    @JavascriptInterface
    fun error(message: String) = mainHandler.post { onConsole(message, "error") }

    @JavascriptInterface
    fun deviceConnect(addr: String, authkey: String): Boolean = try {
        NativeDevice.deviceConnect("", addr, authkey, 2, "SPP", ByteArray(0))
        true
    } catch (e: Exception) {
        mainHandler.post { onConsole("connect failed: ${e.message}", "error") }
        false
    }

    @JavascriptInterface
    fun deviceDisconnect(addr: String): Boolean = try {
        NativeDevice.deviceDisconnect(addr)
        true
    } catch (e: Exception) {
        mainHandler.post { onConsole("disconnect failed: ${e.message}", "error") }
        false
    }

    @JavascriptInterface
    fun getWatchfaceList(): String = runCatching {
        val s = session() ?: return "[]"
        NativeDevice.watchfaceGetList(s.device.addr)
    }.getOrElse {
        mainHandler.post { onConsole("getWatchfaceList: ${it.message}", "error") }
        "[]"
    }

    @JavascriptInterface
    fun setWatchface(watchfaceId: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.watchfaceSetCurrent(s.device.addr, watchfaceId)
    }.getOrElse {
        mainHandler.post { onConsole("setWatchface: ${it.message}", "error") }
        false
    }

    @JavascriptInterface
    fun uninstallWatchface(watchfaceId: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.watchfaceUninstall(s.device.addr, watchfaceId)
    }.getOrElse {
        mainHandler.post { onConsole("uninstallWatchface: ${it.message}", "error") }
        false
    }

    @JavascriptInterface
    fun getAppList(): String = runCatching {
        val s = session() ?: return "[]"
        NativeDevice.thirdpartyappGetList(s.device.addr)
    }.getOrElse {
        mainHandler.post { onConsole("getAppList: ${it.message}", "error") }
        "[]"
    }

    @JavascriptInterface
    fun launchApp(packageName: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.thirdpartyappLaunch(s.device.addr, packageName, "")
    }.getOrElse {
        mainHandler.post { onConsole("launchApp: ${it.message}", "error") }
        false
    }

    @JavascriptInterface
    fun sendMessage(packageName: String, data: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.thirdpartyappSendMessage(s.device.addr, packageName, data)
    }.getOrElse {
        mainHandler.post { onConsole("sendMessage: ${it.message}", "error") }
        false
    }

    @JavascriptInterface
    fun uninstallApp(packageName: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.thirdpartyappUninstall(s.device.addr, packageName)
    }.getOrElse {
        mainHandler.post { onConsole("uninstallApp: ${it.message}", "error") }
        false
    }

    @JavascriptInterface
    fun getDeviceData(dataType: String): String = runCatching {
        val s = session() ?: return "{}"
        NativeDevice.deviceGetData(s.device.addr, dataType)
    }.getOrElse {
        mainHandler.post { onConsole("getDeviceData: ${it.message}", "error") }
        "{}"
    }

    @JavascriptInterface
    fun getCurrentTime(): Long = currentTimeMillis()

    @JavascriptInterface
    fun hexToBytes(hex: String): String {
        val clean = hex.replace("\\s".toRegex(), "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }.joinToString("") { "%02x".format(it) }
    }

    @JavascriptInterface
    fun bytesToHex(bytes: String): String = bytes.toByteArray().joinToString("") { "%02x".format(it) }

    @JavascriptInterface
    fun renderGui(configJson: String) = mainHandler.post { onShowGui() }

    /**
     * 将设备事件推送到 WebView 的 JS 事件系统
     * @param eventName 事件类型（thirdpartyapp_message / pb_packet / device_connected / device_disconnected）
     * @param dataJson 事件数据 JSON
     */
    @JavascriptInterface
    fun pushEvent(eventName: String, dataJson: String) {
        val wv = scriptWebView ?: return
        val escaped = dataJson.replace("\\", "\\\\").replace("'", "\\'")
        mainHandler.post {
            wv.evaluateJavascript(
                "_emitEvent(${jsonEscape(eventName)}, JSON.parse('$escaped'));",
                null,
            )
        }
    }

    @JavascriptInterface
    fun getThemeMode(): String {
        val ctx = appContext ?: return "light"
        val nightMode = ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
    }

    // Storage — 内存级
    private val storage = mutableMapOf<String, String>()

    @JavascriptInterface
    fun storageGet(key: String): String? = storage[key]

    @JavascriptInterface
    fun storageSet(key: String, value: String) {
        storage[key] = value
    }

    @JavascriptInterface
    fun storageRemove(key: String) {
        storage.remove(key)
    }

    @JavascriptInterface
    fun storageClear() {
        storage.clear()
    }

    /**
     * 保存脚本到 app 本地存储（SharedPreferences）
     * @param name 脚本名称
     * @param content 脚本内容
     * @return true 保存成功
     */
    @JavascriptInterface
    fun saveScript(name: String, content: String): Boolean {
        val ctx = appContext ?: return false
        return try {
            val prefs = ctx.getSharedPreferences("bandkit_scripts", android.content.Context.MODE_PRIVATE)
            val raw = prefs.getString("saved_scripts", null)
            val list: MutableList<Map<String, Any>> = if (raw != null) {
                org.json.JSONArray(raw).let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).toMap() }.toMutableList()
                }
            } else {
                mutableListOf()
            }
            // 跳过同名脚本
            if (list.any { it["name"] == name }) return false
            val id = "script_${currentTimeMillis()}_${(0..9999).random()}"
            val now = currentTimeMillis()
            val newEntry = mapOf(
                "id" to id,
                "name" to name,
                "content" to content,
                "createdAt" to now,
                "updatedAt" to now,
            )
            list.add(newEntry)
            val jsonArray = org.json.JSONArray()
            list.forEach { jsonArray.put(org.json.JSONObject(it)) }
            prefs.edit().putString("saved_scripts", jsonArray.toString()).apply()
            true
        } catch (e: Exception) {
            mainHandler.post { onConsole("saveScript failed: ${e.message}", "error") }
            false
        }
    }

    private fun org.json.JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key -> map[key] = get(key) }
        return map
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScriptRunnerContent(
    scriptCode: String,
    session: DeviceSession?,
    modifier: Modifier = Modifier,
) {
    var isRunning by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(true) }
    val consoleLog = remember { mutableStateListOf<ConsoleEntry>() }
    val consoleBuffer = remember { mutableListOf<ConsoleEntry>() }
    val flushHandler = remember { Handler(Looper.getMainLooper()) }
    val flushRunnable = remember {
        Runnable {
            if (consoleBuffer.isNotEmpty()) {
                consoleLog.addAll(consoleBuffer)
                consoleBuffer.clear()
            }
            while (consoleLog.size > 200) {
                consoleLog.removeFirst()
            }
        }
    }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val deviceName = session?.device?.name ?: ""
    val deviceAddr = session?.device?.addr ?: ""
    val authKey = session?.device?.authkey ?: ""

    val context = androidx.compose.ui.platform.LocalContext.current
    val bridge = remember(session) {
        ScriptBridge(
            session = { session },
            onConsole = { msg, level ->
                consoleBuffer.add(ConsoleEntry(consoleIdCounter.incrementAndGet(), msg, level))
                flushHandler.removeCallbacks(flushRunnable)
                flushHandler.postDelayed(flushRunnable, 33L)
            },
            onShowGui = { },
            appContext = context.applicationContext,
        )
    }

    // 注册 NativeDevice 事件回调，将事件推送到 WebView
    LaunchedEffect(session) {
        NativeDevice.registerEventSink { eventType, eventData ->
            bridge.pushEvent(eventType, eventData)
        }
        NativeDevice.registerThirdpartyAppMessageCallback { json ->
            try {
                val obj = JSONObject(json)
                val hexPayload = obj.optString("payload", "")
                // 解码 hex payload → UTF-8 字符串 → 尝试解析为 JSON
                val decodedPayload = try {
                    val bytes = hexPayload.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val text = String(bytes, Charsets.UTF_8)
                    // 如果解码结果是 JSON 对象/数组，解析为结构化数据
                    if (text.startsWith("{") || text.startsWith("[")) {
                        JSONObject(text) // 尝试解析，失败回退到字符串
                    } else {
                        text
                    }
                } catch (_: Exception) {
                    hexPayload // 解码失败就用原始 hex 字符串
                }

                val transformed = JSONObject()
                transformed.put("package_name", obj.optString("pkg_name", ""))
                transformed.put("data", decodedPayload)
                transformed.put("rawMessage", json)
                transformed.put("timestamp", System.currentTimeMillis())
                // 保留原始 Rust 字段
                transformed.put("device_addr", obj.optString("device_addr", ""))
                transformed.put("pkg_name", obj.optString("pkg_name", ""))
                bridge.pushEvent("thirdpartyapp_message", transformed.toString())
            } catch (_: Exception) {
                bridge.pushEvent("thirdpartyapp_message", json)
            }
        }
        NativeDevice.registerPbPacketCallback { json ->
            bridge.pushEvent("pb_packet", json)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 运行/停止
            IconButton(onClick = {
                if (isRunning) {
                    webView?.evaluateJavascript("window.stop();", null)
                    isRunning = false
                    consoleLog.add(ConsoleEntry(consoleIdCounter.incrementAndGet(), "脚本已停止", "info"))
                } else {
                    consoleLog.clear()
                    consoleLog.add(ConsoleEntry(consoleIdCounter.incrementAndGet(), "运行脚本...", "info"))
                    isRunning = true
                    val wv = webView
                    if (wv != null) {
                        val bridgeJs = buildJsBridge(deviceName, deviceAddr, authKey)
                        wv.evaluateJavascript(bridgeJs, null)
                        wv.evaluateJavascript(
                            """
                            (async function() {
                                try {
                                    $scriptCode
                                } catch(e) {
                                    sandbox.error(e.toString() + '\\n' + (e.stack || ''));
                                }
                            })();
                            """.trimIndent(),
                            null,
                        )
                    } else {
                        consoleLog.add(ConsoleEntry(consoleIdCounter.incrementAndGet(), "WebView 未就绪", "error"))
                        isRunning = false
                    }
                }
            }) {
                Icon(
                    imageVector = if (isRunning) MiuixIcons.Refresh else MiuixIcons.Play,
                    contentDescription = if (isRunning) "停止" else "运行",
                    modifier = Modifier.size(20.dp),
                    tint = if (isRunning) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                )
            }
            // 清空控制台
            IconButton(onClick = { consoleLog.clear() }) {
                Icon(
                    MiuixIcons.Delete,
                    contentDescription = "清空控制台",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // WebView — 脚本执行 + GUI 渲染都在同一个 WebView 中（与 bandburg 一致）
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        settings.isAlgorithmicDarkeningAllowed = true
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        settings.forceDark = android.webkit.WebSettings.FORCE_DARK_AUTO
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val bridgeJs = buildJsBridge(deviceName, deviceAddr, authKey)
                            view?.evaluateJavascript(bridgeJs, null)
                            webView = view
                            bridge.scriptWebView = view
                            consoleLog.add(
                                0,
                                ConsoleEntry(
                                    consoleIdCounter.incrementAndGet(),
                                    "脚本引擎就绪" + if (deviceAddr.isNotEmpty()) "（设备: $deviceName）" else "（无设备）",
                                    "info",
                                ),
                            )

                            // WebView 准备就绪后自动执行脚本
                            if (!isRunning && scriptCode.isNotBlank()) {
                                isRunning = true
                                view?.evaluateJavascript(
                                    """
                                    (async function() {
                                        try {
                                            $scriptCode
                                        } catch(e) {
                                            sandbox.error(e.toString() + '\\n' + (e.stack || ''));
                                        }
                                    })();
                                    """.trimIndent(),
                                    null,
                                )
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                bridge.log(it.message())
                            }
                            return true
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<android.net.Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            WebViewFileChooser.filePathCallback?.onReceiveValue(null)
                            WebViewFileChooser.filePathCallback = filePathCallback

                            val intent = fileChooserParams?.createIntent()
                                ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                                }

                            try {
                                (ctx as? Activity)?.startActivityForResult(intent, WebViewFileChooser.REQUEST_CODE)
                                    ?: return false
                            } catch (_: Exception) {
                                WebViewFileChooser.filePathCallback?.onReceiveValue(null)
                                WebViewFileChooser.filePathCallback = null
                                return false
                            }
                            return true
                        }
                    }

                    addJavascriptInterface(bridge, "ScriptBridge")
                    loadDataWithBaseURL(
                        "https://bandkit.app",
                        """<html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><link rel="icon" href="data:,"><style>:root{--bg:#ffffff;--text:#333333;--text-muted:#666666;--border:#dddddd;--button-bg:#4a9eff;--button-text:#ffffff;--info-text:#999999}@media(prefers-color-scheme:dark){:root{--bg:#1e1e1e;--text:#e0e0e0;--text-muted:#aaaaaa;--border:#555555;--button-bg:#4a9eff;--button-text:#ffffff;--info-text:#888888}}</style></head><body style="background:var(--bg);color:var(--text);font-family:system-ui,sans-serif;margin:0;padding:0;"></body></html>""",
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (showConsole) 0.6f else 1f),
        )

        // 可折叠控制台
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showConsole = !showConsole }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showConsole) "\u25bc" else "\u25b6",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "控制台",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (consoleLog.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${consoleLog.size})",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        AnimatedVisibility(
            visible = showConsole,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(160.dp),
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(consoleLog.size) {
                    if (consoleLog.isNotEmpty()) listState.animateScrollToItem(consoleLog.lastIndex)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    state = listState,
                    reverseLayout = true,
                ) {
                    items(consoleLog, key = { it.id }) { entry ->
                        val color = when (entry.level) {
                            "error" -> MiuixTheme.colorScheme.error
                            "warn" -> Color(0xFFFF9800)
                            "info" -> MiuixTheme.colorScheme.primary
                            else -> MiuixTheme.colorScheme.onSurface
                        }
                        Text(
                            text = "[${entry.level.uppercase()}] ${entry.message}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** WebView 文件选择器回调存储 */
object WebViewFileChooser {
    var filePathCallback: ValueCallback<Array<android.net.Uri>>? = null
    const val REQUEST_CODE = 1001
}
