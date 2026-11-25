package com.example.ui

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.config.AppConfig
import com.example.config.UrlPolicy
import com.example.ui.components.WebErrorOverlay

private const val WEB_COMPAT_POLYFILL_JS = """
(function() {
  try {
    if (typeof window.Notification === 'undefined') {
      window.Notification = {
        permission: 'denied',
        requestPermission: function() { return Promise.resolve('denied'); }
      };
    }
  } catch (e) {}
})();
"""

@Composable
fun WebViewScreen(
    modifier: Modifier = Modifier,
    viewModel: MiniAppViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // Zero-permission system file picker for <input type="file"> inside web pages.
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback != null) {
            if (uris.isEmpty()) {
                callback.onReceiveValue(null)
            } else {
                callback.onReceiveValue(uris.toTypedArray())
            }
        }
    }

    // System Back navigates inside the site's history until the root page.
    BackHandler(enabled = uiState.canGoBack) {
        webViewRef?.goBack()
    }

    // Sync WebView lifecycle and flush cookies so auth sessions & theme preferences persist reliably.
    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    CookieManager.getInstance().flush()
                    webViewRef?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Preserve WebView state bundle on dispose.
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                val bundle = Bundle()
                wv.saveState(bundle)
                viewModel.webViewStateBundle = bundle
                wv.stopLoading()
                wv.destroy()
            }
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
        }
    }

    // True full-bleed edge-to-edge layout without top/bottom black bars; only adjusts for soft keyboard.
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        AndroidView(
            factory = { ctx ->
                MiniAppWebView.create(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        handleFileDownload(
                            context = ctx,
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                        )
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val targetUri = request?.url ?: return false
                            val target = targetUri.toString()
                            val scheme = targetUri.scheme?.lowercase().orEmpty()

                            if (scheme == "http" ||
                                scheme == "https" ||
                                scheme == "javascript" ||
                                scheme == "about" ||
                                scheme == "data" ||
                                scheme == "blob"
                            ) {
                                return if (AppConfig.LOCK_TO_SINGLE_SITE) {
                                    !UrlPolicy.isUrlAllowed(target)
                                } else {
                                    false
                                }
                            }

                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                true
                            }
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            super.onPageStarted(view, url, favicon)
                            view?.evaluateJavascript(WEB_COMPAT_POLYFILL_JS, null)
                            viewModel.onPageStarted(url)
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            super.onPageCommitVisible(view, url)
                            view?.evaluateJavascript(WEB_COMPAT_POLYFILL_JS, null)
                            viewModel.onPageVisibleOrFinished(
                                url = url,
                                canGoBack = view?.canGoBack() == true,
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            viewModel.onPageVisibleOrFinished(
                                url = url,
                                canGoBack = view?.canGoBack() == true,
                            )
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            viewModel.onHistoryUpdated(view?.canGoBack() == true)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val code = error?.errorCode ?: ERROR_UNKNOWN
                                val desc = error?.description?.toString().orEmpty()
                                if (code != ERROR_UNSUPPORTED_SCHEME &&
                                    !desc.contains("ERR_ABORTED", ignoreCase = true)
                                ) {
                                    val isOffline = code == ERROR_HOST_LOOKUP ||
                                        code == ERROR_CONNECT ||
                                        code == ERROR_TIMEOUT ||
                                        code == ERROR_IO
                                    viewModel.onMainFrameError(
                                        errorCode = code,
                                        description = desc,
                                        isOffline = isOffline,
                                    )
                                }
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?,
                        ) {
                            handler?.cancel()
                            viewModel.onMainFrameError(
                                errorCode = ERROR_FAILED_SSL_HANDSHAKE,
                                description = "SSL error (${error?.primaryError ?: -1})",
                                isOffline = false,
                            )
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            view?.reload()
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?,
                        ): Boolean {
                            AlertDialog.Builder(ctx)
                                .setMessage(message.orEmpty())
                                .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                .setOnCancelListener { result?.cancel() }
                                .show()
                            return true
                        }

                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?,
                        ): Boolean {
                            AlertDialog.Builder(ctx)
                                .setMessage(message.orEmpty())
                                .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                                .setOnCancelListener { result?.cancel() }
                                .show()
                            return true
                        }

                        override fun onJsPrompt(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            defaultValue: String?,
                            result: JsPromptResult?,
                        ): Boolean {
                            val input = EditText(ctx).apply {
                                setText(defaultValue.orEmpty())
                            }
                            AlertDialog.Builder(ctx)
                                .setMessage(message.orEmpty())
                                .setView(input)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    result?.confirm(input.text.toString())
                                }
                                .setNegativeButton(android.R.string.cancel) { _, _ ->
                                    result?.cancel()
                                }
                                .setOnCancelListener { result?.cancel() }
                                .show()
                            return true
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            pendingFileCallback?.onReceiveValue(null)
                            pendingFileCallback = filePathCallback
                            val mimeTypes = fileChooserParams?.acceptTypes
                                ?.filter { it.isNotBlank() }
                                ?.toTypedArray()
                                ?.takeIf { it.isNotEmpty() }
                                ?: arrayOf("*/*")
                            return try {
                                fileChooserLauncher.launch(mimeTypes)
                                true
                            } catch (_: Exception) {
                                pendingFileCallback?.onReceiveValue(null)
                                pendingFileCallback = null
                                false
                            }
                        }
                    }

                    val savedBundle = viewModel.webViewStateBundle
                    if (savedBundle != null) {
                        restoreState(savedBundle)
                        viewModel.onPageVisibleOrFinished(
                            url = url ?: AppConfig.TARGET_URL,
                            canGoBack = canGoBack(),
                        )
                    } else {
                        loadUrl(AppConfig.TARGET_URL)
                    }

                    requestFocus()
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("miniapp_webview"),
        )

        uiState.errorState?.let { error ->
            WebErrorOverlay(
                error = error,
                onRetry = {
                    viewModel.clearErrorForRetry()
                    val wv = webViewRef
                    if (wv != null) {
                        if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
                            wv.loadUrl(AppConfig.TARGET_URL)
                        } else {
                            wv.reload()
                        }
                    }
                },
            )
        }
    }
}

private fun handleFileDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
) {
    val parsedUri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val scheme = parsedUri.scheme?.lowercase().orEmpty()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (scheme == "http" || scheme == "https")) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(parsedUri).apply {
                setMimeType(mimeType)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setTitle(fileName)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                dm.enqueue(request)
                return
            }
        } catch (_: Exception) {
            // Fall through to ACTION_VIEW fallback.
        }
    }

    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
