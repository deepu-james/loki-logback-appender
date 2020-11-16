package com.github.loki4j.logback;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.github.loki4j.common.LogRecord;
import com.github.loki4j.logback.AbstractLoki4jAppender.LokiResponse;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

public class InstrumentedLokiJavaHttpAppender extends LokiJavaHttpAppender {

    private Timer encodeTimer = Timer
        .builder("encode.timer")
        .description("Timer for encode operation")
        .register(Metrics.globalRegistry);

    private Timer sendTimer = Timer
        .builder("send.timer")
        .description("Timer for encode operation")
        .register(Metrics.globalRegistry);

    private DistributionSummary logEventsSummary = DistributionSummary
        .builder("encode.event.summary")
        .description("Number of log events processed by encoder")
        .register(Metrics.globalRegistry);

    private DistributionSummary bytesSentSummary = DistributionSummary
        .builder("send.summary")
        .description("Size of batches sent to Loki")
        .baseUnit("bytes")
        .register(Metrics.globalRegistry);

    public InstrumentedLokiJavaHttpAppender() {
    }

    @Override
    public void start() {
        super.start();

        new ExecutorServiceMetrics(scheduler, "loki-scheduler", null).bindTo(Metrics.globalRegistry);
        new ExecutorServiceMetrics(httpThreadPool, "loki-http-sender", null).bindTo(Metrics.globalRegistry);
    }
    
    @Override
    protected byte[] encode(LogRecord[] batch) {
        logEventsSummary.record(batch.length);
        return encodeTimer.record(() -> super.encode(batch));
    }

    @Override
    protected CompletableFuture<LokiResponse> sendAsync(byte[] batch) {
        bytesSentSummary.record(batch.length);
        var started = System.nanoTime();
        return super
            .sendAsync(batch)
            .whenComplete((r, e) -> sendTimer.record(Duration.ofNanos(System.nanoTime() - started)));
    }

}
