// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app.core

import com.bandkit.app.models.SavedDevice

object DeviceExportImportState {
    var pendingExportResult: ((Boolean) -> Unit)? = null
    var pendingImportResult: ((List<SavedDevice>?) -> Unit)? = null
    var exportDevices: List<SavedDevice>? = null
    var importLauncher: ((String) -> Unit)? = null
    var exportLauncher: ((String, String) -> Unit)? = null
    var filePickerLauncher: ((String) -> Unit)? = null
}
