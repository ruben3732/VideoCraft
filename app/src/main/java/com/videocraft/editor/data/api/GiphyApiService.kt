package com.videocraft.editor.data.api

import com.google.gson.annotations.SerializedName
import com.videocraft.editor.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

data class GiphyResponse(
    val data: List<GiphyGif>,
    val pagination: GiphyPagination,
    val meta: GiphyMeta
)

data class GiphyGif(
    val id: String,
    val title: String,
    val url: String,
    val images: GiphyImages,
    val rating: String
)

data class GiphyImages(
    val original: GiphyImage,
    @SerializedName("fixed_height") val fixedHeight: GiphyImage,
    @SerializedName("fixed_width") val fixedWidth: GiphyImage,
    @SerializedName("downsized") val downsized: GiphyImage,
    @SerializedName("preview_gif") val previewGif: GiphyImage
)

data class GiphyImage(
    val url: String,
    val width: String,
    val height: String,
    val size: String? = null,
    val frames: String? = null
)

data class GiphyPagination(
    val count: Int,
    val offset: Int,
    @SerializedName("total_count") val totalCount: Int
)

data class GiphyMeta(
    val status: Int,
    val msg: String
)

interface GiphyApiService {

    @GET("v1/gifs/search")
    suspend fun searchGifs(
        @Query("api_key") apiKey: String = BuildConfig.GIPHY_API_KEY,
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "pg-13",
        @Query("lang") lang: String = "en"
    ): GiphyResponse

    @GET("v1/gifs/trending")
    suspend fun getTrendingGifs(
        @Query("api_key") apiKey: String = BuildConfig.GIPHY_API_KEY,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "pg-13"
    ): GiphyResponse
}
