package org.fenixuz.ads

import androidx.annotation.Keep
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import org.telegram.messenger.BuildConfig
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Novagram: minimal client for the ads backend's "search_ads" — a sponsored channel shown at the top of the
 * dialog search results. Only the two anonymous search_ads endpoints are used (no auth / no JWT /
 * no registration); chat_ads and the advertiser dashboard are intentionally out of scope.
 *
 * The ad is matched server-side by the search query ([AdSearchRequest.tag]); [AdSearchRequest.viewerId]
 * is only for view/click counting and frequency capping. We send the real Telegram user id so the same
 * person is recognised consistently across our Android and iOS clients (both send the same id). This
 * means the search query + user id are disclosed to the ads backend — must be declared in the Play Data
 * Safety form / privacy policy.
 */

// Field types match the live ads-backend OpenAPI schema (v2.0.0): viewer_id and channel_id are STRINGS,
// order_id is a uuid string. We send the Telegram user id as its decimal string and parse channel_id
// back to a long for MessagesController.
@Keep
data class AdSearchRequest(
    val tag: String,
    @com.google.gson.annotations.SerializedName("viewer_id") val viewerId: String
)

@Keep
data class AdSearchResult(
    @com.google.gson.annotations.SerializedName("channel_id") val channelId: String? = null,
    @com.google.gson.annotations.SerializedName("channel_name") val channelName: String? = null,
    @com.google.gson.annotations.SerializedName("order_id") val orderId: String? = null
)

@Keep
data class AdClickRequest(
    @com.google.gson.annotations.SerializedName("order_id") val orderId: String,
    @com.google.gson.annotations.SerializedName("viewer_id") val viewerId: String
)

interface AdsApiService {
    @POST("api/search_ads/order/search/")
    fun searchAd(@Body body: AdSearchRequest): Call<AdSearchResult>

    @POST("api/search_ads/order/click/")
    fun clickAd(@Body body: AdClickRequest): Call<Void>
}

object AdsRetrofitClient {
    // Base URL + API key come from BuildConfig (fed by ADS_BASE_URL / ADS_API_KEY in local.properties), so
    // neither the ads endpoint nor the key lives in the public source tree. Blank when unset — [enabled] is
    // then false and no request is ever made (Retrofit is never built with a blank baseUrl, which would throw).
    private val BASE_URL: String = BuildConfig.ADS_BASE_URL
    private val API_KEY: String = BuildConfig.ADS_API_KEY

    // Both must be present: the search_ads endpoints reject requests without X-API-Key (HTTP 401) since
    // backend v2.1.0, so without a key the feature would only hammer the backend with 401s — disable it instead.
    val enabled: Boolean get() = BASE_URL.isNotBlank() && API_KEY.isNotBlank()

    val service: AdsApiService by lazy {
        // Attach X-API-Key to every request. This Retrofit only ever talks to the ads backend, so a blanket
        // interceptor is safe and also covers any future ads endpoints without per-method annotations.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-API-Key", API_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build()
            .create(AdsApiService::class.java)
    }
}
