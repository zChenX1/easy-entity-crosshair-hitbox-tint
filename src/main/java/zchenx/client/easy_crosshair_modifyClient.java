package zchenx.client;

import net.fabricmc.api.ClientModInitializer;

public class easy_crosshair_modifyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Creates config/easy_entity_crosshair_hitbox_tint.json on first launch and loads it afterwards.
        ModConfig.get();
    }
}
