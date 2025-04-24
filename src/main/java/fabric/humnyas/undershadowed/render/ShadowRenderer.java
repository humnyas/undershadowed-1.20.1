package fabric.humnyas.undershadowed.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fabric.humnyas.undershadowed.math.PolygonMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class ShadowRenderer {
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
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
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
        RenderSystem.depthFunc(GL11.GL_LESS);
    }

    private static void addVertex(BufferBuilder buffer, Matrix4f matrix, Vec2f point, Vec3d entityPos, Vec3d camPos, float opacity) {
        float x = (float) (point.x + entityPos.getX() - camPos.getX());
        float y = (float) (entityPos.getY() - camPos.getY() + 0.01); // Offset slightly to prevent z-fighting
        float z = (float) (point.y + entityPos.getZ() - camPos.getZ());

        buffer.vertex(matrix, x, y, z)
                .color(0f, 0f, 0f, opacity) // Black shadow
                .next();
    }

}
