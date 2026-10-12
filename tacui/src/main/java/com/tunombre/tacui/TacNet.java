package com.tunombre.tacui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Canal de red de tacui. Tolerante: clientes o servidores sin el mod pueden conectarse igual. */
public final class TacNet {
    private TacNet() {}

    public static final String VERSION = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TacUI.ID, "main"), () -> VERSION, v -> true, v -> true);

    public static void init() {
        int id = 0;
        CH.messageBuilder(ImageData.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ImageData::encode).decoder(ImageData::decode).consumerMainThread(ImageData::handle).add();
        CH.messageBuilder(ShowImage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShowImage::encode).decoder(ShowImage::decode).consumerMainThread(ShowImage::handle).add();
        CH.messageBuilder(ClearImage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClearImage::encode).decoder(ClearImage::decode).consumerMainThread(ClearImage::handle).add();
    }

    /** Un trozo del archivo de imagen. key = nombre#crc. */
    public record ImageData(String key, int index, int total, byte[] data) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(key); b.writeVarInt(index); b.writeVarInt(total); b.writeByteArray(data); }
        public static ImageData decode(FriendlyByteBuf b) { return new ImageData(b.readUtf(), b.readVarInt(), b.readVarInt(), b.readByteArray(1 << 20)); }
        public void handle(Supplier<NetworkEvent.Context> c) { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ImageOverlay.onData(this)); }
    }

    /** Mostrar una imagen ya enviada. mode: full / center / corner. sound: id de sonido o "". */
    public record ShowImage(String key, float seconds, String mode, String sound, float volume, String text) {
        public void encode(FriendlyByteBuf b) { b.writeUtf(key); b.writeFloat(seconds); b.writeUtf(mode); b.writeUtf(sound); b.writeFloat(volume); b.writeUtf(text, 512); }
        public static ShowImage decode(FriendlyByteBuf b) { return new ShowImage(b.readUtf(), b.readFloat(), b.readUtf(), b.readUtf(), b.readFloat(), b.readUtf(512)); }
        public void handle(Supplier<NetworkEvent.Context> c) { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ImageOverlay.onShow(this)); }
    }

    public record ClearImage(byte dummy) {
        public void encode(FriendlyByteBuf b) { b.writeByte(dummy); }
        public static ClearImage decode(FriendlyByteBuf b) { return new ClearImage(b.readByte()); }
        public void handle(Supplier<NetworkEvent.Context> c) { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ImageOverlay::onClear); }
    }
}
