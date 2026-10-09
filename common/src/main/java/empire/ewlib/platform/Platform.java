package empire.ewlib.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * The small set of things EWLIB needs from the mod loader. Each loader
 * module provides an implementation through {@code META-INF/services}.
 */
public interface Platform {

    Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No EWLIB platform implementation found"));

    /**
     * @return The loader's config directory, usually {@code .minecraft/config}
     */
    Path configDir();

    /**
     * @return {@code true} if this is a physical client
     */
    boolean isClient();

    /**
     * @return The name of the running loader, e.g. {@code "fabric"} or {@code "neoforge"}
     */
    String loaderName();
}
