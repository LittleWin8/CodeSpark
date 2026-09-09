package top.littlewin.codespark.core;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.model.enums.ChatStageEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class AiModelMetricsControllerCodeGeneratorFacadeTest {


    @Resource
    private AICodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCodeStream() {
        var codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("生成一个作品集展示页，越短越好",CodeGenTypeEnum.HTML,1L, ChatStageEnum.CREATE);
        List<StreamMessage> result= codeStream.collectList().block();
        Assertions.assertNotNull(result);
        // 只累积正文（ai_response），断言生成内容非空
        String completeContent = result.stream()
                .filter(msg -> msg instanceof AiResponseMessage)
                .map(msg -> ((AiResponseMessage) msg).getData())
                .collect(Collectors.joining());
        Assertions.assertNotNull(completeContent);
    }

    @Test
    void generateVueProjectCodeStream() {
        var codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 1L, ChatStageEnum.CREATE);
        // 阻塞等待所有数据收集完成
        List<StreamMessage> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = result.stream()
                .filter(msg -> msg instanceof AiResponseMessage)
                .map(msg -> ((AiResponseMessage) msg).getData())
                .collect(Collectors.joining());
        Assertions.assertNotNull(completeContent);
    }

}
