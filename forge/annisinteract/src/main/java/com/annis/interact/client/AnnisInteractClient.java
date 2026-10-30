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
import com.mojang.blaze3d.platform.InputConstants;
import com.annis.npc.compat.ClientModInitializer;
import com.annis.npc.compat.ClientTickEvents;
import com.annis.npc.compat.KeyBindingHelper;
import com.annis.npc.compat.ClientPlayConnectionEvents;
import com.annis.npc.compat.ClientPlayNetworking;
import com.annis.npc.compat.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AnnisInteractClient implements ClientModInitializer {
    public static KeyMapping WHEEL;
    public static KeyMapping ACCEPT;
    public static KeyMapping DECLINE;

    @Override
    public void onInitializeClient() {
        RigBridge.registerAll();
        com.annis.interact.client.anim.PairAnims.handLink = new HandLinkImpl();
        com.annis.interact.client.anim.PairAnims.touchLink = new TouchLinkImpl();
        CarryPose.clientAnim = AnnisInteractClient::animOf;
        CarryPose.clientAnchored = e -> ClientState.ANCHORS.containsKey(e.getId());

        WHEEL = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.annisinteract.wheel",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.annisinteract"));
        ACCEPT = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.annisinteract.accept",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.annisinteract"));
        DECLINE = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.annisinteract.decline",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.annisinteract"));

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
        ClientPlayNetworking.registerGlobalReceiver(com.annis.interact.net.CustomInteractionsPayload.ID,
                (p, ctx) -> com.annis.interact.CustomInteractions.apply(p.json()));
        ClientPlayNetworking.registerGlobalReceiver(com.annis.interact.net.TouchSyncPayload.ID, (p, ctx) -> onTouch(p));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> ClientState.reset());

        HudRenderCallback.EVENT.register(InteractHud::render);
        TestDriver.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (WHEEL.consumeClick()) {
                if (client.screen == null) {
                    openWheel(client);
                }
            }
            boolean invite = !ClientState.inviteFrom.isEmpty() && System.currentTimeMillis() < ClientState.inviteUntil;
            while (ACCEPT.consumeClick()) {
                if (invite) {
                    ClientPlayNetworking.send(new AnswerPayload(true));
                    ClientState.inviteFrom = "";
                }
            }
            while (DECLINE.consumeClick()) {
                if (invite) {
                    ClientPlayNetworking.send(new AnswerPayload(false));
                    ClientState.inviteFrom = "";
                }
            }
        });
    }

    /** Modo mimos: alguien empieza, toca, suelta o termina. */
    static void onTouch(com.annis.interact.net.TouchSyncPayload p) {
        Minecraft mc = Minecraft.getInstance();
        boolean mine = mc.player != null && p.toucher() == mc.player.getId();
        if (p.receiver() < 0) {
            ClientState.TOUCHES.remove(p.toucher());
            return;
        }
        ClientState.TouchState s = ClientState.TOUCHES.get(p.toucher());
        boolean fresh = s == null;
        boolean startMsg = p.zone() == com.annis.interact.Touch.NONE && !p.active();
        if (mine && fresh && !startMsg) {
            // eco tardio despues de cerrar la pantalla: no reabrir
            return;
        }
        if (fresh) {
            s = new ClientState.TouchState();
            ClientState.TOUCHES.put(p.toucher(), s);
        }
        s.toucher = p.toucher();
        s.receiver = p.receiver();
        if (mine && !fresh) {
            // lo que hago yo ya se ve al instante; el eco del servidor llega tarde
            return;
        }
        s.zone = p.zone();
        s.active = p.active();
        s.target[0] = p.x();
        s.target[1] = p.y();
        s.target[2] = p.z();
        s.start[0] = p.sx();
        s.start[1] = p.sy();
        s.start[2] = p.sz();
        if (mine && fresh) {
            TouchScreen.openFor(p.receiver());
        }
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
        if (!(e instanceof LivingEntity l) || !l.isAlive() || e == Minecraft.getInstance().player) {
            return false;
        }
        if (e instanceof Player || e instanceof AnnisNpcEntity || ClientLinks.hasLook(e)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return "mca".equals(id.getNamespace()) && id.getPath().endsWith("_villager") && !id.getPath().startsWith("zombie");
    }

    static LivingEntity findTarget(Minecraft client) {
        if (client.crosshairPickEntity != null && targetable(client.crosshairPickEntity)
                && client.player.distanceToSqr(client.crosshairPickEntity) < 6 * 6) {
            return (LivingEntity) client.crosshairPickEntity;
        }
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getViewVector(1.0f);
        LivingEntity best = null;
        double bestScore = -1;
        for (Entity e : client.level.entitiesForRendering()) {
            if (!targetable(e)) {
                continue;
            }
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d > 4.5) {
                continue;
            }
            double dot = to.normalize().dot(look);
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

    static void openWheel(Minecraft client) {
        LivingEntity target = findTarget(client);
        if (target == null) {
            client.player.displayClientMessage(Component.literal("Mira a alguien cerca para interactuar"), true);
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
