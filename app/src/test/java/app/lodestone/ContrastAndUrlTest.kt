package app.lodestone

import androidx.compose.ui.graphics.Color
import app.lodestone.data.Ingest
import app.lodestone.ui.theme.Ink
import app.lodestone.ui.theme.Schemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ContrastAndUrlTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test
    fun everySchemeAndModeKeepsTextReadable() {
        val fails = mutableListOf<String>()
        for (s in Schemes) for ((mode, t) in listOf("dark" to s.dark, "light" to s.light)) {
            val pairs = mapOf(
                "tx/bg" to (t.tx to t.bg), "tx/card" to (t.tx to t.card), "tx/hi" to (t.tx to t.hi),
                "sec/bg" to (t.sec to t.bg), "sec/card" to (t.sec to t.card), "sec/hi" to (t.sec to t.hi),
                "onFab/fab" to (t.onFab to t.fab), "onChip/chip" to (t.onChip to t.chip),
                "onP/p" to (t.onP to t.p), "onPc/pc" to (t.onPc to t.pc), "onSc/sc" to (t.onSc to t.sc), "onTc/tc" to (t.onTc to t.tc),
                "good/card" to (t.good to t.card),
            ) + t.x.mapIndexed { i, x -> "ink/x$i" to (Ink to x) }
            for ((name, p) in pairs) if (ratio(p.first, p.second) < 4.5) fails += "${s.name} $mode $name %.2f".format(ratio(p.first, p.second))
        }
        assertTrue("Low contrast: $fails", fails.isEmpty())
    }

    @Test
    fun extractsLinksFromSharedText() {
        assertEquals("https://example.com/a", Ingest.extractUrl("Look at this https://example.com/a"))
        assertEquals("https://example.com/a", Ingest.extractUrl("(see https://example.com/a)."))
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", Ingest.extractUrl("https://en.wikipedia.org/wiki/Foo_(bar)"))
        assertEquals(null, Ingest.extractUrl("no link here"))
    }
}
