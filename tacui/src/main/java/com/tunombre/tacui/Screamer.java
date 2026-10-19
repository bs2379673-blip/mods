package com.tunombre.tacui;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Palo screamer: un palo normal con etiqueta "TacScreamer". Cuando un operador (nivel 2) golpea a otro jugador con el,
 * el golpe no hace dano y a la victima se le muestra la imagen/GIF a pantalla completa con el sonido configurado.
 * Todo (imagen, sonido, duracion, volumen) viene del servidor. No registra items: los clientes sin tacui no notan nada.
 */
@Mod.EventBusSubscriber(modid = TacUI.ID)
public final class Screamer {
    private Screamer() {}

    static final String TAG = "TacScreamer";
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    static ItemStack make(String image, float secs, String sound, float vol, String text) {
        ItemStack st = new ItemStack(Items.STICK);
        CompoundTag t = st.getOrCreateTag();
        CompoundTag c = new CompoundTag();
        c.putString("img", image);
        c.putFloat("secs", secs);
        c.putString("sound", sound);
        c.putFloat("vol", vol);
        c.putString("text", text);
        t.put(TAG, c);
        st.setHoverName(Component.literal("§4§lPalo Screamer"));
        st.enchant(Enchantments.UNBREAKING, 1);
        t.putInt("HideFlags", 1);
        CompoundTag disp = t.getCompound("display");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§7Imagen: §f" + image + " §7(" + secs + " s)"))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§7Sonido: §f" + (sound.isEmpty() ? "ninguno" : sound) + " §7x" + vol))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§8Solo los operadores pueden usarlo"))));
        disp.put("Lore", lore);
        t.put("display", disp);
        return st;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { LAST.remove(e.getEntity().getUUID()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer atk) || !(e.getTarget() instanceof ServerPlayer victim)) return;
        ItemStack st = atk.getMainHandItem();
        if (st.getItem() != Items.STICK || st.getTag() == null || !st.getTag().contains(TAG, 10)) return;
        e.setCanceled(true);                       // sin dano ni empuje, lo use quien lo use
        if (!atk.hasPermissions(2)) return;        // solo operadores disparan el susto
        long now = System.currentTimeMillis();
        Long last = LAST.get(atk.getUUID());
        if (last != null && now - last < 1200) return;
        LAST.put(atk.getUUID(), now);
        CompoundTag c = st.getTag().getCompound(TAG);
        String r = ServerImages.push(victim, c.getString("img"), Math.max(0.3f, c.getFloat("secs")), "full",
                c.getString("sound"), Math.max(0.1f, c.getFloat("vol")), c.getString("text"));
        atk.displayClientMessage(Component.literal(r == null ? "§aScreamer enviado a " + victim.getGameProfile().getName()
                : "§cNo se pudo: " + r + " (" + victim.getGameProfile().getName() + ")"), true);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        // /tacpalo <jugadores> <imagen> [segundos] [sonido|none] [volumen] [texto]  -> da el palo a los jugadores indicados
        e.getDispatcher().register(Commands.literal("tacpalo").requires(s -> s.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("image", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ServerImages.files(), b))
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "image"), 3f, "", 1f, ""))
                                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.3f, 60f))
                                        .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "image"),
                                                FloatArgumentType.getFloat(c, "seconds"), "", 1f, ""))
                                        .then(Commands.argument("sound", ResourceLocationArgument.id())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(ServerImages.soundSuggestions(), b))
                                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "image"),
                                                        FloatArgumentType.getFloat(c, "seconds"), ServerImages.sound(c), 1f, ""))
                                                .then(Commands.argument("volume", FloatArgumentType.floatArg(0.1f, 4f))
                                                        .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "image"),
                                                                FloatArgumentType.getFloat(c, "seconds"), ServerImages.sound(c), FloatArgumentType.getFloat(c, "volume"), ""))
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "image"),
                                                                        FloatArgumentType.getFloat(c, "seconds"), ServerImages.sound(c), FloatArgumentType.getFloat(c, "volume"),
                                                                        StringArgumentType.getString(c, "text"))))))))));
    }

    private static int give(net.minecraft.commands.CommandSourceStack src, Collection<ServerPlayer> targets, String image, float secs, String sound, float vol, String text) {
        if (ServerImages.badName(image) || !ServerImages.files().contains(image)) {
            src.sendFailure(Component.literal("No existe config/tacui/images/" + image + " (usa /tacshow list)"));
            return 0;
        }
        if (sound.startsWith(TacUI.ID + ":") && !ServerImages.customSounds().contains(sound.substring(TacUI.ID.length() + 1))) {
            src.sendFailure(Component.literal("No existe config/tacui/sounds/" + sound.substring(TacUI.ID.length() + 1) + ".ogg"));
            return 0;
        }
        int n = 0;
        for (ServerPlayer p : targets) {
            ItemStack st = make(image, secs, sound, vol, text);
            if (!p.getInventory().add(st)) p.drop(st, false);
            n++;
        }
        int fn = n;
        src.sendSuccess(() -> Component.literal("Palo screamer (" + image + ") entregado a " + fn + " jugador(es)"), true);
        return n;
    }
}
