package fabric.humnyas.undershadowed.geometry;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class ShadowGeometry {
    // Turns the boneData into a list of vertices
    public static List<List<Vector3f>> getVertices(Map<ModelPart, BoneDataRecord> boneData, Entity entity) {
        List<List<Vector3f>> vertices = new LinkedList<>();

        float entityYaw = entity.getBodyYaw();
        Quaternionf entityRotation = new Quaternionf().rotateY((float) Math.toRadians(-entityYaw));

        for (BoneDataRecord data : boneData.values()) {
            float
                    x = data.posX(),
                    y = data.posY(),
                    z = data.posZ(),
                    pitch = data.pitch(),
                    yaw = data.yaw(),
                    roll = data.roll(),
                    w = data.sizeX() / 2,
                    h = data.sizeY() / 2,
                    d = data.sizeZ() / 2;

            Quaternionf boneRotation = new Quaternionf()
                    .rotateZ(roll)
                    .rotateY(yaw)
                    .rotateX(pitch);

            // Cube vertices
            Vector3f[] localVertices = {
                    new Vector3f(+w, -h, +d),
                    new Vector3f(-w, -h, +d),
                    new Vector3f(+w, +h, +d),
                    new Vector3f(-w, +h, +d),
                    new Vector3f(+w, -h, -d),
                    new Vector3f(-w, -h, -d),
                    new Vector3f(+w, +h, -d),
                    new Vector3f(-w, +h, -d)
            };

            // Rotates the vertices, and moves them to the position
            List<Vector3f> boneVertices = new ArrayList<>();
            for (int j = 0; j < 8; j++) {
                Vector3f vertex = new Vector3f(localVertices[j]);
                Vector3f pivot = new Vector3f(x, y - (h / 2), z);

                vertex.add(x, y, z)
                        .sub(pivot)
                        .rotate(boneRotation)
                        .add(pivot)
                        .rotate(entityRotation);
                boneVertices.add(vertex);
            }
            vertices.add(boneVertices);
        }

        return vertices;
    }

    // Removes redundant vertices for better / more optimal rendering
    public static List<List<Vec2f>> pruneVertices(List<List<Vec2f>> vertices) {
        List<List<Vec2f>> pruned = new LinkedList<>();
        float epsilon = 1e-2f;

        for (List<Vec2f> entry : vertices) {
            if (entry.size() < 3) continue;

            List<Vec2f> uniqueVertices = PolygonMath.removeNearDuplicates(entry, epsilon);
            List<Vec2f> hull = PolygonMath.convexHull(uniqueVertices);
            List<Vec2f> cleanedHull = PolygonMath.removeColinearPoints(hull, epsilon);

            if (cleanedHull.size() >= 3) {
                pruned.add(cleanedHull);
            }
        }

        return pruned;
    }

    // Flattens all the vertices in the inputted list to face towards the source angle
    public static List<List<Vec2f>> flattenVertices(List<List<Vector3f>> vertices, float sourceAngle) {
        List<List<Vec2f>> flattenedVertices = new LinkedList<>();

        float
                angleRad = (float) Math.toRadians(sourceAngle),
                cos = (float) Math.cos(angleRad),
                sin = (float) Math.sin(angleRad);

        for (List<Vector3f> bone : vertices) {
            List<Vec2f> flattenedBone = new ArrayList<>();

            for (Vector3f vertex : bone) {
                float
                        x = vertex.x,
                        z = vertex.z,
                        flattenedX = x;

                if (sourceAngle != 0) {
                    flattenedX = x * cos - z * sin;
                }

                Vec2f flattenedVertex = new Vec2f(flattenedX, vertex.y);
                flattenedBone.add(flattenedVertex);
            }

            flattenedVertices.add(flattenedBone);
        }

        return flattenedVertices;
    }

    // Iterates through all the bones in a model and returns their properties
    public static Map<ModelPart, BoneDataRecord> extractBoneData(Object model) {
        Map<ModelPart, BoneDataRecord> partDataMap = new HashMap<>();

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
                    BoneDataRecord data = extractPartData(part);
                    if (data != null) partDataMap.put(part, data);
                }
            } catch (IllegalAccessException ignored) {}
        }

        // Returns the list of all the part data
        return partDataMap;
    }

    // Returns the properties for the specified part
    public static BoneDataRecord extractPartData(ModelPart part) {
        if (part == null) return null;

        Float[] partSize = getPartSize(part);

        return new BoneDataRecord(
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
        return new Float[] {
                max[0] - min[0],
                max[1] - min[1],
                max[2] - min[2]
        };
    }
}
