package top.littlewin.codespark.exception;

import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.ResultUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        // 限流拒绝是预期内的高频事件，不打全栈，避免刷屏
        if (e.getCode() == ErrorCode.TOO_MANY_REQUEST.getCode()) {
            log.warn("请求被限流拒绝: {}", e.getMessage());
        } else {
            log.error("BusinessException", e);
        }
        // SSE 请求的错误走事件流（EventSource 读不到普通 JSON），已直接写响应时返回 null
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        // 对于普通请求，返回标准 JSON 响应
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * SSE 请求的错误响应。
     * <p>
     * EventSource 只认 {@code event:}/{@code data:} 行，普通 JSON 会被浏览器静默丢弃，
     * 因此错误以 {@code business-error} 命名事件发出（避开与连接失败的 {@code error} 事件冲突）。
     * 注意不再补发 {@code done}：前端收到 {@code business-error} 即视为终态关闭，
     * 若补 {@code done} 会误触发成功收尾（刷预览/订阅构建流）。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息（可为 null，内部兜底空串）
     * @return true 表示是 SSE 请求并已处理，false 表示不是 SSE 请求
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null || response.isCommitted()) {
            return false;
        }
        // 判断是否是 SSE 请求（通过 Accept 头或 URL 路径，覆盖对话流与构建流）
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        if ((accept != null && accept.contains("text/event-stream")) ||
            uri.contains("/chat/gen/code") || uri.contains("/build/stream")) {
            try {
                // 设置 SSE 响应头（HTTP 状态保持 200：非 200 时 EventSource 直接按连接失败处理，照样读不到）
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                // 构造错误消息的 SSE 格式（Map.of 不接受 null value，message 为空时兜底空串）
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("error", true);
                errorData.put("code", errorCode);
                errorData.put("message", errorMessage == null ? "" : errorMessage);
                String sseData = "event: business-error\ndata: " + JSONUtil.toJsonStr(errorData) + "\n\n";
                response.getWriter().write(sseData);
                response.getWriter().flush();
                // 表示已处理 SSE 请求（响应已提交，调用方返回 null 即可，Spring 不再写 body）
                return true;
            } catch (IOException ioException) {
                log.error("Failed to write SSE error response", ioException);
                // 即使写入失败，也表示这是 SSE 请求
                return true;
            }
        }
        return false;
    }
}
