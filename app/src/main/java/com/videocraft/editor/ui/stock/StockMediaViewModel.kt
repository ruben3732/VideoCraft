package com.videocraft.editor.ui.stock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.videocraft.editor.data.api.StockMediaRepository
import com.videocraft.editor.data.model.StockMediaItem
import kotlinx.coroutines.launch

class StockMediaViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StockMediaRepository()

    private val _mediaItems = MutableLiveData<List<StockMediaItem>>(emptyList())
    val mediaItems: LiveData<List<StockMediaItem>> = _mediaItems

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun searchPhotos(query: String, page: Int = 1, append: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = repository.searchPhotos(query, page)
                _mediaItems.value = if (append) (_mediaItems.value ?: emptyList()) + results else results
            } catch (e: Exception) {
                _errorMessage.value = "Photos search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCuratedPhotos(append: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = repository.getCuratedPhotos()
                _mediaItems.value = if (append) (_mediaItems.value ?: emptyList()) + results else results
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load photos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchVideos(query: String, page: Int = 1, append: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = repository.searchVideos(query, page)
                _mediaItems.value = if (append) (_mediaItems.value ?: emptyList()) + results else results
            } catch (e: Exception) {
                _errorMessage.value = "Video search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchGifs(query: String, page: Int = 1, append: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val offset = (page - 1) * 20
                val results = if (query == "trending") repository.getTrendingGifs(offset)
                              else repository.searchGifs(query, offset)
                _mediaItems.value = if (append) (_mediaItems.value ?: emptyList()) + results else results
            } catch (e: Exception) {
                _errorMessage.value = "GIF search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }
}
