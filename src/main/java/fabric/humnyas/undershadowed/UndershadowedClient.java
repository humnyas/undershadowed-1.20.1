package fabric.humnyas.undershadowed;

import fabric.humnyas.undershadowed.core.ShadowEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class UndershadowedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// At the end of every tick, goes through every entity and adds custom shadows to them
		WorldRenderEvents.AFTER_ENTITIES.register((context) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientWorld world = client.world;
			if (world == null) return;

			for (Entity entity : client.world.getEntities()) {
				if (entity instanceof LivingEntity) {
					ShadowEngine.processEntityData(entity, context);
				}
			}
		});

		// TODO: Also remove vanilla shadows on the client side
	}
}