package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.ModConfiguration;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModUserValidatorTest {
    @Test
    void acceptsFiniteValuesOutsideClientUiRanges() {
        assertDoesNotThrow(() -> ModUserValidator.validate(user(3.0f, -2.0f, 8.0f, -4.0f,
                9.0f, -9.0f, 4.0f, 5.0f)));
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(Float.NaN, 1.0f, 0.2f, 0.5f,
                0.0f, 0.0f, 0.0f, 0.0f)));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f,
                Float.POSITIVE_INFINITY, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f)));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f,
                0.2f, 0.5f, 0.0f, 0.0f, 0.0f, Float.NEGATIVE_INFINITY)));
    }

    private static ModUser user(float bust, float voice, float buoyancy, float floppiness,
                                float x, float y, float z, float cleavage) {
        return new ModUser(UUID.randomUUID(), new ModConfiguration(
                new GeneralOptions(GenderIdentities.OTHER, true, voice, true),
                new PhysicsOptions(true, false, buoyancy, floppiness),
                new BreastOptions(bust, x, y, z, true, cleavage),
                null));
    }
}
