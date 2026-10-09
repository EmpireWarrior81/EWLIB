package empire.ewlib.platform;

import java.util.ServiceLoader;

/**
 * Client-only loader hooks. Only touched from client code, so loader
 * implementations may freely reference client classes.
 */
public interface ClientPlatform {

    ClientPlatform INSTANCE = ServiceLoader.load(ClientPlatform.class, ClientPlatform.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No EWLIB client platform implementation found"));

    /**
     * Called when a config screen provider is registered for {@code modId}, so the
     * loader can show a "Config" button for that mod in its mod list
     */
    void onConfigScreenProviderRegistered(String modId);
}
