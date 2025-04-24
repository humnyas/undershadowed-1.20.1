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
					ShadowEngine.makeShadow(entity, context);
				}
			}
		});
	}

	/*
		v main
			v java
				v fabric.humnyas.undershadowed
					v core
						ShadowDataRegistry.class
						ShadowEngine.class
					v geometry
						PolygonMath.class
						ShadowDataUtils.class
						ShadowGeometry.class
					> mixin
					v render
						LightSourceHelper.class
						ShadowRenderer.class
						TransparencyCalculator.class
					UndershadowedClient.class
			> resources

		Where
			ShadowDataRegistry - Holds important data, maps and records
			ShadowEngine - Entry point for the shadow rendering
			PolygonMath - Holds mathematical functions, like earClipping
			ShadowDataUtils - Has methods which get data for rendering shadows
			ShadowGeometry - Manipulates the vertices so the shadows can react to the environment
			LightSourceHelper - Calculates data like the angles to nearby light sources
			ShadowRenderer - Holds methods to actually render the shadow
			TransparencyCalculator - Calculates data like the transparency of the shadow
			UndershadowedClient - Main entry point for the mod
	*/
}