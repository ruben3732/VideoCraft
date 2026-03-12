package com.videocraft.editor.data.api

import com.google.gson.annotations.SerializedName
import com.videocraft.editor.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

data class PixabayImageResponse(
    val total: Int,
    val totalHits: Int,
    val hits: List<PixabayImage>
)

data class PixabayImage(
    val id: Int,
    val tags: String,
    val previewURL: String,
    val webformatURL: String,
    val largeImageURL: String,
    @SerializedName("imageWidth") val imageWidth: Int,
    @SerializedName("imageHeight") val imageHeight: Int,
    val user: String,
    val downloads: Int
)

data class PixabayVideoResponse(
    val total: Int,
    val totalHits: Int,
    val hits: List<PixabayVideo>
)

data class PixabayVideo(
    val id: Int,
    val tags: String,
    val duration: Int,
    val picture_id: String,
    val videos: PixabayVideoFormats,
    val user: String
) {
    val thumbnailUrl: String get() = "https://i.vimeocdn.com/video/${picture_id}_295x166.jpg"
}

data class PixabayVideoFormats(
    val large: PixabayVideoFile?,
    val medium: PixabayVideoFile?,
    val small: PixabayVideoFile?,
    val tiny: PixabayVideoFile?
)

data class PixabayVideoFile(
    val url: String,
    val width: Int,
    val height: Int,
    val size: Long
)

interface PixabayApiService {

    @GET("api/")
    suspend fun searchImages(
        @Query("key") key: String = BuildConfig.PIXABAY_API_KEY,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("image_type") imageType: String = "photo",
        @Query("orientation") orientation: String = "all",
        @Query("safesearch") safeSearch: Boolean = true
    ): PixabayImageResponse

    @GET("api/videos/")
    suspend fun searchVideos(
        @Query("key") key: String = BuildConfig.PIXABAY_API_KEY,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 15,
        @Query("page") page: Int = 1,
        @Query("video_type") videoType: String = "all",
        @Query("safesearch") safeSearch: Boolean = true
    ): PixabayVideoResponse
}
