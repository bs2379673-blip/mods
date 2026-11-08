package io.github.memorytoame.immersiveeating.fabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.memorytoame.immersiveeating.fabric.client.animation.CameraRotationComposer;
import io.github.memorytoame.immersiveeating.fabric.client.animation.ClientAnimationManager;
import io.github.memorytoame.immersiveeating.fabric.client.animation.HeldItemMotion;
import io.github.memorytoame.immersiveeating.fabric.client.animation.VanillaArmRenderer;
import io.github.memorytoame.immersiveeating.fabric.common.content.definition.FoodDefinitionManager;
import io.github.memorytoame.immersiveeating.fabric.common.content.item.Empty;
import io.github.memorytoame.immersiveeating.fabric.init.ModItems;
import io.github.memorytoame.immersiveeating.fabric.utils.AnimationUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.cache.model.GeoQuad;
import software.bernie.geckolib.cache.model.GeoVertex;
import software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone;
import software.bernie.geckolib.cache.model.cuboid.GeoCube;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Port to Minecraft 1.21.11 / GeckoLib 5: the model is walked during the submit pass (with the animated bone
 * poses applied), arm bones are replaced by the player's vanilla arm and the rest of the cubes are drawn with the
 * matrices captured at that moment.
 */
@Environment(EnvType.CLIENT)
public class CustomRenderer extends GeoItemRenderer<Empty> {
   public static Map<String, CustomRenderer> rendererMap = new HashMap<>(32);
   private final List<VanillaArmRenderer.CapturedArm> capturedArms = new ArrayList<>(8);
   private final List<DrawnCube> drawnCubes = new ArrayList<>(64);
   private final String itemId;
   private boolean capturingCameraRotation;
   private boolean mirrorLeftHandModel;
   private ItemStack currentStack = ItemStack.EMPTY;
   private float currentPartialTick;

   private record DrawnCube(GeoCube cube, Matrix4f pose, Matrix3f normal) {
   }

   public CustomRenderer(String id) {
      super(new CustomRenderer.Model(getModelId(id)));
      this.itemId = getItemId(id);
   }

   /** Renders the animated dish (and the player's arms) in first person. */
   public void renderFirstPerson(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
      Minecraft mc = Minecraft.getInstance();
      this.capturedArms.clear();
      this.drawnCubes.clear();
      poseStack.pushPose();
      float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      HeldItemMotion.applyIdleMotion(poseStack, partialTick);
      HeldItemMotion.applyWalkMotion(poseStack, partialTick);
      HeldItemMotion.applyInertiaMotion(poseStack, partialTick);
      HeldItemMotion.applyJumpMotion(poseStack, partialTick);
      HeldItemMotion.applyCrouchMotion(poseStack, partialTick);
      boolean firstPerson = transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
      this.mirrorLeftHandModel = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
      boolean mirroredRender = this.mirrorLeftHandModel;
      this.capturingCameraRotation = firstPerson && AnimationUtils.isAnimationLocked();
      this.currentStack = stack;
      this.currentPartialTick = partialTick;
      if (firstPerson) {
         CameraRotationComposer.clearBlockbenchRotation();
      }

      try {
         GeoItemRenderer.RenderData data = new GeoItemRenderer.RenderData(stack, new ItemStackRenderState(), transformType, mc.level, mc.player);
         GeoRenderState state = this.fillRenderState(ModItems.EMPTY, data, this.createRenderState(ModItems.EMPTY, data), partialTick);
         state.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight);
         state.addGeckolibData(DataTickets.PACKED_OVERLAY, OverlayTexture.NO_OVERLAY);
         state.addGeckolibData(DataTickets.HAS_GLINT, false);
         this.submit(state, poseStack, collector, 0);
      } finally {
         this.capturingCameraRotation = false;
         this.mirrorLeftHandModel = false;
      }

      poseStack.popPose();
      VanillaArmRenderer.renderCapturedArms(this.capturedArms, collector, packedLight, partialTick, mirroredRender);
      this.capturedArms.clear();
      this.currentStack = ItemStack.EMPTY;
   }

   @Override
   public void submitRenderTasks(RenderPassInfo<GeoRenderState> renderPassInfo, net.minecraft.client.renderer.OrderedSubmitNodeCollector renderTasks,
                                 @Nullable RenderType renderType) {
      if (renderType == null) {
         return;
      }
      this.drawnCubes.clear();
      final PoseStack poseStack = renderPassInfo.poseStack();
      renderPassInfo.renderPosed(() -> {
         for (GeoBone bone : renderPassInfo.model().topLevelBones()) {
            this.walkBone(poseStack, bone, true);
         }
      });
      if (this.drawnCubes.isEmpty()) {
         return;
      }
      final List<DrawnCube> cubes = new ArrayList<>(this.drawnCubes);
      final boolean mirror = this.mirrorLeftHandModel;
      final int light = renderPassInfo.packedLight();
      final int overlay = renderPassInfo.packedOverlay();
      final int colour = renderPassInfo.renderColor();
      // the matrices are already complete (camera space): submit from an identity pose
      renderTasks.submitCustomGeometry(new PoseStack(), renderType, (pose, buffer) -> {
         for (DrawnCube c : cubes) {
            renderCube(c, buffer, light, overlay, colour, mirror);
         }
      });
   }

   private void walkBone(PoseStack poseStack, GeoBone bone, boolean root) {
      poseStack.pushPose();
      RenderUtil.prepMatrixForBone(poseStack, bone);
      if (this.mirrorLeftHandModel && root) {
         poseStack.mulPose(new Matrix4f().m00(-1.0F));
      }

      boolean cameraBone = "camera".equals(bone.name());
      boolean armBone = VanillaArmRenderer.isArmBone(bone);
      boolean moveBoneBehindCamera = !this.hasInvisibleBoneParent(bone) && this.isInvisibleBone(bone) && this.shouldHideInvisibleBone(this.currentPartialTick);
      if (this.capturingCameraRotation && cameraBone) {
         float rx = bone.baseRotX(), ry = bone.baseRotY(), rz = bone.baseRotZ();
         if (bone.frameSnapshot != null) {
            rx += bone.frameSnapshot.getRotX();
            ry += bone.frameSnapshot.getRotY();
            rz += bone.frameSnapshot.getRotZ();
         }
         CameraRotationComposer.captureBlockbenchRotationRadians(rx, ry, rz);
      }

      if (moveBoneBehindCamera) {
         poseStack.translate(0.0F, 0.0F, -1000.0F);
      }

      boolean hidden = bone.frameSnapshot != null && bone.frameSnapshot.isHidden();
      if (armBone) {
         VanillaArmRenderer.CapturedArm capturedArm = VanillaArmRenderer.captureArmPose(poseStack, bone, this.mirrorLeftHandModel);
         if (capturedArm != null) {
            this.capturedArms.add(capturedArm);
         }
      } else if (!hidden && bone instanceof CuboidGeoBone cuboid) {
         for (GeoCube cube : cuboid.cubes) {
            poseStack.pushPose();
            cube.translateToPivotPoint(poseStack);
            cube.rotate(poseStack);
            cube.translateAwayFromPivotPoint(poseStack);
            this.drawnCubes.add(new DrawnCube(cube, new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal())));
            poseStack.popPose();
         }
      }

      if (bone.frameSnapshot == null || !bone.frameSnapshot.areChildrenHidden()) {
         for (GeoBone child : bone.children()) {
            this.walkBone(poseStack, child, false);
         }
      }
      poseStack.popPose();
   }

   private static void renderCube(DrawnCube c, VertexConsumer buffer, int packedLight, int packedOverlay, int colour, boolean mirror) {
      GeoQuad[] quads = c.cube().quads();
      if (quads == null) {
         return;
      }
      for (GeoQuad quad : quads) {
         if (quad == null) {
            continue;
         }
         Vector3f normal = c.normal().transform(quad.normalVec());
         RenderUtil.fixInvertedFlatCube(c.cube(), normal);
         GeoVertex[] vertices = quad.vertices();
         if (mirror) {
            for (int i = vertices.length - 1; i >= 0; i--) {
               addVertex(vertices[i], c.pose(), normal, buffer, packedLight, packedOverlay, colour);
            }
         } else {
            for (GeoVertex vertex : vertices) {
               addVertex(vertex, c.pose(), normal, buffer, packedLight, packedOverlay, colour);
            }
         }
      }
   }

   private static void addVertex(GeoVertex vertex, Matrix4f poseMatrix, Vector3f normal, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
      Vector4f position = poseMatrix.transform(new Vector4f(vertex.posX(), vertex.posY(), vertex.posZ(), 1.0F));
      buffer.addVertex(position.x(), position.y(), position.z(), colour, vertex.texU(), vertex.texV(), packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
   }

   @Override
   public @Nullable RenderType getRenderType(GeoRenderState renderState, Identifier texture) {
      // like the original (entityTranslucentCull): back faces culled, so the dish does not look see-through
      return RenderTypes.itemEntityTranslucentCull(texture);
   }

   private static String getModelId(String id) {
      Identifier resourceLocation = Identifier.tryParse(id);
      return resourceLocation == null ? id : resourceLocation.getPath();
   }

   private static String getItemId(String id) {
      Identifier resourceLocation = Identifier.tryParse(id);
      return resourceLocation == null ? id : resourceLocation.toString();
   }

   private boolean shouldHideInvisibleBone(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      boolean armSwinging = mc.player != null && (mc.player.swinging || mc.player.getAttackAnim(partialTick) > 1.0E-4F);
      boolean animationPlaying = AnimationUtils.isAnimationLocked() && this.isPlayingAnimation();
      return armSwinging || !animationPlaying;
   }

   private boolean isInvisibleBone(GeoBone bone) {
      return FoodDefinitionManager.isInvisible(this.itemId, bone.name());
   }

   private boolean hasInvisibleBoneParent(GeoBone bone) {
      for (GeoBone parent = bone.parent(); parent != null; parent = parent.parent()) {
         if (this.isInvisibleBone(parent)) {
            return true;
         }
      }
      return false;
   }

   private boolean isPlayingAnimation() {
      ItemStack stack = this.currentStack;
      if (stack != null && !stack.isEmpty()) {
         long instanceId = GeoItem.getId(stack);
         if (instanceId == Long.MAX_VALUE && Minecraft.getInstance().player != null) {
            instanceId = ClientAnimationManager.getOrCreateControllerId(Minecraft.getInstance().player);
         }
         for (AnimationController<?> controller : ModItems.EMPTY.getAnimatableInstanceCache().getManagerForId(instanceId).getAnimationControllers().values()) {
            if (controller != null && controller.isPlayingTriggeredAnimation() && "eat".equals(controller.getName())) {
               return true;
            }
         }
      }
      return false;
   }

   private static class Model extends GeoModel<Empty> {
      private final String id;

      public Model(String id) {
         this.id = id;
      }

      @Override
      public Identifier getModelResource(GeoRenderState renderState) {
         return Identifier.fromNamespaceAndPath("food", "item/" + this.id);
      }

      @Override
      public Identifier getTextureResource(GeoRenderState renderState) {
         return Identifier.fromNamespaceAndPath("food", "textures/item/" + this.id + ".png");
      }

      @Override
      public Identifier getAnimationResource(Empty animatable) {
         // sakura fubuki shares the flower tea animations
         return Identifier.fromNamespaceAndPath("food", "item/" + ("sakura_fubuki".equals(this.id) ? "flower_tea" : this.id));
      }
   }
}
