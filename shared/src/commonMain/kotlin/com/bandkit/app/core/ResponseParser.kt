// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import com.bandkit.app.models.DeviceInfo
import com.bandkit.app.models.InstalledApp
import com.bandkit.app.models.Watchface
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

object ResponseParser {

    fun parseDeviceInfo(infoJson: String, statusJson: String, storageJson: String, fallbackName: String): DeviceInfo {
        val merged = mutableMapOf<String, JsonElement>()
        mergeJsonInto(infoJson, merged)
        mergeJsonInto(statusJson, merged)
        mergeJsonInto(storageJson, merged)

        val model = findString(merged, listOf("model", "device_model", "deviceModel", "name", "product", "device_name")) ?: fallbackName
        val firmwareVersion = findString(merged, listOf("firmwareVersion", "firmware_version", "fw_version", "fwVersion", "version", "ver", "firmware")) ?: "-"
        val serialNumber = findString(merged, listOf("serialNumber", "serial_number", "sn", "serial", "device_id", "deviceId")) ?: "-"
        val batteryPercent = parseBattery(merged)
        val (totalStorage, usedStorage, freeStorage) = parseStorage(merged)

        return DeviceInfo(
            model = model,
            firmwareVersion = firmwareVersion,
            serialNumber = serialNumber,
            batteryPercent = batteryPercent,
            totalStorage = totalStorage,
            usedStorage = usedStorage,
            freeStorage = freeStorage,
        )
    }

    fun parseWatchfaceList(jsonString: String): List<Watchface> {
        val items = extractListItems(jsonString) ?: return emptyList()
        return items.mapIndexed { index, obj ->
            Watchface(
                id = findPrimitiveString(obj, listOf("id", "watchface_id", "fileId")) ?: index.toString(),
                name = findPrimitiveString(obj, listOf("name", "title", "filename")) ?: "表盘 $index",
                isCurrent = findPrimitiveBool(obj, listOf("isCurrent", "current", "is_current")),
            )
        }
    }

    fun parseAppList(jsonString: String): List<InstalledApp> {
        val items = extractListItems(jsonString) ?: return emptyList()
        return items.mapIndexed { index, obj ->
            InstalledApp(
                packageName = findPrimitiveString(obj, listOf("packageName", "package_name", "pkg")) ?: "app_$index",
                name = findPrimitiveString(obj, listOf("name", "title", "appName")) ?: "应用 $index",
            )
        }
    }

    private fun extractListItems(jsonString: String): List<JsonObject>? {
        if (jsonString.isBlank() || jsonString == "[]") return null
        return try {
            val element = json.parseToJsonElement(jsonString)
            when (element) {
                is JsonArray -> element.filterIsInstance<JsonObject>()

                is JsonObject -> {
                    val list = element["list"] ?: element["watchfaces"] ?: element["data"]
                        ?: element["apps"] ?: element["quickApps"]
                    if (list is JsonArray) list.filterIsInstance<JsonObject>() else null
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeJsonInto(jsonString: String, merged: MutableMap<String, JsonElement>) {
        if (jsonString.isBlank() || jsonString == "[]") return
        try {
            val obj = json.parseToJsonElement(jsonString).jsonObject
            for ((key, value) in obj) {
                merged[key] = value
            }
        } catch (_: Exception) {}
    }

    private fun findString(data: Map<String, JsonElement>, keys: List<String>): String? {
        for (key in keys) {
            val v = data[key] ?: continue
            val s = v.jsonPrimitive.contentOrNull
            if (!s.isNullOrBlank() && s != "null" && s != "-") return s
        }
        return null
    }

    private fun findPrimitiveString(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val v = obj[key] ?: continue
            val s = v.jsonPrimitive.contentOrNull
            if (!s.isNullOrBlank()) return s
        }
        return null
    }

    private fun findPrimitiveBool(obj: JsonObject, keys: List<String>): Boolean {
        for (key in keys) {
            val v = obj[key] ?: continue
            try {
                return v.jsonPrimitive.boolean
            } catch (_: Exception) {}
        }
        return false
    }

    private fun safeInt(primitive: JsonPrimitive): Int? = try {
        primitive.contentOrNull?.toIntOrNull() ?: primitive.boolean.let { if (it) 1 else 0 }
    } catch (_: Exception) {
        null
    }

    private fun parseBattery(data: Map<String, JsonElement>): Int {
        for (key in listOf("battery", "battery_capacity", "capacity", "battery_percent", "batteryPercent")) {
            val v = data[key] ?: continue
            try {
                if (v is JsonObject) {
                    val cap = v["capacity"]?.jsonPrimitive?.let { safeInt(it) }
                    if (cap != null && cap in 0..100) return cap
                } else {
                    val num = safeInt(v.jsonPrimitive)
                    if (num != null && num in 0..100) return num
                }
            } catch (_: Exception) {}
        }
        for ((key, v) in data) {
            val lk = key.lowercase()
            if (lk.contains("battery") || lk.contains("power") || lk.contains("capacity")) {
                try {
                    if (v is JsonObject) {
                        val cap = v["capacity"]?.jsonPrimitive?.let { safeInt(it) }
                        if (cap != null && cap in 0..100) return cap
                    } else {
                        val num = safeInt(v.jsonPrimitive)
                        if (num != null && num in 0..100) return num
                    }
                } catch (_: Exception) {}
            }
        }
        return 0
    }

    private fun parseStorage(data: Map<String, JsonElement>): Triple<String, String, String> {
        var totalBytes: Long? = null
        var usedBytes: Long? = null

        for (key in listOf("total", "storage_total", "total_storage", "totalStorage", "capacity", "total_capacity")) {
            val v = data[key] ?: continue
            totalBytes = v.jsonPrimitive.contentOrNull?.toLongOrNull()
            if (totalBytes != null) break
        }
        for (key in listOf("used", "storage_used", "used_storage", "usedStorage")) {
            val v = data[key] ?: continue
            usedBytes = v.jsonPrimitive.contentOrNull?.toLongOrNull()
            if (usedBytes != null) break
        }

        if (totalBytes != null && usedBytes != null) {
            return Triple(formatStorage(totalBytes), formatStorage(usedBytes), formatStorage(totalBytes - usedBytes))
        }

        var totalStr: String? = null
        var usedStr: String? = null
        for (key in listOf("total_storage", "totalStorage", "storage_total", "storageTotal", "total_capacity", "storage_total_storage", "storage_capacity")) {
            val v = data[key] ?: continue
            totalStr = v.jsonPrimitive.contentOrNull
            if (totalStr != null) break
        }
        for (key in listOf("used_storage", "usedStorage", "storage_used", "storageUsed", "used_capacity", "storage_used_storage")) {
            val v = data[key] ?: continue
            usedStr = v.jsonPrimitive.contentOrNull
            if (usedStr != null) break
        }

        if (totalStr != null && usedStr != null) {
            return Triple(formatStorageFromAny(totalStr), formatStorageFromAny(usedStr), "-")
        }

        for ((key, v) in data) {
            val lk = key.lowercase()
            if (totalStr == null && (lk.contains("total") || lk.contains("capacity"))) {
                val s = v.jsonPrimitive.contentOrNull
                if (s != null) {
                    totalStr = formatStorageFromAny(s)
                    break
                }
            }
        }

        return Triple(totalStr ?: "-", usedStr ?: "-", "-")
    }

    private fun formatStorageFromAny(storage: String): String {
        val num = storage.toLongOrNull()
        if (num != null && num > 0) return formatStorage(num)
        if (storage.contains("GB") || storage.contains("MB") || storage.contains("KB") || storage.contains("B")) {
            return storage
        }
        return storage
    }

    private fun formatStorage(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        return when {
            bytes >= 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
            bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024L -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}
