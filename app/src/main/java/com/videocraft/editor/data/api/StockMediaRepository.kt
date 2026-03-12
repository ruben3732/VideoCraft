package com.videocraft.editor.data.api

import com.videocraft.editor.data.model.StockMediaItem
import com.videocraft.editor.data.model.StockType

class StockMediaRepository {

    // ── Photos ──────────────────────────────────────────────────────────────

    suspend fun searchPhotos(query: String, page: Int = 1): List<StockMediaItem> {
        val results = mutableListOf<StockMediaItem>()
        try {
            val pexels = ApiClient.pexelsApi.searchPhotos(query, page = page)
            results += pexels.photos.map { photo ->
                StockMediaItem(
                    id = "pexels_photo_${photo.id}",
                    type = StockType.PHOTO,
                    thumbnailUrl = photo.src.medium,
                    previewUrl = photo.src.large,
                    downloadUrl = photo.src.original,
                    width = photo.width,
                    height = photo.height,
                    attribution = "Photo by ${photo.photographer} on Pexels"
                )
            }
        } catch (_: Exception) {}

        try {
            val pixabay = ApiClient.pixabayApi.searchImages(query = query, page = page)
            results += pixabay.hits.map { img ->
                StockMediaItem(
                    id = "pixabay_photo_${img.id}",
                    type = StockType.PHOTO,
                    thumbnailUrl = img.previewURL,
                    previewUrl = img.webformatURL,
                    downloadUrl = img.largeImageURL,
                    width = img.imageWidth,
                    height = img.imageHeight,
                    attribution = "Image by ${img.user} on Pixabay",
                    tags = img.tags
                )
            }
        } catch (_: Exception) {}

        return results
    }

    suspend fun getCuratedPhotos(page: Int = 1): List<StockMediaItem> {
        return try {
            ApiClient.pexelsApi.getCuratedPhotos(page = page).photos.map { photo ->
                StockMediaItem(
                    id = "pexels_photo_${photo.id}",
                    type = StockType.PHOTO,
                    thumbnailUrl = photo.src.medium,
                    previewUrl = photo.src.large,
                    downloadUrl = photo.src.original,
                    width = photo.width,
                    height = photo.height,
                    attribution = "Photo by ${photo.photographer} on Pexels"
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Videos ──────────────────────────────────────────────────────────────

    suspend fun searchVideos(query: String, page: Int = 1): List<StockMediaItem> {
        val results = mutableListOf<StockMediaItem>()
        try {
            val pexels = ApiClient.pexelsApi.searchVideos(query, page = page)
            results += pexels.videos.map { video ->
                val hd = video.videoFiles.firstOrNull { it.quality == "hd" }
                    ?: video.videoFiles.firstOrNull { it.quality == "sd" }
                    ?: video.videoFiles.firstOrNull()
                StockMediaItem(
                    id = "pexels_video_${video.id}",
                    type = StockType.VIDEO,
                    thumbnailUrl = video.image,
                    previewUrl = hd?.link ?: "",
                    downloadUrl = hd?.link ?: "",
                    width = video.width,
                    height = video.height,
                    duration = video.duration,
                    attribution = "Video from Pexels"
                )
            }
        } catch (_: Exception) {}

        try {
            val pixabay = ApiClient.pixabayApi.searchVideos(query = query, page = page)
            results += pixabay.hits.map { video ->
                val file = video.videos.medium ?: video.videos.small ?: video.videos.tiny
                StockMediaItem(
                    id = "pixabay_video_${video.id}",
                    type = StockType.VIDEO,
                    thumbnailUrl = video.thumbnailUrl,
                    previewUrl = file?.url ?: "",
                    downloadUrl = file?.url ?: "",
                    duration = video.duration,
                    attribution = "Video by ${video.user} on Pixabay",
                    tags = video.tags
                )
            }
        } catch (_: Exception) {}

        return results
    }

    // ── GIFs ────────────────────────────────────────────────────────────────

    suspend fun searchGifs(query: String, offset: Int = 0): List<StockMediaItem> {
        return try {
            ApiClient.giphyApi.searchGifs(query = query, offset = offset).data.map { gif ->
                StockMediaItem(
                    id = "giphy_${gif.id}",
                    type = StockType.GIF,
                    thumbnailUrl = gif.images.previewGif.url,
                    previewUrl = gif.images.fixedHeight.url,
                    downloadUrl = gif.images.original.url,
                    width = gif.images.original.width.toIntOrNull() ?: 0,
                    height = gif.images.original.height.toIntOrNull() ?: 0,
                    attribution = gif.title
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getTrendingGifs(offset: Int = 0): List<StockMediaItem> {
        return try {
            ApiClient.giphyApi.getTrendingGifs(offset = offset).data.map { gif ->
                StockMediaItem(
                    id = "giphy_${gif.id}",
                    type = StockType.GIF,
                    thumbnailUrl = gif.images.previewGif.url,
                    previewUrl = gif.images.fixedHeight.url,
                    downloadUrl = gif.images.original.url,
                    attribution = gif.title
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
