package io.github.danilkiff.lgbmserving.lgbm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Загруженная модель LightGBM: raw margin и нативные SHAP contributions
 * ({@code C_API_PREDICT_CONTRIB}) из одного предиктора, поэтому код причины не
 * пересчитывается заново, а берётся оттуда же, откуда решение.
 *
 * <p>Небезопасен для конкурентных вызовов: буферы обмена с нативной стороной
 * переиспользуются между вызовами ради безаллокационного горячего пути. Для
 * параллельного инференса используйте {@link Pool}.
 */
public final class Booster implements AutoCloseable {

    /**
     * Арена именно shared, а не confined: хэндл из пула по очереди берут разные
     * потоки, и confined-арена запретила бы доступ всем, кроме создавшего.
     */
    private final Arena arena = Arena.ofShared();

    private final MemorySegment handle;
    private final int nFeature;
    private final MemorySegment in;
    private final MemorySegment out;
    private final MemorySegment outLen;

    private boolean closed;

    /** Загружает модель, записанную в Python методом {@code Booster.save_model}. */
    public static Booster load(Path path) {
        try {
            return fromBytes(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("lgbm: не прочитать модель " + path, e);
        }
    }

    /**
     * Загружает модель из содержимого файла. Байты - единица идентичности:
     * вызывающий хеширует ровно то, что загружено, без гонки с файлом на диске
     * между хешированием и загрузкой.
     */
    public static Booster fromBytes(byte[] model) {
        return new Booster(model);
    }

    private Booster(byte[] model) {
        MemorySegment loaded;
        int features;
        try (Arena tmp = Arena.ofConfined()) {
            // Байты копируются как есть плюс завершающий ноль: перекодировка через
            // String навязала бы модели предположение о кодировке файла.
            MemorySegment cstr = tmp.allocate(model.length + 1L);
            MemorySegment.copy(model, 0, cstr, ValueLayout.JAVA_BYTE, 0, model.length);
            cstr.set(ValueLayout.JAVA_BYTE, model.length, (byte) 0);

            MemorySegment numIterations = tmp.allocate(ValueLayout.JAVA_INT);
            MemorySegment handleOut = tmp.allocate(ValueLayout.ADDRESS);
            check(call(() -> (int) CApi.LOAD_MODEL_FROM_STRING
                    .invokeExact(cstr, numIterations, handleOut)));
            loaded = handleOut.get(ValueLayout.ADDRESS, 0);

            MemorySegment featureOut = tmp.allocate(ValueLayout.JAVA_INT);
            MemorySegment held = loaded;
            checkOrFree(held, call(() -> (int) CApi.GET_NUM_FEATURE.invokeExact(held, featureOut)));
            features = featureOut.get(ValueLayout.JAVA_INT, 0);

            // Один выход на строку: мультиклассовая модель молча теряла бы классы
            // за пределами out[0] - отказываем на загрузке, а не в проде. Гейт
            // заодно фиксирует длины вывода: raw - 1, contrib - nFeature+1.
            MemorySegment predictLen = tmp.allocate(ValueLayout.JAVA_LONG);
            checkOrFree(held, call(() -> (int) CApi.CALC_NUM_PREDICT
                    .invokeExact(held, 1, CApi.PREDICT_RAW, 0, -1, predictLen)));
            long perRow = predictLen.get(ValueLayout.JAVA_LONG, 0);
            if (perRow != 1) {
                free(held);
                throw new LgbmException(
                        "lgbm: модель отдаёт %d значений на строку, ожидалось 1 (бинарная или регрессия)"
                                .formatted(perRow));
            }
        }

        this.handle = loaded;
        this.nFeature = features;
        this.in = arena.allocate(ValueLayout.JAVA_DOUBLE, features);
        this.out = arena.allocate(ValueLayout.JAVA_DOUBLE, features + 1L);
        this.outLen = arena.allocate(ValueLayout.JAVA_LONG);
    }

    /** Число входных признаков модели. */
    public int numFeature() {
        return nFeature;
    }

    /**
     * Raw margin (до сигмоиды) для одной строки - прямой аналог Python
     * {@code predict(raw_score=True)}.
     */
    public double predictRaw(double[] row) {
        predictInto(row, CApi.PREDICT_RAW);
        return out.get(ValueLayout.JAVA_DOUBLE, 0);
    }

    /**
     * Нативные SHAP contributions для одной строки, длина {@code numFeature()+1}.
     * Последний элемент - base value; сумма всех элементов равна raw margin -
     * инвариант согласованности, который проверяет harness паритета.
     */
    public double[] predictContrib(double[] row) {
        long written = predictInto(row, CApi.PREDICT_CONTRIB);
        double[] result = new double[(int) written];
        MemorySegment.copy(out, ValueLayout.JAVA_DOUBLE, 0, result, 0, result.length);
        return result;
    }

    private long predictInto(double[] row, int predictType) {
        if (closed) {
            throw new IllegalStateException("lgbm: Booster закрыт");
        }
        if (row.length != nFeature) {
            throw new FeatureCountException(nFeature, row.length);
        }
        MemorySegment.copy(row, 0, in, ValueLayout.JAVA_DOUBLE, 0, nFeature);
        check(call(() -> (int) CApi.PREDICT_FOR_MAT.invokeExact(
                handle,
                in,
                CApi.DTYPE_FLOAT64,
                1, // nrow
                nFeature,
                1, // is_row_major
                predictType,
                0, // start_iteration
                -1, // num_iteration: все
                CApi.PREDICT_PARAM,
                outLen,
                out)));
        return outLen.get(ValueLayout.JAVA_LONG, 0);
    }

    /** Освобождает нативную модель. Повторный вызов безопасен. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        free(handle);
        arena.close();
    }

    private interface NativeCall {
        int invoke() throws Throwable;
    }

    private static int call(NativeCall c) {
        try {
            return c.invoke();
        } catch (Throwable t) {
            throw new LgbmException("lgbm: сбой вызова C API", t);
        }
    }

    private static void check(int rc) {
        if (rc != 0) {
            throw new LgbmException("lightgbm: " + CApi.lastError());
        }
    }

    private static void checkOrFree(MemorySegment handle, int rc) {
        if (rc != 0) {
            free(handle);
            throw new LgbmException("lightgbm: " + CApi.lastError());
        }
    }

    private static void free(MemorySegment handle) {
        call(() -> (int) CApi.FREE.invokeExact(handle));
    }
}
