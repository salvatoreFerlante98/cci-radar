package com.cciradar.client;

import com.cciradar.network.VeinEntry;

import java.util.ArrayList;
import java.util.List;

public final class ClientVeinStore {
    private static volatile List<VeinEntry> veins = List.of();
    private static final List<Runnable> changeListeners = new ArrayList<>();

    public static void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public static void update(List<VeinEntry> newVeins) {
        veins = List.copyOf(newVeins);
        changeListeners.forEach(Runnable::run);
    }

    /** Clears the store and notifies listeners, triggering overlay cleanup in the JM plugin. */
    public static void clear() {
        update(List.of());
    }

    public static List<VeinEntry> getVeins() {
        return veins;
    }
}
