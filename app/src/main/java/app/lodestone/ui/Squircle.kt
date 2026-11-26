package app.lodestone.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * A true squircle (a superellipse), the soft square app icons use: the curve eases into each side
 * instead of meeting it at a fixed round corner.
 */
object Squircle : Shape {
    private const val N = 4.6
    private const val STEPS = 96

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val path = Path()
        for (i in 0..STEPS) {
            val t = i * 2.0 * Math.PI / STEPS
            val c = cos(t)
            val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2.0 / N)
            val y = b + b * sign(s) * abs(s).pow(2.0 / N)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}
