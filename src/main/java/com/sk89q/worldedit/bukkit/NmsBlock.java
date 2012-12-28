// $Id$
/*
 * This file is a part of WorldEdit.
 * Copyright (c) sk89q <http://www.sk89q.com>
 * Copyright (c) the WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldedit.bukkit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;

import org.bukkit.World;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.ByteTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.DoubleTag;
import com.sk89q.jnbt.EndTag;
import com.sk89q.jnbt.FloatTag;
import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.LongTag;
import com.sk89q.jnbt.NBTConstants;
import com.sk89q.jnbt.ShortTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.TileEntityBlock;
import com.sk89q.worldedit.data.DataException;
import com.sk89q.worldedit.foundation.Block;

/**
 * A blind handler of blocks with TileEntity data that directly access Minecraft's
 * classes through CraftBukkit.
 * </p>
 * Usage of this class may break terribly in the future, and therefore usage should
 * be trapped in a handler for {@link Throwable}.
 */
class NmsBlock extends BaseBlock implements TileEntityBlock {

    private static final Logger logger = Logger.getLogger(NmsBlock.class.getCanonicalName());
    private static Field compoundMapField;
    private static final Field nmsBlock_isTileEntityField; // The field is deobfuscated but the method isn't. No idea why.
    private NBTTagCompound nbtData = null;

    static {
        Field field;
        try {
            field = net.minecraft.block.Block.class.getDeclaredField("isTileEntity");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            logger.severe("Could not find NMS block tile entity field!");
            field = null;
        }
        nmsBlock_isTileEntityField = field;
    }

    /**
     * Create a new instance with a given type ID, data value, and previous
     * {@link TileEntityBlock}-implementing object.
     *
     * @param type block type ID
     * @param data data value
     * @param tileEntityBlock tile entity block
     */
    public NmsBlock(int type, int data, TileEntityBlock tileEntityBlock) {
        super(type, data);

        nbtData = (NBTTagCompound) fromNative(tileEntityBlock.getNbtData());
    }

    /**
     * Create a new instance with a given type ID, data value, and raw
     * {@link NBTTagCompound} copy.
     *
     * @param type block type ID
     * @param data data value
     * @param nbtData raw NBT data
     */
    public NmsBlock(int type, int data, NBTTagCompound nbtData) {
        super(type, data);

        this.nbtData = nbtData;
    }

    /**
     * Build a {@link NBTTagCompound} that has valid coordinates.
     *
     * @param pt coordinates to set
     * @return the tag compound
     */
    private NBTTagCompound getNmsData(Vector pt) {
        if (nbtData == null) {
            return null;
        }

        nbtData.setTag("x", new NBTTagInt("x", pt.getBlockX()));
        nbtData.setTag("y", new NBTTagInt("y", pt.getBlockY()));
        nbtData.setTag("z", new NBTTagInt("z", pt.getBlockZ()));

        return nbtData;
    }

    @Override
    public boolean hasNbtData() {
        return nbtData != null;
    }

    @Override
    public String getNbtId() {
        if (nbtData == null) {
            return "";
        }

        return nbtData.getString("id");
    }

    @Override
    public CompoundTag getNbtData() {
        if (nbtData == null) {
            return new CompoundTag(getNbtId(),
                    new HashMap<String, Tag>());
        }
        return (CompoundTag) toNative(nbtData);
    }

    @Override
    public void setNbtData(CompoundTag tag) throws DataException {
        if (tag == null) {
            this.nbtData = null;
        }
        this.nbtData = (NBTTagCompound) fromNative(tag);
    }

    /**
     * Build an instance from the given information.
     *
     * @param world world to get the block from
     * @param position position to get the block at
     * @param type type ID of block
     * @param data data value of block
     * @return the block, or null
     */
    public static NmsBlock get(World world, Vector position, int type, int data) {
        if (!hasTileEntity(type)) {
            return null;
        }

        TileEntity te = ((keepcalm.mods.bukkit.bukkitAPI.BukkitWorld) world).getHandle().getBlockTileEntity(
                position.getBlockX(), position.getBlockY(), position.getBlockZ());

        if (te != null) {
            NBTTagCompound tag = new NBTTagCompound();
            te.readFromNBT(tag); // Load data
            return new NmsBlock(type, data, tag);
        }

        return null;
    }

    /**
     * Set an instance or a {@link TileEntityBlock} to the given position.
     *
     * @param world world to set the block in
     * @param position position to set the block at
     * @param block the block to set
     * @return true if tile entity data was copied to the world
     */
    public static boolean set(World world, Vector position, BaseBlock block) {
        NBTTagCompound data = null;
        if (!hasTileEntity(world.getBlockTypeIdAt(position.getBlockX(), position.getBlockY(), position.getBlockZ()))) {
            return false;
        }

        if (block instanceof NmsBlock) {
            NmsBlock nmsProxyBlock = (NmsBlock) block;
            data = nmsProxyBlock.getNmsData(position);
        } else if (block instanceof TileEntityBlock) {
            NmsBlock nmsProxyBlock = new NmsBlock(
                    block.getType(), block.getData(), block);
            data = nmsProxyBlock.getNmsData(position);
        }

        if (data != null) {
            TileEntity te = ((keepcalm.mods.bukkit.bukkitAPI.BukkitWorld) world).getHandle().getBlockTileEntity(
                    position.getBlockX(), position.getBlockY(), position.getBlockZ());
            if (te != null) {
                te.readFromNBT(data); // Load data
                return true;
            }
        }

        return false;
    }

    /**
     * Tries to set a block 'safely', as in setting the block data to the location, and
     * then triggering physics only at the end.
     *
     * @param world world to set the block in
     * @param position position to set the block at
     * @param block the block to set
     * @param notifyAdjacent true to notify physics and what not
     * @return true if block id or data was changed
     */
    public static boolean setSafely(BukkitWorld world, Vector position,
            Block block, boolean notifyAdjacent) {

        int x = position.getBlockX();
        int y = position.getBlockY();
        int z = position.getBlockZ();

        keepcalm.mods.bukkit.bukkitAPI.BukkitWorld craftWorld = ((keepcalm.mods.bukkit.bukkitAPI.BukkitWorld) world.getWorld());

        boolean changed = craftWorld.getHandle().setBlockAndMetadata(
                x, y, z, block.getId(), block.getData());

        if (block instanceof BaseBlock) {
            world.copyToWorld(position, (BaseBlock) block);
        }

        if (changed) {
            if (notifyAdjacent) {
                craftWorld.getHandle().notifyBlocksOfNeighborChange(x, y, z, block.getId());
            } else {
                craftWorld.getHandle().notifyBlockChange(x, y, z, block.getId());
            }
        }

        return changed;
    }

    public static boolean hasTileEntity(int type) {
        net.minecraft.block.Block nmsBlock = getNmsBlock(type);
        if (nmsBlock == null) {
            return false;
        }

        try {
            return nmsBlock_isTileEntityField.getBoolean(nmsBlock); // Once we have the field stord, gets are fast
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    public static net.minecraft.block.Block getNmsBlock(int type) {
        if (type < 0 || type >= net.minecraft.block.Block.blocksList.length) {
            return null;
        }
        return net.minecraft.block.Block.blocksList[type];
    }

    /**
     * Converts from a non-native NMS NBT structure to a native WorldEdit NBT
     * structure.
     *
     * @param foreign non-native NMS NBT structure
     * @return native WorldEdit NBT structure
     */
    @SuppressWarnings("unchecked")
    private static Tag toNative(NBTBase foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof NBTTagCompound) {
            Map<String, Tag> values = new HashMap<String, Tag>();
            Collection<Object> foreignValues = null;

            if (compoundMapField == null) {
                try {
                    // Method name may change!
                    foreignValues = ((NBTTagCompound) foreign).getTags();
                } catch (Throwable t) {
                    try {
                        logger.warning("WorldEdit: Couldn't get NBTTagCompound.c(), " +
                        		"so we're going to try to get at the 'map' field directly from now on");

                        if (compoundMapField == null) {
                            compoundMapField = NBTTagCompound.class.getDeclaredField("map");
                            compoundMapField.setAccessible(true);
                        }
                    } catch (Throwable e) {
                        // Can't do much beyond this
                        throw new RuntimeException(e);
                    }
                }
            }

            if (compoundMapField != null) {
                try {
                    foreignValues = ((HashMap<Object, Object>) compoundMapField.get(foreign)).values();
                } catch (Throwable e) {
                    // Can't do much beyond this
                    throw new RuntimeException(e);
                }
            }

            for (Object obj : foreignValues) {
                NBTBase base = (NBTBase) obj;
                values.put(base.getName(), toNative(base));
            }
            return new CompoundTag(foreign.getName(), values);
        } else if (foreign instanceof NBTTagByte) {
            return new ByteTag(foreign.getName(), ((NBTTagByte) foreign).data);
        } else if (foreign instanceof NBTTagByteArray) {
            return new ByteArrayTag(foreign.getName(),
                    ((NBTTagByteArray) foreign).byteArray);
        } else if (foreign instanceof NBTTagDouble) {
            return new DoubleTag(foreign.getName(),
                    ((NBTTagDouble) foreign).data);
        } else if (foreign instanceof NBTTagFloat) {
            return new FloatTag(foreign.getName(), ((NBTTagFloat) foreign).data);
        } else if (foreign instanceof NBTTagInt) {
            return new IntTag(foreign.getName(), ((NBTTagInt) foreign).data);
        } else if (foreign instanceof NBTTagIntArray) {
            return new IntArrayTag(foreign.getName(),
                    ((NBTTagIntArray) foreign).intArray);
        } else if (foreign instanceof NBTTagList) {
            List<Tag> values = new ArrayList<Tag>();
            NBTTagList foreignList = (NBTTagList) foreign;
            int type = NBTConstants.TYPE_BYTE;
            for (int i = 0; i < foreignList.tagCount()(); i++) {
                NBTBase foreignTag = foreignList.tagAt(i);
                values.add(toNative(foreignTag));
                type = foreignTag.getId();
            }
            Class<? extends Tag> cls = NBTConstants.getClassFromType(type);
            return new ListTag(foreign.getName(), cls, values);
        } else if (foreign instanceof NBTTagLong) {
            return new LongTag(foreign.getName(), ((NBTTagLong) foreign).data);
        } else if (foreign instanceof NBTTagShort) {
            return new ShortTag(foreign.getName(), ((NBTTagShort) foreign).data);
        } else if (foreign instanceof NBTTagString) {
            return new StringTag(foreign.getName(),
                    ((NBTTagString) foreign).data);
        } else if (foreign instanceof NBTTagEnd) {
            return new EndTag();
        } else {
            throw new IllegalArgumentException("Don't know how to make native "
                    + foreign.getClass().getCanonicalName());
        }
    }

    /**
     * Converts a WorldEdit-native NBT structure to a NMS structure.
     *
     * @param foreign structure to convert
     * @return non-native structure
     */
    private static NBTBase fromNative(Tag foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof CompoundTag) {
            NBTTagCompound tag = new NBTTagCompound(foreign.getName());
            for (Map.Entry<String, Tag> entry : ((CompoundTag) foreign)
                    .getValue().entrySet()) {
                tag.setTag(entry.getKey(), fromNative(entry.getValue()));
            }
            return tag;
        } else if (foreign instanceof ByteTag) {
            return new NBTTagByte(foreign.getName(),
                    ((ByteTag) foreign).getValue());
        } else if (foreign instanceof ByteArrayTag) {
            return new NBTTagByteArray(foreign.getName(),
                    ((ByteArrayTag) foreign).getValue());
        } else if (foreign instanceof DoubleTag) {
            return new NBTTagDouble(foreign.getName(),
                    ((DoubleTag) foreign).getValue());
        } else if (foreign instanceof FloatTag) {
            return new NBTTagFloat(foreign.getName(),
                    ((FloatTag) foreign).getValue());
        } else if (foreign instanceof IntTag) {
            return new NBTTagInt(foreign.getName(),
                    ((IntTag) foreign).getValue());
        } else if (foreign instanceof IntArrayTag) {
            return new NBTTagIntArray(foreign.getName(),
                    ((IntArrayTag) foreign).getValue());
        } else if (foreign instanceof ListTag) {
            NBTTagList tag = new NBTTagList(foreign.getName());
            ListTag foreignList = (ListTag) foreign;
            for (Tag t : foreignList.getValue()) {
                tag.appendTag(fromNative(t));
            }
            return tag;
        } else if (foreign instanceof LongTag) {
            return new NBTTagLong(foreign.getName(),
                    ((LongTag) foreign).getValue());
        } else if (foreign instanceof ShortTag) {
            return new NBTTagShort(foreign.getName(),
                    ((ShortTag) foreign).getValue());
        } else if (foreign instanceof StringTag) {
            return new NBTTagString(foreign.getName(),
                    ((StringTag) foreign).getValue());
        } else if (foreign instanceof EndTag) {
            return new NBTTagEnd();
        } else {
            throw new IllegalArgumentException("Don't know how to make NMS "
                    + foreign.getClass().getCanonicalName());
        }
    }
}
