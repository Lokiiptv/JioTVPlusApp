package com.jiotvplus.app.di

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.data.remote.*
import com.jiotvplus.app.data.repository.AuthRepository
import com.jiotvplus.app.data.repository.ChannelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jiotvplus_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(app: Application): DataStore<Preferences> = app.dataStore

    @Provides
    @Singleton
    fun provideTokenStore(dataStore: DataStore<Preferences>): TokenStore = TokenStore(dataStore)

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: TokenStore, @Named("token") tokenApi: JioTokenApi): AuthInterceptor =
        AuthInterceptor(tokenStore, tokenApi)

    @Provides
    @Singleton
    @Named("token")
    fun provideTokenOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("auth")
    fun provideJioAuthApi(client: OkHttpClient): JioAuthApi =
        Retrofit.Builder()
            .baseUrl("https://tv.media.jio.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioAuthApi::class.java)

    @Provides
    @Singleton
    @Named("token")
    fun provideJioTokenApi(@Named("token") client: OkHttpClient): JioTokenApi =
        Retrofit.Builder()
            .baseUrl("https://jiotvapi.media.jio.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioTokenApi::class.java)

    @Provides
    @Singleton
    @Named("content")
    fun provideJioContentApi(client: OkHttpClient): JioContentApi =
        Retrofit.Builder()
            .baseUrl("https://content-jiotvplus.media.jio.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioContentApi::class.java)

    @Provides
    @Singleton
    @Named("playback")
    fun provideJioPlaybackApi(client: OkHttpClient): JioPlaybackApi =
        Retrofit.Builder()
            .baseUrl("https://api-jiotvplus.media.jio.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioPlaybackApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRepository(
        @Named("auth") authApi: JioAuthApi,
        @Named("token") tokenApi: JioTokenApi,
        tokenStore: TokenStore
    ): AuthRepository = AuthRepository(authApi, tokenApi, tokenStore)

    @Provides
    @Singleton
    fun provideChannelRepository(
        @Named("content") contentApi: JioContentApi,
        @Named("playback") playbackApi: JioPlaybackApi,
        tokenStore: TokenStore
    ): ChannelRepository = ChannelRepository(contentApi, playbackApi, tokenStore)
}
