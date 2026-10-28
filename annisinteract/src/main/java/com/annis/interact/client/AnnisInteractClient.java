package com.annis.interact.client;

import com.annis.interact.CarryPose;
import com.annis.interact.Interaction;
import com.annis.interact.net.AnchorPayload;
import com.annis.interact.net.AnswerPayload;
import com.annis.interact.net.DownedPayload;
import com.annis.interact.net.InvitePayload;
import com.annis.interact.net.SessionPayload;
import com.annis.npc.client.ClientLinks;
import com.annis.npc.entity.AnnisNpcEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AnnisInteractClient implements ClientModInitializer {
    public static KeyBinding WHEEL;
    public static KeyBinding ACCEPT;
    public static KeyBinding DECLINE;

    @Override
    public void onInitializeClient() {
        RigBridge.registerAll();
        com.annis.interact.client.anim.PairAnims.handLink = new HandLinkImpl();
        CarryPose.clientAnim = AnnisInteractClient::animOf;

        WHEEL = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.annisinteract.wheel",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.annisinteract"));
        ACCEPT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.annisinteract.accept",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.annisinteract"));
        DECLINE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.annisinteract.decline",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.annisinteract"));

        ClientPlayNetworking.registerGlobalReceiver(InvitePayload.ID, (p, ctx) -> {
            ClientState.inviteFrom = p.from();
            ClientState.inviteType = p.interaction();
            ClientState.inviteUntil = System.currentTimeMillis() + p.seconds() * 1000L;
        });
        ClientPlayNetworking.registerGlobalReceiver(AnchorPayload.ID, (p, ctx) -> {
            if (p.on()) {
                ClientState.ANCHORS.put(p.entityId(), p.yaw());
            } else {
                ClientState.ANCHORS.remove(p.entityId());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(DownedPayload.ID, (p, ctx) -> {
            ClientState.downed = p.on();
            ClientState.downedSeconds = p.seconds();
        });
        ClientPlayNetworking.registerGlobalReceiver(SessionPayload.ID, (p, ctx) -> ClientState.session = p.interaction());
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> ClientState.reset());

        HudRenderCallback.EVENT.register(InteractHud::render);
        TestDriver.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (WHEEL.wasPressed()) {
                if (client.currentScreen == null) {
                    openWheel(client);
                }
            }
            boolean invite = !ClientState.inviteFrom.isEmpty() && System.currentTimeMillis() < ClientState.inviteUntil;
            while (ACCEPT.wasPressed()) {
                if (invite) {
                    ClientPlayNetworking.send(new AnswerPayload(true));
                    ClientState.inviteFrom = "";
                }
            }
            while (DECLINE.wasPressed()) {
                if (invite) {
                    ClientPlayNetworking.send(new AnswerPayload(false));
                    ClientState.inviteFrom = "";
                }
            }
        });
    }

    /** Animacion que este cliente ve en una entidad (para colocar al que va en brazos). */
    static String animOf(Entity e) {
        if (e instanceof AnnisNpcEntity npc) {
            return npc.getEmote();
        }
        ClientLinks.Anim a = ClientLinks.anim(e);
        return a == null ? "" : a.name();
    }

    static boolean isDowned(Entity e) {
        String a = animOf(e);
        return "caido".equals(a) || "caido_suelo".equals(a);
    }

    /** A quien se puede invitar: jugadores, NPC Annis, aldeanos de MCA y entidades con aspecto Annis. */
    static boolean targetable(Entity e) {
        if (!(e instanceof LivingEntity l) || !l.isAlive() || e == MinecraftClient.getInstance().player) {
            return false;
        }
        if (e instanceof PlayerEntity || e instanceof AnnisNpcEntity || ClientLinks.hasLook(e)) {
            return true;
        }
        Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
        return "mca".equals(id.getNamespace()) && id.getPath().endsWith("_villager") && !id.getPath().startsWith("zombie");
    }

    static LivingEntity findTarget(MinecraftClient client) {
        if (client.targetedEntity != null && targetable(client.targetedEntity)
                && client.player.squaredDistanceTo(client.targetedEntity) < 6 * 6) {
            return (LivingEntity) client.targetedEntity;
        }
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f);
        LivingEntity best = null;
        double bestScore = -1;
        for (Entity e : client.world.getEntities()) {
            if (!targetable(e)) {
                continue;
            }
            Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d > 4.5) {
                continue;
            }
            double dot = to.normalize().dotProduct(look);
            if (dot < 0.55) {
                continue;
            }
            double score = dot - d * 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    static void openWheel(MinecraftClient client) {
        LivingEntity target = findTarget(client);
        if (target == null) {
            client.player.sendMessage(Text.literal("Mira a alguien cerca para interactuar"), true);
            return;
        }
        List<Interaction> options = new ArrayList<>();
        if (isDowned(target)) {
            options.add(Interaction.HELP);
        } else if (!ClientState.downed) {
            for (Interaction i : Interaction.all()) {
                if (i.kind() != Interaction.Kind.REVIVE) {
                    options.add(i);
                }
            }
        }
        if (options.isEmpty()) {
            return;
        }
        client.setScreen(new WheelScreen(target, options));
    }
}
