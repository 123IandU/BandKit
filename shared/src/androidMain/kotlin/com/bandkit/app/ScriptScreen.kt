// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandkit.app.core.createFilePicker
import com.bandkit.app.core.currentTimeMillis
import com.bandkit.app.core.pickFileFromPicker
import com.bandkit.app.models.DeviceSession
import com.bandkit.app.models.ScriptDoc
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.scripta.editor.CodeEditor
import top.yukonga.scripta.editor.rememberSaveableCodeEditorController

private const val PREFS_NAME = "bandkit_scripts"
private const val KEY_SCRIPTS = "saved_scripts"
private val json = Json { ignoreUnknownKeys = true }

@Composable
actual fun PlatformScriptScreen(session: DeviceSession?) {
    val context = LocalContext.current

    val scripts = remember { mutableStateListOf<ScriptDoc>() }
    var currentScript by remember { mutableStateOf<ScriptDoc?>(null) }
    var showFileManager by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var newScriptName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val filePicker = remember { createFilePicker() }

    val controller = rememberSaveableCodeEditorController(
        initialText = currentScript?.content ?: "",
    )

    // 加载脚本列表，打开第一个
    LaunchedEffect(Unit) {
        scripts.clear()
        scripts.addAll(loadScripts(context))
        if (currentScript == null && scripts.isNotEmpty()) {
            currentScript = scripts[0]
        }
    }

    // 页面恢复时重新加载（从 ScriptRunnerActivity 返回后刷新列表）
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scripts.clear()
                scripts.addAll(loadScripts(context))
                // 保持当前选中的脚本
                val cur = currentScript
                if (cur != null) {
                    currentScript = scripts.find { it.id == cur.id } ?: scripts.firstOrNull()
                }
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // 当前脚本切换时更新编辑器
    LaunchedEffect(currentScript?.id) {
        currentScript?.let { controller.setDocument(it.content) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部操作栏
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 保存
                IconButton(onClick = {
                    val script = currentScript ?: return@IconButton
                    val updated = script.copy(
                        content = controller.getText(),
                        updatedAt = currentTimeMillis(),
                    )
                    val idx = scripts.indexOfFirst { it.id == script.id }
                    if (idx >= 0) scripts[idx] = updated
                    currentScript = updated
                    saveScripts(context, scripts.toList())
                }) {
                    Icon(MiuixIcons.Ok, contentDescription = "保存", modifier = Modifier.size(20.dp))
                }
                // 运行
                IconButton(onClick = {
                    val code = controller.getText()

                    @Suppress("UNCHECKED_CAST")
                    val activityClass = Class.forName("com.bandkit.app.ScriptRunnerActivity") as Class<android.app.Activity>
                    val intent = Intent(context, activityClass).apply {
                        putExtra(EXTRA_SCRIPT_CODE, code)
                        putExtra(EXTRA_DEVICE_ADDR, session?.device?.addr ?: "")
                        putExtra(EXTRA_DEVICE_NAME, session?.device?.name ?: "")
                        putExtra(EXTRA_AUTH_KEY, session?.device?.authkey ?: "")
                    }
                    context.startActivity(intent)
                }) {
                    Icon(MiuixIcons.Play, contentDescription = "运行", modifier = Modifier.size(20.dp))
                }
                // 新建
                IconButton(onClick = { showNewDialog = true }) {
                    Icon(MiuixIcons.Add, contentDescription = "新建脚本", modifier = Modifier.size(20.dp))
                }
                // 导入
                IconButton(onClick = {
                    scope.launch {
                        val picked = pickFileFromPicker(filePicker)
                        if (picked != null) {
                            val content = picked.data.toString(Charsets.UTF_8)
                            val name = picked.name.removeSuffix(".js").removeSuffix(".ts")
                                .removeSuffix(".jsx").removeSuffix(".tsx")
                            val script = ScriptDoc.create(name = name, content = content)
                            scripts.add(script)
                            currentScript = script
                            saveScripts(context, scripts.toList())
                        }
                    }
                }) {
                    Icon(MiuixIcons.Import, contentDescription = "导入JS文件", modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // 文件管理
                IconButton(onClick = { showFileManager = true }) {
                    Icon(MiuixIcons.Edit, contentDescription = "文件管理", modifier = Modifier.size(20.dp))
                }
            }

            // 当前文件名
            currentScript?.let { script ->
                Text(
                    text = script.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            // 编辑器
            Card(
                modifier = Modifier.fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                CodeEditor(
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 文件管理对话框
        if (showFileManager) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showFileManager = false },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(400.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = "脚本管理",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(scripts) { script ->
                                val isCurrent = script.id == currentScript?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrent) {
                                                MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .clickable {
                                            currentScript = script
                                            showFileManager = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = script.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = formatTime(script.updatedAt),
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scripts.remove(script)
                                            if (currentScript?.id == script.id) {
                                                currentScript = scripts.firstOrNull()
                                            }
                                            saveScripts(context, scripts.toList())
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            MiuixIcons.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MiuixTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = {
                                    showFileManager = false
                                    @Suppress("UNCHECKED_CAST")
                                    val cls = Class.forName("com.bandkit.app.ScriptRunnerActivity") as Class<android.app.Activity>
                                    val intent = Intent(context, cls).apply {
                                        putExtra(EXTRA_SCRIPT_CODE, SCRIPT_STORE_CODE)
                                        putExtra(EXTRA_DEVICE_ADDR, session?.device?.addr ?: "")
                                        putExtra(EXTRA_DEVICE_NAME, session?.device?.name ?: "")
                                        putExtra(EXTRA_AUTH_KEY, session?.device?.authkey ?: "")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text("在线商店")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { showFileManager = false }) {
                                Text("关闭")
                            }
                        }
                    }
                }
            }
        }

        // 新建脚本对话框
        if (showNewDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable {
                        showNewDialog = false
                        newScriptName = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "新建脚本",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MiuixTextField(
                            value = newScriptName,
                            onValueChange = { newScriptName = it },
                            label = "脚本名称",
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            MiuixDialogButton(
                                text = "取消",
                                onClick = {
                                    showNewDialog = false
                                    newScriptName = ""
                                },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            MiuixDialogButton(
                                text = "创建",
                                primary = true,
                                onClick = {
                                    val name = newScriptName.trim().ifEmpty { "未命名脚本" }
                                    val script = ScriptDoc.create(
                                        name = name,
                                        content = """// $name
sandbox.log("Hello from $name!");""",
                                    )
                                    scripts.add(script)
                                    currentScript = script
                                    saveScripts(context, scripts.toList())
                                    showNewDialog = false
                                    newScriptName = ""
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> "${diff / 86400_000} 天前"
    }
}

@Composable
private fun MiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    top.yukonga.miuix.kmp.basic.TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MiuixDialogButton(
    text: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(text)
        }
    } else {
        Button(onClick = onClick) {
            Text(text)
        }
    }
}

private fun loadScripts(context: Context): List<ScriptDoc> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_SCRIPTS, null) ?: return defaultScripts()
    return try {
        json.decodeFromString<List<ScriptDoc>>(raw)
    } catch (e: Exception) {
        defaultScripts()
    }
}

private fun saveScripts(context: Context, scripts: List<ScriptDoc>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SCRIPTS, json.encodeToString(scripts)).apply()
}

private fun defaultScripts(): List<ScriptDoc> {
    val now = currentTimeMillis()
    return listOf(
        ScriptDoc(
            id = "example_hello",
            name = "Hello World",
            content = """// Hello World 示例
sandbox.log("Hello from BandKit!");

// 获取当前设备信息
const data = sandbox.wasm.miwear_get_data("", "info");
sandbox.log("设备数据: " + JSON.stringify(data));""",
            createdAt = now,
            updatedAt = now,
        ),
        ScriptDoc(
            id = "example_watchface",
            name = "表盘管理",
            content = """// 获取表盘列表
const watchfaces = sandbox.wasm.watchface_get_list("");
sandbox.log("表盘数量: " + watchfaces.length);

if (watchfaces.length > 0) {
    sandbox.log("当前表盘: " + watchfaces[0].name);
}""",
            createdAt = now,
            updatedAt = now,
        ),
    )
}

const val EXTRA_SCRIPT_CODE = "script_code"
const val EXTRA_DEVICE_ADDR = "device_addr"
const val EXTRA_DEVICE_NAME = "device_name"
const val EXTRA_AUTH_KEY = "auth_key"

private val SCRIPT_STORE_CODE = """
// 在线脚本商店
sandbox.log("=== 在线脚本商店 ===");

var INDEX_URL = "https://bandburgscript.02studio.xyz/scripts.json";

async function fetchScriptList() {
    sandbox.log("正在获取脚本列表...");
    var resp = await fetch(INDEX_URL);
    if (!resp.ok) throw new Error("HTTP " + resp.status);
    return await resp.json();
}

async function fetchScriptCode(url) {
    var resp = await fetch(url);
    if (!resp.ok) throw new Error("HTTP " + resp.status);
    return await resp.text();
}

var gui = sandbox.gui({
    title: "在线脚本商店",
    elements: [
        { type: "label", text: "点击「刷新」获取最新脚本列表" },
        { type: "label", id: "status", text: "就绪", style: "color:#888;font-size:12px;" },
        { type: "button", id: "refresh", text: "刷新列表" },
        { type: "label", id: "listArea", text: "暂无数据", style: "white-space:pre-wrap;min-height:80px;border:1px solid #444;border-radius:6px;padding:8px;font-size:12px;" }
    ]
});

var scriptEntries = [];

gui.on("button:click", "refresh", async function() {
    try {
        gui.setValue("status", "加载中...");
        scriptEntries = await fetchScriptList();
        var lines = scriptEntries.map(function(s, i) {
            return (i + 1) + ". " + s.name + " - " + s.author;
        });
        gui.setValue("listArea", lines.join("\n"));
        gui.setValue("status", "共 " + scriptEntries.length + " 个脚本");

        // 创建下载按钮区域
        var btnArea = document.getElementById("__dl_buttons__");
        if (btnArea) btnArea.remove();
        btnArea = document.createElement("div");
        btnArea.id = "__dl_buttons__";
        btnArea.style.cssText = "margin-top:8px;display:flex;flex-wrap:wrap;gap:6px;";

        scriptEntries.forEach(function(entry) {
            var btn = document.createElement("button");
            btn.textContent = entry.name;
            btn.style.cssText = "padding:8px 12px;border:none;border-radius:6px;background:#4a9eff;color:#fff;font-size:13px;cursor:pointer;";
            btn.onclick = async function() {
                try {
                    btn.textContent = "...";
                    btn.disabled = true;
                    var code = await fetchScriptCode(entry.url);
                    var ok = sandbox.saveScript(entry.name, code);
                    if (ok) {
                        sandbox.log("已保存: " + entry.name);
                        btn.textContent = "已保存";
                        btn.style.background = "#555";
                    } else {
                        sandbox.log("已存在: " + entry.name);
                        btn.textContent = "已存在";
                        btn.style.background = "#555";
                    }
                } catch (e) {
                    sandbox.log("失败: " + entry.name + " - " + e.message);
                    btn.textContent = "失败";
                    btn.style.background = "#e04040";
                    btn.disabled = false;
                }
            };
            btnArea.appendChild(btn);
        });

        var allBtn = document.createElement("button");
        allBtn.textContent = "全部下载";
        allBtn.style.cssText = "padding:8px 12px;border:none;border-radius:6px;background:#e04040;color:#fff;font-size:13px;cursor:pointer;";
        allBtn.onclick = async function() {
            allBtn.disabled = true;
            allBtn.textContent = "下载中...";
            for (var i = 0; i < scriptEntries.length; i++) {
                var entry = scriptEntries[i];
                try {
                    var code = await fetchScriptCode(entry.url);
                    sandbox.saveScript(entry.name, code);
                    sandbox.log("(" + (i+1) + "/" + scriptEntries.length + ") " + entry.name);
                } catch (e) {}
            }
            allBtn.textContent = "完成";
            allBtn.style.background = "#555";
            sandbox.log("全部下载完成，请返回脚本页查看");
        };
        btnArea.appendChild(allBtn);

        document.body.appendChild(btnArea);
    } catch (e) {
        gui.setValue("status", "加载失败: " + e.message);
        sandbox.log("获取列表失败: " + e.message);
    }
});

sandbox.log("请点击「刷新列表」开始");
""".trimIndent()
