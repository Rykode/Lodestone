package app.lodestone

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.lodestone.data.Store

class LodestoneApp : Application() {
    /** Puts "Save to Lodestone" in the top row of the system share sheet (Direct Share). */
    fun publishShareShortcut() {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(this, "save_to_lodestone")
                .setShortLabel("Save to Lodestone").setLongLabel("Save to Lodestone")
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(Intent(Intent.ACTION_VIEW).setClass(this, MainActivity::class.java))
                .setLongLived(true)
                .setCategories(setOf("app.lodestone.category.SAVE"))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        app.lodestone.data.AppIcons.ensureOne(this)
        publishShareShortcut()
    }
}
