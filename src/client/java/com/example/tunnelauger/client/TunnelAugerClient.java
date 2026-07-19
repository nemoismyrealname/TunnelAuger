package com.example.tunnelauger.client;

import net.fabricmc.api.ClientModInitializer;

public class TunnelAugerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TunnelAugerTooltipHandler.register();
	}
}