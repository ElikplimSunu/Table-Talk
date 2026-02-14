package com.sunueric.tabletalk.di

import androidx.lifecycle.AndroidViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.server.application.Application
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ViewModels with @HiltViewModel are automatically provided by Hilt.
    // Use this module for providing external dependencies (like Retrofit, Room, etc.)

    @Provides
    @Singleton
    fun provideLocalLlamaExecutor(): com.sunueric.tabletalk.agent.LocalLlamaExecutor {
        return com.sunueric.tabletalk.agent.LocalLlamaExecutor()
    }

}