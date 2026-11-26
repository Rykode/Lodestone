package app.lodestone.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.lodestone.LodestoneApp
import app.lodestone.R

/**
 * The launcher icons Lodestone can wear. Each one is an activity-alias in the manifest; exactly one is enabled.
 * The alias names are permanent. Renaming an enabled one would make the app disappear from the launcher.
 */
object AppIcons {
    enum class Look { Light, Ink }

    data class Choice(val alias: String, val name: String, val look: Look, val bg: Long, val bg2: Long = bg)

    val all = listOf(
        Choice("IconLavender", "Lavender", Look.Light, 0xFFC3B4FF, 0xFF9B86F4),
        Choice("IconMint", "Mint", Look.Light, 0xFF9EDDB5),
        Choice("IconPeach", "Peach", Look.Light, 0xFFF0C48C),
        Choice("IconSky", "Sky", Look.Light, 0xFF9CC5F0),
        Choice("IconRose", "Rose", Look.Light, 0xFFF2A7C0),
        Choice("IconInk", "Ink", Look.Ink, 0xFF1B1B1F),
    )

    private fun component(ctx: Context, c: Choice) = ComponentName(ctx.packageName, "app.lodestone.${c.alias}")

    private fun isOn(ctx: Context, c: Choice): Boolean {
        val s = ctx.packageManager.getComponentEnabledSetting(component(ctx, c))
        return s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && c == all[0])
    }

    fun current(ctx: Context): Choice = all.firstOrNull { isOn(ctx, it) } ?: all[0]

    /** Turns the new icon on first, then the others off, so the app is never without a launcher entry. */
    fun set(ctx: Context, choice: Choice) {
        val pm = ctx.packageManager
        pm.setComponentEnabledSetting(component(ctx, choice), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all.filter { it != choice }.forEach {
            pm.setComponentEnabledSetting(component(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        (ctx.applicationContext as? LodestoneApp)?.publishShareShortcut()
    }

    /** If nothing is enabled (it should never happen), brings the default back so the app can always be opened. */
    fun ensureOne(ctx: Context) {
        if (all.none { isOn(ctx, it) }) {
            ctx.packageManager.setComponentEnabledSetting(component(ctx, all[0]), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
    }

    fun foreground(look: Look): Int = when (look) {
        Look.Light -> R.drawable.ic_launcher_foreground
        Look.Ink -> R.drawable.ic_cat_cream
    }
}
