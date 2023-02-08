/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viabackwards.protocol.protocol1_19_3to1_19_4.packets;

import com.viaversion.viabackwards.api.entities.storage.EntityData;
import com.viaversion.viabackwards.api.rewriters.EntityRewriter;
import com.viaversion.viabackwards.protocol.protocol1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.api.minecraft.entities.Entity1_19_4Types;
import com.viaversion.viaversion.api.minecraft.entities.EntityType;
import com.viaversion.viaversion.api.minecraft.metadata.Metadata;
import com.viaversion.viaversion.api.protocol.remapper.PacketRemapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.version.Types1_19_3;
import com.viaversion.viaversion.api.type.types.version.Types1_19_4;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.ByteTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.ListTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.StringTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.Tag;
import com.viaversion.viaversion.protocols.protocol1_19_3to1_19_1.ClientboundPackets1_19_3;
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.ClientboundPackets1_19_4;

public final class EntityPackets1_19_4 extends EntityRewriter<ClientboundPackets1_19_4, Protocol1_19_3To1_19_4> {

    public EntityPackets1_19_4(final Protocol1_19_3To1_19_4 protocol) {
        super(protocol);
    }

    @Override
    public void registerPackets() {
        registerTrackerWithData1_19(ClientboundPackets1_19_4.SPAWN_ENTITY, null);
        registerRemoveEntities(ClientboundPackets1_19_4.REMOVE_ENTITIES);
        registerMetadataRewriter(ClientboundPackets1_19_4.ENTITY_METADATA, Types1_19_4.METADATA_LIST);

        protocol.registerClientbound(ClientboundPackets1_19_4.JOIN_GAME, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.INT); // Entity id
                map(Type.BOOLEAN); // Hardcore
                map(Type.UNSIGNED_BYTE); // Gamemode
                map(Type.BYTE); // Previous Gamemode
                map(Type.STRING_ARRAY); // World List
                map(Type.NBT); // Dimension registry
                map(Type.STRING); // Dimension key
                map(Type.STRING); // World
                handler(dimensionDataHandler());
                handler(biomeSizeTracker());
                handler(worldDataTrackerHandlerByKey());
                handler(wrapper -> {
                    final CompoundTag registry = wrapper.get(Type.NBT, 0);
                    registry.remove("minecraft:trim_pattern");
                    registry.remove("minecraft:trim_material");
                    registry.remove("minecraft:damage_type");

                    final CompoundTag biomeRegistry = registry.get("minecraft:worldgen/biome");
                    final ListTag biomes = biomeRegistry.get("value");
                    for (final Tag biomeTag : biomes) {
                        final CompoundTag biomeData = ((CompoundTag) biomeTag).get("element");
                        final ByteTag hasPrecipitation = biomeData.get("has_precipitation");
                        biomeData.put("precipitation", new StringTag(hasPrecipitation.asByte() == 1 ? "rain" : "none"));
                    }
                });
            }
        });

        protocol.registerClientbound(ClientboundPackets1_19_4.HIT_ANIMATION, ClientboundPackets1_19_3.ENTITY_ANIMATION, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // Entity id
                read(Type.FLOAT); // Yaw
                create(Type.UNSIGNED_BYTE, (short) 1); // Hit
            }
        });

        protocol.registerClientbound(ClientboundPackets1_19_4.RESPAWN, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.STRING); // Dimension
                map(Type.STRING); // World
                handler(worldDataTrackerHandlerByKey());
            }
        });
    }

    @Override
    public void registerRewrites() {
        filter().handler((event, meta) -> {
            int id = meta.metaType().typeId();
            if (id >= 25) { // Vector3f/quaternion types
                event.cancel();
                return;
            } else if (id >= 15) { // Optional block state - just map down to block state
                id--;
            }

            meta.setMetaType(Types1_19_3.META_TYPES.byId(id));
        });
        registerMetaTypeHandler(Types1_19_3.META_TYPES.itemType, null, null);

        filter().filterFamily(Entity1_19_4Types.DISPLAY).handler((event, meta) -> {
            // Remove a large heap of display metadata
            if (event.index() > 7) {
                event.cancel();
            }
        });
        filter().filterFamily(Entity1_19_4Types.ABSTRACT_HORSE).addIndex(18); // Owner UUID
    }

    @Override
    public void onMappingDataLoaded() {
        mapTypes();
        // TODO Use text/item/block
        final EntityData.MetaCreator metaCreator = storage -> {
            storage.add(new Metadata(0, Types1_19_4.META_TYPES.byteType, (byte) 0x20)); // Invisible
            storage.add(new Metadata(5, Types1_19_4.META_TYPES.booleanType, true)); // No gravity
            storage.add(new Metadata(15, Types1_19_4.META_TYPES.byteType, (byte) (0x01 | 0x10))); // Small marker
        };
        mapEntityTypeWithData(Entity1_19_4Types.BLOCK_DISPLAY, Entity1_19_4Types.ARMOR_STAND).spawnMetadata(metaCreator);
        mapEntityTypeWithData(Entity1_19_4Types.ITEM_DISPLAY, Entity1_19_4Types.ARMOR_STAND).spawnMetadata(metaCreator);
        mapEntityTypeWithData(Entity1_19_4Types.TEXT_DISPLAY, Entity1_19_4Types.ARMOR_STAND).spawnMetadata(metaCreator);
    }

    @Override
    public EntityType typeFromId(final int type) {
        return Entity1_19_4Types.getTypeFromId(type);
    }
}