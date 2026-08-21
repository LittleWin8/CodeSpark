package top.littlewin.codespark.exception;

public class ThrowUtils {

    /**
     * 条件成立则抛异常
     *
     * @param condition        条件
     * @param runtimeException 异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 条件成立则抛异常
     *
     * @param condition 条件
     * @param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立则抛异常
     *
     * @param condition 条件
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }

    /**
     * 条件成立则抛异常（错误信息使用统一枚举，避免硬编码字符串）
     *
     * @param condition    条件
     * @param errorCode    错误码
     * @param errorMessage 错误信息枚举
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, ErrorMessage errorMessage) {
        throwIf(condition, new BusinessException(errorCode, errorMessage));
    }
}
