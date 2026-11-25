package com.example.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import com.example.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WebError(
    val errorCode: Int,
    val description: String,
    val isOffline: Boolean,
)

data class MiniAppUiState(
    val currentUrl: String = AppConfig.TARGET_URL,
    val canGoBack: Boolean = false,
    val errorState: WebError? = null,
)

class MiniAppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MiniAppUiState())
    val uiState: StateFlow<MiniAppUiState> = _uiState.asStateFlow()

    var webViewStateBundle: Bundle? = null

    fun onPageStarted(url: String?) {
        _uiState.update { state ->
            state.copy(
                currentUrl = url ?: state.currentUrl,
                errorState = null,
            )
        }
    }

    fun onPageVisibleOrFinished(url: String?, canGoBack: Boolean) {
        _uiState.update { state ->
            state.copy(
                currentUrl = url ?: state.currentUrl,
                canGoBack = canGoBack,
            )
        }
    }

    fun onHistoryUpdated(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    fun onMainFrameError(errorCode: Int, description: String, isOffline: Boolean) {
        _uiState.update { state ->
            state.copy(
                errorState = WebError(
                    errorCode = errorCode,
                    description = description,
                    isOffline = isOffline,
                ),
            )
        }
    }

    fun clearErrorForRetry() {
        _uiState.update { state ->
            state.copy(errorState = null)
        }
    }
}
