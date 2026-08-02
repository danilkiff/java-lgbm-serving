package io.github.danilkiff.lgbmserving.lgbm;

import io.github.danilkiff.lgbmserving.data.Csv;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Доступ к эталонам паритета из {@code refs/} (получаются через {@code make refs}). */
final class Refs {

    static final Path DIR = Path.of("refs");

    private Refs() {}

    record Meta(
            @JsonProperty("lightgbm_version") String lightgbmVersion,
            @JsonProperty("n_features") int nFeatures,
            @JsonProperty("n_holdout") int nHoldout,
            @JsonProperty("contrib_shape") int[] contribShape,
            @JsonProperty("score_is_raw_margin") boolean scoreIsRawMargin) {}

    /** Пропускает тест, если эталоны не получены: без них паритет не проверить. */
    static void require() {
        Assumptions.assumeTrue(
                Files.isRegularFile(DIR.resolve("model.txt")),
                "нет эталонов - выполните `make refs`");
    }

    static Meta meta() {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Meta meta = mapper.readValue(DIR.resolve("meta.json").toFile(), Meta.class);
        // Гейт семантики эталонов: сравниваем raw margin, а не вероятности.
        if (!meta.scoreIsRawMargin()) {
            throw new IllegalStateException(
                    "meta.score_is_raw_margin=false, эталоны обязаны нести raw margin");
        }
        return meta;
    }

    static double[][] matrix(String name) {
        return Csv.read(DIR.resolve(name));
    }

    static Path model() {
        return DIR.resolve("model.txt");
    }
}
