// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

internal fun jsonEscape(s: String): String {
    val escaped = s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "'$escaped'"
}

/**
 * 与 bandburg 兼容的 sandbox JS 桥接代码。
 * 所有 GUI 渲染在同一个 WebView 的 DOM 中完成（与 bandburg 一致），
 * 不需要跨 WebView 通信。
 */
internal fun buildJsBridge(deviceName: String, deviceAddr: String, authKey: String): String = """
// ===== 全局配置 =====
var _deviceName = ${jsonEscape(deviceName)};
var _deviceAddr = ${jsonEscape(deviceAddr)};
var _eventSinks = [];
var _activeGUI = null;

// ===== 事件系统（与 bandburg 一致）=====
var _eventCallbacks = {};

function _emitEvent(eventName, data) {
    // 规范化事件名：device-connected → device_connected
    var normalizedName = eventName.replace(/-/g, '_');
    // 构建标准事件对象（bandburg 兼容格式）
    function wrapEvent(extraData) {
        var ev = { type: normalizedName, event: eventName };
        for (var k in extraData) ev[k] = extraData[k];
        return ev;
    }
    // 分发给指定事件名的回调（直接传递原始 data，不包装）
    var callbacks = _eventCallbacks[normalizedName];
    if (callbacks) {
        var evForNamed = wrapEvent(data);
        callbacks.forEach(function(cb) { try { cb(evForNamed); } catch(e) { _log('[事件] 回调错误: ' + e); } });
    }
    // 分发给通配符 '*' 回调
    var wildcardCallbacks = _eventCallbacks['*'];
    if (wildcardCallbacks) {
        var evForWildcard = wrapEvent(data);
        wildcardCallbacks.forEach(function(cb) { try { cb(evForWildcard); } catch(e) { _log('[事件] 回调错误: ' + e); } });
    }
    // 分发给 register_event_sink 注册的回调
    _eventSinks.forEach(function(cb) {
        try {
            var evForSink = wrapEvent(data);
            cb(evForSink);
        } catch(e) { _log('[事件] 回调错误: ' + e); }
    });
}

// 处理 WASM 日志中的事件消息（与 bandburg processWasmLog 一致）
function _processWasmLog(logMessage) {
    // thirdpartyapp_message
    if (logMessage.indexOf('[WASM] Received third-party app message from') !== -1) {
        var parts = logMessage.split('[WASM] Received third-party app message from');
        if (parts.length > 1) {
            var rest = parts[1].trim();
            var colonIdx = rest.indexOf(':');
            if (colonIdx !== -1) {
                var packageName = rest.substring(0, colonIdx).trim();
                var content = rest.substring(colonIdx + 1).trim();
                var parsed = content;
                try { parsed = JSON.parse(content); } catch(e) {}
                _emitEvent('thirdpartyapp_message', {
                    type: 'thirdpartyapp_message',
                    package_name: packageName,
                    data: parsed,
                    rawMessage: logMessage,
                    timestamp: Date.now()
                });
            }
        }
        return;
    }
    // pb_packet
    if (logMessage.indexOf('[WASM] on_pb_packet:') !== -1) {
        var parts2 = logMessage.split('[WASM] on_pb_packet:');
        if (parts2.length > 1) {
            var packetData;
            try { packetData = JSON.parse(parts2[1].trim()); } catch(e) { packetData = parts2[1].trim(); }
            _emitEvent('pb_packet', {
                type: 'pb_packet',
                packet: packetData,
                rawMessage: logMessage,
                timestamp: Date.now()
            });
        }
        return;
    }
    // device_connected
    if (logMessage.indexOf('[WASM] Device connected:') !== -1 || logMessage.indexOf('[WASM] 设备已连接:') !== -1) {
        _emitEvent('device_connected', {
            type: 'device_connected',
            message: logMessage,
            timestamp: Date.now()
        });
        return;
    }
    // device_disconnected
    if (logMessage.indexOf('[WASM] Device disconnected:') !== -1 || logMessage.indexOf('[WASM] 设备已断开:') !== -1) {
        _emitEvent('device_disconnected', {
            type: 'device_disconnected',
            message: logMessage,
            timestamp: Date.now()
        });
        return;
    }
}

// ===== 控制台桥接（拦截 WASM 日志）=====
var _origConsoleLog = console.log;
var _origConsoleWarn = console.warn;
var _origConsoleError = console.error;

function _log() {
    var args = Array.prototype.slice.call(arguments).join(' ');
    ScriptBridge.log(String(args));
    _processWasmLog(args);
}
function _warn() {
    var args = Array.prototype.slice.call(arguments).join(' ');
    ScriptBridge.warn(String(args));
    _processWasmLog(args);
}
function _error() {
    var args = Array.prototype.slice.call(arguments).join(' ');
    ScriptBridge.error(String(args));
    _processWasmLog(args);
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
        },
        on: function(event, cb) {
            if (!_eventCallbacks[event]) _eventCallbacks[event] = [];
            _eventCallbacks[event].push(cb);
        },
        off: function(event, cb) {
            if (!_eventCallbacks[event]) return;
            var idx = _eventCallbacks[event].indexOf(cb);
            if (idx !== -1) _eventCallbacks[event].splice(idx, 1);
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
                            values[elementId] = file;
                            var infoEl = document.getElementById('__file_info_' + elementId + '__');
                            if (infoEl) infoEl.textContent = file.name + ' (' + (file.size > 1024 ? Math.round(file.size/1024) + 'KB' : file.size + 'B') + ')';
                            var listeners = eventListeners['file:change'][elementId];
                            if (listeners) listeners.forEach(function(cb) { cb(values[elementId]); });
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
