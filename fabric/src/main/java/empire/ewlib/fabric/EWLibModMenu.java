package empire.ewlib.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import empire.ewlib.config.ui.ConfigScreenProviders;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes every config screen registered through EWLIB to Mod Menu
 */
public class EWLibModMenu implements ModMenuApi {

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        var factories = new HashMap<String, ConfigScreenFactory<?>>();
        ConfigScreenProviders.forEach((modId, provider) -> factories.put(modId, provider::apply));
        return factories;
    }
}
