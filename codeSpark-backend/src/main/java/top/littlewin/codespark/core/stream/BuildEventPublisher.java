package top.littlewin.codespark.core.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import top.littlewin.codespark.model.enums.BuildStatusEnum;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 构建事件发布器：为每个应用维护一个可订阅的构建状态事件流。
 * <p>
 * 设计要点：
 * 1. 使用 {@link Sinks#many()} {@code replay().latest()}：缓存最新一条状态，新订阅者（含
 *    前端刷新/断线重连）立即收到当前状态，之后继续接收后续事件，天然解决"重连拿不到已完成状态"的问题；
 * 2. 终态（SUCCESS/FAILED）发布后自动结束该流（complete），订阅侧流关闭；
 * 3. 终态后 Sink 保留在 map 中：重连的订阅者仍能立即拿到终态；新一轮构建 publish 时若旧 Sink
 *    已终止，自动重建。应用数量有限，内存占用可控。
 */
@Slf4j
@Component
public class BuildEventPublisher {

    private final ConcurrentHashMap<Long, Sinks.Many<BuildEvent>> sinks = new ConcurrentHashMap<>();

    /**
     * 订阅指定应用的构建事件流（冷流，每次订阅各自生效）
     *
     * @param appId 应用 ID
     * @return 构建事件流：先收到最近状态（若有），再持续接收直到终态后结束
     */
    public Flux<BuildEvent> subscribe(Long appId) {
        Sinks.Many<BuildEvent> sink = sinks.computeIfAbsent(appId, k -> createSink());
        return sink.asFlux()
                .doFinally(signal -> log.debug("构建事件流结束: appId={}, signal={}", appId, signal));
    }

    /**
     * 发布构建状态事件；终态（SUCCESS/FAILED）发布后自动结束该事件流。
     *
     * @param appId   应用 ID
     * @param status  构建状态
     * @param message 状态说明（失败原因）
     */
    public void publish(Long appId, BuildStatusEnum status, String message) {
        Sinks.Many<BuildEvent> sink = sinks.computeIfAbsent(appId, k -> createSink());
        BuildEvent event = new BuildEvent(appId, status, message, LocalDateTime.now());
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result == Sinks.EmitResult.FAIL_TERMINATED) {
            // 旧 Sink 已终态（complete 后无法再 emit），重建以支持新一轮构建后重试
            sink = createSink();
            sinks.put(appId, sink);
            sink.tryEmitNext(event);
        }
        if (status == BuildStatusEnum.SUCCESS || status == BuildStatusEnum.FAILED) {
            sink.tryEmitComplete();
        }
        log.info("发布构建事件: appId={}, status={}, message={}", appId, status, message);
    }

    private Sinks.Many<BuildEvent> createSink() {
        return Sinks.many().replay().latest();
    }
}