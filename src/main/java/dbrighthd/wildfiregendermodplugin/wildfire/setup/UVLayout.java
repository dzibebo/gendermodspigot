package dbrighthd.wildfiregendermodplugin.wildfire.setup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class UVLayout {
    private final Map<UVDirection, UVQuad> quads;

    public UVLayout() {
        this(new EnumMap<>(UVDirection.class));
    }

    public UVLayout(Map<UVDirection, UVQuad> quads) {
        EnumMap<UVDirection, UVQuad> map = new EnumMap<>(UVDirection.class);
        if (quads != null) {
            map.putAll(quads);
        }
        this.quads = Collections.unmodifiableMap(map);
    }

    public Map<UVDirection, UVQuad> getQuads() {
        return quads;
    }
}
