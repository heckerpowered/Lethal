package heckerpowered.lethal.bridge.math

fun interface Interpolatable<T> {
    fun interpolate(target: T, alpha: Double): T
}