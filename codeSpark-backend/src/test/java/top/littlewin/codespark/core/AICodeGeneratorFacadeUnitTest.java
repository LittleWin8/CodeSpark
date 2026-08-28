package top.littlewin.codespark.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AICodeGeneratorService;
import top.littlewin.codespark.ai.AICodeGeneratorServiceFactory;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.core.stream.TokenStreamMessageEmitter;
import top.littlewin.codespark.model.enums.ChatStageEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.service.AppService;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AICodeGeneratorFacade 单元测试（生成物动作：VUE 流完成后触发异步构建，构建成功后触发封面截图）
 * <p>
 * 与 @SpringBootTest 的 {@link AICodeGeneratorFacadeTest} 互补：此处不启动 Spring 容器，
 * 避免真实调用 AI、执行 npm 安装/构建与网页截图。
 */
class AICodeGeneratorFacadeUnitTest {

    private final AICodeGeneratorServiceFactory factory = mock(AICodeGeneratorServiceFactory.class);
    private final TokenStreamMessageEmitter emitter = mock(TokenStreamMessageEmitter.class);
    private final VueProjectBulider vueProjectBulider = mock(VueProjectBulider.class);
    private final AppService appService = mock(AppService.class);
    private final AICodeGeneratorFacade facade = new AICodeGeneratorFacade();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facade, "aiCodeGeneratorServiceFactory", factory);
        ReflectionTestUtils.setField(facade, "tokenStreamMessageEmitter", emitter);
        ReflectionTestUtils.setField(facade, "vueProjectBulider", vueProjectBulider);
        ReflectionTestUtils.setField(facade, "appService", appService);
    }

    @Test
    void vueProject_triggersAsyncBuildAfterStreamCompletes() {
        AICodeGeneratorService service = mock(AICodeGeneratorService.class);
        when(factory.getAICodeGeneratorService(1L, CodeGenTypeEnum.VUE_PROJECT)).thenReturn(service);
        when(emitter.emit(any())).thenReturn(Flux.just(new AiResponseMessage("代码")));

        Flux<StreamMessage> result = facade.generateAndSaveCodeStream(
                "生成 Vue 项目", CodeGenTypeEnum.VUE_PROJECT, 1L, ChatStageEnum.CREATE);
        result.collectList().block();

        // 流完成后启动异步构建（仅 VUE 模式；HTML/MULTI 走 attachParseAndSave 的解析落盘分支）
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(vueProjectBulider).buildProjectAsync(
                eq(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_1"), callbackCaptor.capture());

        // 构建成功回调被触发后，才发起封面截图（VUE 预览指向 dist/index.html）
        callbackCaptor.getValue().run();
        verify(appService).generateAppScreenshotAsync(
                eq(1L), eq(AppConstant.PREVIEW_BASE_URL + "/vue_1/dist/index.html"));
    }
}