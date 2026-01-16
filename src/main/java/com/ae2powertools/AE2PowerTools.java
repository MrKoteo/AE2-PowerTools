package com.ae2powertools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.features.scanner.ScannerTickHandler;


@Mod(
    modid = Tags.MODID,
    name = Tags.MODNAME,
    version = Tags.VERSION,
    dependencies = "required-after:appliedenergistics2@[rv6-stable-7,);",
    acceptedMinecraftVersions = "[1.12.2]"
)
public class AE2PowerTools {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @SidedProxy(
        clientSide = "com.ae2powertools.ClientProxy",
        serverSide = "com.ae2powertools.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.Instance(Tags.MODID)
    public static AE2PowerTools instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ItemRegistry.init();
        PowerToolsNetwork.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register server tick handler for scanner processing
        MinecraftForge.EVENT_BUS.register(new ScannerTickHandler());

        proxy.init(event);
    }
}
