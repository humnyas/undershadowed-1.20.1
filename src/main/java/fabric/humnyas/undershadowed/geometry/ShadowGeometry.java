package fabric.humnyas.undershadowed.geometry;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;

import fabric.humnyas.undershadowed.math.PolygonMath;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class ShadowGeometry {
    // Turns the boneData into a list of vertices
    public static List<List<Vector3f>> getVertices(Map<ModelPart, BoneDataRecord> boneData, Entity entity) {
        List<List<Vector3f>> vertices = new LinkedList<>();

        float bodyYaw = ModelDataExtractor.estimateBodyYaw(entity);

        Quaternionf entityRotation = new Quaternionf().rotateY((float) Math.toRadians(-bodyYaw));

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
            //List<Vec2f> cleanedHull = PolygonMath.removeColinearPoints(hull, epsilon);

            if (hull.size() >= 3) {
                pruned.add(hull);
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

                    float epsilon = 1e-2f;
                    if (Math.abs(flattenedX) < epsilon) {
                        flattenedX = epsilon * Math.signum(flattenedX + epsilon/10);
                    }
                }

                Vec2f flattenedVertex = new Vec2f(flattenedX, vertex.y);
                flattenedBone.add(flattenedVertex);
            }

            flattenedVertices.add(flattenedBone);
        }

        return flattenedVertices;
    }

    public static List<List<Vec2f>> rotateShadow(List<List<Vec2f>> bones, float sourceDegrees, float size) {
        List<List<Vec2f>> rotatedBones = new LinkedList<>();

        float
                angleRad = (float) Math.toRadians(sourceDegrees + 180), // Make it face away from the source
                cos = (float) Math.cos(angleRad),
                sin = (float) Math.sin(angleRad);

        for (List<Vec2f> bone : bones) {
            List<Vec2f> rotatedBone = new ArrayList<>();

            for (Vec2f vertex : bone) {
                float
                        translatedX = vertex.x,
                        translatedY = vertex.y - size,
                        rotatedX = translatedX * cos - translatedY * sin,
                        rotatedY = translatedX * sin + translatedY * cos;

                float epsilon = 1e-2f;
                if (Math.abs(rotatedX) < epsilon) {
                    rotatedX = epsilon * Math.signum(rotatedX + epsilon/10);
                } if (Math.abs(rotatedY) < epsilon) {
                    rotatedY = epsilon * Math.signum(rotatedY + epsilon/10);
                }

                rotatedBone.add(new Vec2f(rotatedX, rotatedY));
            }

            rotatedBones.add(rotatedBone);
        }

        return rotatedBones;
    }

    public static List<List<Vec2f>> squishShadow(List<List<Vec2f>> vertices, float sizeMultiplier) { // Make the shadow sizeMultiplier times bigger/smaller
        return vertices; // Remove errors temporarily
    }
}
