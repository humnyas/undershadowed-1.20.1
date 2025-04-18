package fabric.humnyas.undershadowed.render;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;

import com.mojang.blaze3d.systems.RenderSystem;
import fabric.humnyas.undershadowed.geometry.PolygonMath;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.*;

public class AppearanceHandler {
    // Light source and direction handling
    public static List<Float> getSourceAngles(Entity entity, List<Vec3d> lightPositions) {
        Vec3d entityPos = entity.getPos();
        List<Float> sources = new ArrayList<>();

        // Sunlight - Pos X morning, neg X evening, nothing at night
        Integer sunAngle = getSunDirectionAngle(entity.getWorld());
        if (sunAngle != null) sources.add(Float.valueOf(sunAngle));

        for (Vec3d lightPos : lightPositions) {
            double dx = lightPos.x - entityPos.x;
            double dz = lightPos.z - entityPos.z;
            float angle = (float) Math.toDegrees(Math.atan2(dz, dx));
            if (angle < 0) angle += 360;
            sources.add(angle);
        }

        return sources;
    }

    public static List<Vec3d> getNearbySourcePositions(Entity entity) {
        World world = entity.getWorld();
        BlockPos entityBlockPos = entity.getBlockPos();
        Vec3d entityPosVec = entity.getPos();
        List<Vec3d> lightPositions = new ArrayList<>();

        for (int dx = -SOURCE_BLOCK_RADIUS; dx <= SOURCE_BLOCK_RADIUS; dx++) {
            for (int dy = -SOURCE_BLOCK_RADIUS; dy <= SOURCE_BLOCK_RADIUS; dy++) {
                for (int dz = -SOURCE_BLOCK_RADIUS; dz <= SOURCE_BLOCK_RADIUS; dz++) {
                    BlockPos pos = entityBlockPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    int luminance = state.getLuminance();
                    if (luminance <= 0) continue;

                    Vec3d lightPos = Vec3d.ofCenter(pos);
                    double distSq = entityPosVec.squaredDistanceTo(lightPos);

                    if (luminance * luminance > distSq) {
                        HitResult hit = world.raycast(new RaycastContext(entity.getEyePos(), lightPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
                        if (hit.getType() == HitResult.Type.MISS) {
                            lightPositions.add(lightPos);
                        }
                    }
                }
            }
        }

        return lightPositions;
    }

    @Nullable public static Integer getSunDirectionAngle(World world) {
        if (world == null) return null;

        long timeOfDay = world.getTimeOfDay() % 24000;

        if (timeOfDay < 6000) {
            return 0; // Morning
        } else if (timeOfDay < 12000) {
            return 180; // Afternoon
        } else {
            return null; // Nighttime
        }
    }


    // Rendering logic
    public static void renderShadow (
            MatrixStack matrices, Entity entity,
            float tickDelta, List<List<Vec2f>> verticesList, float opacity
    ) {
        if (verticesList == null || verticesList.isEmpty()) return;

        Vec3d
                camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos(),
                pos = entity.getLerpedPos(tickDelta);

        MatrixStack.Entry matrixEntry = matrices.peek();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        Matrix4f matrix = matrixEntry.getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (List<Vec2f> polygon : verticesList) {
            List<List<Vec2f>> triangles = PolygonMath.earClipPolygon(polygon);
            for (List<Vec2f> tri : triangles) {
                addVertex(buffer, matrix, tri.get(0), pos, camPos, opacity);
                addVertex(buffer, matrix, tri.get(1), pos, camPos, opacity);
                addVertex(buffer, matrix, tri.get(2), pos, camPos, opacity);
            }
        }
        tessellator.draw();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addVertex(BufferBuilder buffer, Matrix4f matrix, Vec2f point, Vec3d entityPos, Vec3d camPos, float opacity) {
        float x = (float) (point.x + entityPos.getX() - camPos.getX());
        float y = (float) (entityPos.getY() - camPos.getY() + 0.01); // Offset slightly to prevent z-fighting
        float z = (float) (point.y + entityPos.getZ() - camPos.getZ());

        buffer.vertex(matrix, x, y, z)
                .color(0f, 0f, 0f, opacity) // Black shadow
                .next();
    }


    // Transparency calculations
    public static float getTransparency(Entity entity, List<Float> sources) {
        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager resourceManager = client.getResourceManager();
        EntityRenderer<? super Entity> renderer = client.getEntityRenderDispatcher().getRenderer(entity);

        Identifier texture = renderer.getTexture(entity);
        World world = entity.getEntityWorld();
        BlockPos entityPos = entity.getBlockPos();

        if (sources.isEmpty()) return 0;

        int
                lightLevel = world.getLightLevel(entityPos);
        float
                transparency = 0.8f,
                lightMultiplier = lightLevel / 15.0f,
                airPenalty = 0.0f,
                entityAlpha; // Will always be below 1.0f based on implementation

        // Calculate the distance between the entities feet and the floor
        for (int i = 1; i <= 8; i++) {
            BlockPos below = entityPos.down(i);
            if (!world.getBlockState(below).isAir()) break;

            airPenalty += 0.1f;
        }

        // Calculate the entities alpha
        if (ALPHA_CACHE.containsKey(texture)) {
            entityAlpha = ALPHA_CACHE.get(texture);
        } else {
            entityAlpha = getAlpha(resourceManager, texture);
            ALPHA_CACHE.put(texture, entityAlpha);
        }

        transparency -= airPenalty;  // -0.1f for every block of air between feet and floor
        transparency *= lightMultiplier; // Make the shadow stronger by the light level
        transparency /= sources.size(); // Divvy up the shadows based on the amount of light sources
        transparency *= entityAlpha; // Make the shadow as transparent as the entity

        return transparency;
    }

    private static float getAlpha(ResourceManager resourceManager, Identifier texture) {
        Resource resource = resourceManager.getResource(texture).orElse(null);

        float entityAlpha = 0.0f;

        if (resource != null) {
            try (NativeImage image = NativeImage.read(resource.getInputStream())) {
                int pixels = image.getHeight() * image.getWidth();

                // Iterates through every pixel
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {

                        // Gets the alpha of each pixel
                        entityAlpha += image.getOpacity(x, y);
                    }
                }

                // Averages the alpha
                // Get opacity returns a value between 0-255, so it needs to be divided by 255
                return Math.abs(entityAlpha / pixels);
            } catch (IOException ignored) {}
        }

        // Fallback value
        return 1.0f;
    }

}
