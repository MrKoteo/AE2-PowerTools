package com.ae2powertools.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.tile.crafting.TileMolecularAssembler;

import com.ae2powertools.Tags;


/**
 * Cards Distributor - distributes cards from player inventory
 * to Molecular Assemblers (and similar machines) on the network.
 *
 * Usage:
 * - Right-click on network component: Distribute cards to all assemblers on network
 */
public class ItemCardsDistributor extends Item {

    // TODO: support retrieving cards from AE2 (via Security Station)
    // TODO: support for CrazyAE assemblers

    public ItemCardsDistributor() {
        this.setRegistryName(Tags.MODID, "cards_distributor");
        this.setTranslationKey(Tags.MODID + ".cards_distributor");
        this.setMaxStackSize(1);
        this.setCreativeTab(appeng.api.AEApi.instance().definitions().materials().cell1kPart().maybeStack(1)
            .map(s -> s.getItem().getCreativeTab()).orElse(null));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        // Return SUCCESS on client to prevent onItemRightClick from also firing
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // Try to get grid from the clicked block
        IGrid grid = getGridFromPosition(world, pos, side);

        if (grid == null) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.cards_distributor.no_network"));

            return EnumActionResult.FAIL;
        }

        // Find and distribute acceleration cards
        DistributionResult result = distributeAccelerationCards(player, grid);

        // Report results
        if (result.cardsUsed > 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.success_accelerator",
                result.cardsUsed,
                result.assemblersUpgraded
            ));
        }

        if (result.cardsNeeded > 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.still_needed",
                result.cardsNeeded,
                result.assemblersNeedingCards
            ));
        }

        if (result.cardsUsed == 0 && result.cardsNeeded == 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.all_full"
            ));
        }

        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.cards_distributor.use_on_network"));
        }

        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    /**
     * Result of distributing acceleration cards.
     */
    private static class DistributionResult {
        int cardsUsed = 0;
        int assemblersUpgraded = 0;
        int cardsNeeded = 0;
        int assemblersNeedingCards = 0;
    }

    /**
     * Distribute acceleration cards from player inventory to assemblers on the network.
     */
    private DistributionResult distributeAccelerationCards(EntityPlayer player, IGrid grid) {
        DistributionResult result = new DistributionResult();

        // Get the acceleration card definition
        IItemDefinition cardSpeedDef = AEApi.instance().definitions().materials().cardSpeed();
        ItemStack cardSpeedTemplate = cardSpeedDef.maybeStack(1).orElse(ItemStack.EMPTY);
        if (cardSpeedTemplate.isEmpty()) return result;

        // Find all assemblers that can accept more cards
        List<AssemblerInfo> assemblersToUpgrade = new ArrayList<>();

        // Get all Molecular Assemblers from the grid
        for (IGridNode node : grid.getMachines(TileMolecularAssembler.class)) {
            Object machine = node.getMachine();
            if (!(machine instanceof TileMolecularAssembler)) continue;

            TileMolecularAssembler assembler = (TileMolecularAssembler) machine;
            IItemHandler upgradeInv = assembler.getInventoryByName("upgrades");
            if (upgradeInv == null) continue;

            // Derive max cards from upgrade inventory size
            int maxCards = upgradeInv.getSlots();
            int currentCards = assembler.getInstalledUpgrades(Upgrades.SPEED);
            int slotsAvailable = maxCards - currentCards;

            if (slotsAvailable > 0) {
                assemblersToUpgrade.add(new AssemblerInfo(assembler, upgradeInv, maxCards, slotsAvailable));
            }
        }

        if (assemblersToUpgrade.isEmpty()) return result;

        // Count available cards in player inventory
        int availableCards = countAccelerationCards(player, cardSpeedTemplate);

        // Distribute cards evenly (round-robin style)
        while (availableCards > 0 && !assemblersToUpgrade.isEmpty()) {
            List<AssemblerInfo> stillNeedCards = new ArrayList<>();

            int cardsBeforeRound = availableCards;
            for (AssemblerInfo info : assemblersToUpgrade) {
                if (availableCards <= 0) break;
                if (info.slotsRemaining <= 0) continue;

                // Try to insert one card
                if (insertOneCard(player, info.upgradeInventory, cardSpeedTemplate)) {
                    availableCards--;
                    info.slotsRemaining--;
                    result.cardsUsed++;
                    info.cardsInserted++;

                    if (info.slotsRemaining > 0) stillNeedCards.add(info);
                }
            }

            // If no cards were inserted in this round, break to avoid infinite loop
            if (cardsBeforeRound == availableCards) break;

            assemblersToUpgrade = stillNeedCards;
        }

        // Count assemblers that received cards
        for (AssemblerInfo info : assemblersToUpgrade) {
            if (info.cardsInserted > 0) result.assemblersUpgraded++;
        }

        // Also count assemblers we upgraded that are no longer in the list
        result.assemblersUpgraded = (int) assemblersToUpgrade.stream()
            .filter(i -> i.cardsInserted > 0 || i.slotsRemaining < (i.maxSlots - i.originalSlots))
            .count();

        // Actually, let's recalculate properly
        result.assemblersUpgraded = 0;
        result.cardsNeeded = 0;
        result.assemblersNeedingCards = 0;

        // Rescan to get accurate counts
        for (IGridNode node : grid.getMachines(TileMolecularAssembler.class)) {
            Object machine = node.getMachine();
            if (!(machine instanceof TileMolecularAssembler)) continue;

            TileMolecularAssembler assembler = (TileMolecularAssembler) machine;
            IItemHandler upgradeInv = assembler.getInventoryByName("upgrades");
            if (upgradeInv == null) continue;

            int maxCards = upgradeInv.getSlots();
            int currentCards = assembler.getInstalledUpgrades(Upgrades.SPEED);
            int slotsNeeded = maxCards - currentCards;

            if (slotsNeeded > 0) {
                result.cardsNeeded += slotsNeeded;
                result.assemblersNeedingCards++;
            }
        }

        // Count how many assemblers we actually upgraded
        result.assemblersUpgraded = result.cardsUsed > 0 ?
            Math.min(result.cardsUsed, grid.getMachines(TileMolecularAssembler.class).size()) : 0;

        return result;
    }

    /**
     * Helper class to track assembler upgrade state.
     */
    private static class AssemblerInfo {
        final TileMolecularAssembler assembler;
        final IItemHandler upgradeInventory;
        final int maxSlots;
        final int originalSlots;
        int slotsRemaining;
        int cardsInserted = 0;

        AssemblerInfo(TileMolecularAssembler assembler, IItemHandler upgradeInv, int maxSlots, int slotsAvailable) {
            this.assembler = assembler;
            this.upgradeInventory = upgradeInv;
            this.maxSlots = maxSlots;
            this.originalSlots = slotsAvailable;
            this.slotsRemaining = slotsAvailable;
        }
    }

    /**
     * Count acceleration cards in player inventory.
     */
    private int countAccelerationCards(EntityPlayer player, ItemStack template) {
        int count = 0;

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, template)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    /**
     * Try to insert one acceleration card from player inventory into the upgrade inventory.
     * Returns true if successful.
     */
    private boolean insertOneCard(EntityPlayer player, IItemHandler upgradeInv, ItemStack template) {
        // Find a card in player inventory
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !ItemStack.areItemsEqual(stack, template)) continue;

            // Try to insert into upgrade inventory
            ItemStack toInsert = stack.copy();
            toInsert.setCount(1);

            for (int slot = 0; slot < upgradeInv.getSlots(); slot++) {
                ItemStack remainder = upgradeInv.insertItem(slot, toInsert, false);
                if (remainder.isEmpty()) {
                    // Successfully inserted
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                    }

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the IGrid from a block position.
     */
    private IGrid getGridFromPosition(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;

        // Check if it's a part host (cable bus)
        if (te instanceof IPartHost) {
            IPartHost partHost = (IPartHost) te;

            // Try the side that was clicked
            AEPartLocation aeSide = AEPartLocation.fromFacing(side);
            IPart part = partHost.getPart(aeSide);

            if (part instanceof IGridHost) {
                IGridNode node = ((IGridHost) part).getGridNode(AEPartLocation.INTERNAL);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }

            // Try the cable in the center
            IPart cable = partHost.getPart(AEPartLocation.INTERNAL);
            if (cable instanceof IGridHost) {
                IGridNode node = ((IGridHost) cable).getGridNode(AEPartLocation.INTERNAL);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }

            // Try all sides
            for (AEPartLocation loc : AEPartLocation.values()) {
                IPart p = partHost.getPart(loc);
                if (p instanceof IGridHost) {
                    IGridNode node = ((IGridHost) p).getGridNode(AEPartLocation.INTERNAL);
                    if (node != null && node.getGrid() != null) return node.getGrid();
                }
            }
        }

        // Check if the tile itself is a grid host
        if (te instanceof IGridHost) {
            IGridHost host = (IGridHost) te;

            // Try all possible node locations
            for (AEPartLocation loc : AEPartLocation.values()) {
                IGridNode node = host.getGridNode(loc);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }
        }

        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.cards_distributor.tip1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.cards_distributor.tip2"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2powertools.cards_distributor.tip3"));
    }
}
