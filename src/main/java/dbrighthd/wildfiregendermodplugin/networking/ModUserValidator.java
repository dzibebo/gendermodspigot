package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;

import java.io.IOException;

final class ModUserValidator {
    private ModUserValidator() {
    }

    static void validate(ModUser user) throws IOException {
        GeneralOptions general = user.configuration().generalOptions();
        PhysicsOptions physics = user.configuration().physicsOptions();
        BreastOptions breast = user.configuration().breastOptions();

        requireFinite("bust size", breast.bustSize());
        requireFinite("voice pitch", general.voicePitch());
        requireFinite("bounce multiplier", physics.buoyancy());
        requireFinite("floppiness", physics.floppiness());
        requireFinite("X offset", breast.xOffset());
        requireFinite("Y offset", breast.yOffset());
        requireFinite("Z offset", breast.zOffset());
        requireFinite("cleavage", breast.cleavage());
    }

    private static void requireFinite(String name, float value) throws IOException {
        if (!Float.isFinite(value)) throw new IOException("Non-finite " + name + ": " + value);
    }
}
