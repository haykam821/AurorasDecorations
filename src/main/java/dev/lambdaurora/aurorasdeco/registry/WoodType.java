/*
 * Copyright (c) 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.lambdaurora.aurorasdeco.registry;

import dev.lambdaurora.aurorasdeco.mixin.AbstractBlockAccessor;
import dev.lambdaurora.aurorasdeco.util.AuroraUtil;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.Material;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a wood type.
 *
 * @author LambdAurora
 * @version 1.0.0
 * @since 1.0.0
 */
public final class WoodType {
    private static final List<WoodType> TYPES = new ArrayList<>();
    private static final List<ModificationCallbackEntry> CALLBACKS = new ArrayList<>();
    private final Map<ComponentType, Component> components = new Object2ObjectOpenHashMap<>();
    private final List<ModificationCallbackEntry> toTrigger = new ArrayList<>();
    private final Identifier id;
    private final String pathName;
    private final String langPath;

    public WoodType(Identifier id) {
        this.id = id;
        this.pathName = getPathName(this.id);
        this.langPath = this.pathName.replaceAll("/", ".");

        this.toTrigger.addAll(CALLBACKS);
    }

    public Identifier getId() {
        return this.id;
    }

    public String getPathName() {
        return this.pathName;
    }

    public String getLangPath() {
        return this.langPath;
    }

    public boolean hasLog() {
        return this.getComponent(ComponentType.LOG) != null;
    }

    public String getLogType() {
        var component = this.getComponent(ComponentType.LOG);
        if (component == null) return "none";

        return component.id().getPath().substring(this.id.getPath().length() + 1);
    }

    public @Nullable Block getLog() {
        var component = this.getComponent(ComponentType.LOG);
        if (component == null) return null;
        return component.block();
    }

    /**
     * {@return the log side texture if a log component is associated, otherwise the planks texture}
     */
    public Identifier getLogSideTexture() {
        var log = this.getComponent(ComponentType.LOG);
        if (log == null) return this.getComponent(ComponentType.PLANKS).texture();
        return log.texture();
    }

    /**
     * {@return the log top texture if a log component is associated, otherwise the planks texture}
     */
    public Identifier getLogTopTexture() {
        var log = this.getComponent(ComponentType.LOG);
        if (log == null) return this.getComponent(ComponentType.PLANKS).texture();
        return log.topTexture();
    }

    /**
     * Returns the component associated to the given component type.
     *
     * @param type the component type
     * @return the component if associated to the given component type, otherwise {@code null}
     */
    public Component getComponent(ComponentType type) {
        return this.components.get(type);
    }

    private void addComponent(ComponentType type, Component component) {
        this.components.put(type, component);

        this.onWoodTypeModified();
    }

    private void onWoodTypeModified() {
        var it = this.toTrigger.iterator();
        while (it.hasNext()) {
            var entry = it.next();

            if (AuroraUtil.contains(this.components.keySet(), entry.requiredComponents())) {
                entry.callback().accept(this);
                it.remove();
            }
        }
    }

    private void tryTriggerCallback(ModificationCallbackEntry callbackEntry) {
        if (AuroraUtil.contains(this.components.keySet(), callbackEntry.requiredComponents())) {
            callbackEntry.callback().accept(this);
            this.toTrigger.remove(callbackEntry);
        }
    }

    public static void registerWoodTypeModificationCallback(Consumer<WoodType> callback, ComponentType... requiredComponents) {
        var entry = new ModificationCallbackEntry(callback, Arrays.asList(requiredComponents));
        CALLBACKS.add(entry);

        for (var woodType : TYPES) {
            woodType.toTrigger.add(entry);
            woodType.tryTriggerCallback(entry);
        }
    }

    public static void onBlockRegister(Identifier id, Block block) {
        for (var componentType : ComponentType.types()) {
            var woodName = componentType.filter(id, block);
            if (woodName == null) continue;

            var woodId = new Identifier(id.getNamespace(), woodName);
            var woodType = TYPES.stream().filter(type -> type.getId().equals(woodId)).findFirst()
                    .orElseGet(() -> {
                        var newWoodType = new WoodType(woodId);
                        TYPES.add(newWoodType);
                        return newWoodType;
                    });
            woodType.addComponent(componentType, new Component(block));
            break;
        }
    }

    private static String getPathName(Identifier id) {
        var path = id.getPath();
        var namespace = id.getNamespace();
        if (!namespace.equals("minecraft"))
            path = namespace + '/' + path;
        return path;
    }

    public record Component(Block block) {
        public Identifier id() {
            return Registry.BLOCK.getId(this.block());
        }

        public Material material() {
            return ((AbstractBlockAccessor) this.block()).getMaterial();
        }

        public MapColor mapColor() {
            return this.block().getDefaultMapColor();
        }

        public BlockSoundGroup blockSoundGroup() {
            return this.block().getSoundGroup(this.block().getDefaultState());
        }

        public Item item() {
            return this.block().asItem();
        }

        public boolean hasItem() {
            return this.item() != Items.AIR;
        }

        public Identifier getItemId() {
            return Registry.ITEM.getId(this.item());
        }

        public Identifier texture() {
            var id = this.id();
            return new Identifier(id.getNamespace(), "block/" + id.getPath());
        }

        public Identifier topTexture() {
            if (this.block() == Blocks.MUSHROOM_STEM) return new Identifier("block/mushroom_block_inside");
            var id = this.id();
            return new Identifier(id.getNamespace(), "block/" + id.getPath() + "_top");
        }

        public @Nullable FlammableBlockRegistry.Entry getFlammableEntry() {
            return FlammableBlockRegistry.getDefaultInstance().get(this.block());
        }

        public boolean isFlammable() {
            var entry = this.getFlammableEntry();
            return entry != null && entry.getBurnChance() != 0 && entry.getSpreadChance() != 0;
        }

        @Environment(EnvType.CLIENT)
        public BlockColorProvider getBlockColorProvider() {
            return ColorProviderRegistry.BLOCK.get(this.block());
        }

        @Environment(EnvType.CLIENT)
        public ItemColorProvider getItemColorProvider() {
            return ColorProviderRegistry.ITEM.get(this.block());
        }
    }

    // This can't be good
    public enum ComponentType {
        PLANKS((id, block) -> {
            if (!id.getPath().endsWith("_planks")) return null;
            return id.getPath().substring(0, id.getPath().length() - "_planks".length());
        }),
        LOG((id, block) -> {
            var material = ((AbstractBlockAccessor) block).getMaterial();
            if (material != Material.WOOD && material != Material.NETHER_WOOD) return null;
            String logType;
            if (id.getPath().startsWith("stripped_")) return null;
            else if (id.getPath().endsWith("_log")) logType = "_log";
            else if (id.getPath().endsWith("_stem")) logType = "_stem";
            else return null;

            return id.getPath().substring(0, id.getPath().length() - logType.length());
        }),
        SLAB((id, block) -> {
            if (!id.getPath().endsWith("_slab")) return null;
            var material = ((AbstractBlockAccessor) block).getMaterial();
            if (material != Material.WOOD && material != Material.NETHER_WOOD) return null;
            return id.getPath().substring(0, id.getPath().length() - "_slab".length());
        }),
        LEAVES((id, block) -> {
            String leavesType;
            if (AuroraUtil.idEqual(id, "minecraft", "nether_wart_block"))
                return "crimson"; // Thanks Minecraft.
            else if (id.getPath().endsWith("_leaves")) leavesType = "_leaves";
            else if (id.getPath().endsWith("_wart_block")) leavesType = "_wart_block";
            else return null;

            if (id.getPath().startsWith("flowering")) return null;

            return id.getPath().substring(0, id.getPath().length() - leavesType.length());
        });

        private static final List<ComponentType> COMPONENT_TYPES = List.of(values());
        private final Filter filter;

        ComponentType(Filter filter) {
            this.filter = filter;
        }

        public @Nullable String filter(Identifier id, Block block) {
            return this.filter.filter(id, block);
        }

        public static List<ComponentType> types() {
            return COMPONENT_TYPES;
        }
    }

    public interface Filter {
        @Nullable String filter(Identifier id, Block block);
    }

    private record ModificationCallbackEntry(Consumer<WoodType> callback, List<ComponentType> requiredComponents) {
    }
}
