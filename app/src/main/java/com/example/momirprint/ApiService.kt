package com.example.momirprint

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Url

private const val SCRYFALL_BASE_URL = "https://api.scryfall.com/"

/**
 * Scryfall asks every client to send a User-Agent that names the app and not to let the HTTP
 * library pick one. Shared so the API client (below) and the image loader (MomirPrintApp) match.
 */
internal const val SCRYFALL_USER_AGENT = "MomirPrint/0.1"

interface ApiService {
    @GET ("cards/named")
    suspend fun getCardByName(@Query("fuzzy") name: String): MagicCard

    @GET ("cards/random")
    suspend fun getRandomCard(@Query("q") query: String? = null, @Query("format") format: String? = null): MagicCard

    @GET ("cards/search")
    suspend fun searchCards(@Query("q") query: String): CardSearchResponse

    @GET ("cards/autocomplete")
    suspend fun getCardAutocomplete(@Query("q") query: String): AutocompleteResponse

    @GET
    suspend fun getCardByUri(@Url url: String): MagicCard
}

object ScryfallApi {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", SCRYFALL_USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .build()

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SCRYFALL_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
