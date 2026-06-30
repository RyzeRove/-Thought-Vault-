package com.example.thoughtvault.di

import android.content.Context
import androidx.room.Room
import com.example.thoughtvault.data.local.AppDatabase
import com.example.thoughtvault.data.local.dao.EntryDao
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.remote.WebdavApi
import com.example.thoughtvault.data.remote.WebdavClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWebdavClient(): WebdavClient = WebdavClient()

    @Provides
    @Singleton
    fun provideWebdavApi(client: WebdavClient): WebdavApi = WebdavApi(client)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "thought_vault_cache.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
}
