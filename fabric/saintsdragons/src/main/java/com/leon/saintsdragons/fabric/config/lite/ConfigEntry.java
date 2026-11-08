package com.leon.saintsdragons.fabric.config.lite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class ConfigEntry {
    private ConfigEntry() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface BoundedDiscrete {
        long min() default 0;

        long max();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Category {
        String value();
    }

    public static final class Gui {
        private Gui() {
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.FIELD)
        public @interface Tooltip {
            int count() default 1;
        }
    }
}
