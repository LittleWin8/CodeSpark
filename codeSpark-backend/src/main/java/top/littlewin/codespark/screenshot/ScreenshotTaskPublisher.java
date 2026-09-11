package top.littlewin.codespark.screenshot;

/**
 * 截图任务发布器
 * <p>调用方只依赖该接口；当前为进程内队列实现，未来若需多实例/持久化可替换为 MQ 实现，调用方零改动。
 */
public interface ScreenshotTaskPublisher {

    /**
     * 投递截图任务（非阻塞，队列满则内部丢弃并记日志，不抛异常）
     */
    void publish(Long appId, String appUrl);
}
