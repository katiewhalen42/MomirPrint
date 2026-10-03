package com.example.momirprint

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * One instance per app process. Besides holding the shared PrinterService, it implements
 * SingletonImageLoader.Factory: Coil looks for that interface on the Application the first time
 * it needs an ImageLoader, so this is where we customise how every image in the app is fetched.
 */
class MomirPrintApp : Application(), SingletonImageLoader.Factory {
    val printerService by lazy { PrinterService(this) }

    override fun newImageLoader(context: Context): ImageLoader {
        // Scryfall wants a User-Agent naming the app (not the HTTP library's default) and an
        // Accept header. This client adds both to every request Coil makes. `header()` replaces
        // any existing value; the API client's `addHeader` would append a second one.
        // Accept is */* here because these responses are JPEG/PNG; the API client's
        // "application/json" would be wrong for images.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", SCRYFALL_USER_AGENT)
                        .header("Accept", "*/*")
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
