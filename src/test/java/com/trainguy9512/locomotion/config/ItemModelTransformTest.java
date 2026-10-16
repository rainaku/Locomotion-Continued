package com.trainguy9512.locomotion.config;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemModelTransformTest {
    @Test
    void defaultsPreserveTheAnimatedPose() {
        var animated = new Matrix4f().translate(1, 2, 3).rotateY(0.7f);
        assertTrue(animated.equals(new ItemModelTransform().applyTo(new Matrix4f(animated), false), 0.00001f));
        assertTrue(animated.equals(new ItemModelTransform().applyTo(new Matrix4f(animated), true), 0.00001f));
    }

    @Test
    void offsetsUseModelPixelsInTheAnimatedLocalFrame() {
        var transform = new ItemModelTransform();
        transform.positionX = 16;
        var matrix = transform.applyTo(new Matrix4f().rotateY((float) (Math.PI / 2)), false);
        assertVector(new Vector3f(0, 0, -1), matrix.transformPosition(new Vector3f()));
    }

    @Test
    void scalingKeepsTheChosenPivotFixedExceptForPositionOffset() {
        var transform = new ItemModelTransform();
        transform.positionX = 8;
        transform.positionY = -4;
        transform.pivotX = 16;
        transform.pivotY = 32;
        transform.pivotZ = -8;
        transform.scale = 2.5f;
        var matrix = transform.applyTo(new Matrix4f(), false);
        assertVector(new Vector3f(1.5f, 1.75f, -0.5f), matrix.transformPosition(new Vector3f(1, 2, -0.5f)));
        assertVector(new Vector3f(4f, 1.75f, -0.5f), matrix.transformPosition(new Vector3f(2, 2, -0.5f)));
    }

    @Test
    void bothPositionAndPivotMirrorForTheLeftHand() {
        var transform = new ItemModelTransform();
        transform.positionX = 8;
        transform.positionY = 4;
        transform.positionZ = -2;
        transform.rotationX = 15;
        transform.rotationY = -25;
        transform.rotationZ = 35;
        transform.pivotX = 12;
        transform.pivotY = 6;
        transform.scale = 2;
        var right = transform.applyTo(new Matrix4f(), false).transformPosition(new Vector3f(1, 2, 3));
        var left = transform.applyTo(new Matrix4f(), true).transformPosition(new Vector3f(-1, 2, 3));
        assertVector(new Vector3f(-right.x, right.y, right.z), left);
    }

    @Test
    void capturedMatricesDoNotChangeWhenTheConfigChanges() {
        var transform = new ItemModelTransform();
        transform.positionX = 16;
        var frame = transform.applyTo(new Matrix4f(), false);
        transform.positionX = 32;
        transform.scale = 2;
        assertVector(new Vector3f(1, 0, 0), frame.transformPosition(new Vector3f()));
    }

    @Test
    void malformedNumbersCannotProduceNonFiniteOrSingularTransforms() {
        var transform = new ItemModelTransform();
        transform.positionX = Float.NaN;
        transform.positionY = Float.POSITIVE_INFINITY;
        transform.pivotZ = Float.NEGATIVE_INFINITY;
        transform.scale = Float.NaN;
        assertTrue(transform.applyTo(new Matrix4f(), false).equals(new Matrix4f(), 0.00001f));
        transform.scale = -10;
        transform.positionZ = 100000;
        transform.sanitize();
        assertEquals(0.05f, transform.scale);
        assertEquals(64f, transform.positionZ);
        assertTrue(Float.isFinite(transform.applyTo(new Matrix4f(), true).determinant()));
        assertTrue(transform.applyTo(new Matrix4f(), true).determinant() > 0);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertTrue(expected.equals(actual, 0.00001f), () -> "Expected " + expected + ", got " + actual);
    }
}
