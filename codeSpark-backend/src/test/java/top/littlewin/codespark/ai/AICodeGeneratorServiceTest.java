package top.littlewin.codespark.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class AICodeGeneratorServiceTest {

    @Resource
    private AICodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Test
    void generateHTMLCode() {
        AICodeGeneratorService service = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(1L, CodeGenTypeEnum.HTML);
        HTMLCodeResult result = service.generateHTMLCode("写一个CodeSpark AI零代码应用生成平台的宣传页，不超过个50行");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        AICodeGeneratorService service = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(1L, CodeGenTypeEnum.MULTI_FILE);
        MultiFileCodeResult result = service.generateMultiFileCode("写一个CodeSpark AI零代码应用生成平台的付款页，不超过个50行");
        Assertions.assertNotNull(result);
    }

    @Test
    void testChatMemory() {
        // 不同 appId 拿到独立记忆的 AI 服务实例
        AICodeGeneratorService app1 = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(1L, CodeGenTypeEnum.HTML);
        AICodeGeneratorService app2 = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(2L, CodeGenTypeEnum.HTML);

        HTMLCodeResult result = app1.generateHTMLCode("随便做个工具网站，总代码量不超过 20 行");
        Assertions.assertNotNull(result);
        result = app1.generateHTMLCode("不要生成代码，只需要告诉我你刚刚做了什么？");
        Assertions.assertNotNull(result);
        result = app2.generateHTMLCode("随便做个工具网站，总代码量不超过 20 行");
        Assertions.assertNotNull(result);
        result = app2.generateHTMLCode("不要生成代码，只需要告诉我你刚刚做了什么？");
        Assertions.assertNotNull(result);
    }

}
