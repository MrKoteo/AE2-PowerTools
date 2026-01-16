package com.ae2powertools.features.scanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.common.collect.ImmutableSetMultimap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.DimensionalCoord;
import appeng.tile.networking.TileController;

import com.ae2powertools.AE2PowerTools;


/**
 * Detects loops and unloaded chunks in AE2 networks by performing BFS from the controller and tracking paths.
 * A loop is detected when a node is reached through two different paths.
 *
 * Note: getConnections() only returns same-grid connections. Quartz fibers create separate grids
 * and won't appear here. P2P tunnels create INTERNAL connections between proxies.
 */
public class NetworkScanner {

    private static final int MAX_NODES_PER_TICK = 100;
    private static final int MAX_TOTAL_NODES = 1000000;

    private final IGrid grid;
    private final World world;
    private final ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> forcedChunks;

    // BFS state
    private final Queue<PathNode> openList = new LinkedList<>();
    private final Map<IGridNode, PathNode> visitedNodes = new HashMap<>();
    private final Set<IssueLocation> detectedLoops = new HashSet<>();
    private final Set<BlockPos> unloadedChunks = new HashSet<>();

    // Status
    private boolean isComplete = false;
    private boolean hasController = false;
    private int nodesProcessed = 0;
    private String statusMessage = "";

    /**
     * Wrapper to track the path to each node during BFS.
     */
    private static class PathNode {
        final IGridNode node;
        final PathNode parent;
        final IGridConnection connectionFromParent;
        final int depth;

        PathNode(IGridNode node, PathNode parent, IGridConnection connection, int depth) {
            this.node = node;
            this.parent = parent;
            this.connectionFromParent = connection;
            this.depth = depth;
        }
    }

    public NetworkScanner(IGrid grid, World world) {
        this.grid = grid;
        this.world = world;
        this.forcedChunks = ForgeChunkManager.getPersistentChunksFor(world);

        initialize();
    }

    /**
     * Initialize the BFS by finding controller(s) and starting from them.
     */
    private void initialize() {
        try {
            IPathingGrid pathingGrid = grid.getCache(IPathingGrid.class);

            if (pathingGrid == null) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.no_pathing_grid");
                isComplete = true;

                return;
            }

            ControllerState state = pathingGrid.getControllerState();
            hasController = (state == ControllerState.CONTROLLER_ONLINE);

            if (!hasController) {
                // Controllerless network - start from any node
                for (IGridNode node : grid.getNodes()) {
                    PathNode pathNode = new PathNode(node, null, null, 0);
                    openList.add(pathNode);
                    visitedNodes.put(node, pathNode);
                    break; // Just start from one node
                }

                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.scanning_controllerless");
            } else {
                // Start from all controller blocks
                for (IGridNode node : grid.getMachines(TileController.class)) {
                    PathNode pathNode = new PathNode(node, null, null, 0);
                    openList.add(pathNode);
                    visitedNodes.put(node, pathNode);
                }

                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.scanning_controller");
            }

            if (openList.isEmpty()) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.no_start");
                isComplete = true;
            }
        } catch (Exception e) {
            AE2PowerTools.LOGGER.error("Error initializing network scanner", e);
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.error", e.getMessage());
            isComplete = true;
        }
    }

    /**
     * Process a batch of nodes. Call this each tick to spread work over time.
     * @return true if scan is complete
     */
    public boolean processBatch() {
        if (isComplete) return true;

        int processed = 0;

        while (!openList.isEmpty() && processed < MAX_NODES_PER_TICK) {
            if (nodesProcessed >= MAX_TOTAL_NODES) {
                statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.too_large",
                    MAX_TOTAL_NODES);
                isComplete = true;

                return true;
            }

            PathNode current = openList.poll();
            processNode(current);
            processed++;
            nodesProcessed++;
        }

        if (openList.isEmpty()) {
            isComplete = true;

            if (detectedLoops.isEmpty() && unloadedChunks.isEmpty()) {
                statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.no_issues",
                    nodesProcessed);
            } else {
                statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.found",
                    detectedLoops.size(), unloadedChunks.size());
            }
        } else {
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.scanning",
                nodesProcessed);
        }

        return isComplete;
    }

    /**
     * Process a single node: check connections and detect loops.
     * A loop is detected when we find a connection that would create a cycle in the graph.
     * We track visited connections to avoid counting the same edge twice.
     */
    private void processNode(PathNode current) {
        IGridNode node = current.node;

        for (IGridConnection connection : node.getConnections()) {
            IGridNode neighbor = connection.getOtherSide(node);
            if (neighbor == null) continue;

            // Skip the connection we came from (the edge to our parent)
            if (connection == current.connectionFromParent) continue;

            PathNode existingPath = visitedNodes.get(neighbor);

            if (existingPath != null) {
                // We found a node that was already visited through a different path.
                // This is only a loop if we haven't already processed this edge.
                // Since connections are bidirectional, we need to make sure we only count once.
                // We only report the loop from the node with higher depth (later discovery).
                if (current.depth > existingPath.depth) {
                    addLoopLocation(connection, current, existingPath);
                }
            } else {
                // New node - add to open list
                PathNode newPath = new PathNode(neighbor, current, connection, current.depth + 1);
                openList.add(newPath);
                visitedNodes.put(neighbor, newPath);

                // Check if the node's chunk is loaded
                checkChunkLoaded(neighbor);
            }
        }
    }

    /**
     * Add a loop location to the detected loops set.
     * We report the location of the current node that discovered the loop-closing edge.
     */
    private void addLoopLocation(IGridConnection connection, PathNode current, PathNode existing) {
        // Report the current node's position as the loop location
        IGridNode node = current.node;
        IGridHost host = node.getMachine();
        BlockPos pos = getNodePosition(node);
        if (pos == null) return;

        int dimension = world.provider.getDimension();
        String dimName = world.provider.getDimensionType().getName();

        IBlockState blockState = Blocks.AIR.getDefaultState();
        ChunkPos chunkPos = new ChunkPos(pos);
        boolean isLoaded = world.isBlockLoaded(pos);

        if (isLoaded) blockState = world.getBlockState(pos);

        String description = getNodeDescription(host);
        IssueLocation loopLoc = new IssueLocation(pos, dimension, dimName, blockState, isLoaded, description);
        detectedLoops.add(loopLoc);
    }

    /**
     * Check if a node's chunk is force-loaded (chunkloaded) and track non-chunkloaded chunks.
     * Note: This checks for FORCED chunk loading (chunkloaders), not just loaded chunks.
     * Chunks can be loaded temporarily when players are nearby but not force-loaded.
     */
    private void checkChunkLoaded(IGridNode node) {
        BlockPos pos = getNodePosition(node);
        if (pos == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);

        // Check if the chunk is force-loaded (persistent)
        if (!forcedChunks.containsKey(chunkPos)) {
            // Store chunk coordinates, not block coordinates
            unloadedChunks.add(new BlockPos(chunkPos.x, 0, chunkPos.z));
        }
    }

    /**
     * Get the block position of a grid node.
     */
    private BlockPos getNodePosition(IGridNode node) {
        IGridHost host = node.getMachine();
        if (host instanceof TileEntity) return ((TileEntity) host).getPos();

        // For parts and other non-tile hosts, use the grid block location
        try {
            DimensionalCoord coord = node.getGridBlock().getLocation();
            if (coord != null) return coord.getPos();
        } catch (Exception e) {
            // Fall through
        }

        return null;
    }

    /**
     * Get a human-readable description of a grid node.
     */
    private String getNodeDescription(IGridHost host) {
        if (host == null) return "Unknown";

        String className = host.getClass().getSimpleName();

        // TODO: should we try to localize some common AE2 part names here?
        //       Or maybe we can get the part name from the host if it's a part?
        // Clean up common names
        if (className.startsWith("Tile")) {
            className = className.substring(4);
        } else if (className.startsWith("Part")) {
            className = className.substring(4);
        }

        return className;
    }

    // ========== Getters ==========

    public boolean isComplete() {
        return isComplete;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Set<IssueLocation> getDetectedLoops() {
        return detectedLoops;
    }

    public Set<BlockPos> getUnloadedChunks() {
        return unloadedChunks;
    }

    public int getNodesProcessed() {
        return nodesProcessed;
    }

    public boolean hasController() {
        return hasController;
    }
}
