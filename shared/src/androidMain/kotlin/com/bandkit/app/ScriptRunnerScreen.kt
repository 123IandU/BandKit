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

private fun jsonEscape(s: String): String {
    val escaped = s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "'$escaped'"
}

private fun jsonEscapeHtml(s: String): String = s.replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

/**
 * 与 bandburg 兼容的 sandbox JS 桥接代码。
 * 所有 GUI 渲染在同一个 WebView 的 DOM 中完成（与 bandburg 一致），
 * 不需要跨 WebView 通信。
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
console.log = _log;
console.warn = _warn;
console.error = _error;

// ===== sandbox 对象（与 bandburg 完全一致）=====
var sandbox = {
    log: _log,
    warn: _warn,
    error: _error,
    currentDevice: {
        name: _deviceName,
        addr: _deviceAddr,
        type: _deviceAddr ? 2 : 0,
        transport: _deviceAddr ? 'SPP' : '',
        authKey: ${jsonEscape(authKey)}
    },
    devices: _deviceAddr ? [{ name: _deviceName, addr: _deviceAddr }] : [],
    activeGUI: null,
    utils: {
        hexToBytes: function(h) { return ScriptBridge.hexToBytes(String(h)); },
        bytesToHex: function(b) { return ScriptBridge.bytesToHex(String(b)); }
    },
    wasm: {
        miwear_connect: function(addr, authkey) {
            return ScriptBridge.deviceConnect(String(addr), String(authkey||''));
        },
        miwear_disconnect: function(addr) {
            return ScriptBridge.deviceDisconnect(String(addr));
        },
        miwear_get_connected_devices: function() {
            return _deviceAddr ? [{name:_deviceName,addr:_deviceAddr}] : [];
        },
        miwear_get_data: function(addr, type) {
            return ScriptBridge.getDeviceData(String(type));
        },
        miwear_get_file_type: function(addr, path) { return ''; },
        miwear_install: function(addr, data) { return false; },
        thirdpartyapp_get_list: function(addr) {
            try { return JSON.parse(ScriptBridge.getAppList()); } catch(e) { return []; }
        },
        thirdpartyapp_launch: function(addr, pkg, msg) {
            return ScriptBridge.launchApp(String(pkg));
        },
        thirdpartyapp_send_message: function(addr, pkg, data) {
            return ScriptBridge.sendMessage(String(pkg), String(data));
        },
        thirdpartyapp_uninstall: function(addr, pkg) {
            return ScriptBridge.uninstallApp(String(pkg));
        },
        watchface_get_list: function(addr) {
            try { return JSON.parse(ScriptBridge.getWatchfaceList()); } catch(e) { return []; }
        },
        watchface_set_current: function(addr, id) {
            return ScriptBridge.setWatchface(String(id));
        },
        watchface_uninstall: function(addr, id) {
            return ScriptBridge.uninstallWatchface(String(id));
        },
        register_event_sink: function(cb) {
            if (typeof cb === 'function') {
                _eventSinks.push(cb);
                _log('[事件] 事件监听器已注册');
            }
            return true;
        }
    },
    gui: function(config) {
        // 关闭之前创建的 GUI
        if (_activeGUI) {
            try { _activeGUI.close(); } catch(e) {}
            _activeGUI = null;
        }

        _log('[GUI] 创建界面: ' + (config.title || ''));

        // 直接替换 body 内容渲染 GUI（Android WebView 不支持 position:fixed）
        document.body.style.cssText = 'margin:0;padding:12px;background:var(--bg);color:var(--text);font-family:system-ui,sans-serif;font-size:14px;overflow-y:auto;';
        document.body.innerHTML = '';

        // 标题栏
        var titleBar = document.createElement('div');
        titleBar.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;';
        var title = document.createElement('h3');
        title.textContent = config.title || 'GUI';
        title.style.cssText = 'color:var(--text);margin:0;font-size:16px;';
        titleBar.appendChild(title);
        var closeBtn = document.createElement('button');
        closeBtn.innerHTML = '&times;';
        closeBtn.style.cssText = 'background:none;border:none;color:var(--text-muted);font-size:24px;cursor:pointer;padding:0 4px;';
        closeBtn.onclick = function() { controller.close(); };
        titleBar.appendChild(closeBtn);
        document.body.appendChild(titleBar);

        // 元素容器
        var container = document.createElement('div');
        document.body.appendChild(container);

        // 存储
        var elements = {};
        var values = {};
        var eventListeners = {
            'button:click': {},
            'input:change': {},
            'file:change': {}
        };

        // 创建表单元素（与 bandburg 一致）
        (config.elements || []).forEach(function(element, index) {
            var elementId = element.id || 'element_' + index;

            switch (element.type) {
                case 'label':
                    if (!element.text) break;
                    var label = document.createElement('div');
                    label.id = elementId;
                    label.textContent = element.text;
                    label.style.cssText = 'padding:8px;border:1px solid var(--border);border-radius:8px;margin-bottom:8px;color:var(--text);' + (element.style || '');
                    container.appendChild(label);
                    elements[elementId] = label;
                    values[elementId] = element.text;
                    break;

                case 'input':
                    if (element.label) {
                        var inputLabel = document.createElement('label');
                        inputLabel.textContent = element.label;
                        inputLabel.style.cssText = 'display:block;margin-bottom:4px;font-weight:bold;color:var(--text-muted);font-size:13px;';
                        container.appendChild(inputLabel);
                    }
                    var input = document.createElement('input');
                    input.type = 'text';
                    input.id = elementId;
                    input.placeholder = element.placeholder || '';
                    input.value = element.value || '';
                    input.style.cssText = 'width:100%;padding:8px;border:1px solid var(--border);border-radius:6px;background:var(--bg);color:var(--text);font-size:14px;box-sizing:border-box;margin-bottom:8px;';
                    input.addEventListener('change', function() {
                        values[elementId] = input.value;
                        var listeners = eventListeners['input:change'][elementId];
                        if (listeners) listeners.forEach(function(cb) { cb(input.value); });
                    });
                    container.appendChild(input);
                    elements[elementId] = input;
                    values[elementId] = input.value;
                    break;

                case 'textarea':
                    if (element.label) {
                        var taLabel = document.createElement('label');
                        taLabel.textContent = element.label;
                        taLabel.style.cssText = 'display:block;margin-bottom:4px;font-weight:bold;color:var(--text-muted);font-size:13px;';
                        container.appendChild(taLabel);
                    }
                    var textarea = document.createElement('textarea');
                    textarea.id = elementId;
                    textarea.placeholder = element.placeholder || '';
                    textarea.value = element.value || '';
                    textarea.style.cssText = 'width:100%;padding:8px;border:1px solid var(--border);border-radius:6px;background:var(--bg);color:var(--text);font-size:14px;min-height:60px;resize:vertical;box-sizing:border-box;margin-bottom:8px;';
                    textarea.addEventListener('change', function() {
                        values[elementId] = textarea.value;
                        var listeners = eventListeners['input:change'][elementId];
                        if (listeners) listeners.forEach(function(cb) { cb(textarea.value); });
                    });
                    container.appendChild(textarea);
                    elements[elementId] = textarea;
                    values[elementId] = textarea.value;
                    break;

                case 'select':
                    if (element.label) {
                        var sLabel = document.createElement('label');
                        sLabel.textContent = element.label;
                        sLabel.style.cssText = 'display:block;margin-bottom:4px;font-weight:bold;color:var(--text-muted);font-size:13px;';
                        container.appendChild(sLabel);
                    }
                    var select = document.createElement('select');
                    select.id = elementId;
                    select.style.cssText = 'width:100%;padding:8px;border:1px solid var(--border);border-radius:6px;background:var(--bg);color:var(--text);font-size:14px;box-sizing:border-box;margin-bottom:8px;';
                    (element.options || []).forEach(function(opt) {
                        var optionEl = document.createElement('option');
                        optionEl.value = opt.value;
                        optionEl.textContent = opt.label || opt.value;
                        if (opt.selected) optionEl.selected = true;
                        select.appendChild(optionEl);
                    });
                    select.addEventListener('change', function() {
                        values[elementId] = select.value;
                        var listeners = eventListeners['input:change'][elementId];
                        if (listeners) listeners.forEach(function(cb) { cb(select.value); });
                    });
                    container.appendChild(select);
                    elements[elementId] = select;
                    values[elementId] = select.value;
                    break;

                case 'button':
                    var button = document.createElement('button');
                    button.textContent = element.text || '按钮';
                    button.id = elementId;
                    button.style.cssText = 'width:100%;padding:10px;border:none;border-radius:8px;background:var(--button-bg);color:var(--button-text);font-size:14px;cursor:pointer;margin-bottom:8px;';
                    button.addEventListener('click', function() {
                        var listeners = eventListeners['button:click'][elementId];
                        if (listeners) listeners.forEach(function(cb) { cb(); });
                    });
                    button.addEventListener('mousedown', function() { button.style.opacity = '0.7'; });
                    button.addEventListener('mouseup', function() { button.style.opacity = '1'; });
                    container.appendChild(button);
                    elements[elementId] = button;
                    break;

                case 'file':
                    if (element.label) {
                        var fLabel = document.createElement('label');
                        fLabel.textContent = element.label;
                        fLabel.style.cssText = 'display:block;margin-bottom:4px;font-weight:bold;color:var(--text-muted);font-size:13px;';
                        container.appendChild(fLabel);
                    }
                    var fileInput = document.createElement('input');
                    fileInput.type = 'file';
                    fileInput.id = elementId;
                    if (element.accept) fileInput.accept = element.accept;
                    fileInput.style.cssText = 'width:100%;padding:8px;border:1px solid var(--border);border-radius:6px;background:var(--bg);color:var(--text);font-size:14px;box-sizing:border-box;margin-bottom:8px;';
                    fileInput.addEventListener('change', function(e) {
                        var file = e.target.files && e.target.files[0];
                        if (file) {
                            var reader = new FileReader();
                            reader.onload = function(event) {
                                var arrayBuffer = event.target.result;
                                var bytes = new Uint8Array(arrayBuffer);
                                var binary = '';
                                for (var i = 0; i < bytes.length; i++) {
                                    binary += String.fromCharCode(bytes[i]);
                                }
                                var base64Data = btoa(binary);
                                values[elementId] = {
                                    name: file.name,
                                    type: file.type,
                                    size: file.size,
                                    data: base64Data
                                };
                                var infoEl = document.getElementById('__file_info_' + elementId + '__');
                                if (infoEl) infoEl.textContent = file.name + ' (' + (file.size > 1024 ? Math.round(file.size/1024) + 'KB' : file.size + 'B') + ')';
                                var listeners = eventListeners['file:change'][elementId];
                                if (listeners) listeners.forEach(function(cb) { cb(values[elementId]); });
                            };
                            reader.readAsArrayBuffer(file);
                        }
                    });
                    container.appendChild(fileInput);
                    var fileInfo = document.createElement('div');
                    fileInfo.id = '__file_info_' + elementId + '__';
                    fileInfo.style.cssText = 'color:var(--info-text);font-size:12px;margin-bottom:8px;';
                    fileInfo.textContent = element.value || '未选择文件';
                    container.appendChild(fileInfo);
                    elements[elementId] = fileInput;
                    break;
            }
        });

        // GUI 控制器（与 bandburg 完全一致）
        var controller = {
            getValues: function() {
                var copy = {};
                for (var k in values) copy[k] = values[k];
                return copy;
            },
            getValue: function(id) { return values[id]; },
            setValue: function(id, value) {
                if (elements[id]) {
                    if (elements[id].tagName === 'DIV') {
                        elements[id].textContent = value;
                    } else if (elements[id].type !== 'file') {
                        elements[id].value = value;
                    }
                }
                values[id] = value;
            },
            on: function(event, id, callback) {
                if (!eventListeners[event]) eventListeners[event] = {};
                if (!eventListeners[event][id]) eventListeners[event][id] = [];
                eventListeners[event][id].push(callback);
                _log('[GUI] 绑定事件 ' + event + ':' + id);
            },
            close: function() {
                document.body.innerHTML = '';
                document.body.style.cssText = 'margin:0;padding:0;background:var(--bg);';
                if (sandbox.activeGUI === controller) sandbox.activeGUI = null;
                if (_activeGUI === controller) _activeGUI = null;
                _log('[GUI] 窗口关闭');
            },
            show: function() { document.body.style.display = 'block'; },
            hide: function() { document.body.style.display = 'none'; }
        };

        sandbox.activeGUI = controller;
        _activeGUI = controller;

        (config.elements || []).forEach(function(el) {
            _log('[GUI] 元素: ' + el.type + (el.id ? ' id=' + el.id : '') + (el.label ? ' label=' + el.label : ''));
        });

        return controller;
    },
    storage: {
        get: function(key) { return ScriptBridge.storageGet(String(key)); },
        set: function(key, val) { ScriptBridge.storageSet(String(key), String(val)); },
        remove: function(key) { ScriptBridge.storageRemove(String(key)); },
        clear: function() { ScriptBridge.storageClear(); }
    },
    saveScript: function(name, content) { return ScriptBridge.saveScript(String(name), String(content)); }
};
var bandkit = sandbox;

// ===== 主题同步（从 Kotlin 检测系统深色/浅色模式）=====
var _lastThemeMode = ScriptBridge.getThemeMode();
var _themes = {
    light: { '--bg':'#ffffff', '--text':'#333333', '--text-muted':'#666666', '--border':'#dddddd', '--button-bg':'#4a9eff', '--button-text':'#ffffff', '--info-text':'#999999' },
    dark:  { '--bg':'#1e1e1e', '--text':'#e0e0e0', '--text-muted':'#aaaaaa', '--border':'#555555', '--button-bg':'#4a9eff', '--button-text':'#ffffff', '--info-text':'#888888' }
};
function _applyTheme(mode) {
    var vars = _themes[mode] || _themes.light;
    var root = document.documentElement;
    for (var k in vars) root.style.setProperty(k, vars[k]);
}
_applyTheme(_lastThemeMode);
setInterval(function() {
    var mode = ScriptBridge.getThemeMode();
    if (mode !== _lastThemeMode) {
        _lastThemeMode = mode;
        _applyTheme(mode);
        _log('[主题] 切换为' + (mode === 'dark' ? '深色' : '浅色') + '模式');
    }
}, 2000);
""".trimIndent()

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
