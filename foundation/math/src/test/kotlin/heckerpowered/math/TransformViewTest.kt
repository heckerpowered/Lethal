/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.abs
import kotlin.test.*

class TransformViewTest {
    private val rotation = Quaternions.fromAxisAngleDegrees(Vectors.UnitZ, 90.0)

    @Test
    fun positionsVectorsAndRotationOnlyFollowScaleRotationTranslationOrder() {
        val transform = Transforms.of(Vectors.of(10.0, 20.0, 30.0), rotation, Vectors.of(2.0, 3.0, 4.0))
        assertVector(7.0, 22.0, 34.0, transform.transformPosition(Vectors.One), 1e-12)
        assertVector(-3.0, 2.0, 4.0, transform.transformVector(Vectors.One), 1e-12)
        assertVector(-1.0, 1.0, 1.0, transform.transformVectorNoScale(Vectors.One), 1e-12)
        assertVector(transform.transformPosition(Vectors.One), transform.toAffine().transformPosition(Vectors.One))
        assertVector(transform.transformVector(Vectors.One), transform.toMatrix4().transformVector(Vectors.One))
    }

    @Test
    fun composingNonuniformScaleAndRotationPreservesShearInAffineResult() {
        val first = Transforms.of(Vectors.of(4.0, -2.0, 1.0), scale = Vectors.of(2.0, 3.0, 4.0))
        val second = Transforms.of(Vectors.of(1.0, 2.0, 3.0), Quaternions.fromAxisAngleDegrees(Vectors.UnitZ, 45.0))
        val composed = first * second
        assertNull(first.tryCompose(second))
        assertTrue(abs(composed.axisX.dot(composed.axisY)) > 1.0)
        assertVector(first.transformPosition(second.transformPosition(Vectors.One)), composed.transformPosition(Vectors.One))
        assertVector(first.transformVector(second.transformVector(Vectors.One)), first.compose(second).transformVector(Vectors.One))
    }

    @Test
    fun representableCompositionsKeepTrsWhenScaleCommutesWithRotation() {
        val first = Transforms.of(Vectors.of(1.0, 2.0, 3.0), rotation, Vectors.of(-2.0, -2.0, -2.0))
        val second = Transforms.of(Vectors.UnitY, rotation, Vectors.of(2.0, 3.0, 4.0))
        val uniform = assertNotNull(first.tryCompose(second))
        assertVector(first.transformPosition(second.transformPosition(Vectors.One)), uniform.transformPosition(Vectors.One))
        val nonuniform = Transforms.fromScale(Vectors.of(2.0, 3.0, 4.0))
        val unrotated = Transforms.of(Vectors.UnitX, scale = Vectors.of(5.0, 6.0, 7.0))
        val result = assertNotNull(nonuniform.tryCompose(unrotated))
        assertVector(nonuniform.transformPosition(unrotated.transformPosition(Vectors.One)), result.transformPosition(Vectors.One))
    }

    @Test
    fun interpolationBlendsTranslationScaleAndShortestRotation() {
        val target = Transforms.of(Vectors.of(10.0, 20.0, 30.0), -rotation, Vectors.of(3.0, 5.0, 7.0))
        val midpoint = Transforms.Identity.interpolate(target, 0.5)
        assertVector(5.0, 10.0, 15.0, midpoint.translation)
        assertVector(2.0, 3.0, 4.0, midpoint.scale)
        assertTrue(midpoint.rotation.isSameRotation(Quaternions.fromAxisAngleDegrees(Vectors.UnitZ, 45.0)))
        val extrapolated = Transforms.Identity.interpolate(target, 2.0)
        assertVector(20.0, 40.0, 60.0, extrapolated.translation)
        assertVector(5.0, 9.0, 13.0, extrapolated.scale)
    }

    @Test
    fun factoriesAndPredicatesPreserveIdentityAndDetectInvalidComponents() {
        assertTrue(Transforms.of().isIdentity())
        assertTrue(Transforms.Identity.isFinite())
        assertTrue(Transforms.of(rotation = -Quaternions.Identity).isIdentity())
        assertFalse(Transforms.fromTranslation(Vectors.UnitX).isIdentity())
        assertVector(Vectors.UnitX, Transforms.fromTranslation(Vectors.UnitX).transformPosition(Vectors.Zero))
        assertVector(rotation * Vectors.One, Transforms.fromRotation(rotation).transformVector(Vectors.One))
        assertFalse(Transforms.fromScale(Vectors.Zero).isIdentity())
        assertFalse(Transforms.fromTranslation(Vectors.of(Double.NaN, 0.0, 0.0)).isFinite())
        assertFalse(Transforms.fromScale(Vectors.of(1.0, Double.POSITIVE_INFINITY, 1.0)).isFinite())
        assertFalse(Transforms.fromRotation(Quaternions.Identity.withW(Double.NaN)).isFinite())
    }
}
