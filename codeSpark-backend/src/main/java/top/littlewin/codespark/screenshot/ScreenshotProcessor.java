package top.littlewin.codespark.screenshot;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.service.FileService;

import java.io.File;

/**
 * 单任务处理流水线：截图 → 上传封面 → 回写 app.cover
 * <p>与队列/worker 解耦，便于单独测试。
 */
@Slf4j
@Component
public class ScreenshotProcessor {

    @Resource
    private FileService fileService;

    /**
     * 处理一个截图任务
     *
     * @param task   任务
     * @param driver 执行截图的驱动（由 worker 独占持有）
     * @throws org.openqa.selenium.WebDriverException 浏览器会话异常（由 worker 重建 driver）
     */
    public void process(ScreenshotTask task, ScreenshotDriver driver) {
        ThrowUtils.throwIf(task == null || task.appId() == null, ErrorCode.PARAMS_ERROR,
                ErrorMessage.APP_ID_REQUIRED);
        ThrowUtils.throwIf(StrUtil.isBlank(task.appUrl()), ErrorCode.PARAMS_ERROR,
                ErrorMessage.EMPTY_FILE_URL);

        // 1. 本地截图
        String localScreenshotPath = driver.saveWebPageScreenshot(task.appUrl());
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR,
                ErrorMessage.SCREENSHOT_GENERATE_FAILED);

        // 2. 上传为封面（OSS 优先，失败本地回退），并清理临时截图
        String cover;
        try {
            cover = fileService.saveCover(task.appId(), new File(localScreenshotPath));
        } finally {
            File localFile = new File(localScreenshotPath);
            if (localFile.exists()) {
                FileUtil.del(localFile);
            }
        }
        if (StrUtil.isBlank(cover)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, ErrorMessage.FILE_SAVE_FAILED);
        }

        // 3. 回写应用封面
        boolean updated = UpdateChain.of(App.class)
                .set(App::getCover, cover)
                .where(App::getId).eq(task.appId())
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, ErrorMessage.UPDATE_COVER_FAILED);
        log.info("应用封面截图更新成功: appId={}, cover={}", task.appId(), cover);
    }
}
