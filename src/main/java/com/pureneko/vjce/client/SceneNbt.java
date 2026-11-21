package com.pureneko.vjce.client;

import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;

/** Read world snapshots from 1.21.1 without rewriting the project's stored payload. */
final class SceneNbt {
    private static final int LEGACY_DATA_VERSION = 3955;

    private SceneNbt() {}

    static int currentVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    static CompoundTag upgrade(DSL.TypeReference ref, CompoundTag row, String id, CompoundTag data) {
        int from = row.getIntOr("dataVersion", LEGACY_DATA_VERSION);
        int to = currentVersion();
        if (from >= to || !id.startsWith("minecraft:")) return data;

        var tagged = data.copy();
        tagged.putString("id", id);
        var fixed = DataFixers.getDataFixer().update(ref, new Dynamic<>(NbtOps.INSTANCE, tagged), from, to).getValue();
        if (fixed instanceof CompoundTag result) return result;
        return tagged;
    }
}
