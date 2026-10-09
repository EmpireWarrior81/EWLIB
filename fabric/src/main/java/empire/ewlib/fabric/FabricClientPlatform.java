package empire.ewlib.fabric;

import empire.ewlib.platform.ClientPlatform;

public class FabricClientPlatform implements ClientPlatform {

    @Override
    public void onConfigScreenProviderRegistered(String modId) {
        // Mod Menu asks EWLibModMenu for all registered screens, nothing to do here
    }
}
