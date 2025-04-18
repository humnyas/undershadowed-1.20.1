package fabric.humnyas.undershadowed.core;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;

import fabric.humnyas.undershadowed.render.AppearanceHandler;
import fabric.humnyas.undershadowed.geometry.ShadowGeometry;
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

//      TODO: Entity head looks wrong
//      TODO: Animations aren't played in first person
//      TODO: Shadows are visible through entities and blocks

// TODO: fix removing vanilla shadows

public class ShadowEngine {
    public static void processEntityData(Entity entity, WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Object renderer = dispatcher.getRenderer(entity);

        String entityClassName = entity.getClass().getSimpleName();
        if (entityClassName.contains("Client")) {
            entityClassName = entityClassName.replace("Client", "");
        }

        try {
            Class<?> rendererClass = Class.forName("net.minecraft.client.render.entity." + entityClassName + "Renderer");
            Map<ModelPart, BoneDataRecord> boneData = null;

            if (rendererClass.isInstance(renderer)) {
                Method getModelMethod = rendererClass.getMethod("getModel");
                Object model = getModelMethod.invoke(renderer);

                boneData = ShadowGeometry.extractBoneData(model);
            }

            if (boneData != null) {
                MatrixStack matrices = context.matrixStack();

                makeShadow(entity, boneData, matrices);
            }
        } catch (Exception ignored) {}
    }

    public static void makeShadow(
            Entity entity, Map<ModelPart, BoneDataRecord> boneData, MatrixStack matrices
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) return;

        float tickDelta = client.getTickDelta();
        long lastTick; List<Vec3d> sources = new ArrayList<>();
        List<List<Vec2f>> totalVertices = new LinkedList<>();

        // Resets the time for each entity every
        if (LAST_UPDATE_TICK.containsKey(entity)) {
            lastTick = LAST_UPDATE_TICK.get(entity);
        } else {
            lastTick = world.getTime();
            LAST_UPDATE_TICK.put(entity, lastTick);
        }

        // Gets new light source vectors every UPDATE_INTERVAL ticks
        if (world.getTime() - lastTick > UPDATE_INTERVAL) {
            sources = AppearanceHandler.getNearbySourcePositions(entity);
            SOURCE_POSITIONS.put(entity, sources);
            LAST_UPDATE_TICK.put(entity, world.getTime());
        } else if (SOURCE_POSITIONS.containsKey(entity)) {
            sources = SOURCE_POSITIONS.get(entity);
        }

        List<List<Vector3f>> vertices = ShadowGeometry.getVertices(boneData, entity);
        List<Float> sourceAngles = AppearanceHandler.getSourceAngles(entity, sources);
        float opacity = AppearanceHandler.getTransparency(entity, sourceAngles);

        // Gets vertices for each light source affecting the entity
        for (float angle : sourceAngles) {
            List<List<Vec2f>> flattenedVertices = ShadowGeometry.flattenVertices(vertices, angle); // TODO : Make sure it returns the list with the vertices rotated away from the source
            List<List<Vec2f>> prunedVertices = ShadowGeometry.pruneVertices(flattenedVertices);

            // TODO: Get size
            //      -> Size proportionate to the angle of the sun over the entity
            //            --> Directly above the character, make it look almost like a circle
            //            --> At the horizon, make it approximately 1.5x the actual height

            totalVertices.addAll(prunedVertices);
        }

        // TODO: Size proportionate to the angle of the sun over the entity
        // TODO: Pixelate shadow output
        // TODO: Bend shadow if intersecting non-flat surfaces
        // Renders the shadow
        AppearanceHandler.renderShadow(matrices, entity, tickDelta, totalVertices, opacity);
    }
}
