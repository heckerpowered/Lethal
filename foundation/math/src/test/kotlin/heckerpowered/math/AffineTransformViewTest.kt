/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AffineTransformViewTest {
    private val shear = AffineTransforms.of(
        axisX = Vectors.of(2.0, 0.0, 0.0),
        axisY = Vectors.of(1.0, 3.0, 0.0),
        axisZ = Vectors.of(0.0, 0.0, -4.0),
        translation = Vectors.of(5.0, 6.0, 7.0)
    )

    @Test
    fun columnsRepresentTransformedAxesIncludingShearAndReflection() {
        assertVector(shear.axisX, shear.transformVector(Vectors.UnitX))
        assertVector(shear.axisY, shear.transformVector(Vectors.UnitY))
        assertVector(shear.axisZ, shear.transformVector(Vectors.UnitZ))
        assertVector(3.0, 3.0, -4.0, shear.transformVector(Vectors.One))
        assertVector(8.0, 9.0, 3.0, shear.transformPosition(Vectors.One))
        assertVector(shear.transformPosition(Vectors.One), shear.toMatrix4().transformPosition(Vectors.One))
    }

    @Test
    fun compositionAppliesTheRightOperandFirstAndRetainsShear() {
        val translation = AffineTransforms.fromTranslation(Vectors.of(1.0, 2.0, 3.0))
        val composed = shear * translation
        assertVector(shear.transformPosition(translation.transformPosition(Vectors.One)), composed.transformPosition(Vectors.One))
        assertVector(shear.transformVector(Vectors.One), composed.transformVector(Vectors.One))
        assertMatrix(shear.toMatrix4() * translation.toMatrix4(), composed.toMatrix4())
        assertFalse(composed.toMatrix4().isNearlyEqual((translation * shear).toMatrix4()))
    }

    @Test
    fun coefficientInterpolationRetainsAffineStructureAndAllowsExtrapolation() {
        val midpoint = AffineTransforms.Identity.interpolate(shear, 0.5)
        assertVector(1.5, 0.0, 0.0, midpoint.axisX)
        assertVector(0.5, 2.0, 0.0, midpoint.axisY)
        assertVector(0.0, 0.0, -1.5, midpoint.axisZ)
        assertVector(2.5, 3.0, 3.5, midpoint.translation)
        assertTrue(midpoint.toMatrix4().isAffine())
        assertMatrix(AffineTransforms.Identity.toMatrix4().interpolate(shear.toMatrix4(), 2.0), AffineTransforms.Identity.interpolate(shear, 2.0).toMatrix4())
    }

    @Test
    fun factoriesPreserveReferencesAndConvertTrsWithoutLosingGeometry() {
        val copy = AffineTransform(shear.axisX, shear.axisY, shear.axisZ, shear.translation)
        assertSame(shear.axisX, copy.axisX)
        assertSame(shear.axisY, copy.axisY)
        assertSame(shear.axisZ, copy.axisZ)
        assertSame(shear.translation, copy.translation)
        val transform = Transforms.of(shear.translation, Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 40.0), Vectors.of(-2.0, 3.0, 4.0))
        assertVector(transform.transformPosition(Vectors.One), AffineTransforms.fromTransform(transform).transformPosition(Vectors.One))
        assertTrue(AffineTransforms.of().isIdentity())
        assertTrue(AffineTransforms.Identity.isFinite())
        assertFalse(shear.isIdentity())
        assertFalse(AffineTransforms.of(axisX = Vectors.of(Double.NaN, 0.0, 0.0)).isFinite())
        assertFalse(AffineTransforms.fromTranslation(Vectors.of(Double.POSITIVE_INFINITY, 0.0, 0.0)).isFinite())
    }
}
