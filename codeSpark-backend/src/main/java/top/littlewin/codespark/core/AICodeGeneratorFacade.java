package top.littlewin.codespark.core;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AICodeGeneratorService;
import top.littlewin.codespark.ai.AICodeGeneratorServiceFactory;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;
import top.littlewin.codespark.core.parser.CodeParserExecutor;
import top.littlewin.codespark.core.saver.CodeFileSaverExecutor;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * AI 代码生成门面类，组合代码生成和保存功能，并封装 App 命名
 */
@Slf4j
@Service
public class AICodeGeneratorFacade {

    @Resource
    private AICodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;


    /**
     * 根据 App 的初始提示词生成应用名称
     * @param userMessage 应用初始提示词
     * @param codeGenType 生成类型
     * @return 应用名称
     */
    public String generateAppName(String userMessage, CodeGenTypeEnum codeGenType){

        ThrowUtils.throwIf(codeGenType != CodeGenTypeEnum.NAMING, ErrorCode.PARAMS_ERROR, "只有命名功能才可以调用方法");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(0, codeGenType);

        return aiCodeGeneratorService.generateAppName(userMessage);
    }

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @param appId 应用 ID
     * @return 保存目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType, Long appId){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(appId, codeGenType);

        return switch (codeGenType){
            case HTML -> {
                HTMLCodeResult result = aiCodeGeneratorService.generateHTMLCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenType, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenType, appId);
            }
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码(流式)
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @param appId 应用 ID
     * @return 保存目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(appId, codeGenType);

        Flux<String> codeStream = switch (codeGenType){
            case HTML -> aiCodeGeneratorService.generateHTMLCodeStream(userMessage);
            case MULTI_FILE -> aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
            case VUE_PROJECT -> aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };

        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            codeGenType = CodeGenTypeEnum.MULTI_FILE;
        }
        return processCodeStream(codeStream, codeGenType, appId);
    }

    /**
     * 代码流处理方法
     *
     * @param codeStream 代码流
     * @param codeGenType 代码生成类别
     * @param appId 应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId){
        StringBuilder codeBulider = new StringBuilder();
        return codeStream.doOnNext(chunk ->{
            codeBulider.append(chunk);
        }).doOnComplete(() ->{
            try {
                String completeCode = codeBulider.toString();
                Object parseResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                File saveDir = CodeFileSaverExecutor.executeSaver(parseResult, codeGenType, appId);
                log.info("文件创建成功，保持至" + saveDir.getAbsolutePath());
            } catch (Exception e){
                log.error("保存失败，原因：" + e.getMessage());
            }
        });
    }


}
