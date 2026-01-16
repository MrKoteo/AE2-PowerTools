package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.scanner.IssueLocation;
import com.ae2powertools.features.scanner.NetworkScanner;
import com.ae2powertools.features.scanner.ScanSessionManager;
import com.ae2powertools.features.scanner.ScannerClientState;
import com.ae2powertools.features.scanner.ScannerClientState.ChunkLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.LoopLocationClient;


/**
 * Server -> Client packet to sync network scan results.
 */
public class PacketScannerSync implements IMessage {

    private boolean hasSession;
    private boolean isComplete;
    private String statusMessage;
    private List<LoopLocationData> loopLocations;
    private List<ChunkLocationData> chunkLocations;

    /**
     * Data structure for transmitting loop locations.
     */
    private static class LoopLocationData {
        BlockPos pos;
        int dimension;
        String dimensionName;
        String blockName;
        String description;
        boolean isLoaded;

        LoopLocationData() {}

        LoopLocationData(BlockPos pos, int dimension, String dimensionName,
                String blockName, String description, boolean isLoaded) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.isLoaded = isLoaded;
        }
    }

    /**
     * Data structure for transmitting unloaded chunk locations.
     */
    private static class ChunkLocationData {
        int chunkX;
        int chunkZ;
        int dimension;
        String dimensionName;

        ChunkLocationData() {}

        ChunkLocationData(int chunkX, int chunkZ, int dimension, String dimensionName) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
        }
    }

    public PacketScannerSync() {
        this.loopLocations = new ArrayList<>();
        this.chunkLocations = new ArrayList<>();
    }

    public PacketScannerSync(ScanSessionManager.ScanSession session) {
        this.hasSession = true;
        this.loopLocations = new ArrayList<>();
        this.chunkLocations = new ArrayList<>();

        if (session != null) {
            NetworkScanner scanner = session.getScanner();
            this.isComplete = scanner.isComplete();
            this.statusMessage = scanner.getStatusMessage();

            // Add loop locations
            for (IssueLocation loc : scanner.getDetectedLoops()) {
                String blockName = loc.getBlockState().getBlock().getLocalizedName();
                loopLocations.add(new LoopLocationData(
                    loc.getPos(),
                    loc.getDimension(),
                    loc.getDimensionName(),
                    blockName,
                    loc.getDescription(),
                    loc.isLoaded()
                ));
            }

            // Add unloaded chunk locations
            String dimName = session.getDimensionName();
            int dimension = session.getDimension();
            for (BlockPos chunkPos : scanner.getUnloadedChunks()) {
                chunkLocations.add(new ChunkLocationData(
                    chunkPos.getX(),
                    chunkPos.getZ(),
                    dimension,
                    dimName
                ));
            }
        } else {
            this.isComplete = true;
            this.statusMessage = "";
        }
    }

    /**
     * Create a packet indicating no session.
     */
    public static PacketScannerSync noSession() {
        PacketScannerSync packet = new PacketScannerSync();
        packet.hasSession = false;
        packet.isComplete = true;
        packet.statusMessage = "";

        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hasSession = buf.readBoolean();
        isComplete = buf.readBoolean();
        statusMessage = ByteBufUtils.readUTF8String(buf);

        // Read loop locations
        int loopCount = buf.readInt();
        loopLocations = new ArrayList<>(loopCount);
        for (int i = 0; i < loopCount; i++) {
            LoopLocationData data = new LoopLocationData();
            data.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            data.dimension = buf.readInt();
            data.dimensionName = ByteBufUtils.readUTF8String(buf);
            data.blockName = ByteBufUtils.readUTF8String(buf);
            data.description = ByteBufUtils.readUTF8String(buf);
            data.isLoaded = buf.readBoolean();
            loopLocations.add(data);
        }

        // Read chunk locations
        int chunkCount = buf.readInt();
        chunkLocations = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            ChunkLocationData data = new ChunkLocationData();
            data.chunkX = buf.readInt();
            data.chunkZ = buf.readInt();
            data.dimension = buf.readInt();
            data.dimensionName = ByteBufUtils.readUTF8String(buf);
            chunkLocations.add(data);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(hasSession);
        buf.writeBoolean(isComplete);
        ByteBufUtils.writeUTF8String(buf, statusMessage != null ? statusMessage : "");

        // Write loop locations
        buf.writeInt(loopLocations.size());
        for (LoopLocationData data : loopLocations) {
            buf.writeInt(data.pos.getX());
            buf.writeInt(data.pos.getY());
            buf.writeInt(data.pos.getZ());
            buf.writeInt(data.dimension);
            ByteBufUtils.writeUTF8String(buf, data.dimensionName);
            ByteBufUtils.writeUTF8String(buf, data.blockName);
            ByteBufUtils.writeUTF8String(buf, data.description);
            buf.writeBoolean(data.isLoaded);
        }

        // Write chunk locations
        buf.writeInt(chunkLocations.size());
        for (ChunkLocationData data : chunkLocations) {
            buf.writeInt(data.chunkX);
            buf.writeInt(data.chunkZ);
            buf.writeInt(data.dimension);
            ByteBufUtils.writeUTF8String(buf, data.dimensionName);
        }
    }

    public static class Handler implements IMessageHandler<PacketScannerSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketScannerSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ScannerClientState.setActiveSession(message.hasSession);
                ScannerClientState.setScanComplete(message.isComplete);
                ScannerClientState.setStatusMessage(message.statusMessage);

                // Set loop locations
                List<LoopLocationClient> clientLoops = new ArrayList<>();
                for (LoopLocationData data : message.loopLocations) {
                    clientLoops.add(new LoopLocationClient(
                        data.pos,
                        data.dimension,
                        data.dimensionName,
                        data.blockName,
                        data.description,
                        data.isLoaded
                    ));
                }
                ScannerClientState.setLoopLocations(clientLoops);

                // Set chunk locations
                List<ChunkLocationClient> clientChunks = new ArrayList<>();
                for (ChunkLocationData data : message.chunkLocations) {
                    clientChunks.add(new ChunkLocationClient(
                        data.chunkX,
                        data.chunkZ,
                        data.dimension,
                        data.dimensionName
                    ));
                }
                ScannerClientState.setChunkLocations(clientChunks);
            });

            return null;
        }
    }
}
