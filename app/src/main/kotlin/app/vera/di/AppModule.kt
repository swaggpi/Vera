package app.vera.di

import android.content.Context
import androidx.room.Room
import app.vera.core.briefing.BriefingGenerator
import app.vera.core.briefing.StoryChat
import app.vera.core.llm.LlmEngine
import app.vera.core.news.NewsRepository
import app.vera.core.research.MultiSearchProvider
import app.vera.core.research.ResearchRepository
import app.vera.core.research.SearchProvider
import app.vera.core.speech.SpeechService
import app.vera.core.training.SiftCoach
import app.vera.data.AndroidSpeechService
import app.vera.data.BriefingCacheDao
import app.vera.data.DuckDuckGoSearchProvider
import app.vera.data.ModelManager
import app.vera.data.NewsRepositoryImpl
import app.vera.data.ProgressDao
import app.vera.data.ProgressRepository
import app.vera.data.ReadLogDao
import app.vera.data.ReadLogRepository
import app.vera.data.SettingsRepository
import app.vera.data.SecureKeyStore
import app.vera.data.SourceCatalogProvider
import app.vera.data.SwitchableLlmEngine
import app.vera.data.VeraDatabase
import app.vera.data.WikipediaSearchProvider
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
        Room.databaseBuilder(ctx, VeraDatabase::class.java, "vera.db")
            // The briefing cache is disposable; a schema bump can safely rebuild it.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun progressDao(db: VeraDatabase): ProgressDao = db.progressDao()
    @Provides fun readLogDao(db: VeraDatabase): ReadLogDao = db.readLogDao()
    @Provides fun briefingCacheDao(db: VeraDatabase): BriefingCacheDao = db.briefingCacheDao()

    @Provides @Singleton
    fun progressRepository(dao: ProgressDao): ProgressRepository = ProgressRepository(dao)

    @Provides @Singleton
    fun readLogRepository(dao: ReadLogDao, catalog: SourceCatalogProvider): ReadLogRepository =
        ReadLogRepository(dao, catalog)

    @Provides @Singleton
    fun settingsRepository(@ApplicationContext ctx: Context): SettingsRepository =
        SettingsRepository(ctx)

    @Provides @Singleton
    fun catalog(@ApplicationContext ctx: Context): SourceCatalogProvider =
        SourceCatalogProvider(ctx)

    @Provides @Singleton
    fun newsRepository(client: OkHttpClient): NewsRepository = NewsRepositoryImpl(client)

    // On-device model: FakeLlmEngine until the Gemma .task is downloaded, then real MediaPipe/Gemma.
    @Provides @Singleton
    fun secureKeyStore(@ApplicationContext ctx: Context): SecureKeyStore = SecureKeyStore(ctx)

    @Provides @Singleton
    fun switchableEngine(
        @ApplicationContext ctx: Context,
        keys: SecureKeyStore,
        client: OkHttpClient
    ): SwitchableLlmEngine = SwitchableLlmEngine(ctx, keys, client)

    @Provides @Singleton
    fun llmEngine(engine: SwitchableLlmEngine): LlmEngine = engine

    @Provides @Singleton
    fun modelManager(@ApplicationContext ctx: Context, engine: SwitchableLlmEngine): ModelManager =
        ModelManager(ctx, engine)

    @Provides @Singleton
    fun briefingGenerator(llm: LlmEngine): BriefingGenerator = BriefingGenerator(llm)

    @Provides @Singleton
    fun storyChat(llm: LlmEngine, search: SearchProvider): StoryChat = StoryChat(llm, search)

    // Multi-domain first (variety of outlets), Wikipedia for background/definitions.
    @Provides @Singleton
    fun searchProvider(client: OkHttpClient): SearchProvider = MultiSearchProvider(
        listOf(DuckDuckGoSearchProvider(client), WikipediaSearchProvider(client))
    )

    @Provides @Singleton
    fun siftCoach(llm: LlmEngine): SiftCoach = SiftCoach(llm)

    @Provides @Singleton
    fun researchRepository(llm: LlmEngine, search: SearchProvider): ResearchRepository =
        ResearchRepository(llm, search)

    @Provides @Singleton
    fun speechService(@ApplicationContext ctx: Context): SpeechService = AndroidSpeechService(ctx)
}
