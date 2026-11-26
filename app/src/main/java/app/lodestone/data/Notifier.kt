package app.lodestone.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.lodestone.MainActivity
import app.lodestone.R

/**
 * Progress notifications for long jobs (backup, restore, import, saving many files, fetching previews).
 * On Android 16 and newer they use the system's live progress style, which can sit in the status bar and on the
 * lock screen. On older phones they are ordinary progress notifications. Without permission nothing is shown.
 */
object Notifier {
    private const val CHANNEL = "progress"
    const val ID_BACKUP = 4101
    const val ID_SAVE = 4102
    const val ID_PREVIEWS = 4103
    const val ID_OCR = 4104

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun needsPermissionAsk(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && !canPost(ctx) && !Store.prefs.getBoolean("askedNotifications", false)

    fun markAsked() { Store.prefs.edit().putBoolean("askedNotifications", true).apply() }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Saving and backups", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress while Lodestone saves, backs up or restores your things"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** [total] of 0 means the length is not known, so the bar just moves. */
    fun progress(id: Int, title: String, text: String, done: Int, total: Int) {
        val ctx = Store.app
        if (!canPost(ctx)) return
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n: Notification = if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
            if (total > 0) {
                style.setProgressSegments(listOf(Notification.ProgressStyle.Segment(total)))
                style.setProgress(done.coerceIn(0, total))
            } else {
                style.setProgressIndeterminate(true)
            }
            Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lodestone).setContentTitle(title).setContentText(text)
                .setStyle(style).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp(ctx))
                .setShortCriticalText(if (total > 0) "$done/$total" else "…")
                .also { if (canPromote()) promote(it) }
                .build()
        } else {
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lodestone).setContentTitle(title).setContentText(text)
                .setProgress(total, done, total == 0).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp(ctx))
                .setPriority(NotificationCompat.PRIORITY_LOW).build()
        }
        nm.notify(id, n)
    }

    /** Asking the system to show it as a live (promoted) notification arrived in Android 16 QPR2, so check the exact version. */
    private fun canPromote(): Boolean =
        Build.VERSION.SDK_INT >= 37 || (Build.VERSION.SDK_INT == 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1)

    @android.annotation.SuppressLint("NewApi")
    private fun promote(b: Notification.Builder) { runCatching { b.setRequestPromotedOngoing(true) } }

    /** Replaces the progress with a plain, dismissible result. */
    fun finish(id: Int, title: String, text: String) {
        val ctx = Store.app
        if (!canPost(ctx)) return
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lodestone).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setTimeoutAfter(15_000).setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun cancel(id: Int) { Store.app.getSystemService(NotificationManager::class.java).cancel(id) }
}
