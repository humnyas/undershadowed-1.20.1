package fabric.humnyas.undershadowed.core;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShadowDataRegistry {
    public record BoneDataRecord(
            float posX, float posY, float posZ,
            float pitch, float yaw, float roll,
            float sizeX, float sizeY, float sizeZ
    ) {}

    // Makes it so expensive checks are run less often - stores the pre-computed results
    public static final Map<Entity, List<Vec3d>> SOURCE_POSITIONS = new ConcurrentHashMap<>();
    public static final Map<Entity, Long> LAST_UPDATE_TICK = new HashMap<>();

    // Caches data for each model that will stay the same indefinitely
    public static final Map<Identifier, Float> ALPHA_CACHE = new HashMap<>();
    public static final Map<Class<?>, Field[]> MODEL_FIELD_CACHE = new HashMap<>();

    public final static int UPDATE_INTERVAL = 20; // In ticks
    public final static int SOURCE_BLOCK_RADIUS = 14; // In blocks, square
}
