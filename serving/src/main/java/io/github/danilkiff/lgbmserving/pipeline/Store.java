package io.github.danilkiff.lgbmserving.pipeline;

import java.util.Optional;

/**
 * Сохраняет и достаёт объяснения по id решения. Дефолтный {@link MemStore}
 * работает в процессе; устойчивое хранилище - более поздний адаптер.
 */
public interface Store {

    void put(Explanation explanation);

    Optional<Explanation> get(String id);
}
