package fabric.humnyas.undershadowed.render;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fabric.humnyas.undershadowed.core.ShadowDataRegistry.SOURCE_BLOCK_RADIUS;

public class LightSourceHelper {
    public static List<Float[]> getSourceAngles(Entity entity, List<Vec3d> lightPositions) {
        Vec3d entityPos = entity.getPos();
        List<Float[]> sources = new ArrayList<>();

        // Sunlight - Pos X morning, neg X evening, nothing at night
        Float[] sunAngles = getSunDirectionAngle(entity.getWorld());
        if (sunAngles != null)  sources.add(sunAngles);

        for (Vec3d lightPos : lightPositions) {
            double dx = lightPos.x - entityPos.x,
                    dy = lightPos.y - entityPos.y,
                    dz = lightPos.z - entityPos.z;

            // Horizontal angle
            float horAngle = (float) Math.toDegrees(Math.atan2(dz, dx)) + 90;
            if (horAngle < 0) horAngle += 360;

            // Vertical angle
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float vertAngle = (float) Math.toDegrees(Math.atan2(dy, horizontalDistance));

            sources.add(new Float[] { horAngle, vertAngle });
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

    @Nullable
    public static Float[] getSunDirectionAngle(World world) {
        if (world == null) return null;

        // Celestial angle ranges from 0.0 (sunrise) -> 1.0 (next sunrise)
        float celestialAngle = world.getSkyAngle(1.0f); // 1.0 = full partial tick
        float radians = celestialAngle * 2.0f * (float) Math.PI;
        float elevation = (float) Math.cos(radians); // -1 at midnight, +1 at noon

        float horDegrees = celestialAngle < 0.5f ? -90f : 90f;
        float vertDegrees = (float) Math.toDegrees((float) Math.asin(elevation));

        if (elevation <= 0f || vertDegrees < 0f) return null;
        return new Float[]{horDegrees, vertDegrees};
    }

}
