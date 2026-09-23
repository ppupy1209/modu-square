package board.push.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PushMetrics {
    private final MeterRegistry registry;

    public PushMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 발송 단위 하나를 처리한 결과. */
    public void recordDelivery(String result) {
        Counter.builder("modu.push.delivery")
                .description("공지 발송 단위 처리 결과")
                .tag("result", result)
                .register(registry)
                .increment();
    }
}
