package com.example.momirprint

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.converter.gson.GsonConverterFactory

private const val SCRYFALL_BASE_URL = "https://api.scryfall.com/"

interface ApiService {
    @GET ("cards/named")
    suspend fun getCardByName(@Query("fuzzy") name: String): MagicCard

    @GET ("cards/random")
    suspend fun getRandomCard(@Query("q") query: String, @Query("format") format: String): MagicCard

    @GET ("cards/search")
    suspend fun searchCards(@Query("q") query: String): MagicCard

    //@GET ("cards/autocomplete")
    //suspend fun getCardAutocomplete(@Query("q") query: String): AutocompleteResponse
}

object ScryfallApi {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val httpClient = OkHttpClient.Builder()
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
