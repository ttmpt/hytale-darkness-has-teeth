package net.ttmpt;

import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DarknessMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private static final int THRESHOLD_NOTICE = 20;
    private static final int THRESHOLD_WARN = 30;
    private static final int THRESHOLD_SPAWN = 40;
    private static final double INCREASE_DARKNESS_CHANCE = 0.5;

    private final String[] creatures = new String[]{
        "Edible_Rat", "Rat", "Scarak_Louse", "Crawler_Void"
    };

    private final String[] sounds = new String[]{
        "SFX_Crawler_Void_Alerted",
        "SFX_Crawler_Void_Alerted_02",
        "SFX_Crawler_Void_Death"
    };

    private Map<Integer, PlayerDarkness> players = new HashMap<>();
    private final Random random = new Random();

    public DarknessMiningSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
                      @Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                      @Nonnull final BreakBlockEvent event) {

        Vector3i blockPos = event.getTargetBlock();
        String blockId = event.getBlockType().getId();
        if (blockId.equals("Empty")) {
            return;
        }

        // Get player
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        // Get world
        World world = player.getWorld();
        if (world == null) {
            return;
        }

        // Get player darkness object
        int playerHash = player.hashCode();
        PlayerDarkness darkness = players.get(playerHash);
        if (darkness == null) {
            darkness = new PlayerDarkness();
            players.put(playerHash, darkness);
        }

        // Get block break delta time
        long currentTick = world.getTick();
        long deltaTick = currentTick - darkness.lastBreakTick;
        darkness.lastBreakTick = currentTick;

        if (deltaTick > 2000) {
            darkness.darknessLevel = 0;
        }

        // Get player pos. player Y must be similar to block.
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return;
        }
        Vector3d playerPos = playerTransform.getPosition();
        if (Math.abs(blockPos.getY() - playerPos.getY()) > 1) {
            darkness.darknessLevel = 0;
            return;
        }

        // Check player main and offhand for light source.
        Inventory inventory = player.getInventory();
        ItemStack firstItem = inventory.getActiveHotbarItem();
        if (firstItem != null) {
            BlockType blocktype = BlockType.getAssetMap().getAsset(firstItem.getBlockKey());
            if (blocktype != null) {
                ColorLight itemLight = blocktype.getLight();
                if (itemLight != null) {
                    darkness.darknessLevel = 0;
                    return;
                }
            }
        }

        ItemStack secondItem = inventory.getUtilityItem();
        if (secondItem != null) {
            BlockType blocktype = BlockType.getAssetMap().getAsset(secondItem.getBlockKey());
            if (blocktype != null) {
                ColorLight itemLight = blocktype.getLight();
                if (itemLight != null) {
                    darkness.darknessLevel = 0;
                    return;
                }
            }
        }

        // Check if there is blockLight
        byte blockLight = getBlockLight(world, blockPos);
        if (blockLight != 0) {
            darkness.darknessLevel = 0;
            return;
        }

        // Player is mining in the dark, increase level randomly
        if (random.nextDouble() > INCREASE_DARKNESS_CHANCE) {
            return;
        }

        darkness.darknessLevel++;

        if (darkness.darknessLevel > THRESHOLD_SPAWN) {
            player.sendMessage(Message.raw("A shadowy creature has spawned from the darkness!"));
            spawnCreature(player.getWorld(), blockPos);
            darkness.darknessLevel = -10;
            playSound(world, store, playerRef, playerPos, getSound());
        } else if (darkness.darknessLevel == THRESHOLD_WARN) {
            player.sendMessage(Message.raw("You can hear sounds from the darkness itself... Quick, return to the light!"));
            playSound(world, store, playerRef, playerPos, getSound());
        } else if (darkness.darknessLevel == THRESHOLD_NOTICE) {
            player.sendMessage(Message.raw("It is pitch black, you are likely to be eaten by something..."));
        }
    }

    private static class PlayerDarkness {
        int darknessLevel = 0;
        long lastBreakTick = 0;
        PlayerDarkness() {}
    }

    @Nonnull
    private String getCreature() {
        int index = random.nextInt(creatures.length);
        return creatures[index];
    }

    /**
     * Get light level of block and sky at a block position.
     */
    private byte getBlockLight(World world, Vector3i blockPos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPos.getX(), blockPos.getZ());
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) {
            return 15;
        }

        BlockChunk blockChunk = chunk.getBlockChunk();
        if (blockChunk == null) {
            return 15;
        }

        int localX = blockPos.getX();
        int localY = blockPos.getY();
        int localZ = blockPos.getZ();

        byte skyLight = blockChunk.getSkyLight(localX, localY, localZ);
        byte blockLight = blockChunk.getBlockLightIntensity(localX, localY, localZ);

        return (byte) Math.max(skyLight, blockLight - 2);
    }

    private void spawnCreature(World world, Vector3i blockPos) {
        world.execute(() -> {
            Vector3i spawnPos = blockPos.add(0, 1, 0);
            Store<EntityStore> store = world.getEntityStore().getStore();

            Vector3d position = new Vector3d(
                spawnPos.getX() + 0.5,
                spawnPos.getY() + 0.1,
                spawnPos.getZ() + 0.5
            );
            Vector3f rotation = new Vector3f(0, 0, 0);

            com.hypixel.hytale.server.npc.NPCPlugin npcPlugin = com.hypixel.hytale.server.npc.NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(getCreature());

            if (roleIndex == Integer.MIN_VALUE) {
                return;
            }

            npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, null);
        });
    }

    private void playSound(@Nonnull final World world, @Nonnull final Store<EntityStore> store,
            @Nonnull final Ref<EntityStore> playerRef, @Nonnull final Vector3d position, @Nonnull final String sound) {
        int index = SoundEvent.getAssetMap().getIndex(sound);
        world.execute(() -> {
            SoundUtil.playSoundEvent3dToPlayer(playerRef, index, SoundCategory.Ambient, position.getX(), position.getY(), position.getZ(), 0.5F, 1.2F, store);
        });
    }

    @Nonnull
    private String getSound() {
        int index = random.nextInt(sounds.length);
        return sounds[index];
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
