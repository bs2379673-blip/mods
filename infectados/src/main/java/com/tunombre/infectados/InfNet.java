package com.tunombre.infectados;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class InfNet {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(Infectados.MODID, "main"), () -> V, V::equals, V::equals);

    private InfNet() { }

    static void init() {
        CH.registerMessage(0, Struggle.class, (m, b) -> { }, b -> new Struggle(), Struggle::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(1, Qte.class, Qte::write, Qte::read, Qte::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(2, Prompt.class, Prompt::write, Prompt::read, Prompt::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(4, Drag.class, (m, b) -> b.writeBoolean(m.on()), b -> new Drag(b.readBoolean()), Drag::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(3, Dodge.class, (m, b) -> b.writeByte(m.key()), b -> new Dodge(b.readByte()), Dodge::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public record Struggle() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null && p.getVehicle() instanceof InfMob m) m.struggle(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** estado del quick time event: barra 0-100, activo, tipo (0 aldeano, 1 motosierra) */
    public record Qte(int grip, boolean active, int kind) {
        void write(FriendlyByteBuf b) {
            b.writeByte(grip);
            b.writeBoolean(active);
            b.writeByte(kind);
        }

        static Qte read(FriendlyByteBuf b) { return new Qte(b.readByte(), b.readBoolean(), b.readByte()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { Infectados.clientHeld = active; DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.infectados.client.QteHud.update(grip, active, kind)); });
            ctx.get().setPacketHandled(true);
        }
    }

    /** aviso de esquiva: tecla (0 izquierda, 1 derecha), ticks de margen, activo */
    public record Prompt(int key, int ticks, boolean active) {
        void write(FriendlyByteBuf b) {
            b.writeByte(key);
            b.writeByte(ticks);
            b.writeBoolean(active);
        }

        static Prompt read(FriendlyByteBuf b) { return new Prompt(b.readByte(), b.readByte(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.infectados.client.QteHud.prompt(key, ticks, active)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** el jugador pulso una tecla de esquiva (0 izquierda, 1 derecha) */
    public record Dodge(int key) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null) return;
                for (com.tunombre.infectados.mob.Ganado g : p.level().getEntitiesOfClass(com.tunombre.infectados.mob.Ganado.class, p.getBoundingBox().inflate(8))) {
                    if (g.tryDodge(p, key)) break;
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** arrastrado por el Smoker: el cliente se pone tumbado (camara baja) */
    public record Drag(boolean on) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
                if (lp != null) lp.setForcedPose(on ? net.minecraft.world.entity.Pose.SWIMMING : null);
            }));
            ctx.get().setPacketHandled(true);
        }
    }
}
