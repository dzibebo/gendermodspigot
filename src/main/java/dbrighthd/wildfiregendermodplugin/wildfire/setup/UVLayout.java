package dbrighthd.wildfiregendermodplugin.wildfire.setup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class UVLayout {
    private final Map<UVDirection, UVQuad> quads;

    public UVLayout(Map<UVDirection, UVQuad> quads) {
        this.quads = Collections.unmodifiableMap(new EnumMap<>(quads));
    }

    public Map<UVDirection, UVQuad> getQuads() {
        return quads;
    }
}
