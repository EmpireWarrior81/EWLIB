package empire.ewlib.config.ui;

import empire.ewlib.platform.ClientPlatform;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Config screens per mod id. Registered screens open from the mod list:
 * Mod Menu on Fabric, the built-in Mods screen on NeoForge.
 */
public final class ConfigScreenProviders {

    private static final Map<String, Function<Screen, ? extends Screen>> PROVIDERS = new LinkedHashMap<>();

    private ConfigScreenProviders() {}

    /**
     * Register a config screen for the given mod
     *
     * @param provider Creates the screen, given the screen to return to when it closes
     */
    public static <S extends Screen> void register(String modId, Function<Screen, S> provider) {
        if (PROVIDERS.put(modId, provider) != null) {
            throw new IllegalArgumentException("Tried to register config screen provider for mod id " + modId + " twice");
        }

        ClientPlatform.INSTANCE.onConfigScreenProviderRegistered(modId);
    }

    @SuppressWarnings("unchecked")
    public static <S extends Screen> @Nullable Function<Screen, S> get(String modId) {
        return (Function<Screen, S>) PROVIDERS.get(modId);
    }

    public static void forEach(BiConsumer<String, Function<Screen, ? extends Screen>> action) {
        PROVIDERS.forEach(action);
    }
}
