package top.littlewin.codespark.core.parser;


import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 * 根据不同的代码类型执行不同的逻辑
 */
public class CodeParserExecutor {

    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();
    private static final MultiFileCodeParser  multiFileCodeParser = new MultiFileCodeParser();

    public static Object executeParser(String context, CodeGenTypeEnum codeGenTypeEnum){
        return switch (codeGenTypeEnum){
            case HTML -> htmlCodeParser.parseCode(context);
            case MULTI_FILE -> multiFileCodeParser.parseCode(context);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };

    }
}
