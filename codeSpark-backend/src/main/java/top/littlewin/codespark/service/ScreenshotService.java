package top.littlewin.codespark.service;

/**
 * 网页截图服务
 */
public interface ScreenshotService {

    /**
     * 生成网页截图并保存为应用封面
     * <p>
     * 优先上传 OSS：成功返回稳定标识（oss: 前缀 + 对象 key），读取时由输出层动态签名；
     * OSS 失败则本地回退到 tmp/app_cover/{appId}，返回可直接访问的本地 URL（永不失效）。
     *
     * @param url   应用访问 URL（预览地址）
     * @param appId 应用 ID（本地回退目录与 URL 需要）
     * @return 封面存储标识（oss:key 或本地 URL），失败抛异常
     */
    String generateAndUploadScreenshot(String url, Long appId);
}
