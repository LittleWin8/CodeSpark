package top.littlewin.codespark.core.parser;

/**
 * 代码解析器策略接口
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     *
     * @param context 原始代码内容
     * @return 解析后的结束对象
     */
    T parseCode(String context);
}
