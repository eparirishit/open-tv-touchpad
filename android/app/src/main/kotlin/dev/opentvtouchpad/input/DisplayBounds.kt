package dev.opentvtouchpad.input

data class PointF(
    val x: Float,
    val y: Float,
)

data class DisplayBounds(
    val width: Int,
    val height: Int,
) {
    fun clamp(point: PointF): PointF = PointF(
        x = point.x.coerceIn(0f, (width - 1).toFloat()),
        y = point.y.coerceIn(0f, (height - 1).toFloat()),
    )
}
