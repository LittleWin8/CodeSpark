package top.littlewin.codespark.core;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AICodeGeneratorService;
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
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Slf4j
@Service
public class AICodeGeneratorFacade {

    @Resource
    private AICodeGeneratorService aiCodeGeneratorService;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @return 保存目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        return switch (codeGenType){
            case HTML -> {
                HTMLCodeResult result = aiCodeGeneratorService.generateHTMLCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenType);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenType);
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
     * @return 保存目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        Flux<String> codeStream = switch (codeGenType){
            case HTML -> aiCodeGeneratorService.generateHTMLCodeStream(userMessage);
            case MULTI_FILE -> aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };

        return processCodeStream(codeStream, codeGenType);
    }

    /**
     * 代码流处理方法
     *
     * @param codeStream 代码流
     * @param codeGenType 代码生成类别
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType){
        StringBuilder codeBulider = new StringBuilder();
        return codeStream.doOnNext(chunk ->{
            codeBulider.append(chunk);
        }).doOnComplete(() ->{
            try {
                String completeCode = codeBulider.toString();
                Object parseResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                File saveDir = CodeFileSaverExecutor.executeSaver(parseResult, codeGenType);
                log.info("文件创建成功，保持至" + saveDir.getAbsolutePath());
            } catch (Exception e){
                log.error("保存失败，原因：" + e.getMessage());
            }
        });

    }
}
