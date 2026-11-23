package com.carcare.analytics.core.network

import com.carcare.analytics.BuildConfig
import com.carcare.analytics.core.security.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.carcare.analytics.feature.logbook.JournalApi
import com.carcare.analytics.feature.insights.InsightsApi
import com.carcare.analytics.feature.forecast.ForecastApi

@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun moshi(): Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    @Provides @Singleton fun client(tokens: TokenStore): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val isAuth = request.url.encodedPath.endsWith("/auth/login") || request.url.encodedPath.endsWith("/auth/register")
            val token = tokens.session.value?.accessToken.takeUnless { isAuth }
            val authenticated = request.newBuilder().apply { token?.let { header("Authorization", "Bearer $it") } }.build()
            chain.proceed(authenticated).also { if (it.code == 401 && token != null) tokens.invalidate(token) }
        }.build()
    @Provides @Singleton fun retrofit(client: OkHttpClient, moshi: Moshi): Retrofit {
        require(BuildConfig.API_BASE_URL.endsWith('/')) { "API_BASE_URL must end with /" }
        require(BuildConfig.DEBUG || BuildConfig.API_BASE_URL.startsWith("https://")) { "Release requires HTTPS API_BASE_URL" }
        return Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build()
    }
    @Provides @Singleton fun api(retrofit: Retrofit): CarCareApi = retrofit.create(CarCareApi::class.java)
    @Provides @Singleton fun journalApi(retrofit: Retrofit): JournalApi = retrofit.create(JournalApi::class.java)
    @Provides @Singleton fun insightsApi(retrofit: Retrofit): InsightsApi = retrofit.create(InsightsApi::class.java)
    @Provides @Singleton fun forecastApi(retrofit: Retrofit): ForecastApi = retrofit.create(ForecastApi::class.java)
}
