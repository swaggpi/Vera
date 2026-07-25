package app.vera.di

import android.content.Context
import androidx.room.Room
import app.vera.core.briefing.BriefingGenerator
import app.vera.core.llm.FakeLlmEngine
import app.vera.core.llm.LlmEngine
import app.vera.core.news.NewsRepository
import app.vera.data.NewsRepositoryImpl
import app.vera.data.ProgressDao
import app.vera.data.ProgressRepository
import app.vera.data.SettingsRepository
import app.vera.data.SourceCatalogProvider
import app.vera.data.VeraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): VeraDatabase =
        Room.databaseBuilder(ctx, VeraDatabase::class.java, "vera.db").build()

    @Provides fun progressDao(db: VeraDatabase): ProgressDao = db.progressDao()

    @Provides @Singleton
    fun progressRepository(dao: ProgressDao): ProgressRepository = ProgressRepository(dao)

    @Provides @Singleton
    fun settingsRepository(@ApplicationContext ctx: Context): SettingsRepository =
        SettingsRepository(ctx)

    @Provides @Singleton
    fun catalog(@ApplicationContext ctx: Context): SourceCatalogProvider =
        SourceCatalogProvider(ctx)

    @Provides @Singleton
    fun newsRepository(client: OkHttpClient): NewsRepository = NewsRepositoryImpl(client)

    // On-device model: FakeLlmEngine until the Gemma .task is side-loaded (see MediaPipeLlmEngine).
    @Provides @Singleton
    fun llmEngine(): LlmEngine = FakeLlmEngine()

    @Provides @Singleton
    fun briefingGenerator(llm: LlmEngine): BriefingGenerator = BriefingGenerator(llm)
}
