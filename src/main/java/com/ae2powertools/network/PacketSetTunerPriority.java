package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.items.ItemPriorityTuner;


/**
 * Packet sent from client to server to set the Priority Tuner's stored priority.
 */
public class PacketSetTunerPriority implements IMessage {

    private int handIndex;
    private int priority;

    public PacketSetTunerPriority() {
    }

    public PacketSetTunerPriority(int handIndex, int priority) {
        this.handIndex = handIndex;
        this.priority = priority;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.handIndex = buf.readInt();
        this.priority = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(handIndex);
        buf.writeInt(priority);
    }

    public static class Handler implements IMessageHandler<PacketSetTunerPriority, IMessage> {

        @Override
        public IMessage onMessage(PacketSetTunerPriority message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                EnumHand hand = message.handIndex == 0 ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
                ItemStack stack = player.getHeldItem(hand);

                if (stack.getItem() instanceof ItemPriorityTuner) {
                    ItemPriorityTuner tuner = (ItemPriorityTuner) stack.getItem();
                    tuner.setStoredPriority(stack, message.priority);
                }
            });

            return null;
        }
    }
}
