package top.littlewin.codespark.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class AICodeGeneratorServiceTest {

    @Resource
    private AICodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHTMLCode() {
        HTMLCodeResult result = aiCodeGeneratorService.generateHTMLCode("写一个CodeSpark AI零代码应用生成平台的宣传页，不超过个50行");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode("写一个CodeSpark AI零代码应用生成平台的付款页，不超过个50行");
        Assertions.assertNotNull(result);
    }
}