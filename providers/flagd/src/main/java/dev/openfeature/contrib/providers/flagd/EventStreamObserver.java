package dev.openfeature.contrib.providers.flagd;

import java.util.Map;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import com.google.protobuf.Value;

/**
 * EventStreamObserver handles events emitted by flagd.
 */
@Slf4j
public class EventStreamObserver implements StreamObserver<EventStreamResponse> {
    private EventStreamCallback callback;
    private FlagdCache cache;

    private static final String configurationChange = "configuration_change";
    private static final String providerReady = "provider_ready";
    private static final String flagsKey = "flags";

    EventStreamObserver(FlagdCache cache, EventStreamCallback callback) {
        this.cache = cache;
        this.callback = callback;
    }

    @Override
    public void onNext(EventStreamResponse value) {
        switch (value.getType()) {
            case configurationChange:
                this.handleConfigurationChangeEvent(value);
                break;
            case providerReady:
                this.handleProviderReadyEvent();
                break;
            default:
                log.debug("unhandled event type {}", value.getType());
                return;
        }
    }

    @Override
    public void onError(Throwable t) {
        log.error("event stream", t);
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
        this.callback.setEventStreamAlive(false);
        try {
            this.callback.restartEventStream();
        } catch (Exception e) {
            log.error("restart event stream", e);
        }
    }

    @Override
    public void onCompleted() {
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
        this.callback.setEventStreamAlive(false);
    }

    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        if (!this.cache.getEnabled()) {
            return;
        }

        Map<String, Value> data = value.getData().getFieldsMap();
        Value flagsValue = data.get(flagsKey);
        if (flagsValue == null) {
            this.cache.clear();
            return;
        }

        Map<String, Value> flags = flagsValue.getStructValue().getFieldsMap();

        for (String flagKey : flags.keySet()) {
            this.cache.remove(flagKey);
        }
    }

    private void handleProviderReadyEvent() {
        this.callback.setEventStreamAlive(true);
        if (this.cache.getEnabled()) {
            this.cache.clear();
        }
    }
}
