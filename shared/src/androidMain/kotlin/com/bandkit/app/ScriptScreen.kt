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
import top.yukonga.miuix.kmp.icon.extended.Link
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
    var selectedIndex by remember { mutableStateOf(-1) }
    var showNewDialog by remember { mutableStateOf(false) }
    var newScriptName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val filePicker = remember { createFilePicker() }

    // 当前编辑的脚本
    fun selectedScript(): ScriptDoc? = if (selectedIndex in scripts.indices) scripts[selectedIndex] else null

    val controller = rememberSaveableCodeEditorController(
        initialText = selectedScript()?.content ?: """// BandKit Script
sandbox.log("Hello from BandKit!");

// 获取设备数据
const data = sandbox.wasm.miwear_get_data("", "info");
sandbox.log("Device data: " + JSON.stringify(data));""",
    )

    // 加载脚本
    LaunchedEffect(Unit) {
        scripts.clear()
        scripts.addAll(loadScripts(context))
        if (scripts.isNotEmpty()) selectedIndex = 0
    }

    // 当切换选中脚本时更新编辑器内容
    LaunchedEffect(selectedIndex) {
        selectedScript()?.let { controller.setDocument(it.content) }
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
                Button(
                    onClick = {
                        val script = selectedScript() ?: return@Button
                        val updatedScript = script.copy(
                            content = controller.getText(),
                            updatedAt = currentTimeMillis(),
                        )
                        val idx = scripts.indexOf(script)
                        if (idx >= 0) scripts[idx] = updatedScript
                        saveScripts(context, scripts.toList())
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存")
                }
                Button(
                    onClick = {
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
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("运行")
                }
                IconButton(onClick = { showNewDialog = true }) {
                    Icon(MiuixIcons.Add, contentDescription = "新建脚本")
                }
                IconButton(onClick = {
                    scope.launch {
                        val picked = pickFileFromPicker(filePicker)
                        if (picked != null) {
                            val content = picked.data.toString(Charsets.UTF_8)
                            val name = picked.name.removeSuffix(".js").removeSuffix(".ts")
                                .removeSuffix(".jsx").removeSuffix(".tsx")
                            val script = ScriptDoc.create(name = name, content = content)
                            scripts.add(script)
                            selectedIndex = scripts.size - 1
                            saveScripts(context, scripts.toList())
                        }
                    }
                }) {
                    Icon(MiuixIcons.Link, contentDescription = "导入JS文件")
                }
            }

            // 脚本列表 + 编辑器
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            ) {
                // 脚本列表（左侧，约 35% 宽度）
                Card(
                    modifier = Modifier.width(140.dp).padding(end = 8.dp),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "脚本列表",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 4.dp),
                        )
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(scripts) { script ->
                                val idx = scripts.indexOf(script)
                                val isSelected = idx == selectedIndex
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(2.dp, 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = script.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                            .padding(4.dp)
                                            .clickable { selectedIndex = idx },
                                    )
                                    IconButton(
                                        onClick = {
                                            if (idx == selectedIndex && idx < scripts.size - 1) {
                                                selectedIndex = idx
                                            }
                                            scripts.removeAt(idx)
                                            if (selectedIndex >= scripts.size) {
                                                selectedIndex = scripts.size - 1
                                            }
                                            saveScripts(context, scripts.toList())
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(14.dp),
                                            tint = MiuixTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 编辑器（右侧）
                Card(
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        selectedScript()?.let { script ->
                            Text(
                                text = script.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        CodeEditor(
                            controller = controller,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // 新建脚本对话框（半透明遮罩层 + Card）
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
                                    selectedIndex = scripts.size - 1
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
