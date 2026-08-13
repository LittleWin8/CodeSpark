package top.littlewin.codespark.core;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.ai.AICodeGeneratorService;
import top.littlewin.codespark.ai.model.AppNameResult;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.service.AppService;

/**
 * 应用名称生成器：AI 生成 + 截取兜底
 */
@Slf4j
@Service
public class AppNameGenerator {

    /** 应用名称最大长度，防止 AI 输出超长内容 */
    private static final int MAX_NAME_LENGTH = 20;

    @Resource
    private AICodeGeneratorService aiCodeGeneratorService;

    @Resource
    private AppService appService;

    /**
     * AI 根据提示词自动生成 App 名称
     *
     * @param userMessage 用户提示词
     * @return 应用名称（AI 生成失败时降级为截取提示词前 12 位）
     */
    public String genAppName(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return "未命名应用";
        }
        try {
            AppNameResult result = aiCodeGeneratorService.generateAppName(userMessage);
            String name = result != null ? result.getAppName() : null;
            if (StrUtil.isNotBlank(name)) {
                name = name.trim().replace("\"", "").replace("'", "");
                // 限制名称长度，防止 AI 输出超长内容
                return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
            }
            log.warn("AI 生成应用名称为空，降级为截取提示词");
        } catch (Exception e) {
            log.error("AI 生成应用名称失败，降级为截取提示词", e);
        }
        return userMessage.substring(0, Math.min(userMessage.length(), MAX_NAME_LENGTH));
    }

    /**
     * 异步生成并更新应用名称（不阻塞创建请求）
     *
     * @param appId      应用 ID
     * @param userMessage 用户提示词
     */
    @Async
    public void updateAppNameAsync(Long appId, String userMessage) {
        try {
            String name = genAppName(userMessage);
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setAppName(name);
            appService.updateById(updateApp);
            log.info("异步更新应用名称成功: appId={}, appName={}", appId, name);
        } catch (Exception e) {
            log.error("异步更新应用名称失败: appId={}", appId, e);
        }
    }
}
