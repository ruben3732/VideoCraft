package com.videocraft.editor.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// ─── Pexels Data Classes ───────────────────────────────────────────────────────

data class PexelsPhotoResponse(
    val page: Int,
    @SerializedName("per_page") val perPage: Int,
    val photos: List<PexelsPhoto>,
    @SerializedName("total_results") val totalResults: Int,
    @SerializedName("next_page") val nextPage: String?
)

data class PexelsPhoto(
    val id: Int,
    val width: Int,
    val height: Int,
    val url: String,
    val photographer: String,
    val src: PexelsPhotoSrc
)

data class PexelsPhotoSrc(
    val original: String,
    val large2x: String,
    val large: String,
    val medium: String,
    val small: String,
    val portrait: String,
    val landscape: String,
    val tiny: String
)

data class PexelsVideoResponse(
    val page: Int,
    @SerializedName("per_page") val perPage: Int,
    val videos: List<PexelsVideo>,
    @SerializedName("total_results") val totalResults: Int,
    @SerializedName("next_page") val nextPage: String?
)

data class PexelsVideo(
    val id: Int,
    val width: Int,
    val height: Int,
    val duration: Int,
    val url: String,
    val image: String,             // thumbnail
    @SerializedName("video_files") val videoFiles: List<PexelsVideoFile>,
    @SerializedName("video_pictures") val videoPictures: List<PexelsVideoPicture>
)

data class PexelsVideoFile(
    val id: Int,
    val quality: String,
    @SerializedName("file_type") val fileType: String,
    val width: Int?,
    val height: Int?,
    val link: String
)

data class PexelsVideoPicture(
    val id: Int,
    val picture: String,
    val nr: Int
)

// ─── Pexels API Service ────────────────────────────────────────────────────────

interface PexelsApiService {

    @GET("v1/search")
    suspend fun searchPhotos(
        @Query("query") query: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("orientation") orientation: String? = null   // "landscape" | "portrait" | "square"
    ): PexelsPhotoResponse

    @GET("v1/curated")
    suspend fun getCuratedPhotos(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): PexelsPhotoResponse

    @GET("videos/search")
    suspend fun searchVideos(
        @Query("query") query: String,
        @Query("per_page") perPage: Int = 15,
        @Query("page") page: Int = 1,
        @Query("orientation") orientation: String? = null,
        @Query("size") size: String? = null   // "large" | "medium" | "small"
    ): PexelsVideoResponse

    @GET("videos/popular")
    suspend fun getPopularVideos(
        @Query("per_page") perPage: Int = 15,
        @Query("page") page: Int = 1
    ): PexelsVideoResponse
}
