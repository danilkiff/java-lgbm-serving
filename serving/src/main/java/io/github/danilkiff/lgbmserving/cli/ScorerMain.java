package io.github.danilkiff.lgbmserving.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.danilkiff.lgbmserving.lgbm.FeatureCountException;
import io.github.danilkiff.lgbmserving.lgbm.Pool;
import io.github.danilkiff.lgbmserving.pipeline.ChannelQueue;
import io.github.danilkiff.lgbmserving.pipeline.Explanation;
import io.github.danilkiff.lgbmserving.pipeline.MemStore;
import io.github.danilkiff.lgbmserving.pipeline.ScoreResult;
import io.github.danilkiff.lgbmserving.pipeline.Scorer;
import io.github.danilkiff.lgbmserving.pipeline.Worker;
import io.github.danilkiff.lgbmserving.pipeline.WorkerConfig;
import io.github.danilkiff.lgbmserving.reasoncode.Catalog;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Сервис decline-explain. {@code POST /score} возвращает решение из пула
 * Booster и для отклонений выкладывает событие вне горячего пути; шаг SHAP
 * никогда не идёт инлайн. Воркеры explain считают его асинхронно,
 * {@code GET /explain/{id}} отдаёт результат, {@code GET /metrics} - снимок.
 *
 * <pre>scorer -model serving/fixtures/model.txt -addr :8080 -threshold 0</pre>
 */
public final class ScorerMain {

    /** Валидный запрос - сотни байт; мегабайт отсекает мусор, не давая раздуть память. */
    private static final int MAX_BODY = 1 << 20;

    private static final Duration SHUTDOWN = Duration.ofSeconds(15);

    private ScorerMain() {}

    public static void main(String[] argv) throws Exception {
        Args args;
        try {
            args = Args.parse(argv);
        } catch (IllegalArgumentException e) {
            System.err.println("scorer: " + e.getMessage());
            System.exit(2);
            return;
        }

        Path modelPath = Path.of(args.require("model", "например -model serving/fixtures/model.txt"));
        String addr = args.string("addr", ":8080");
        double threshold = args.number("threshold", 0);
        int queueBuffer = args.integer("queue", 1024);
        int topK = args.integer("topk", 3);
        int workers = args.integer("workers", 2);
        String codes = args.string("codes", "");

        // Файл читается один раз: версия и пул считаются от одних байт, иначе
        // замена файла между хешированием и загрузкой дала бы отпечаток одной
        // модели при пуле другой.
        byte[] modelBytes = Files.readAllBytes(modelPath);
        String modelVer = modelVersion(modelPath, modelBytes);
        Catalog catalog = codes.isEmpty() ? Catalog.empty() : Catalog.load(Path.of(codes));

        // Хэндлов больше, чем воркеров explain: даже при всех воркерах, занятых
        // SHAP, горячему пути остаётся по хэндлу на ядро.
        int handles = Runtime.getRuntime().availableProcessors() + workers;
        Pool pool = Pool.fromBytes(modelBytes, handles);

        ChannelQueue queue = new ChannelQueue(queueBuffer);
        MemStore store = new MemStore();
        Scorer scorer = new Scorer(pool, threshold, modelVer, queue);
        Worker worker = new Worker(pool, store, new WorkerConfig(topK, catalog,
                (event, error) -> System.err.printf("explain: dead-letter id=%s: %s%n", event.id(), error)));
        worker.start(queue, workers);

        // Тело запроса - ровно один JSON-объект: непробельный хвост (мусор или
        // второй объект) отвергается до скоринга.
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();

        HttpServer server = HttpServer.create(address(addr), 0);
        ExecutorService httpPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2);
        server.setExecutor(httpPool);
        server.createContext("/score", scoreHandler(scorer, mapper));
        server.createContext("/explain/", explainHandler(store, mapper));
        server.createContext("/metrics", metricsHandler(scorer, queue, worker, mapper));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("scorer: signal received, draining...");
            // По порядку: перестать принимать запросы (доделав текущие), слить
            // очередь explain, затем освободить хэндлы модели. Close пула под
            // активным predict - use-after-free.
            server.stop((int) SHUTDOWN.toSeconds());
            httpPool.shutdown();
            queue.close();
            if (!worker.awaitDrain(SHUTDOWN)) {
                System.err.printf("scorer: explain drain timed out, %d events unprocessed%n", queue.size());
                worker.stopNow();
                worker.awaitDrain(SHUTDOWN);
            }
            pool.close();
            System.err.println("scorer: stopped");
        }));

        server.start();
        System.err.printf(
                "scorer: %d handles, %d explain workers, threshold=%s, top-%d reason codes, listening on %s%n",
                handles, workers, threshold, topK, server.getAddress());
        Thread.currentThread().join();
    }

    /**
     * Идентификатор модели - путь и префикс sha256 её байт: объяснение сверяется
     * с содержимым, а не с именем файла.
     */
    static String modelVersion(Path path, byte[] data) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256").digest(data);
            return "%s@%s".formatted(path, HexFormat.of().formatHex(sum, 0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("нет SHA-256", e);
        }
    }

    private static InetSocketAddress address(String addr) {
        int colon = addr.lastIndexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("адрес без порта: " + addr);
        }
        String host = addr.substring(0, colon);
        int port = Integer.parseInt(addr.substring(colon + 1));
        return host.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
    }

    private static HttpHandler scoreHandler(Scorer scorer, ObjectMapper mapper) {
        return exchange -> {
            try (exchange) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    text(exchange, 405, "method not allowed");
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    text(exchange, 413, "request body too large");
                    return;
                }
                Wire.ScoreRequest request;
                try {
                    request = mapper.readValue(body, Wire.ScoreRequest.class);
                } catch (RuntimeException e) {
                    text(exchange, 400, "bad request: " + e.getMessage());
                    return;
                }
                List<Double> features = request == null ? null : request.features();
                if (features == null) {
                    text(exchange, 400, "bad request: no features");
                    return;
                }
                double[] row = new double[features.size()];
                for (int i = 0; i < row.length; i++) {
                    Double v = features.get(i);
                    row[i] = v == null ? Double.NaN : v;
                }
                ScoreResult result;
                try {
                    result = scorer.score(row);
                } catch (FeatureCountException e) {
                    // Неверная ширина входа - ошибка клиента; всё прочее - сбой
                    // нативного предиктора, и это 500, а не вина запроса.
                    text(exchange, 422, e.getMessage());
                    return;
                } catch (RuntimeException e) {
                    text(exchange, 500, String.valueOf(e.getMessage()));
                    return;
                }
                json(exchange, 200, new Wire.ScoreResponse(
                        result.id(), result.margin(), result.decision(), result.explainQueued()), mapper);
            }
        };
    }

    private static HttpHandler explainHandler(MemStore store, ObjectMapper mapper) {
        return exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    text(exchange, 405, "method not allowed");
                    return;
                }
                String id = exchange.getRequestURI().getPath().substring("/explain/".length());
                Optional<Explanation> found = store.get(id);
                if (found.isEmpty()) {
                    // Согласованность в конечном счёте: только что отклонённый id
                    // может быть ещё не объяснён.
                    text(exchange, 404, "explanation not found");
                    return;
                }
                json(exchange, 200, found.get(), mapper);
            }
        };
    }

    private static HttpHandler metricsHandler(
            Scorer scorer, ChannelQueue queue, Worker worker, ObjectMapper mapper) {
        return exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    text(exchange, 405, "method not allowed");
                    return;
                }
                long scored = scorer.scored();
                long declined = scorer.declined();
                json(exchange, 200, new Wire.Metrics(
                        scored,
                        declined,
                        scored > 0 ? (double) declined / scored : 0,
                        queue.size(),
                        queue.capacity(),
                        queue.dropped(),
                        worker.explained(),
                        worker.dropped()), mapper);
            }
        };
    }

    private static void json(HttpExchange exchange, int status, Object value, ObjectMapper mapper)
            throws IOException {
        byte[] payload = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static void text(HttpExchange exchange, int status, String message) throws IOException {
        byte[] payload = (message + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
