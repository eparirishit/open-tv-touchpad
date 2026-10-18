package dev.opentvtouchpad.input

sealed interface GestureSpec {
    data class Tap(
        val point: PointF,
        val durationMillis: Long,
    ) : GestureSpec

    data class Swipe(
        val start: PointF,
        val end: PointF,
        val durationMillis: Long,
    ) : GestureSpec

    data class Drag(
        val points: List<PointF>,
        val durationMillis: Long,
    ) : GestureSpec
}
