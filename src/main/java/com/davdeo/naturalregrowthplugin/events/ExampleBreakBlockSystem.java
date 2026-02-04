package com.davdeo.naturalregrowthplugin.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ExampleBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public ExampleBreakBlockSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int id, @NonNull ArchetypeChunk<EntityStore> archetypeChunk, @NonNull Store<EntityStore> store,
                       @NonNull CommandBuffer<EntityStore> commandBuffer, @NonNull BreakBlockEvent event) {
        var reference = archetypeChunk.getReferenceTo(id);
        Player player = store.getComponent(reference, Player.getComponentType());
        World world  = player.getWorld();

        if (event.getBlockType().getId().equals("Empty")) {
            return;
        }

        player.sendMessage(Message.raw("Testing message " + event.getBlockType().getId()));
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
