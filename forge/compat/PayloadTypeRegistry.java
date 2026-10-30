package __PKG__;

import net.minecraft.network.FriendlyByteBuf;

public final class PayloadTypeRegistry {
    private static final PayloadTypeRegistry INSTANCE = new PayloadTypeRegistry();

    public static PayloadTypeRegistry playC2S() {
        return INSTANCE;
    }

    public static PayloadTypeRegistry playS2C() {
        return INSTANCE;
    }

    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        Net.register(type, codec);
    }
}
