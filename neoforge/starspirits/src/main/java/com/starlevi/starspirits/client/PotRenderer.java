package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starlevi.starspirits.block.PotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** El espiritu que vive en la maceta: mira a quien se acerca, salta de vez en cuando y de noche duerme. */
public class PotRenderer implements BlockEntityRenderer<PotBlockEntity> {
    private final SpiritRig.Pose pose = new SpiritRig.Pose();

    public PotRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PotBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.empty() || be.getLevel() == null) {
            return;
        }
        float now = be.getLevel().getGameTime() + partial;
        int seed = (int) (be.getBlockPos().asLong() ^ (be.getBlockPos().asLong() >>> 32));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (be.getBlockPos().getX() + 0.5), dz = cam.z - (be.getBlockPos().getZ() + 0.5);
        boolean sleeping = be.getLevel().isNight() && be.getLevel().getMaxLocalRawBrightness(be.getBlockPos().above()) < 8;
        float yaw = sleeping ? (seed & 3) * 90 : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float cycle = (now + Math.floorMod(seed, 200)) % 160;
        SpiritRig.Pose p = this.pose;
        p.walk = 0;
        p.walkAmount = 0;
        p.age = now;
        p.headYaw = 0;
        p.headPitch = sleeping ? 0 : -Mth.clamp((float) (cam.y - be.getBlockPos().getY() - 1) * 15, -20, 20);
        p.happy = !sleeping && cycle < 16 ? cycle / 16f : -1;
        p.sleeping = sleeping;
        p.seed = seed;
        ps.pushPose();
        ps.translate(0.5, 7.5 / 16.0, 0.5);
        SpiritDraw.draw(be.spirit, ps, buffers, light, p, yaw, 0.9f);
        ps.popPose();
    }
}
