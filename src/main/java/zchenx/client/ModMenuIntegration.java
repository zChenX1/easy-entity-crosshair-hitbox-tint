package zchenx.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Optional Mod Menu integration. Mod Menu is not a dependency: this class is only loaded through the
 * {@code modmenu} entrypoint, which only exists while Mod Menu is installed.
 *
 * <p>Clicking "Configure" opens {@link ConfigScreen}, which edits the TOML config with vanilla
 * widgets only (no config library required).
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
