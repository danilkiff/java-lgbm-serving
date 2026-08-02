package io.github.danilkiff.lgbmserving.lgbm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Разрешение пути к нативной библиотеке LightGBM, которую грузит FFM.
 *
 * <p>Битовый паритет с Python держится на том, что это тот же бинарник, что
 * исполнял обучение; отсюда поиск ведётся в каталоге {@code native/}, куда uv
 * ставит колесо фиксированной версии, а не по системным путям загрузчика.
 */
public final class NativeLibrary {

    /** Путь колеса lightgbm внутри окружения uv. */
    private static final String VENV_LIB_DIR =
            "native/.venv/lib/python3.12/site-packages/lightgbm/lib";

    private static final String[] NAMES = {"lib_lightgbm.dylib", "lib_lightgbm.so"};

    private NativeLibrary() {}

    /**
     * Возвращает путь к библиотеке: свойство {@code lgbm.library.path}, иначе
     * переменная {@code LGBM_LIBRARY_PATH}, иначе поиск подъёмом от рабочего
     * каталога - так тесты, бенчмарки и CLI находят библиотеку из любого места
     * дерева.
     *
     * @throws IllegalStateException если библиотека не найдена
     */
    public static Path path() {
        String explicit = System.getProperty("lgbm.library.path");
        if (explicit == null) {
            explicit = System.getenv("LGBM_LIBRARY_PATH");
        }
        if (explicit != null) {
            Path p = Path.of(explicit);
            if (!Files.isRegularFile(p)) {
                throw new IllegalStateException("нет lib_lightgbm по заданному пути: " + p);
            }
            return p;
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            for (String name : NAMES) {
                Path p = dir.resolve(VENV_LIB_DIR).resolve(name);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        }
        throw new IllegalStateException(
                "нет lib_lightgbm в " + VENV_LIB_DIR + " - выполните `make native`"
                        + " или задайте LGBM_LIBRARY_PATH");
    }

    /**
     * Версия поставленного колеса ({@code VERSION.txt} рядом с каталогом
     * библиотеки). Гейт паритета сверяет её с версией, обучавшей модель:
     * другая версия рушит утверждение о битовом совпадении.
     */
    public static Optional<String> version() {
        Path libDir = path().getParent();
        if (libDir == null || libDir.getParent() == null) {
            return Optional.empty();
        }
        Path versionFile = libDir.getParent().resolve("VERSION.txt");
        try {
            return Optional.of(Files.readString(versionFile).trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
