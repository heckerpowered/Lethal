/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Binds LWJGL 2's existing pointer-based JNI entry point to the creating context.
 * <p>
 * Its public glBufferSubData wrappers accept NIO buffers only. Resolving the generated native
 * method once lets the adapter accept arbitrary live native memory without forging a ByteBuffer,
 * copying through another allocation, or truncating a long byte count to an int capacity.
 * Access to these non-public LWJGL 2 members is isolated here and fails during adapter creation
 * when the runtime binding ABI is different or reflection access is denied. No native library
 * is replaced or added. The adapter checks the original current context before invocation.
 */
final class Lwjgl2BufferSubData {
    private final MethodHandle upload;

    private Lwjgl2BufferSubData(MethodHandle upload) {
        this.upload = upload;
    }

    static Lwjgl2BufferSubData create(Object capabilities, Class<?> bindingClass, String entryPoint) throws ReflectiveOperationException {
        Field functionField = capabilities.getClass().getDeclaredField(entryPoint);
        functionField.setAccessible(true);
        long functionAddress = functionField.getLong(capabilities);
        if (functionAddress == 0L) throw new IllegalStateException("Missing LWJGL 2 entry point: " + entryPoint);

        Method nativeMethod = bindingClass.getDeclaredMethod("n" + entryPoint, int.class, long.class, long.class, long.class, long.class);
        nativeMethod.setAccessible(true);
        MethodHandle nativeCall = MethodHandles.lookup().unreflect(nativeMethod);
        MethodHandle contextCall = MethodHandles.insertArguments(nativeCall, 4, functionAddress);
        return new Lwjgl2BufferSubData(contextCall);
    }

    // Java fixes the invokeExact descriptor to (int, long, long, long)void. A failure propagates
    // unchanged; this bridge does not catch Throwable or reinterpret a failure as success.
    void upload(int target, long offsetBytes, long sizeBytes, long sourceAddress) throws Throwable {
        upload.invokeExact(target, offsetBytes, sizeBytes, sourceAddress);
    }
}
