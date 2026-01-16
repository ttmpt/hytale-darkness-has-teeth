package net.ttmpt;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.protocol.ColorLight;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

public class DarknessMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private static final int THRESHOLD_NOTICE = 100;
    private static final int THRESHOLD_WARN = 150;
    private static final int THRESHOLD_SPAWN = 200;
    private static final double INCREASE_DARKNESS_CHANCE = 0.3;

    private final String[] creatures = new String[]{"Edible_Rat", "Rat", "Scarak_Louse"};

    private Map<Integer, PlayerDarkness> players = new HashMap<>();
    private final Random random = new Random();

    public DarknessMiningSystem() {
        super(BreakBlockEvent.class);
        DarknessHasTeeth.logToFile("[DarkMiningSystem] Initialized");
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
        DarknessHasTeeth.logToFile("[Dark] Mining " + blockId + " at " + blockPos);

        // Get player
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            DarknessHasTeeth.logToFile("[Dark] Player is null");
            return;
        }

        // Get world
        World world = player.getWorld();
        if (world == null) {
            DarknessHasTeeth.logToFile("[Dark] World is null");
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

        DarknessHasTeeth.logToFile("[Dark] elapsed ticks: " + deltaTick);
        if (deltaTick > 2000) {
            darkness.darknessLevel = 0;
        }

        // Get player pos. player Y must be similar to block.
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            DarknessHasTeeth.logToFile("[Dark] Player missing pos");
            return;
        }
        Vector3d playerPos = playerTransform.getPosition();
        if (Math.abs(blockPos.getY() - playerPos.getY()) > 1) {
            DarknessHasTeeth.logToFile("[Dark] Player wrong y level");
            darkness.darknessLevel = 0;
            return;
        }

        // Check player main and offhand for light source.
        Inventory inventory = player.getInventory();
        ItemStack firstItem = inventory.getActiveHotbarItem();
        if (firstItem != null) {
            BlockType blocktype = BlockType.getAssetMap().getAsset(firstItem.getBlockKey());
            if (blocktype != null) {
                DarknessHasTeeth.logToFile("[Dark] item has block");
                ColorLight itemLight = blocktype.getLight();
                if (itemLight != null) {
                    DarknessHasTeeth.logToFile("[Dark] item block has light");
                    darkness.darknessLevel = 0;
                    return;
                }
            }
        } else {
            DarknessHasTeeth.logToFile("[Dark] no first item");
        }

        ItemStack secondItem = inventory.getUtilityItem();
        if (secondItem != null) {
            BlockType blocktype = BlockType.getAssetMap().getAsset(secondItem.getBlockKey());
            if (blocktype != null) {
                DarknessHasTeeth.logToFile("[Dark] item has block");
                ColorLight itemLight = blocktype.getLight();
                if (itemLight != null) {
                    DarknessHasTeeth.logToFile("[Dark] item block has light");
                    darkness.darknessLevel = 0;
                    return;
                }
            }
        }

        // Check if there is blockLight
        byte blockLight = getBlockLight(world, blockPos);
        if (blockLight != 0) {
            DarknessHasTeeth.logToFile("[Dark] Has blocklight");
            darkness.darknessLevel = 0;
            return;
        }

        // Player is mining in the dark, increase level randomly
        if (random.nextDouble() > INCREASE_DARKNESS_CHANCE) {
            return;
        }

        darkness.darknessLevel++;
        DarknessHasTeeth.logToFile("[Dark] darkness level " + darkness.darknessLevel);

        if (darkness.darknessLevel > THRESHOLD_SPAWN) {
            player.sendMessage(Message.raw("A shadowy creature has spawned from the darkness!"));
            spawnCreature(player.getWorld(), blockPos);
            darkness.darknessLevel = -100;
            DarknessHasTeeth.logToFile("[Dark] spawned");
        } else if (darkness.darknessLevel == THRESHOLD_WARN) {
            player.sendMessage(Message.raw("You can hear sounds from the darkness itself... Quick, return to the light!"));
            DarknessHasTeeth.logToFile("[Dark] warned");
        } else if (darkness.darknessLevel == THRESHOLD_NOTICE) {
            player.sendMessage(Message.raw("It is pitch black, you are likely to be eaten by something..."));
            DarknessHasTeeth.logToFile("[Dark] notice");
        }
    }

    private static class PlayerDarkness {
        int darknessLevel = 0;
        long lastBreakTick = 0;
        PlayerDarkness() {}
    }

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
            DarknessHasTeeth.logToFile("[Dark] Chunk not loaded for block light");
            return 15;
        }

        BlockChunk blockChunk = chunk.getBlockChunk();
        if (blockChunk == null) {
            DarknessHasTeeth.logToFile("[Dark] Block chunk is null for block light");
            return 15;
        }

        int localX = blockPos.getX();
        int localY = blockPos.getY();
        int localZ = blockPos.getZ();

        byte skyLight = blockChunk.getSkyLight(localX, localY, localZ);
        byte blockLight = blockChunk.getBlockLightIntensity(localX, localY, localZ);

        return (byte) Math.max(skyLight, blockLight);
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
            // Vector3f rotation = Vector3f.FORWARD;
            Vector3f rotation = new Vector3f(0, 0, 0);

            com.hypixel.hytale.server.npc.NPCPlugin npcPlugin = com.hypixel.hytale.server.npc.NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(getCreature());

            if (roleIndex == Integer.MIN_VALUE) {
                DarknessHasTeeth.logToFile("[Dark] Failed to find role!");
                return;
            }

            try {
                var pair = npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, null);
                if (pair != null && pair.first() != null) {
                    DarknessHasTeeth.logToFile("[Dark] Mob spawned at: " + position);
                } else {
                    DarknessHasTeeth.logToFile("[Dark] Failed to spawn!");
                }
            } catch (Exception e) {
                DarknessHasTeeth.logToFile("[Dark] Error spawning: " + e.getMessage());
            }
        });
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
