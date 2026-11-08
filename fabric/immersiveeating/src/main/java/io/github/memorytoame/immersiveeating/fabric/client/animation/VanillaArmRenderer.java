package io.github.memorytoame.immersiveeating.fabric.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.memorytoame.immersiveeating.fabric.client.render.ArmModelOffset;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.GeoQuad;
import software.bernie.geckolib.cache.model.GeoVertex;
import software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;

@Environment(EnvType.CLIENT)
public final class VanillaArmRenderer {
   private static final float PIXEL = 0.0625F;

   public static boolean isArmBone(GeoBone bone) {
      return isDedicatedAnchor(bone) || getArm(bone) != null && !hasDedicatedAnchorChild(bone);
   }

   public static VanillaArmRenderer.CapturedArm captureArmPose(PoseStack poseStack, GeoBone bone, boolean mirrorModel) {
      HumanoidArm arm = getArm(bone);
      GeoCube armCube = findArmCube(bone);
      if (arm == null) {
         return null;
      } else {
         poseStack.pushPose();
         if (armCube == null) {
            poseStack.popPose();
            return null;
         } else {
            HumanoidArm renderedArm = mirrorModel ? opposite(arm) : arm;
            Matrix4f armPose = createPreviewCubeArmPose(
               poseStack, renderedArm, armCube, ArmModelOffset.forPlayer(Minecraft.getInstance().player).matrixFor(renderedArm)
            );
            VanillaArmRenderer.CapturedArm capturedArm = new VanillaArmRenderer.CapturedArm(renderedArm, armPose, new Matrix3f(armPose).normal());
            poseStack.popPose();
            return capturedArm;
         }
      }
   }

   private static HumanoidArm opposite(HumanoidArm arm) {
      return arm == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
   }

   public static void renderCapturedArm(
      VanillaArmRenderer.CapturedArm capturedArm, SubmitNodeCollector collector, int packedLight, float partialTick, boolean mirrored
   ) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && !minecraft.player.isInvisible()) {
         PoseStack poseStack = new PoseStack();
         Matrix4f pose = new Matrix4f(capturedArm.pose());
         Matrix3f normal = new Matrix3f(capturedArm.normal());
         if (mirrored) {
            // the mirrored model would turn the arm inside out (and the faces would be culled): undo the mirror on the arm itself
            pose.scale(-1.0F, 1.0F, 1.0F);
            normal.scale(-1.0F, 1.0F, 1.0F);
         }
         poseStack.last().pose().set(pose);
         poseStack.last().normal().set(normal);
         renderVanillaArm(minecraft, capturedArm, poseStack, collector, packedLight, mirrored);
      }
   }

   private static void renderVanillaArm(
      Minecraft minecraft, VanillaArmRenderer.CapturedArm capturedArm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, boolean mirrored
   ) {
      AvatarRenderer<?> playerRenderer = minecraft.getEntityRenderDispatcher().getPlayerRenderer(minecraft.player);
      net.minecraft.resources.Identifier skin = minecraft.player.getSkin().body().texturePath();
      // when mirrored, the other arm's model is drawn flipped so it ends up where this one goes
      HumanoidArm drawn = mirrored ? opposite(capturedArm.arm()) : capturedArm.arm();
      if (drawn == HumanoidArm.RIGHT) {
         playerRenderer.renderRightHand(poseStack, collector, packedLight, skin, minecraft.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
      } else {
         playerRenderer.renderLeftHand(poseStack, collector, packedLight, skin, minecraft.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
      }
   }

   public static void renderCapturedArms(
      Iterable<VanillaArmRenderer.CapturedArm> capturedArms, SubmitNodeCollector collector, int packedLight, float partialTick, boolean mirrored
   ) {
      for (VanillaArmRenderer.CapturedArm capturedArm : capturedArms) {
         renderCapturedArm(capturedArm, collector, packedLight, partialTick, mirrored);
      }
   }

   private static GeoCube findArmCube(GeoBone bone) {
      return bone instanceof CuboidGeoBone cuboid && cuboid.cubes.length > 0 ? cuboid.cubes[0] : null;
   }

   private static HumanoidArm getArm(GeoBone bone) {
      String normalizedName = normalize(bone.name());

      return switch (normalizedName) {
         case "rightarm", "righthandpos" -> HumanoidArm.RIGHT;
         case "leftarm", "lefthandpos" -> HumanoidArm.LEFT;
         default -> null;
      };
   }

   private static boolean isDedicatedAnchor(GeoBone bone) {
      String normalizedName = normalize(bone.name());
      return normalizedName.equals("lefthandpos") || normalizedName.equals("righthandpos");
   }

   private static boolean hasDedicatedAnchorChild(GeoBone bone) {
      for (GeoBone child : bone.children()) {
         if (isDedicatedAnchor(child)) {
            return true;
         }
      }

      return false;
   }

   private static String normalize(String name) {
      return name.replace("_", "").replace(" ", "").toLowerCase();
   }

   private static Matrix4f createPreviewCubeArmPose(PoseStack poseStack, HumanoidArm arm, GeoCube cube, Matrix4f localOffset) {
      VanillaArmRenderer.Bounds bounds = findBounds(cube);
      Vector3f previewCenter = new Vector3f(
         (bounds.minX() + bounds.maxX()) * 0.5F, (bounds.minY() + bounds.maxY()) * 0.5F, (bounds.minZ() + bounds.maxZ()) * 0.5F
      );
      Matrix4f cubeTransform = new Matrix4f().identity();
      Vec3 cubePivot = cube.pivot();
      Vec3 cubeRotation = cube.rotation();
      cubeTransform.translate((float)cubePivot.x() / 16.0F, (float)cubePivot.y() / 16.0F, (float)cubePivot.z() / 16.0F);
      cubeTransform.rotateZ((float)cubeRotation.z());
      cubeTransform.rotateY((float)cubeRotation.y());
      cubeTransform.rotateX((float)cubeRotation.x());
      cubeTransform.translate(-((float)cubePivot.x()) / 16.0F, -((float)cubePivot.y()) / 16.0F, -((float)cubePivot.z()) / 16.0F);
      Vector3f targetCenter = cubeTransform.transformPosition(previewCenter);
      Vector3f vanillaCenter = arm == HumanoidArm.RIGHT ? new Vector3f(-6.0F, 6.0F, 0.0F).mul(0.0625F) : new Vector3f(6.0F, 6.0F, 0.0F).mul(0.0625F);
      Matrix4f vanillaToPreview = new Matrix4f().identity();
      vanillaToPreview.translate(targetCenter);
      vanillaToPreview.rotateZ((float)cubeRotation.z());
      vanillaToPreview.rotateY((float)cubeRotation.y());
      vanillaToPreview.rotateX((float)cubeRotation.x());
      vanillaToPreview.rotateZ((float) Math.PI);
      Vec3 armSize = cube.size();
      float sizeX = (float)armSize.x();
      float sizeY = (float)armSize.y();
      float sizeZ = (float)armSize.z();
      if (sizeX <= 0.0F || sizeY <= 0.0F || sizeZ <= 0.0F) {
         sizeX = (bounds.maxX() - bounds.minX()) / 0.0625F;
         sizeY = (bounds.maxY() - bounds.minY()) / 0.0625F;
         sizeZ = (bounds.maxZ() - bounds.minZ()) / 0.0625F;
      }

      vanillaToPreview.scale(safeScale(sizeX, 4.0F), safeScale(sizeY, 12.0F), safeScale(sizeZ, 4.0F));
      vanillaToPreview.translate(-vanillaCenter.x(), -vanillaCenter.y(), -vanillaCenter.z());
      return new Matrix4f(poseStack.last().pose()).mul(localOffset).mul(vanillaToPreview);
   }

   private static float safeScale(float size, float vanillaSize) {
      return size > 0.0F ? size / vanillaSize : 1.0F;
   }

   private static VanillaArmRenderer.Bounds findBounds(GeoCube cube) {
      float minX = Float.POSITIVE_INFINITY;
      float minY = Float.POSITIVE_INFINITY;
      float minZ = Float.POSITIVE_INFINITY;
      float maxX = Float.NEGATIVE_INFINITY;
      float maxY = Float.NEGATIVE_INFINITY;
      float maxZ = Float.NEGATIVE_INFINITY;

      for (GeoQuad quad : cube.quads()) {
         if (quad != null) {
            for (GeoVertex vertex : quad.vertices()) {
               minX = Math.min(minX, vertex.posX());
               minY = Math.min(minY, vertex.posY());
               minZ = Math.min(minZ, vertex.posZ());
               maxX = Math.max(maxX, vertex.posX());
               maxY = Math.max(maxY, vertex.posY());
               maxZ = Math.max(maxZ, vertex.posZ());
            }
         }
      }

      return Float.isInfinite(minX)
         ? new VanillaArmRenderer.Bounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)
         : new VanillaArmRenderer.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
   }

   public record CapturedArm(HumanoidArm arm, Matrix4f pose, Matrix3f normal) {
   }
}

