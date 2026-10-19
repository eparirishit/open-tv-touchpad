package dev.opentvtouchpad.input

sealed interface GestureSpec {
    val durationMillis: Long

    data class Tap(
        val point: PointF,
        override val durationMillis: Long,
    ) : GestureSpec

    data class Swipe(
        val start: PointF,
        val end: PointF,
        override val durationMillis: Long,
    ) : GestureSpec

    data class Drag(
        val points: List<PointF>,
        override val durationMillis: Long,
    ) : GestureSpec
}
