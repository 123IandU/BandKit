// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package com.bandkit.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 弹出 WebView 加载小米验证码页面。
 * 用户完成验证后小米会重定向到 callbackUrl，
 * 在重定向时提取 URL 并关闭弹窗。
 * 如果用户点取消，onResult(null) 会被调用。
 */
@SuppressLint("SetJavaScriptEnabled")
fun showCaptchaDialog(
    context: Context,
    captchaUrl: String,
    callbackUrl: String,
    onResult: (redirectUrl: String?) -> Unit,
) {
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
    }

    val dialog = AlertDialog.Builder(context)
        .setTitle("验证码")
        .setView(webView)
        .setCancelable(false)
        .setNegativeButton("取消") { _, _ ->
            onResult(null)
        }
        .create()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            // 检测是否重定向到 callback URL
            if (url != null && url.startsWith(callbackUrl)) {
                dialog.dismiss()
                onResult(url)
            }
        }
    }

    webView.loadUrl(captchaUrl)
    dialog.show()
}
