// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ConsoleEntry(
    val message: String,
    val level: String = "log",
    val timestamp: Long = currentTimeMillis(),
)

/**
 * 与 bandburg 完全兼容的 sandbox JS 桥接代码。
 * register_event_sink 和 gui 在 JS 侧完整实现（无需 Java 桥接回调）。
 */
private fun buildJsBridge(deviceName: String, deviceAddr: String, authKey: String): String = """
// ===== 全局配置 =====
var _deviceName = ${jsonEscape(deviceName)};
var _deviceAddr = ${jsonEscape(deviceAddr)};
var _eventSinks = [];
var _activeGUI = null;

// ===== 控制台桥接 =====
function _log() {
    var args = Array.prototype.slice.call(arguments).join(' ');
    ScriptBridge.log(String(args));
}
function _warn() {
    var args = Array.prototype.slice.call(arguments).join(' ');
    ScriptBridge.warn(String(args));
}
function _error() {
    var args = Array.prototype.slice.call(arguments).join(' ');
    ScriptBridge.error(String(args));
}

// 覆盖 console.log 以捕获所有日志
console.log = _log;
console.warn = _warn;
console.error = _error;

// ===== sandbox 对象（与 bandburg 完全一致）=====
var sandbox = {
    log: _log,
    warn: _warn,
    error: _error,
    currentDevice: _deviceAddr ? {
        name: _deviceName,
        addr: _deviceAddr,
        type: 2,
        transport: 'SPP',
        authKey: ${jsonEscape(authKey)}
    } : null,
    devices: _deviceAddr ? [{ name: _deviceName, addr: _deviceAddr }] : [],
    activeGUI: null,
    utils: {
        hexToBytes: function(h) { return ScriptBridge.hexToBytes(String(h)); },
        bytesToHex: function(b) { return ScriptBridge.bytesToHex(String(b)); }
    },
    wasm: {
        miwear_connect: function(addr, authkey) { return ScriptBridge.deviceConnect(String(addr), String(authkey||'')); },
        miwear_disconnect: function(addr) { return ScriptBridge.deviceDisconnect(String(addr)); },
        miwear_get_connected_devices: function() { return _deviceAddr ? [{name:_deviceName,addr:_deviceAddr}] : []; },
        miwear_get_data: function(addr, type) { return ScriptBridge.getDeviceData(String(type)); },
        miwear_get_file_type: function(addr, path) { return ''; },
        miwear_install: function(addr, data) { return false; },
        thirdpartyapp_get_list: function(addr) { try { return JSON.parse(ScriptBridge.getAppList()); } catch(e) { return []; } },
        thirdpartyapp_launch: function(addr, pkg, msg) { return ScriptBridge.launchApp(String(pkg)); },
        thirdpartyapp_send_message: function(addr, pkg, data) { return ScriptBridge.sendMessage(String(pkg), String(data)); },
        thirdpartyapp_uninstall: function(addr, pkg) { return ScriptBridge.uninstallApp(String(pkg)); },
        watchface_get_list: function(addr) { try { return JSON.parse(ScriptBridge.getWatchfaceList()); } catch(e) { return []; } },
        watchface_set_current: function(addr, id) { return ScriptBridge.setWatchface(String(id)); },
        watchface_uninstall: function(addr, id) { return ScriptBridge.uninstallWatchface(String(id)); },
        register_event_sink: function(cb) {
            if (typeof cb === 'function') {
                _eventSinks.push(cb);
                _log('[事件] 事件监听器已注册');
            }
        }
    },
    gui: function(config) {
        _log('[GUI] 创建界面: ' + (config.title || ''));
        // 返回一个虚拟控制器，所有操作映射到控制台日志
        var controller = {
            _id: 'gui_' + Date.now(),
            _elements: config.elements || [],
            _listeners: {},
            getValues: function() {
                var vals = {};
                if (this._elements) {
                    for (var i = 0; i < this._elements.length; i++) {
                        var el = this._elements[i];
                        if (el.id) vals[el.id] = el.value || '';
                    }
                }
                return vals;
            },
            getValue: function(id) { return this.getValues()[id] || null; },
            setValue: function(id, val) {
                if (this._elements) {
                    for (var i = 0; i < this._elements.length; i++) {
                        if (this._elements[i].id === id) { this._elements[i].value = val; break; }
                    }
                }
            },
            on: function(event, id, cb) {
                var key = event + ':' + id;
                if (!this._listeners[key]) this._listeners[key] = [];
                this._listeners[key].push(cb);
                _log('[GUI] 绑定事件 ' + key);
            },
            close: function() { _log('[GUI] 窗口关闭'); },
            show: function() { _log('[GUI] 窗口显示'); },
            hide: function() { _log('[GUI] 窗口隐藏'); }
        };
        sandbox.activeGUI = controller;
        _activeGUI = controller;
        // 打印 GUI 元素便于调试
        if (config.elements) {
            config.elements.forEach(function(el) {
                _log('[GUI] 元素: ' + el.type + (el.id ? ' id=' + el.id : '') + (el.label ? ' label=' + el.label : ''));
            });
        }
        return controller;
    },
    storage: {
        get: function(key) { return ScriptBridge.storageGet(String(key)); },
        set: function(key, val) { ScriptBridge.storageSet(String(key), String(val)); },
        remove: function(key) { ScriptBridge.storageRemove(String(key)); },
        clear: function() { ScriptBridge.storageClear(); }
    }
};
var bandkit = sandbox; // 旧版兼容
""".trimIndent()

private fun jsonEscape(s: String): String {
    val escaped = s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "'$escaped'"
}

/**
 * Android → JS 桥接类，所有通过 @JavascriptInterface 暴露的方法
 * 都会被 WebView 的 JS 环境直接调用。
 */
class ScriptBridge(
    private val session: () -> DeviceSession?,
    val onConsole: (String, String) -> Unit,
) {
    @JavascriptInterface
    fun log(message: String) = onConsole(message, "log")

    @JavascriptInterface
    fun warn(message: String) = onConsole(message, "warn")

    @JavascriptInterface
    fun error(message: String) = onConsole(message, "error")

    @JavascriptInterface
    fun deviceConnect(addr: String, authkey: String): Boolean = try {
        NativeDevice.deviceConnect("", addr, authkey, 2, "SPP", ByteArray(0))
        true
    } catch (e: Exception) {
        onConsole("connect failed: ${e.message}", "error")
        false
    }

    @JavascriptInterface
    fun deviceDisconnect(addr: String): Boolean = try {
        NativeDevice.deviceDisconnect(addr)
        true
    } catch (e: Exception) {
        onConsole("disconnect failed: ${e.message}", "error")
        false
    }

    @JavascriptInterface
    fun getWatchfaceList(): String = runCatching {
        val s = session() ?: return "[]"
        NativeDevice.watchfaceGetList(s.device.addr)
    }.getOrElse {
        onConsole("getWatchfaceList: ${it.message}", "error")
        "[]"
    }

    @JavascriptInterface
    fun setWatchface(watchfaceId: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.watchfaceSetCurrent(s.device.addr, watchfaceId)
    }.getOrElse {
        onConsole("setWatchface: ${it.message}", "error")
        false
    }

    @JavascriptInterface
    fun uninstallWatchface(watchfaceId: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.watchfaceUninstall(s.device.addr, watchfaceId)
    }.getOrElse {
        onConsole("uninstallWatchface: ${it.message}", "error")
        false
    }

    @JavascriptInterface
    fun getAppList(): String = runCatching {
        val s = session() ?: return "[]"
        NativeDevice.thirdpartyappGetList(s.device.addr)
    }.getOrElse {
        onConsole("getAppList: ${it.message}", "error")
        "[]"
    }

    @JavascriptInterface
    fun launchApp(packageName: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.thirdpartyappLaunch(s.device.addr, packageName, "")
    }.getOrElse {
        onConsole("launchApp: ${it.message}", "error")
        false
    }

    @JavascriptInterface
    fun sendMessage(packageName: String, data: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.thirdpartyappSendMessage(s.device.addr, packageName, data)
    }.getOrElse {
        onConsole("sendMessage: ${it.message}", "error")
        false
    }

    @JavascriptInterface
    fun uninstallApp(packageName: String): Boolean = runCatching {
        val s = session() ?: return false
        NativeDevice.thirdpartyappUninstall(s.device.addr, packageName)
    }.getOrElse {
        onConsole("uninstallApp: ${it.message}", "error")
        false
    }

    @JavascriptInterface
    fun getDeviceData(dataType: String): String = runCatching {
        val s = session() ?: return "{}"
        NativeDevice.deviceGetData(s.device.addr, dataType)
    }.getOrElse {
        onConsole("getDeviceData: ${it.message}", "error")
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

    // Storage — 保存在内存 Map 中
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScriptRunnerContent(
    scriptCode: String,
    session: DeviceSession?,
    modifier: Modifier = Modifier,
) {
    var isRunning by remember { mutableStateOf(false) }
    var showWebView by remember { mutableIntStateOf(0) } // 0=hidden, 1=shown
    val consoleLog = remember { mutableStateListOf<ConsoleEntry>() }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val deviceName = session?.device?.name ?: ""
    val deviceAddr = session?.device?.addr ?: ""
    val authKey = session?.device?.authkey ?: ""

    val bridge = remember(session) {
        ScriptBridge(
            session = { session },
            onConsole = { msg, level ->
                consoleLog.add(0, ConsoleEntry(msg, level))
                if (consoleLog.size > 200) consoleLog.removeLast()
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (isRunning) {
                        webView?.evaluateJavascript("window.stop();", null)
                        isRunning = false
                        consoleLog.add(0, ConsoleEntry("Script stopped", "info"))
                    } else {
                        consoleLog.clear()
                        consoleLog.add(0, ConsoleEntry("运行脚本...", "info"))
                        isRunning = true
                        val wv = webView
                        if (wv != null) {
                            // 每次运行前重新注入 bridge（包含最新 device 信息）
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
                            consoleLog.add(0, ConsoleEntry("WebView 未就绪", "error"))
                            isRunning = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = if (isRunning) {
                    ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            ) {
                Icon(
                    imageVector = if (isRunning) MiuixIcons.Refresh else MiuixIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isRunning) "停止" else "运行")
            }
            Button(onClick = { consoleLog.clear() }) {
                Text("清空")
            }
        }

        // Console output
        Card(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .weight(if (showWebView > 0) 0.4f else 1f),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "控制台",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyListState(),
                ) {
                    items(consoleLog) { entry ->
                        val color = when (entry.level) {
                            "error" -> MiuixTheme.colorScheme.error
                            "warn" -> Color(0xFFFF9800)
                            "info" -> MiuixTheme.colorScheme.primary
                            else -> MiuixTheme.colorScheme.onSurface
                        }
                        SelectionContainer {
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

        // WebView — 默认隐藏（仅用于执行 JS），有 GUI 时展开
        if (showWebView > 0) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val bridgeJs = buildJsBridge(deviceName, deviceAddr, authKey)
                                view?.evaluateJavascript(bridgeJs, null)
                                webView = view
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                                msg?.let {
                                    bridge.onConsole(
                                        it.message(),
                                        it.messageLevel()?.name?.lowercase() ?: "log",
                                    )
                                }
                                return true
                            }
                        }

                        addJavascriptInterface(bridge, "ScriptBridge")
                        loadDataWithBaseURL(
                            null,
                            """<html><body style="background:#1e1e1e;color:#e0e0e0;font-family:sans-serif;padding:8px;margin:0;"></body></html>""",
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(0.6f),
            )
        }

        // Hidden WebView (always present, for executing JS without GUI)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val bridgeJs = buildJsBridge(deviceName, deviceAddr, authKey)
                            view?.evaluateJavascript(bridgeJs, null)
                            webView = view
                            consoleLog.add(0, ConsoleEntry("脚本引擎就绪" + if (deviceAddr.isNotEmpty()) "（设备: $deviceName）" else "（无设备）", "info"))
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                bridge.onConsole(
                                    it.message(),
                                    it.messageLevel()?.name?.lowercase() ?: "log",
                                )
                            }
                            return true
                        }
                    }

                    addJavascriptInterface(bridge, "ScriptBridge")
                    loadDataWithBaseURL(
                        null,
                        "<html><body></body></html>",
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            modifier = Modifier.size(0.dp),
        )
    }
}
