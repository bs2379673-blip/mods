package com.annis.npc.compat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Lista de oyentes al estilo Fabric; la primera vez que alguien se anota se engancha a Forge. */
public final class Event<T> {
    private final List<T> listeners = new CopyOnWriteArrayList<>();
    private final Runnable hook;
    private boolean hooked;

    public Event(Runnable hook) {
        this.hook = hook;
    }

    public synchronized void register(T listener) {
        if (!this.hooked) {
            this.hooked = true;
            this.hook.run();
        }
        this.listeners.add(listener);
    }

    public List<T> listeners() {
        return this.listeners;
    }
}
