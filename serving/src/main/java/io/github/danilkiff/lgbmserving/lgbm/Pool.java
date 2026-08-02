package io.github.danilkiff.lgbmserving.lgbm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Фиксированный набор независимых хэндлов одной модели, по одному на вызов:
 * поток берёт свой хэндл, отсюда настоящий параллелизм без общего состояния
 * предсказания. Общий хэндл сериализовал бы предсказание, а на LightGBM
 * 3.0.0-3.1.1 ещё и молча гонялся через буфер, ключёванный номером
 * OpenMP-потока. Цена - size копий модели в памяти.
 */
public final class Pool implements AutoCloseable {

    private final BlockingQueue<Booster> free;
    private final List<Booster> all;
    private final int nFeature;

    /** Загружает size независимых хэндлов из файла модели. */
    public static Pool load(Path path, int size) {
        try {
            return fromBytes(Files.readAllBytes(path), size);
        } catch (IOException e) {
            throw new UncheckedIOException("lgbm: не прочитать модель " + path, e);
        }
    }

    /**
     * Загружает size независимых хэндлов из содержимого файла модели: файл
     * читается один раз, поэтому все хэндлы гарантированно из одних байт.
     */
    public static Pool fromBytes(byte[] model, int size) {
        return new Pool(model, size);
    }

    private Pool(byte[] model, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("lgbm: размер пула должен быть >= 1, получено " + size);
        }
        this.free = new ArrayBlockingQueue<>(size);
        this.all = new ArrayList<>(size);
        try {
            for (int i = 0; i < size; i++) {
                Booster b = Booster.fromBytes(model);
                all.add(b);
                free.add(b);
            }
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        this.nFeature = all.get(0).numFeature();
    }

    /** Число входных признаков модели. */
    public int numFeature() {
        return nFeature;
    }

    /** Берёт свободный хэндл и считает raw margin. */
    public double predictRaw(double[] row) {
        Booster b = acquire();
        try {
            return b.predictRaw(row);
        } finally {
            free.add(b);
        }
    }

    /** Берёт свободный хэндл и считает нативные SHAP contributions. */
    public double[] predictContrib(double[] row) {
        Booster b = acquire();
        try {
            return b.predictContrib(row);
        } finally {
            free.add(b);
        }
    }

    private Booster acquire() {
        try {
            return free.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lgbm: ожидание хэндла прервано", e);
        }
    }

    /** Освобождает все хэндлы. Небезопасно вызывать конкурентно с predict. */
    @Override
    public void close() {
        for (Booster b : all) {
            b.close();
        }
        all.clear();
        free.clear();
    }
}
