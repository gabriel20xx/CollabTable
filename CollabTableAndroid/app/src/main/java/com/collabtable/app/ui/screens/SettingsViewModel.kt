package com.collabtable.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _serverUrl = MutableStateFlow(preferencesManager.getServerUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _showSuccessMessage = MutableStateFlow(false)
    val showSuccessMessage: StateFlow<Boolean> = _showSuccessMessage.asStateFlow()

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setServerUrl(url)
            ApiClient.setBaseUrl(url)
            _serverUrl.value = preferencesManager.getServerUrl()
            _showSuccessMessage.value = true
        }
    }

    fun clearSuccessMessage() {
        _showSuccessMessage.value = false
    }
}
