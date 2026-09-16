package zchenx.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Optional Mod Menu integration. Mod Menu is not a dependency of this mod: this class is only
 * loaded through the {@code modmenu} entrypoint, which only exists when Mod Menu is installed.
 *
 * <p>Clicking "Configure" opens the JSON config file in the system editor (Notepad on Windows) and
 * immediately returns to Mod Menu, so the config can be edited without leaving the game.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return OpenConfigScreen::new;
    }

    private static final class OpenConfigScreen extends Screen {
        private final Screen parent;
        private boolean opened;

        private OpenConfigScreen(Screen parent) {
            super(Component.literal("Easy Crosshair & Hitbox Tint"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            if (opened) {
                return;
            }
            opened = true;
            ModConfig.openConfigFile();

            Minecraft minecraft = this.minecraft;
            if (minecraft != null) {
                // Close on the next client tick so we do not replace the screen while it is initialising.
                minecraft.execute(() -> minecraft.setScreenAndShow(this.parent));
            }
        }
    }
}
