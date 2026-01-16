package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.items.ItemNetworkHealthScanner;
import com.ae2powertools.features.scanner.ScanSessionManager;


/**
 * Client -> Server packet to cancel network scan.
 */
public class PacketScannerCancel implements IMessage {

    public PacketScannerCancel() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketScannerCancel, IMessage> {

        @Override
        public IMessage onMessage(PacketScannerCancel message, MessageContext ctx) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;

            EntityPlayerMP player = ctx.getServerHandler().player;
            server.addScheduledTask(() -> {
                ScanSessionManager.ScanSession session = ScanSessionManager.getSession(player);

                if (session != null) {
                    int nodesProcessed = session.getScanner().getNodesProcessed();
                    boolean wasComplete = session.getScanner().isComplete();

                    ScanSessionManager.endSession(player);

                    if (!wasComplete) {
                        player.sendMessage(new TextComponentTranslation(
                            "item.ae2powertools.network_health_scanner.cancelled", nodesProcessed));
                    }

                    // Sync to client that session ended
                    ItemNetworkHealthScanner.syncToClient(player);
                }
            });

            return null;
        }
    }
}
