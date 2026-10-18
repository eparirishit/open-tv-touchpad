package dev.opentvtouchpad.input

interface GesturePort {
    fun dispatch(spec: GestureSpec, callback: GestureCallback): Boolean

    fun cancelActiveGesture()
}

interface GestureCallback {
    fun onCompleted()

    fun onCancelled()
}
