package com.ae2powertools.features.scanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.ae2powertools.items.ItemNetworkHealthScanner;


/**
 * Server-side tick handler for processing network scans and syncing results.
 */
public class ScannerTickHandler {

    // Track which players need sync updates
    private static final Map<UUID, Integer> syncCounters = new HashMap<>();
    private static final int SYNC_INTERVAL = 20; // Ticks between syncs

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        // Process all active scan sessions
        ScanSessionManager.tickSessions();

        // Sync to clients periodically
        Set<UUID> toRemove = new HashSet<>();

        for (Map.Entry<UUID, ScanSessionManager.ScanSession> entry : getSessionEntries(server)) {
            UUID playerId = entry.getKey();
            ScanSessionManager.ScanSession session = entry.getValue();

            // Increment sync counter
            int counter = syncCounters.getOrDefault(playerId, 0) + 1;
            syncCounters.put(playerId, counter);

            // Sync at intervals or when complete
            boolean shouldSync = counter >= SYNC_INTERVAL || session.getScanner().isComplete();

            if (shouldSync) {
                syncCounters.put(playerId, 0);

                // Find the player and sync
                EntityPlayerMP player = findPlayer(server, playerId);
                if (player != null) {
                    ItemNetworkHealthScanner.syncToClient(player);
                } else {
                    // Player disconnected, remove session
                    toRemove.add(playerId);
                }
            }
        }

        // Clean up disconnected players
        for (UUID playerId : toRemove) {
            ScanSessionManager.endSession(playerId);
            syncCounters.remove(playerId);
        }
    }

    /**
     * Get all session entries. This is a workaround since ScanSessionManager doesn't expose iteration.
     */
    private Iterable<Map.Entry<UUID, ScanSessionManager.ScanSession>> getSessionEntries(MinecraftServer server) {
        // We need to iterate through known players with sessions
        // Since ScanSessionManager doesn't expose the map, we'll track players ourselves
        Map<UUID, ScanSessionManager.ScanSession> entries = new HashMap<>();

        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            ScanSessionManager.ScanSession session = ScanSessionManager.getSession(player);
            if (session != null) entries.put(player.getUniqueID(), session);
        }

        return entries.entrySet();
    }

    private EntityPlayerMP findPlayer(MinecraftServer server, UUID playerId) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.getUniqueID().equals(playerId)) return player;
        }

        return null;
    }
}
