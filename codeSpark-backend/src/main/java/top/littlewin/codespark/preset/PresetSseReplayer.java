package top.littlewin.codespark.preset;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.AiThinkingMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 预置事件回放：把最终内容切成分片，模拟真实流式输出节奏
 */
@Component
public class PresetSseReplayer {

    @Resource
    private PresetProperties properties;

    public Flux<StreamMessage> replay(List<StreamMessage> events) {
        List<StreamMessage> expanded = new ArrayList<>();
        for (StreamMessage event : events) {
            if (event instanceof AiResponseMessage response) {
                for (String chunk : split(response.getData())) {
                    expanded.add(new AiResponseMessage(chunk));
                }
            } else if (event instanceof AiThinkingMessage thinking) {
                for (String chunk : split(thinking.getData())) {
                    expanded.add(new AiThinkingMessage(chunk));
                }
            } else {
                expanded.add(event);
            }
        }
        return Flux.fromIterable(expanded)
                .delayElements(Duration.ofMillis(Math.max(1, properties.getReplayIntervalMillis())));
    }

    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int size = Math.max(1, properties.getReplayChunkSize());
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }
}
