package __PKG__;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public final class ByteBufCodecs {
    private ByteBufCodecs() {
    }

    static FriendlyByteBuf f(ByteBuf b) {
        return b instanceof FriendlyByteBuf fb ? fb : new FriendlyByteBuf(b);
    }

    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.ofMember((v, b) -> f(b).writeVarInt(v), b -> f(b).readVarInt());
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.ofMember((v, b) -> b.writeInt(v), ByteBuf::readInt);
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.ofMember((v, b) -> b.writeBoolean(v), ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.ofMember((v, b) -> b.writeFloat(v), ByteBuf::readFloat);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.ofMember((v, b) -> b.writeDouble(v), ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.ofMember((v, b) -> f(b).writeVarLong(v), b -> f(b).readVarLong());
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    public static final StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = StreamCodec.ofMember((v, b) -> f(b).writeNbt(v), b -> f(b).readNbt());

    public static StreamCodec<ByteBuf, String> stringUtf8(int max) {
        return StreamCodec.ofMember((v, b) -> f(b).writeUtf(v, max), b -> f(b).readUtf(max));
    }
}
