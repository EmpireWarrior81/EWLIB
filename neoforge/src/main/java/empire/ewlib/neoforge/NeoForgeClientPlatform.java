package empire.ewlib.neoforge;

import empire.ewlib.config.ui.ConfigScreenProviders;
import empire.ewlib.platform.ClientPlatform;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Function;

public class NeoForgeClientPlatform implements ClientPlatform {

    @Override
    public void onConfigScreenProviderRegistered(String modId) {
        // gives the mod a "Config" button in NeoForge's Mods screen
        ModList.get().getModContainerById(modId).ifPresent(container -> container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, parent) -> {
                    Function<Screen, Screen> provider = ConfigScreenProviders.get(modId);
                    return provider == null ? parent : provider.apply(parent);
                }
        ));
    }
}
