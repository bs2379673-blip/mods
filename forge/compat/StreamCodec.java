package __PKG__;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Como el StreamCodec de 1.21 (lo justo que usamos). */
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            @Override
            public O decode(B buf) {
                return to.apply(self.decode(buf));
            }

            @Override
            public void encode(B buf, O value) {
                self.encode(buf, from.apply(value));
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(BiConsumer<V, B> writer, Function<B, V> reader) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return reader.apply(buf);
            }

            @Override
            public void encode(B buf, V value) {
                writer.accept(value, buf);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V value) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return value;
            }

            @Override
            public void encode(B buf, V v) {
            }
        };
    }

    interface F3<A, B, C, R> { R apply(A a, B b, C c); }
    interface F4<A, B, C, D, R> { R apply(A a, B b, C c, D d); }
    interface F5<A, B, C, D, E, R> { R apply(A a, B b, C c, D d, E e); }
    interface F6<A, B, C, D, E, F, R> { R apply(A a, B b, C c, D d, E e, F f); }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, Function<T1, C> f) {
        return new StreamCodec<>() {
            public C decode(B b) { return f.apply(c1.decode(b)); }
            public void encode(B b, C v) { c1.encode(b, g1.apply(v)); }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, java.util.function.BiFunction<T1, T2, C> f) {
        return new StreamCodec<>() {
            public C decode(B b) { T1 a = c1.decode(b); return f.apply(a, c2.decode(b)); }
            public void encode(B b, C v) { c1.encode(b, g1.apply(v)); c2.encode(b, g2.apply(v)); }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            F3<T1, T2, T3, C> f) {
        return new StreamCodec<>() {
            public C decode(B b) { T1 a = c1.decode(b); T2 x = c2.decode(b); return f.apply(a, x, c3.decode(b)); }
            public void encode(B b, C v) { c1.encode(b, g1.apply(v)); c2.encode(b, g2.apply(v)); c3.encode(b, g3.apply(v)); }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4, F4<T1, T2, T3, T4, C> f) {
        return new StreamCodec<>() {
            public C decode(B b) { T1 a = c1.decode(b); T2 x = c2.decode(b); T3 y = c3.decode(b); return f.apply(a, x, y, c4.decode(b)); }
            public void encode(B b, C v) { c1.encode(b, g1.apply(v)); c2.encode(b, g2.apply(v)); c3.encode(b, g3.apply(v)); c4.encode(b, g4.apply(v)); }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4, StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            F5<T1, T2, T3, T4, T5, C> f) {
        return new StreamCodec<>() {
            public C decode(B b) { T1 a = c1.decode(b); T2 x = c2.decode(b); T3 y = c3.decode(b); T4 z = c4.decode(b); return f.apply(a, x, y, z, c5.decode(b)); }
            public void encode(B b, C v) { c1.encode(b, g1.apply(v)); c2.encode(b, g2.apply(v)); c3.encode(b, g3.apply(v)); c4.encode(b, g4.apply(v)); c5.encode(b, g5.apply(v)); }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4, StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6, F6<T1, T2, T3, T4, T5, T6, C> f) {
        return new StreamCodec<>() {
            public C decode(B b) { T1 a = c1.decode(b); T2 x = c2.decode(b); T3 y = c3.decode(b); T4 z = c4.decode(b); T5 w = c5.decode(b); return f.apply(a, x, y, z, w, c6.decode(b)); }
            public void encode(B b, C v) { c1.encode(b, g1.apply(v)); c2.encode(b, g2.apply(v)); c3.encode(b, g3.apply(v)); c4.encode(b, g4.apply(v)); c5.encode(b, g5.apply(v)); c6.encode(b, g6.apply(v)); }
        };
    }
}
