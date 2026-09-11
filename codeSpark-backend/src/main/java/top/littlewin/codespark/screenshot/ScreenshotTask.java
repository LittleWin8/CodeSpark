package top.littlewin.codespark.screenshot;

/**
 * 截图任务
 *
 * @param appId  应用 ID
 * @param appUrl 部署后的可访问地址
 */
public record ScreenshotTask(Long appId, String appUrl) {
}
