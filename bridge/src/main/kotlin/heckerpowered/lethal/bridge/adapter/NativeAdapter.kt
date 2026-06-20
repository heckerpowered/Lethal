package heckerpowered.lethal.bridge.adapter

interface NativeAdapter<in TNative, out TCommon> {
    fun adapt(native: TNative): TCommon
}