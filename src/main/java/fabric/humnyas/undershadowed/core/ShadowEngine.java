package fabric.humnyas.undershadowed.core;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;
import static fabric.humnyas.undershadowed.geometry.ShadowGeometry.*;
import static fabric.humnyas.undershadowed.geometry.ModelDataExtractor.*;

import fabric.humnyas.undershadowed.Undershadowed;
import fabric.humnyas.undershadowed.render.LightSourceHelper;
import fabric.humnyas.undershadowed.render.ShadowRenderer;
import fabric.humnyas.undershadowed.render.TransparencyCalculator;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.*;

public class ShadowEngine {
    public static Map<ModelPart, BoneDataRecord> processEntityData(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Object renderer = dispatcher.getRenderer(entity);

        String entityClassName = entity.getClass().getSimpleName();
        if (entityClassName.contains("Client")) {
            entityClassName = entityClassName.replace("Client", "");
        }

        try {
            Class<?> rendererClass = Class.forName("net.minecraft.client.render.entity." + entityClassName + "Renderer");
            if (rendererClass.isInstance(renderer)) {
                Method getModelMethod = rendererClass.getMethod("getModel");
                Object model = getModelMethod.invoke(renderer);
                return extractBoneData(model);
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static void makeShadow(Entity entity, WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        Map<ModelPart, BoneDataRecord> boneData = processEntityData(entity);
        if (boneData == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getTickDelta();
        World world = client.world;
        if (world == null) return;

        long lastTick;
        List<Vec3d> sources = new ArrayList<>();

        // Resets the time for each entity
        if (LAST_UPDATE_TICK.containsKey(entity)) {
            lastTick = LAST_UPDATE_TICK.get(entity);
        } else {
            lastTick = world.getTime();
            LAST_UPDATE_TICK.put(entity, lastTick);
        }

        // Gets new light source vectors every UPDATE_INTERVAL ticks
        if (world.getTime() - lastTick > UPDATE_INTERVAL) {
            sources = LightSourceHelper.getNearbySourcePositions(entity);
            SOURCE_POSITIONS.put(entity, sources);
            LAST_UPDATE_TICK.put(entity, world.getTime());
        } else if (SOURCE_POSITIONS.containsKey(entity)) {
            sources = SOURCE_POSITIONS.get(entity);
        }

        List<List<Vec2f>> totalVertices = new LinkedList<>();
        List<List<Vector3f>> vertices = getVertices(boneData, entity);
        List<Float[]> sourceAngles = LightSourceHelper.getSourceAngles(entity, sources);

        float opacity = TransparencyCalculator.getTransparency(entity);

        float size = getShadowSize(entity, vertices);
        float centerBottomOffset = centerBottomOffset(entity, vertices);

        for (Float[] angles : sourceAngles) {
            float horizontalAngle = angles[0], verticalAngle = angles[1];

            float morphedSize = morphShadowSize(size, verticalAngle);

            List<List<Vec2f>> flattenedVertices = flattenVertices(vertices, horizontalAngle); // Flattens the vertices to the angle
            List<List<Vec2f>> prunedVertices = pruneVertices(flattenedVertices); // Removes redundant vertices
            List<List<Vec2f>> rotatedVertices = rotateShadow(prunedVertices, horizontalAngle, centerBottomOffset); // Rotates the shadow to face away from light source
            List<List<Vec2f>> squishedVertices = squishShadow(rotatedVertices, morphedSize); // flattens / stretches the shadow to appear morphed in the way it should be expected


            totalVertices.addAll(squishedVertices);
        }
        // Renders the shadow
        ShadowRenderer.renderShadow(matrices, entity, tickDelta, totalVertices, opacity);
    }
}


