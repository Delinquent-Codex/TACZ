package com.tacz.guns.entity.sync;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.sync.core.IDataSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class ModSerializers {
    public static final IDataSerializer<ReloadState> RELOAD_STATE = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, ReloadState value) {
            buf.writeInt(value.getStateType().ordinal());
            buf.writeLong(value.getCountDown());
        }

        @Override
        public ReloadState read(RegistryFriendlyByteBuf buf) {
            ReloadState reloadState = new ReloadState();
            int ordinal = buf.readInt();
            if (ordinal < 0 || ordinal >= ReloadState.StateType.values().length) {
                throw new io.netty.handler.codec.DecoderException("Invalid reload state: " + ordinal);
            }
            reloadState.setStateType(ReloadState.StateType.values()[ordinal]);
            reloadState.setCountDown(buf.readLong());
            return reloadState;
        }

        @Override
        public Tag write(HolderLookup.Provider registries, ReloadState value) {
            CompoundTag compound = new CompoundTag();
            compound.putString("StateType", value.getStateType().toString());
            compound.putLong("CountDown", value.getCountDown());
            return compound;
        }

        @Override
        public ReloadState read(HolderLookup.Provider registries, Tag nbt) {
            CompoundTag compound = (CompoundTag) nbt;
            try {
                ReloadState.StateType stateType = ReloadState.StateType.valueOf(compound.getStringOr("StateType", ""));
                long countDown = compound.getLongOr("CountDown", 0L);
                ReloadState reloadState = new ReloadState();
                reloadState.setStateType(stateType);
                reloadState.setCountDown(countDown);
                return reloadState;
            } catch (IllegalArgumentException ignore) {
            }
            return new ReloadState();
        }
    };
}
