package com.tacz.guns.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Immutable, bounded snapshot of the server's entity-data key numbering. */
public record SyncedDataMapping(List<Entry> entries) {
    public static final int MAX_KEYS = 16_384;

    public record Entry(Identifier classId, Identifier keyId, int id) {
        public Entry {
            Objects.requireNonNull(classId);
            Objects.requireNonNull(keyId);
            if (id < 0 || id >= MAX_KEYS) throw new IllegalArgumentException("Invalid synced key number: " + id);
        }
    }

    public SyncedDataMapping {
        if (entries.size() > MAX_KEYS) throw new IllegalArgumentException("Too many synced keys: " + entries.size());
        entries = List.copyOf(entries);
        var numbers = new HashSet<Integer>();
        var names = new HashSet<List<Identifier>>();
        for (Entry entry : entries) {
            if (!numbers.add(entry.id()) || !names.add(List.of(entry.classId(), entry.keyId()))) {
                throw new IllegalArgumentException("Duplicate synced data key: " + entry);
            }
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeIdentifier(entry.classId());
            buffer.writeIdentifier(entry.keyId());
            buffer.writeVarInt(entry.id());
        }
    }

    public static SyncedDataMapping decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        if (size < 0 || size > MAX_KEYS) throw new DecoderException("Invalid synced key count: " + size);
        var entries = new ArrayList<Entry>(size);
        try {
            for (int i = 0; i < size; i++) {
                entries.add(new Entry(buffer.readIdentifier(), buffer.readIdentifier(), buffer.readVarInt()));
            }
            return new SyncedDataMapping(entries);
        } catch (IllegalArgumentException invalid) {
            throw new DecoderException("Invalid synced entity mapping", invalid);
        }
    }

    /** Resolve the entire snapshot before the caller replaces its live table. */
    public <T> Map<Integer, T> resolve(Function<Entry, T> resolver, int expectedKeys) {
        if (entries.size() != expectedKeys) {
            throw new IllegalArgumentException("Synced key count mismatch: server=" + entries.size() + ", client=" + expectedKeys);
        }
        Map<Integer, T> prepared = new HashMap<>();
        for (Entry entry : entries) {
            T value = resolver.apply(entry);
            if (value == null) throw new IllegalArgumentException("Unknown synced key: " + entry.classId() + "/" + entry.keyId());
            prepared.put(entry.id(), value);
        }
        return Map.copyOf(prepared);
    }
}
