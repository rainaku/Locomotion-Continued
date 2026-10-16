package com.trainguy9512.locomotion.config;

import org.joml.Matrix4f;

/** Positions and pivots use model pixels (16 pixels = one block). */
public class ItemModelTransform {
    public float positionX;
    public float positionY;
    public float positionZ;
    public float rotationX;
    public float rotationY;
    public float rotationZ;
    public float pivotX;
    public float pivotY;
    public float pivotZ;
    public float scale = 1f;

    public Matrix4f applyTo(Matrix4f matrix, boolean leftHand) {
        float mirror = leftHand ? -1f : 1f;
        float px = bounded(pivotX, 0f, -64f, 64f) * mirror / 16f;
        float py = bounded(pivotY, 0f, -64f, 64f) / 16f;
        float pz = bounded(pivotZ, 0f, -64f, 64f) / 16f;
        float rx = (float) Math.toRadians(bounded(rotationX, 0f, -180f, 180f));
        float ry = (float) Math.toRadians(bounded(rotationY, 0f, -180f, 180f) * mirror);
        float rz = (float) Math.toRadians(bounded(rotationZ, 0f, -180f, 180f) * mirror);
        return matrix
                .translate(bounded(positionX, 0f, -64f, 64f) * mirror / 16f,
                        bounded(positionY, 0f, -64f, 64f) / 16f, bounded(positionZ, 0f, -64f, 64f) / 16f)
                .translate(px, py, pz)
                .rotateZYX(rz, ry, rx)
                .scale(bounded(scale, 1f, 0.05f, 8f))
                .translate(-px, -py, -pz);
    }

    public void sanitize() {
        positionX = bounded(positionX, 0f, -64f, 64f);
        positionY = bounded(positionY, 0f, -64f, 64f);
        positionZ = bounded(positionZ, 0f, -64f, 64f);
        rotationX = bounded(rotationX, 0f, -180f, 180f);
        rotationY = bounded(rotationY, 0f, -180f, 180f);
        rotationZ = bounded(rotationZ, 0f, -180f, 180f);
        pivotX = bounded(pivotX, 0f, -64f, 64f);
        pivotY = bounded(pivotY, 0f, -64f, 64f);
        pivotZ = bounded(pivotZ, 0f, -64f, 64f);
        scale = bounded(scale, 1f, 0.05f, 8f);
    }

    public ItemModelTransform copy() {
        var copy = new ItemModelTransform();
        copy.positionX = positionX;
        copy.positionY = positionY;
        copy.positionZ = positionZ;
        copy.rotationX = rotationX;
        copy.rotationY = rotationY;
        copy.rotationZ = rotationZ;
        copy.pivotX = pivotX;
        copy.pivotY = pivotY;
        copy.pivotZ = pivotZ;
        copy.scale = scale;
        return copy;
    }

    private static float bounded(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
