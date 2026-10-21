package com.tunombre.jefes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Para administradores: clic derecho mirando a un jefe = convertirse en el. Si ya lo sos, clic derecho = dejar de serlo. */
public class MandoItem extends Item {
    public MandoItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player pl, InteractionHand hand) {
        ItemStack st = pl.getItemInHand(hand);
        if (level.isClientSide || !(pl instanceof ServerPlayer sp)) return InteractionResultHolder.success(st);
        if (!sp.hasPermissions(2)) { sp.displayClientMessage(Component.literal("§cSolo para administradores."), true); return InteractionResultHolder.fail(st); }
        if (Possession.active(sp)) { Possession.end(sp); return InteractionResultHolder.success(st); }
        Vec3 eye = sp.getEyePosition(), end = eye.add(sp.getLookAngle().scale(80));
        Vec3 wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp)).getLocation();
        AABB box = sp.getBoundingBox().expandTowards(sp.getLookAngle().scale(80)).inflate(2);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp, eye, wall, box, e -> e instanceof JefeEntity, 80 * 80);
        if (hit != null && hit.getEntity() instanceof JefeEntity j) Possession.begin(sp, j);
        else sp.displayClientMessage(Component.literal("§eApuntale a un jefe (o usa /jefe controlar)."), true);
        return InteractionResultHolder.success(st);
    }
}
