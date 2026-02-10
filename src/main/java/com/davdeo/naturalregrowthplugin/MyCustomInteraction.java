package com.davdeo.naturalregrowthplugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.map.IWeightedMap;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.FastRandom;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;
import com.hypixel.hytale.server.worldgen.container.CoverContainer;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class MyCustomInteraction extends SimpleInstantInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "my_custom_interaction";
    public static final BuilderCodec<MyCustomInteraction> CODEC = BuilderCodec.builder(
            MyCustomInteraction.class, MyCustomInteraction::new, SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(@NonNull InteractionType interactionType, @NonNull InteractionContext interactionContext, @NonNull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) {
            interactionContext.getState().state = InteractionState.Failed;
            LOGGER.atInfo().log("Command buffer is null, interaction failed");
            return;
        }

        World world = commandBuffer.getExternalData().getWorld();
        Store<EntityStore> store = commandBuffer.getExternalData().getStore();
        Ref<EntityStore> ref = interactionContext.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            interactionContext.getState().state = InteractionState.Failed;
            LOGGER.atInfo().log("Player is null, interaction failed");
            return;
        }

        ItemStack itemStack = interactionContext.getHeldItem();
        if (itemStack == null) {
            interactionContext.getState().state = InteractionState.Failed;
            LOGGER.atInfo().log("Item stack is null, interaction failed");
            return;
        }

        BlockPosition targetBlockPos = interactionContext.getTargetBlock();
        Ref<EntityStore> targetEntityRef = interactionContext.getTargetEntity();

        if (targetBlockPos == null && targetEntityRef == null) {
            interactionContext.getState().state = InteractionState.Failed;
            LOGGER.atInfo().log("Target block position is null, interaction failed");
            return;
        }

        if (targetEntityRef != null) {
            NPCEntity targetEntity = commandBuffer.getComponent(targetEntityRef, NPCEntity.getComponentType());

            player.sendMessage(Message.raw("You have used the custom item " + itemStack.getItemId() + " on " + targetEntity.getNPCTypeId()));
            interactionContext.getState().state = InteractionState.Finished;
            return;
        }

        Vector3i targetBlockPosVector = new Vector3i(targetBlockPos.x, targetBlockPos.y, targetBlockPos.z);
        BlockType targetBlock = world.getBlockType(targetBlockPosVector);

        IWorldGen worldGen = world.getChunkStore().getGenerator();

        if (!(worldGen instanceof ChunkGenerator generator)) {
            interactionContext.getState().state = InteractionState.Failed;
            LOGGER.atInfo().log("Worldgen is not a chunk generator, interaction failed");
            return;
        }

        int seed = (int)world.getWorldConfig().getSeed();
        int x = targetBlockPosVector.x;
        int z = targetBlockPosVector.z;
        int y = targetBlockPosVector.y;
        ZoneBiomeResult result = generator.getZoneBiomeResultAt(seed, x, z);

        LOGGER.atInfo().log("Targetblock: " + targetBlock.getId());
        LOGGER.atInfo().log("Targetzone: " + result.getZoneResult().getZone().name());
        Random random = new FastRandom(HashUtil.hash(seed, x, z, 5647422603192711886L));

        ArrayList<IWeightedMap<CoverContainer.CoverContainerEntry.CoverContainerEntryPart>> covers = new ArrayList<>(
                Arrays.stream(result
                    .getZoneResult()
                    .getZone()
                    .biomePatternGenerator()
                    .getBiome(seed, x, z)
                    .getCoverContainer()
                    .getEntries()
                ).map(coverContainerEntry -> {

                    // Evaluate if this is a one true others false situation
                    // if true -> generate a block with .get(random) and set directly instead of getting all entries with the workaround
                    LOGGER.atInfo().log("is matching position: " +
                        isMatchingCoverColumn(seed, coverContainerEntry, random, x, z)
                    );
                    LOGGER.atInfo().log("is matching height: " +
                            isMatchingCoverHeight(seed, coverContainerEntry, random, x, y, z)
                    );

                    try {
                        Field f = CoverContainer.CoverContainerEntry.class.getDeclaredField("entries");
                        f.setAccessible(true);
                        LOGGER.atInfo().log("CoverDensity: " + coverContainerEntry.getCoverDensity());
                        LOGGER.atInfo().log("HeightCondition: " + coverContainerEntry.getHeightCondition());
                        LOGGER.atInfo().log("MapCondition: " + coverContainerEntry.getMapCondition());
                        LOGGER.atInfo().log("ParentCondition: " + coverContainerEntry.getParentCondition());
                        return (IWeightedMap<CoverContainer.CoverContainerEntry.CoverContainerEntryPart>) f.get(coverContainerEntry);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }).toList()
        );

        LOGGER.atInfo().log("BlockIds: \n" +
                covers
                        .stream()
                        .map(part -> part.toArray())
                        .flatMap(Arrays::stream)
                        .map(b -> ""
                                        + "offset: " + b.getOffset()
                                        + ", \t fluidId: " + b.getEntry().fluidId()
                                        + ", \t rotation: " + b.getEntry().rotation()
                                        + ", " + BlockType.getAssetMap().getAsset(b.getEntry().blockId()).getId()

                        )
                        .reduce("", (a, b) -> a + ", \n" + b)
        );

        world.setBlock(targetBlockPos.x, targetBlockPos.y + 1, targetBlockPos.z,
                BlockType.getAssetMap().getAsset((covers
                    .stream()
                    .map(part -> part.toArray())
                    .flatMap(Arrays::stream)
                        .toList().getFirst().getEntry().blockId()
                )
        ).getId());



        LOGGER.atInfo().log("");
    }

    private static boolean isMatchingCoverColumn(int seed, @Nonnull CoverContainer.CoverContainerEntry coverContainerEntry, @Nonnull Random random, int x, int z) {
        return random.nextDouble() < coverContainerEntry.getCoverDensity() && coverContainerEntry.getMapCondition().eval(seed, x, z);
    }

    private static boolean isMatchingCoverHeight(int seed, @Nonnull CoverContainer.CoverContainerEntry coverContainerEntry, Random random, int x, int y, int z) {
        return coverContainerEntry.getHeightCondition().eval(seed, x, z, y, random);
    }
}
