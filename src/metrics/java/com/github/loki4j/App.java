package com.github.loki4j;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import org.slf4j.LoggerFactory;

public class App {
    
    public static void main(String[] args) {
        System.out.println("Starting...");
        var metricsServer = initPrometheus();

        try {
            logMessages();
        } finally {
            metricsServer.stop(0);
            System.out.println("Finished");
        }
    }

    private static void logMessages() {
        var maxMessages = 50_000;
        var maxWords = 100;
        var maxDelay = 50;

        var logger = LoggerFactory.getLogger(App.class);

        var tp = Executors.newFixedThreadPool(4);

        for (int i = 0; i < maxMessages; i++) {
            final var num = i;
            tp.execute(() -> {
                var rnd = ThreadLocalRandom.current();
                var sleep = rnd.nextLong(10, maxDelay);
                try { Thread.sleep(sleep); } catch(Exception e) { }
                var lev = rnd.nextDouble();
                var msg = genMessage(maxWords);
                if (lev < 0.7)
                    logger.info("#{} - {}", num, msg);
                else if (lev < 0.8)
                    logger.debug("#{} - {}", num, msg);
                else if (lev < 0.9)
                    logger.warn("#{} - {}", num, msg);
                else
                    //;
                    logger.error(
                        "#" + num + " - New error",
                        new RuntimeException(msg));
            });
        }

        try { Thread.sleep(500 * 1000); } catch(Exception e) { }

        tp.shutdown();
    }

    private static String genMessage(int maxWords) {
        var rnd = ThreadLocalRandom.current();

        var msg = new StringBuilder();
        var words = rnd.nextInt(1, maxWords);
        for (int i = 0; i < words; i++) {
            var letters = rnd.nextInt(1, 20);
            for (int j = 0; j < letters; j++) {
                msg.append(rnd.nextFloat() < 0.1
                    ? (char)('A' + rnd.nextInt('Z' - 'A'))
                    : (char)('a' + rnd.nextInt('z' - 'a')));
            }
            msg.append(rnd.nextFloat() < 0.05
                ? '\n'
                : ' ');
        }
        return msg.toString();
    }

    private static HttpServer initPrometheus() {
        var prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.addRegistry(prometheusRegistry);

        new JvmMemoryMetrics().bindTo(prometheusRegistry);
        new JvmGcMetrics().bindTo(prometheusRegistry);
        new ProcessorMetrics().bindTo(prometheusRegistry);

        try {
            var server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/prometheus", httpExchange -> {
                String response = prometheusRegistry.scrape();
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                try (var os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            new Thread(server::start).start();
            return server;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
