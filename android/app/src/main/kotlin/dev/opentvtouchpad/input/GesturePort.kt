package dev.opentvtouchpad.input

interface GesturePort {
    fun dispatch(spec: GestureSpec, callback: GestureCallback): Boolean
}

interface GestureCallback {
    fun onCompleted()

    fun onCancelled()
}
