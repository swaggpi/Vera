package app.vera.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vera.core.briefing.slotForHour
import app.vera.core.model.BriefingSlot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Generates the next briefing in the background and notifies when it's ready.
 *
 * This is what makes the app feel instant: the slow on-device summarising happens while the phone
 * is idle and charging-agnostic, so opening Vera just reads a finished briefing from the database.
 */
@HiltWorker
class BriefingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: BriefingRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slot = slotForHour(LocalTime.now().hour)
        return try {
            val briefing = repository.generate(slot, System.currentTimeMillis())
            if (briefing.cards.isNotEmpty()) {
                notify(applicationContext, slot, briefing.cards.first().title, briefing.cards.size)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val CHANNEL_ID = "vera_briefings"
        private const val NOTIFICATION_ID = 4201
        private const val MORNING_WORK = "vera_briefing_morning"
        private const val EVENING_WORK = "vera_briefing_evening"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID, "Daily briefings", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Your morning and evening briefing, ready to read." }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        private fun notify(context: Context, slot: BriefingSlot, headline: String, count: Int) {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return   // user hasn't granted notifications — stay silent

            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            val pending = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (slot == BriefingSlot.MORNING) "Your morning briefing is ready"
            else "Your evening briefing is ready"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$headline\n\n$count stories, summarised on your phone."))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        }

        /** Schedule both daily runs. Safe to call on every app start. */
        fun schedule(context: Context) {
            ensureChannel(context)
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                MORNING_WORK, ExistingPeriodicWorkPolicy.KEEP, request(delayUntil(7))
            )
            wm.enqueueUniquePeriodicWork(
                EVENING_WORK, ExistingPeriodicWorkPolicy.KEEP, request(delayUntil(18))
            )
        }

        private fun request(initialDelayMinutes: Long) =
            PeriodicWorkRequestBuilder<BriefingWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

        /** Minutes from now until the next occurrence of [hour] o'clock. */
        internal fun delayUntil(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
            var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return ChronoUnit.MINUTES.between(now, target).coerceAtLeast(1)
        }
    }
}
