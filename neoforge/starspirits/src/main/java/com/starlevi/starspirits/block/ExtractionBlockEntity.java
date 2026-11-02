package com.starlevi.starspirits.block;

import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.Rarity;
import com.starlevi.starspirits.SpiritData;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.SpiritEntity;
import com.starlevi.starspirits.server.Caravan;
import com.starlevi.starspirits.server.SpiritLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Estado del punto de extraccion: listo, extrayendo (animacion de ~3,5 s con haz del color de la
 * rareza) o recargando. Los clientes reciben el estado y dibujan la animacion.
 */
public class ExtractionBlockEntity extends BlockEntity {
    public static final int READY = 0, EXTRACTING = 1, RECHARGING = 2;

    public int phase = READY;
    public long start;
    public int duration = 70;
    public long rechargeUntil;
    public long rechargeStart;
    public String spirit = "";
    public int rarity;
    private UUID extractor;
    /** Espiritu forzado por un admin para la proxima extraccion (no se guarda). */
    public String forced;

    /** Del lado del cliente: hora del juego cuando llego el ultimo cambio de fase (para animar). */
    public long clientSeen = -1;

    public ExtractionBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.EXTRACTION_BE.get(), pos, state);
    }

    public Rarity rarityEnum() {
        return Rarity.values()[Math.max(0, Math.min(Rarity.values().length - 1, this.rarity))];
    }

    public void use(ServerPlayer p) {
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        long now = sl.getGameTime();
        if (this.phase == EXTRACTING) {
            p.displayClientMessage(Component.literal("✿ Ya está brotando un espíritu…"), true);
            return;
        }
        if (this.phase == RECHARGING && now < this.rechargeUntil) {
            long min = Math.max(1, (this.rechargeUntil - now + 1199) / 1200);
            p.displayClientMessage(Component.literal("✿ Este punto se está recargando (" + min + " min)"), true);
            return;
        }
        SpiritDef d = this.forced != null ? SpiritLibrary.get(this.forced) : SpiritLibrary.roll(sl, this.worldPosition, sl.random);
        this.forced = null;
        if (d == null) {
            p.displayClientMessage(Component.literal("✿ Este servidor no tiene espíritus definidos"), true);
            return;
        }
        this.phase = EXTRACTING;
        this.start = now;
        this.duration = Config.extractTicks;
        this.spirit = d.id;
        this.rarity = d.rarity.ordinal();
        this.extractor = p.getUUID();
        sync();
        sound(SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
        sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExtractionBlockEntity be) {
        long now = level.getGameTime();
        if (be.phase == EXTRACTING) {
            long t = now - be.start;
            int d = be.duration;
            if (t == d / 4 || t == d / 2) {
                be.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, t == d / 4 ? 1.0f : 1.4f);
            }
            if (t == (d * 3) / 4) {
                be.sound(SoundEvents.BELL_RESONATE, 0.7f, 1.6f);
            }
            if (t >= d) {
                be.finish((ServerLevel) level, now);
            }
        } else if (be.phase == RECHARGING && now >= be.rechargeUntil) {
            be.phase = READY;
            be.sync();
        }
    }

    private void finish(ServerLevel level, long now) {
        ServerPlayer p = this.extractor == null ? null : level.getServer().getPlayerList().getPlayer(this.extractor);
        Rarity r = rarityEnum();
        double x = this.worldPosition.getX() + 0.5, y = this.worldPosition.getY() + 1.35, z = this.worldPosition.getZ() + 0.5;
        if (p != null) {
            SpiritData.Entry e = Caravan.give(p, this.spirit);
            if (p.level() == level && p.distanceToSqr(x, y, z) < 64 * 64) {
                SpiritEntity s = ModRegistry.SPIRIT.get().create(level);
                if (s != null) {
                    s.moveTo(x, y, z, level.random.nextFloat() * 360, 0);
                    Caravan.adopt(p, e, s);
                    level.addFreshEntity(s);
                    s.setDeltaMovement((p.getX() - x) * 0.08, 0.45, (p.getZ() - z) * 0.08);
                    s.hop();
                }
            }
            SpiritDef d = SpiritLibrary.get(this.spirit);
            String name = d == null ? this.spirit : d.name;
            p.sendSystemMessage(Component.literal("✦ ¡Extrajiste a ").append(Component.literal(name).withStyle(r.chat))
                    .append(Component.literal("! (" + r.label + ")")));
            if (r == Rarity.LEGENDARIO) {
                Component all = Component.literal("✦ " + p.getGameProfile().getName() + " extrajo un espíritu ")
                        .append(Component.literal("Legendario: " + name).withStyle(r.chat));
                for (ServerPlayer o : level.getServer().getPlayerList().getPlayers()) {
                    if (o != p) {
                        o.sendSystemMessage(all);
                    }
                }
            }
        }
        // estallido final
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 30 + r.ordinal() * 15, 0.2, 0.3, 0.2, 0.12);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y, z, 40, 0.6, 0.4, 0.6, 0.05);
        if (r.ordinal() >= Rarity.EPICO.ordinal()) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 30 + 30 * (r.ordinal() - 2), 0.3, 0.4, 0.3, 0.35);
        }
        SoundEvent reveal = switch (r) {
            case COMUN -> SoundEvents.AMETHYST_CLUSTER_BREAK;
            case RARO -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case EPICO -> SoundEvents.PLAYER_LEVELUP;
            case LEGENDARIO -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
        };
        sound(reveal, 1f, r == Rarity.COMUN ? 1.5f : 1.1f);
        sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
        this.phase = RECHARGING;
        this.rechargeStart = now;
        this.rechargeUntil = now + Math.max(40, Config.rechargeMinutes * 1200L);
        this.extractor = null;
        sync();
    }

    /** Admins: deja el punto listo ya. */
    public void reset() {
        this.phase = READY;
        sync();
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, s, SoundSource.BLOCKS, vol, pitch);
        }
    }

    private void sync() {
        setChanged();
        if (this.level != null) {
            BlockState st = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, st, st, 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ExtractionBlockEntity be) {
        if (clientParticles != null) {
            clientParticles.accept(be);
        }
    }

    /** Particulas del lado del cliente (las pone el cliente). */
    public static java.util.function.Consumer<ExtractionBlockEntity> clientParticles;

    // ------------------------------------------------------------------ guardado y envio

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.saveAdditional(tag, prov);
        tag.putInt("fase", this.phase);
        tag.putLong("inicio", this.start);
        tag.putInt("duracion", this.duration);
        tag.putLong("recarga", this.rechargeUntil);
        tag.putLong("recarga_inicio", this.rechargeStart);
        tag.putString("espiritu", this.spirit);
        tag.putInt("rareza", this.rarity);
        if (this.extractor != null) {
            tag.putUUID("extractor", this.extractor);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider prov) {
        super.loadAdditional(tag, prov);
        int old = this.phase;
        this.phase = tag.getInt("fase");
        this.start = tag.getLong("inicio");
        this.duration = tag.contains("duracion") ? tag.getInt("duracion") : 70;
        this.rechargeUntil = tag.getLong("recarga");
        this.rechargeStart = tag.getLong("recarga_inicio");
        this.spirit = tag.getString("espiritu");
        this.rarity = tag.getInt("rareza");
        this.extractor = tag.hasUUID("extractor") ? tag.getUUID("extractor") : null;
        if (this.level != null && this.level.isClientSide && old != this.phase) {
            this.clientSeen = this.level.getGameTime();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider prov) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, prov);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
