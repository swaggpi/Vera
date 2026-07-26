package app.vera.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import app.vera.core.gamification.Gamification
import app.vera.core.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val id: Int = 0,
    val streak: Int,
    val longestStreak: Int,
    val xp: Int,
    val lastCompletedEpochDay: Long
)

@Entity(tableName = "read_log")
data class ReadLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val country: String,
    val epochDay: Long
)

/** Pre-rendered briefings, keyed by slot ("MORNING"/"EVENING"). */
@Entity(tableName = "briefing_cache")
data class BriefingCacheEntity(
    @PrimaryKey val slot: String,
    val payload: String,
    val generatedAtEpochMs: Long
)

@Dao
interface BriefingCacheDao {
    @Query("SELECT * FROM briefing_cache WHERE slot = :slot")
    suspend fun get(slot: String): BriefingCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BriefingCacheEntity)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE id = 0")
    fun observe(): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE id = 0")
    suspend fun get(): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProgressEntity)
}

@Dao
interface ReadLogDao {
    @Insert
    suspend fun insert(entity: ReadLogEntity)

    @Query("SELECT * FROM read_log")
    suspend fun all(): List<ReadLogEntity>
}

@Database(
    entities = [ProgressEntity::class, ReadLogEntity::class, BriefingCacheEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VeraDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun readLogDao(): ReadLogDao
    abstract fun briefingCacheDao(): BriefingCacheDao
}

private fun ProgressEntity.toModel() =
    UserProgress(streak, longestStreak, xp, lastCompletedEpochDay)

private fun UserProgress.toEntity() =
    ProgressEntity(0, streak, longestStreak, xp, lastCompletedEpochDay)

/** Reads/writes gamification state, applying the pure [Gamification] rules from :core. */
class ProgressRepository(private val dao: ProgressDao) {

    val progress: Flow<UserProgress> = dao.observe().map { it?.toModel() ?: UserProgress() }

    suspend fun completeBriefing(correctAnswers: Int) {
        val current = dao.get()?.toModel() ?: UserProgress()
        val today = LocalDate.now().toEpochDay()
        if (current.lastCompletedEpochDay == today) return   // award once per day
        dao.upsert(Gamification.completeBriefing(current, today, correctAnswers).toEntity())
    }
}

/** Records which sources a user reads, and reconstructs them (via the catalog) for the diet meter. */
class ReadLogRepository(
    private val dao: ReadLogDao,
    private val catalog: SourceCatalogProvider
) {
    suspend fun log(sourceIds: List<String>) {
        val today = LocalDate.now().toEpochDay()
        val byId = catalog.all().associateBy { it.id }
        sourceIds.forEach { id ->
            val s = byId[id] ?: return@forEach
            dao.insert(ReadLogEntity(sourceId = s.id, country = s.country, epochDay = today))
        }
    }

    suspend fun readSources(): List<app.vera.core.model.NewsSource> {
        val byId = catalog.all().associateBy { it.id }
        return dao.all().mapNotNull { byId[it.sourceId] }
    }
}
