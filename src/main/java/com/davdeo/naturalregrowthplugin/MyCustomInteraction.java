package com.davdeo.naturalregrowthplugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;
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
import java.util.*;

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

        // Currently this is a square of e.g. 21 x 21 blocks for a value of 10; x:[-10, 10] z:[-10, 10]
        int scope = 10;

        for (int ix = -scope; ix <= scope; ix++) {
            for (int iz = -scope; iz <= scope; iz++) {
                BlockType groundBlock = world.getBlockType(x + ix, y, z + iz);

                // Check if ground exists
                if (groundBlock.getId().equals("Empty")) {
                    continue;
                }

                // Check if block above ground is free for cover
                if (!world.getBlockType(x + ix, y + 1, z + iz).getId().equals("Empty")) {
                    continue;
                }

                ZoneBiomeResult result = generator.getZoneBiomeResultAt(seed, x + ix, z + iz);
                Random random = new FastRandom(HashUtil.hash(seed, x + ix, z + iz, world.getTick()));
                int randomOffsetX = random.nextInt(5) - 2;
                int randomOffsetZ = random.nextInt(5) - 2;
                Optional<CoverContainer.CoverContainerEntry.CoverContainerEntryPart> coverContainerEntryPart = getCoverPart(result, seed, x + ix + randomOffsetX, y, z + iz + randomOffsetZ, random);

                if (coverContainerEntryPart.isPresent()) {
                    BlockType coverBlock = BlockType.getAssetMap().getAsset((coverContainerEntryPart.get().getEntry().blockId()));
                    if (coverBlock == null) {
                        continue;
                    }

                    // There is a weird behavior where this would generate gravel and sand on bodies of water, like rivers. Also this has an offset of -1 so it replaces the water surface.
                    if (coverBlock.getId().contains("Soil")) {
                        LOGGER.atInfo().log("Skipping soil cover generation");
                        continue;
                    }

                    // Skip special plants to prevent heavy exploitation
                    if (
                            coverBlock.getId().contains("Health") ||
                            coverBlock.getId().contains("Mana") ||
                            coverBlock.getId().contains("Stamina")
                    ) {
                        LOGGER.atInfo().log("Skipping special plant cover generation");
                        continue;
                    }

                    if (isCoverPlaceableOnGround(groundBlock, coverBlock)) {
                        LOGGER.atInfo().log("Set " + coverBlock.getId() + " to x:" + (x + ix) + " y:" + (y + 1 + coverContainerEntryPart.get().getOffset()) + " z:" + (z + iz) + " on top of " + groundBlock.getId());
                        world.setBlock(x + ix, y + 1 + coverContainerEntryPart.get().getOffset(), z + iz, coverBlock.getId());
                    }
                }
            }
        }
    }

    private static boolean isMatchingCoverColumn(int seed, @Nonnull CoverContainer.CoverContainerEntry coverContainerEntry, @Nonnull Random random, int x, int z) {
        return random.nextDouble() < coverContainerEntry.getCoverDensity() && coverContainerEntry.getMapCondition().eval(seed, x, z);
    }

    private static boolean isMatchingCoverHeight(int seed, @Nonnull CoverContainer.CoverContainerEntry coverContainerEntry, Random random, int x, int y, int z) {
        return coverContainerEntry.getHeightCondition().eval(seed, x, z, y, random);
    }

    private static Optional<CoverContainer.CoverContainerEntry.CoverContainerEntryPart> getCoverPart(ZoneBiomeResult zoneBiomeResult, int seed, int x, int y, int z, Random random) {
        Optional<CoverContainer.CoverContainerEntry> cce = Arrays.stream(Objects.requireNonNull(zoneBiomeResult
                        .getZoneResult()
                        .getZone()
                        .biomePatternGenerator()
                        .getBiome(seed, x, z))
                .getCoverContainer()
                .getEntries()
        ).filter(coverContainerEntry ->
                isMatchingCoverColumn(seed, coverContainerEntry, random, x, z) &&
                        isMatchingCoverHeight(seed, coverContainerEntry, random, x, y, z)
        ).findAny();

        if (cce.isEmpty()) return Optional.empty();

        CoverContainer.CoverContainerEntry.CoverContainerEntryPart ccep = cce.get().get(random);

        if (ccep == null) return Optional.empty();

        LOGGER.atInfo().log("Selected cover part " + BlockType.getAssetMap().getAsset(ccep.getEntry().blockId()).getId());
        return Optional.of(ccep);
    }

    /**
     * Limitation: Only matches coverBlock on top of groundBlock.
     */
    private static boolean isCoverPlaceableOnGround(@NonNull BlockType groundBlock, @NonNull BlockType coverBlock) {
        var a_groundBFS = groundBlock.getSupporting(0).get(BlockFace.UP);
        var a_coverRBFS_null =coverBlock.getSupport(0);

        if (a_coverRBFS_null == null) {
            return false;
        }

        var a_coverRBFS = a_coverRBFS_null.get(BlockFace.DOWN);

        if (a_coverRBFS == null) {
            LOGGER.atInfo().log("Cover block does not require support");
            return true;
        }

        if (a_groundBFS == null) {
            LOGGER.atInfo().log("Ground block does not provide any support");
            return false;
        }

        for(var coverRBFS : a_coverRBFS) {
            for( var groundBFS : a_groundBFS) {
                if (matchBFSWithRBFS(groundBFS, coverRBFS, groundBlock, coverBlock)) {
                    return true;
                }
            }
        }

        LOGGER.atWarning().log("No cover ground support match found");
        LOGGER.atWarning().log("Cover supports: " + Arrays.stream(a_coverRBFS).map(RequiredBlockFaceSupport::toString).reduce("", (a, b) -> a + ", " + b));
        LOGGER.atWarning().log("Ground supports: " + Arrays.stream(a_groundBFS).map(BlockFaceSupport::toString).reduce("", (a, b) -> a + ", " + b));
        return false;
    }

    private static boolean matchBFSWithRBFS(BlockFaceSupport groundBFS, RequiredBlockFaceSupport coverRBFS, BlockType groundBlock, BlockType coverBlock) {
        // Initially this was set to Bushes, but it also occurs e.g. for fences and possibly for more blocks.
        if (!groundBFS.getFaceType().equals("Full")) {
            LOGGER.atInfo().log("Ground block does not have FacceType = Full, skipping");
            return false;
        }

        if (coverRBFS.getSupport() != RequiredBlockFaceSupport.Match.REQUIRED) {
            LOGGER.atInfo().log("Cover block does not require support");
            return true;
        }

        if (coverRBFS.getFaceType() != null && !coverRBFS.getFaceType().equals(groundBFS.getFaceType())) {
            LOGGER.atInfo().log("Ground block does not support cover block (face type mismatch) " + groundBFS.getFaceType() + " != " + coverRBFS.getFaceType());
            return false;
        }

        var coverRBFSTagId = coverRBFS.getTagId();
        if (coverRBFSTagId != null) {
            var coverRBFSTag = coverRBFSTagId.split("=");
            var groundBlockTag = groundBlock.getData().getRawTags().get("Type")[0];
            LOGGER.atInfo().log("Cover block has type tag [" + coverRBFSTag[0] + ":" + coverRBFSTag[1] + "]");
            LOGGER.atInfo().log("Ground block has type tag [Type:" + groundBlockTag + "]");

            if (coverRBFSTag[1].equals(groundBlockTag)) {
                LOGGER.atInfo().log("Cover block supports ground block");
                return true;
            }

            LOGGER.atInfo().log("Ground block does not support cover block! " + coverRBFSTag[1] + " != " + groundBlockTag);
            return false;
        }

        if (coverBlock.getId().equals("Plant_Crop_Mushroom_Cap_Brown")) {return true;}
        if (coverBlock.getId().contains("Rubble_")) {return true;}

        LOGGER.atWarning().log("Uncaught case!");
        LOGGER.atWarning().log("Ground block " + groundBlock.getId());
        LOGGER.atWarning().log("Cover block " + coverBlock.getId());

        return true;
    }

}
