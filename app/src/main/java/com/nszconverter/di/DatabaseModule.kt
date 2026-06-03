package com.nszconverter.di

import android.content.Context
import androidx.room.Room
import com.nszconverter.data.local.AppDatabase
import com.nszconverter.data.local.JobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "nsz_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideJobDao(db: AppDatabase): JobDao = db.jobDao()
}
