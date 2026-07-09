package net.phoenixvine.domains.client;

import net.minecraftforge.eventbus.api.IEventBus;

public class PhoenixDomainsClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(DomainKeybinds.class);
    }
}
