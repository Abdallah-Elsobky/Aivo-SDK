package com.example.aivo.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aivo.sdk.runtime.memory.MemoryStore
import com.example.aivo.BuildConfig
import com.example.aivo.data.ChatRepository
import com.example.aivo.data.ChatRepositoryImpl
import com.example.aivo.memory.MessageDao
import com.example.aivo.memory.RoomMemoryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AivoModule {

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideMemoryStore(dao: MessageDao): MemoryStore {
        return RoomMemoryStore(dao)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        memoryStore: MemoryStore
    ): ChatRepository {
        return ChatRepositoryImpl(
            apiKeyProvider = { BuildConfig.GEMINI_API_KEY }
        )
    }
}
