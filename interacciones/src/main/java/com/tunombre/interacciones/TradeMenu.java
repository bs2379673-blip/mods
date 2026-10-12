package com.tunombre.interacciones;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tradeo cara a cara: cada uno pone hasta 9 cosas en su lado y ve en vivo lo que pone el otro. Cuando los dos marcan
 * LISTO se intercambia; si alguien cambia algo, los dos 'listo' se quitan. Si alguien cierra, cada uno recupera lo suyo.
 */
public class TradeMenu extends AbstractContainerMenu {
    public static final int MINE_X = 8, THEIRS_X = 116, TOP = 26, INV_Y = 100;
    public final Container mine, theirs;
    public final String otherName;
    private final Session session;
    private final int side;
    private final DataSlot readyMine, readyOther;

    /** cliente */
    public TradeMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, null, 0, new SimpleContainer(9), new SimpleContainer(9), buf.readUtf(64));
    }

    /** servidor */
    public TradeMenu(int id, Inventory inv, Session s, int side) {
        this(id, inv, s, side, s.offer[side], s.offer[1 - side], s.name(1 - side));
    }

    private TradeMenu(int id, Inventory inv, Session s, int side, Container mine, Container theirs, String otherName) {
        super(Interacciones.TRADE.get(), id);
        this.session = s;
        this.side = side;
        this.mine = mine;
        this.theirs = theirs;
        this.otherName = otherName;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                addSlot(new Slot(mine, r * 3 + c, MINE_X + 18 + c * 18, TOP + r * 18));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                addSlot(new Slot(theirs, r * 3 + c, THEIRS_X + c * 18, TOP + r * 18) {
                    @Override public boolean mayPlace(ItemStack st) { return false; }
                    @Override public boolean mayPickup(Player p) { return false; }
                });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
        readyMine = addDataSlot(s == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return s.ready[side] ? 1 : 0; }
            @Override public void set(int v) { }
        });
        readyOther = addDataSlot(s == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return s.ready[1 - side] ? 1 : 0; }
            @Override public void set(int v) { }
        });
    }

    public boolean imReady() { return readyMine.get() != 0; }

    public boolean otherReady() { return readyOther.get() != 0; }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id == 0 && session != null) { session.toggle(side); return true; }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem(), copy = st.copy();
        if (index < 9) {
            if (!moveItemStackTo(st, 18, 54, true)) return ItemStack.EMPTY;
        } else if (index >= 18) {
            if (!moveItemStackTo(st, 0, 9, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (st.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        if (session == null) return true;
        ServerPlayer o = session.p[1 - side];
        return !session.done && p.isAlive() && o.isAlive() && !o.hasDisconnected() && p.distanceTo(o) < 8;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (session != null && p instanceof ServerPlayer sp) {
            Sessions.endTradeAnim(sp);
            if (!session.done) session.cancel();
        }
    }

    // ------------------------------------------------------------------ sesion compartida ----
    public static final class Session {
        final ServerPlayer[] p = new ServerPlayer[2];
        final SimpleContainer[] offer = {new SimpleContainer(9), new SimpleContainer(9)};
        final boolean[] ready = new boolean[2];
        boolean done;

        Session(ServerPlayer a, ServerPlayer b) {
            p[0] = a;
            p[1] = b;
            for (SimpleContainer c : offer) c.addListener(x -> { ready[0] = false; ready[1] = false; });
        }

        String name(int i) { return p[i].getGameProfile().getName(); }

        static void open(ServerPlayer a, ServerPlayer b) {
            Session s = new Session(a, b);
            Sessions.openTrade(a, s, 0, s.name(1));
            Sessions.openTrade(b, s, 1, s.name(0));
        }

        void toggle(int side) {
            if (done) return;
            ready[side] = !ready[side];
            if (ready[0] && ready[1]) complete();
        }

        private void give(ServerPlayer to, SimpleContainer from) {
            for (int i = 0; i < from.getContainerSize(); i++) {
                ItemStack st = from.removeItemNoUpdate(i);
                if (st.isEmpty()) continue;
                if (!to.getInventory().add(st)) to.drop(st, false);
            }
        }

        private void complete() {
            done = true;
            give(p[1], offer[0]);
            give(p[0], offer[1]);
            for (ServerPlayer x : p) {
                x.level().playSound(null, x.getX(), x.getY(), x.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6F, 1.2F);
                x.displayClientMessage(Component.literal("Tradeo completado"), true);
                x.closeContainer();
            }
        }

        void cancel() {
            if (done) return;
            done = true;
            give(p[0], offer[0]);
            give(p[1], offer[1]);
            for (ServerPlayer x : p) {
                x.displayClientMessage(Component.literal("Tradeo cancelado"), true);
                if (x.containerMenu instanceof TradeMenu) x.closeContainer();
            }
        }
    }
}
