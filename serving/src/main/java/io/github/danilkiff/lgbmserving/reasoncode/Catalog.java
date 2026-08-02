package io.github.danilkiff.lgbmserving.reasoncode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Отображение индексов признаков в коды причин. Признаки без явной записи
 * получают обобщённый запасной код, чтобы у объяснения всегда был код.
 */
public final class Catalog {

    private static final Catalog EMPTY = new Catalog(Map.of());

    private final Map<Integer, Code> byIndex;

    private Catalog(Map<Integer, Code> byIndex) {
        this.byIndex = byIndex;
    }

    /** Каталог из отображения индекс признака в код. */
    public static Catalog of(Map<Integer, Code> byIndex) {
        return new Catalog(Map.copyOf(byIndex));
    }

    /** Пустой каталог: любой lookup вернёт запасной код. */
    public static Catalog empty() {
        return EMPTY;
    }

    /**
     * Читает JSON-объект, отображающий индекс признака (строковый ключ) в код,
     * например {@code {"21": {"code": "R21", "label": "transaction amount"}}}.
     */
    public static Catalog load(Path path) {
        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Code> raw = mapper.readValue(path.toFile(), new TypeReference<>() {});
        Map<Integer, Code> byIndex = new HashMap<>(raw.size());
        for (Map.Entry<String, Code> e : raw.entrySet()) {
            int index;
            try {
                index = Integer.parseInt(e.getKey());
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(
                        "reasoncode: плохой индекс признака \"%s\" в %s".formatted(e.getKey(), path));
            }
            byIndex.put(index, e.getValue());
        }
        return new Catalog(Map.copyOf(byIndex));
    }

    /**
     * Код причины для признака; при отсутствии записи - обобщённый
     * {@code R<index>}, поэтому отклонение никогда не остаётся без кода.
     */
    public Code lookup(int feature) {
        Code code = byIndex.get(feature);
        return code != null ? code : new Code("R" + feature, "feature " + feature);
    }
}
