package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Конфигуратор стандартного современного Android WebView с соблюдением требований
 * безопасности Google Play Protect (отключён доступ к локальным файлам file://,
 * запрещён небезопасный mixed content, включён Safe Browsing) и без вмешательства
 * в цвета и переключатель тем самого сайта.
 */
object MiniAppWebView {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView {
        return WebView(context).apply {
            configureSettings(this)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureSettings(webView: WebView) {
        webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                true,
            )
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowContentAccess = true
            // Security hardening: block file:// traversal & cross-file access.
            allowFileAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            // Play Protect compliance: disallow unsafe mixed HTTP content inside HTTPS pages.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            // Remove "; wv" token so sites and OAuth treat WebView as standard mobile Chrome.
            userAgentString = userAgentString.replace("; wv", "")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            // Do NOT force-darken or algorithmically invert site CSS so the site's own theme switcher works 100%.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isAlgorithmicDarkeningAllowed = false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_OFF
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }
}
