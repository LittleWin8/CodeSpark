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
import top.littlewin.codespark.core.builder.BuildResult;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.core.stream.BuildEventPublisher;
import top.littlewin.codespark.core.stream.TokenStreamMessageEmitter;
import top.littlewin.codespark.model.enums.BuildStatusEnum;
import top.littlewin.codespark.model.enums.ChatStageEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AICodeGeneratorFacade 单元测试（生成物动作：VUE 流完成后推 BUILDING 并触发异步构建，
 * 构建回调推 SUCCESS/FAILED 终态，前端订阅构建流刷新预览或提示失败）
 * <p>
 * 与 @SpringBootTest 的 {@link AICodeGeneratorFacadeTest} 互补：此处不启动 Spring 容器，
 * 避免真实调用 AI、执行 npm 安装/构建。
 */
class AICodeGeneratorFacadeUnitTest {

    private final AICodeGeneratorServiceFactory factory = mock(AICodeGeneratorServiceFactory.class);
    private final TokenStreamMessageEmitter emitter = mock(TokenStreamMessageEmitter.class);
    private final VueProjectBulider vueProjectBulider = mock(VueProjectBulider.class);
    private final BuildEventPublisher buildEventPublisher = mock(BuildEventPublisher.class);
    private final AICodeGeneratorFacade facade = new AICodeGeneratorFacade();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facade, "aiCodeGeneratorServiceFactory", factory);
        ReflectionTestUtils.setField(facade, "tokenStreamMessageEmitter", emitter);
        ReflectionTestUtils.setField(facade, "vueProjectBulider", vueProjectBulider);
        ReflectionTestUtils.setField(facade, "buildEventPublisher", buildEventPublisher);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vueProject_triggersAsyncBuildAfterStreamCompletes() {
        AICodeGeneratorService service = mock(AICodeGeneratorService.class);
        when(factory.getAICodeGeneratorService(1L, CodeGenTypeEnum.VUE_PROJECT)).thenReturn(service);
        when(emitter.emit(any())).thenReturn(Flux.just(new AiResponseMessage("代码")));

        Flux<StreamMessage> result = facade.generateAndSaveCodeStream(
                "生成 Vue 项目", CodeGenTypeEnum.VUE_PROJECT, 1L, ChatStageEnum.CREATE);
        result.collectList().block();

        // 流完成后先推送 BUILDING，再启动异步构建（仅 VUE 模式；HTML/MULTI 走解析落盘分支）
        verify(buildEventPublisher).publish(eq(1L), eq(BuildStatusEnum.BUILDING), eq(null));
        ArgumentCaptor<Consumer<BuildResult>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(vueProjectBulider).buildProjectAsync(
                eq(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_1"), callbackCaptor.capture());

        // 构建成功回调触发 SUCCESS 终态（失败则为 FAILED，前端据此刷新预览或提示失败）
        callbackCaptor.getValue().accept(BuildResult.success());
        verify(buildEventPublisher).publish(eq(1L), eq(BuildStatusEnum.SUCCESS), eq(null));
    }
}
