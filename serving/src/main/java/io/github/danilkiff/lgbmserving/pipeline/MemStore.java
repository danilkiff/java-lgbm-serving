package io.github.danilkiff.lgbmserving.pipeline;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище в памяти. Растёт неограниченно: записи не вытесняются, пока жив
 * процесс - долгоживущему сервису нужен адаптер с политикой хранения за тем же
 * интерфейсом {@link Store}.
 */
public final class MemStore implements Store {

    private final Map<String, Explanation> byId = new ConcurrentHashMap<>();

    @Override
    public void put(Explanation explanation) {
        byId.put(explanation.id(), explanation);
    }

    @Override
    public Optional<Explanation> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Сколько объяснений сохранено. */
    public int size() {
        return byId.size();
    }
}
