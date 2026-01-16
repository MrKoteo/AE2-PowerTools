package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Client-side state for scanner overlay and rendering.
 * Stores detected loops and unloaded chunks received from server.
 */
@SideOnly(Side.CLIENT)
public class ScannerClientState {

    /**
     * The currently active tab in the GUI.
     */
    public enum Tab {
        LOOPS,
        UNLOADED_CHUNKS
    }

    private static boolean overlayEnabled = true;
    private static boolean hasActiveSession = false;
    private static String statusMessage = "";
    private static boolean isScanComplete = false;
    private static Tab currentTab = Tab.LOOPS;

    // Loop locations synced from server
    private static final List<LoopLocationClient> loopLocations = new ArrayList<>();
    private static final Set<Integer> selectedLoopIndices = new HashSet<>();
    private static List<LoopLocationClient> sortedLoopLocations = null;

    // Unloaded chunk locations synced from server
    private static final List<ChunkLocationClient> chunkLocations = new ArrayList<>();
    private static final Set<Integer> selectedChunkIndices = new HashSet<>();
    private static List<ChunkLocationClient> sortedChunkLocations = null;

    /**
     * Client-side loop location data.
     */
    public static class LoopLocationClient {
        public final BlockPos pos;
        public final int dimension;
        public final String dimensionName;
        public final String blockName;
        public final String description;
        public final boolean isLoaded;

        public LoopLocationClient(BlockPos pos, int dimension, String dimensionName,
                String blockName, String description, boolean isLoaded) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.isLoaded = isLoaded;
        }

        public double getDistanceFrom(BlockPos from) {
            double dx = pos.getX() - from.getX();
            double dy = pos.getY() - from.getY();
            double dz = pos.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * Client-side unloaded chunk location data.
     */
    public static class ChunkLocationClient {
        public final int chunkX;
        public final int chunkZ;
        public final int dimension;
        public final String dimensionName;

        public ChunkLocationClient(int chunkX, int chunkZ, int dimension, String dimensionName) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
        }

        /**
         * Get the center block position of this chunk.
         */
        public BlockPos getCenterPos() {
            return new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        }

        public double getDistanceFrom(BlockPos from) {
            BlockPos center = getCenterPos();
            double dx = center.getX() - from.getX();
            double dz = center.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dz * dz);
        }

        public String getCoordString() {
            return String.format("[%d, %d]", chunkX, chunkZ);
        }
    }

    // ========== Tab Management ==========

    public static Tab getCurrentTab() {
        return currentTab;
    }

    public static void setCurrentTab(Tab tab) {
        currentTab = tab;
    }

    // ========== State Management ==========

    public static void setOverlayEnabled(boolean enabled) {
        overlayEnabled = enabled;
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
    }

    public static boolean hasActiveSession() {
        return hasActiveSession;
    }

    public static void setActiveSession(boolean active) {
        hasActiveSession = active;

        if (!active) clearData();
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static void setStatusMessage(String message) {
        statusMessage = message;
    }

    public static boolean isScanComplete() {
        return isScanComplete;
    }

    public static void setScanComplete(boolean complete) {
        isScanComplete = complete;
    }

    // ========== Data Management ==========

    public static void clearData() {
        loopLocations.clear();
        selectedLoopIndices.clear();
        sortedLoopLocations = null;
        chunkLocations.clear();
        selectedChunkIndices.clear();
        sortedChunkLocations = null;
        isScanComplete = false;
        currentTab = Tab.LOOPS;
    }

    // ========== Loop Location Management ==========

    public static void setLoopLocations(List<LoopLocationClient> locations) {
        loopLocations.clear();
        loopLocations.addAll(locations);
        sortedLoopLocations = null;
    }

    public static List<LoopLocationClient> getLoopLocations() {
        return loopLocations;
    }

    public static int getLoopCount() {
        return loopLocations.size();
    }

    // ========== Chunk Location Management ==========

    public static void setChunkLocations(List<ChunkLocationClient> locations) {
        chunkLocations.clear();
        chunkLocations.addAll(locations);
        sortedChunkLocations = null;
    }

    public static List<ChunkLocationClient> getChunkLocations() {
        return chunkLocations;
    }

    public static int getChunkCount() {
        return chunkLocations.size();
    }

    // ========== Loop Selection Management ==========

    public static void toggleLoopSelection(int index) {
        if (selectedLoopIndices.contains(index)) {
            selectedLoopIndices.remove(index);
        } else {
            selectedLoopIndices.add(index);
        }
    }

    public static void selectOnlyLoop(int index) {
        selectedLoopIndices.clear();
        selectedLoopIndices.add(index);
    }

    public static void selectAllLoops() {
        selectedLoopIndices.clear();
        for (int i = 0; i < loopLocations.size(); i++) selectedLoopIndices.add(i);
    }

    public static void deselectAllLoops() {
        selectedLoopIndices.clear();
    }

    public static boolean isLoopSelected(int index) {
        return selectedLoopIndices.contains(index);
    }

    public static Set<Integer> getSelectedLoopIndices() {
        return selectedLoopIndices;
    }

    public static List<LoopLocationClient> getSelectedLoopsInDimension(int dimension) {
        List<LoopLocationClient> result = new ArrayList<>();
        for (int index : selectedLoopIndices) {
            if (index >= 0 && index < loopLocations.size()) {
                LoopLocationClient loc = loopLocations.get(index);
                if (loc.dimension == dimension) result.add(loc);
            }
        }

        return result;
    }

    // ========== Chunk Selection Management ==========

    public static void toggleChunkSelection(int index) {
        if (selectedChunkIndices.contains(index)) {
            selectedChunkIndices.remove(index);
        } else {
            selectedChunkIndices.add(index);
        }
    }

    public static void selectOnlyChunk(int index) {
        selectedChunkIndices.clear();
        selectedChunkIndices.add(index);
    }

    public static void selectAllChunks() {
        selectedChunkIndices.clear();
        for (int i = 0; i < chunkLocations.size(); i++) selectedChunkIndices.add(i);
    }

    public static void deselectAllChunks() {
        selectedChunkIndices.clear();
    }

    public static boolean isChunkSelected(int index) {
        return selectedChunkIndices.contains(index);
    }

    public static Set<Integer> getSelectedChunkIndices() {
        return selectedChunkIndices;
    }

    public static List<ChunkLocationClient> getSelectedChunksInDimension(int dimension) {
        List<ChunkLocationClient> result = new ArrayList<>();
        for (int index : selectedChunkIndices) {
            if (index >= 0 && index < chunkLocations.size()) {
                ChunkLocationClient loc = chunkLocations.get(index);
                if (loc.dimension == dimension) result.add(loc);
            }
        }

        return result;
    }

    // ========== Generic Selection for Current Tab ==========

    public static void selectAll() {
        if (currentTab == Tab.LOOPS) {
            selectAllLoops();
        } else {
            selectAllChunks();
        }
    }

    public static void deselectAll() {
        if (currentTab == Tab.LOOPS) {
            deselectAllLoops();
        } else {
            deselectAllChunks();
        }
    }

    public static void toggleSelection(int index) {
        if (currentTab == Tab.LOOPS) {
            toggleLoopSelection(index);
        } else {
            toggleChunkSelection(index);
        }
    }

    public static void selectOnly(int index) {
        if (currentTab == Tab.LOOPS) {
            selectOnlyLoop(index);
        } else {
            selectOnlyChunk(index);
        }
    }

    public static boolean isSelected(int index) {
        if (currentTab == Tab.LOOPS) {
            return isLoopSelected(index);
        } else {
            return isChunkSelected(index);
        }
    }

    public static Set<Integer> getSelectedIndices() {
        return currentTab == Tab.LOOPS ? selectedLoopIndices : selectedChunkIndices;
    }

    public static int getCurrentTabItemCount() {
        return currentTab == Tab.LOOPS ? loopLocations.size() : chunkLocations.size();
    }

    public static int getCurrentTabSelectedCount() {
        return currentTab == Tab.LOOPS ? selectedLoopIndices.size() : selectedChunkIndices.size();
    }

    public static List<LoopLocationClient> getSelectedLoopLocationsInDimension(int dimension) {
        return getSelectedLoopsInDimension(dimension);
    }

    public static List<ChunkLocationClient> getSelectedChunkLocationsInDimension(int dimension) {
        return getSelectedChunksInDimension(dimension);
    }

    // ========== Sorted/Grouped Access ==========

    /**
     * Get loop locations sorted by dimension (current first), then distance.
     */
    public static List<LoopLocationClient> getSortedLoopLocations() {
        if (sortedLoopLocations == null) {
            sortedLoopLocations = new ArrayList<>(loopLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                sortedLoopLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return sortedLoopLocations;
    }

    /**
     * Get chunk locations sorted by dimension (current first), then distance.
     */
    public static List<ChunkLocationClient> getSortedChunkLocations() {
        if (sortedChunkLocations == null) {
            sortedChunkLocations = new ArrayList<>(chunkLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                sortedChunkLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return sortedChunkLocations;
    }

    /**
     * Invalidate sorted cache (call when player moves significantly).
     */
    public static void invalidateSortCache() {
        sortedLoopLocations = null;
        sortedChunkLocations = null;
    }
}
