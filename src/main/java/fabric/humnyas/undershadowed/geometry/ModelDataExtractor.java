package fabric.humnyas.undershadowed.geometry;

import fabric.humnyas.undershadowed.core.ShadowDataRegistry;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.BASE_SHADOW_OFFSET;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.LAST_BODY_YAW;

public class ModelDataExtractor {
    // Iterates through all the bones in a model and returns their properties
    public static Map<ModelPart, ShadowDataRegistry.BoneDataRecord> extractBoneData(Object model) {
        Map<ModelPart, ShadowDataRegistry.BoneDataRecord> partDataMap = new HashMap<>();

        Class<?> modelClass = model.getClass();

        Field[] fields = MODEL_FIELD_CACHE.computeIfAbsent(modelClass, clazz -> {
            Field[] declared = clazz.getDeclaredFields();
            List<Field> nonStaticNonSynthetic = new ArrayList<>();
            for (Field field : declared) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    field.setAccessible(true);
                    nonStaticNonSynthetic.add(field);
                }
            }
            return nonStaticNonSynthetic.toArray(new Field[0]);
        });

        // Checks through all the parts
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            field.setAccessible(true);

            try {
                Object value = field.get(model);
                if (value instanceof ModelPart part) {
                    ShadowDataRegistry.BoneDataRecord data = extractPartData(part);
                    if (data != null) partDataMap.put(part, data);
                }
            } catch (IllegalAccessException ignored) {}
        }

        // Returns the list of all the part data
        return partDataMap;
    }

    // Returns the properties for the specified part
    public static ShadowDataRegistry.BoneDataRecord extractPartData(ModelPart part) {
        if (part == null) return null;

        Float[] partSize = getPartSize(part);

        return new ShadowDataRegistry.BoneDataRecord(
                part.pivotX / 16f,
                part.pivotY / 16f,
                part.pivotZ / 16f,
                part.pitch,
                part.yaw,
                part.roll,
                partSize[0] / 16f,
                partSize[1] / 16f,
                partSize[2] / 16f
        );
    }

    // Returns the size of the specified part
    public static Float[] getPartSize(ModelPart part) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();

        final float pivotX = part.pivotX;
        final float pivotY = part.pivotY;
        final float pivotZ = part.pivotZ;

        final float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        final float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        part.forEachCuboid(matrixStack, (matrix, path, index, cuboid) -> {
            // Apply pivot and transform to world space
            matrixStack.translate(pivotX, pivotY, pivotZ);
            Matrix4f pose = matrixStack.peek().getPositionMatrix();

            // Now calculate world space positions
            float worldMinX = pose.m30() + cuboid.minX;
            float worldMinY = pose.m31() + cuboid.minY;
            float worldMinZ = pose.m32() + cuboid.minZ;

            float worldMaxX = pose.m30() + cuboid.maxX;
            float worldMaxY = pose.m31() + cuboid.maxY;
            float worldMaxZ = pose.m32() + cuboid.maxZ;

            // Find the world-space min/max
            if (worldMinX < min[0]) min[0] = worldMinX;
            if (worldMinY < min[1]) min[1] = worldMinY;
            if (worldMinZ < min[2]) min[2] = worldMinZ;

            if (worldMaxX > max[0]) max[0] = worldMaxX;
            if (worldMaxY > max[1]) max[1] = worldMaxY;
            if (worldMaxZ > max[2]) max[2] = worldMaxZ;
        });

        // Now return the size in world space
        final float minSize = 0.01f;
        return new Float[] {
                Math.max(max[0] - min[0], minSize),
                Math.max(max[1] - min[1], minSize),
                Math.max(max[2] - min[2], minSize)
        };
    }

    public static float getShadowSize(Entity entity, List<List<Vector3f>> vertices) {
        float maxY = 0, minY = 0, size;

        if (BASE_SHADOW_HEIGHT.containsKey(entity)) return BASE_SHADOW_HEIGHT.get(entity);

        for (List<Vector3f> bone : vertices) {
            for (Vector3f vertex : bone) {
                float y = vertex.y;
                if (y > maxY) maxY = y;
                else if (y < minY) minY = y;
            }
        }

        size = maxY - minY;
        BASE_SHADOW_HEIGHT.put(entity, size);
        return size;
    }

    // Returns a multiplier for the height of the shadow
    public static float morphShadowSize(float baseSize, float verticalAngle) { // Where verticalAngle is the angle of the source above/below the entity
        return baseSize; // Remove errors temporarily
    }

    public static float centerBottomOffset(Entity entity, List<List<Vector3f>> vertices) {
        float maxY = 0, offset;

        if (BASE_SHADOW_OFFSET.containsKey(entity)) return BASE_SHADOW_OFFSET.get(entity);

        // Find max y because the shadow is upside-down
        for (List<Vector3f> bone : vertices) {
            for (Vector3f vertex : bone) {
                float y = vertex.y;
                if (y > maxY) maxY = y;
            }
        }

        offset = maxY;
        BASE_SHADOW_OFFSET.put(entity, offset);
        return offset;
    }

    public static float estimateBodyYaw(Entity entity) {
        Vec3d vel = entity.getVelocity();
        float
                headYaw = entity.getYaw() + 90f,
                bodyYaw = LAST_BODY_YAW.getOrDefault(entity, headYaw),
                majorAngleDifference = 35f;

        if (vel.lengthSquared() > 1e-4f) { // If moving, the body follows the movement direction
            float movementYaw = (float) Math.toDegrees(Math.atan2(vel.z, vel.x));
            bodyYaw = (bodyYaw + movementYaw) / 2;
        } else { // When stationary, keep the body at most majorAngleDifference degrees behind the head
            float angleDifference = MathHelper.wrapDegrees(headYaw - bodyYaw);

            if (Math.abs(angleDifference) > majorAngleDifference) {
                bodyYaw = MathHelper.lerpAngleDegrees(bodyYaw, headYaw, 0.05f);
            }
        }

        LAST_BODY_YAW.put(entity, bodyYaw);
        return bodyYaw;
    }
}
