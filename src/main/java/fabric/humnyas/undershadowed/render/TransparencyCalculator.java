package fabric.humnyas.undershadowed.render;
import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;

public class TransparencyCalculator {
    public static float getTransparency(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager resourceManager = client.getResourceManager();
        EntityRenderer<? super Entity> renderer = client.getEntityRenderDispatcher().getRenderer(entity);

        Identifier texture = renderer.getTexture(entity);
        World world = entity.getEntityWorld();
        BlockPos entityPos = entity.getBlockPos();

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
