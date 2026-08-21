package top.littlewin.codespark.service.impl;

import jakarta.annotation.Resource;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.service.FileService;
import top.littlewin.codespark.service.ScreenshotService;
import top.littlewin.codespark.utils.WebScreenshotUtils;

import java.io.File;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private FileService fileService;

    @Override
    public String generateAndUploadScreenshot(String webUrl, Long appId) {

        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_FILE_URL);
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR, ErrorMessage.APP_ID_REQUIRED);
        log.info("开始生成网页截图，URL：{}，appId={}", webUrl, appId);

        // 本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, ErrorMessage.SCREENSHOT_GENERATE_FAILED);

        try {
            // 走文件业务服务统一保存（内含校验；OSS 可用走 OSS，失败自动回退本地）
            String cover = fileService.saveCover(appId, new File(localScreenshotPath));
            if (StrUtil.isBlank(cover)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, ErrorMessage.FILE_SAVE_FAILED);
            }
            log.info("截图封面保存成功: appId={}, cover={}", appId, cover);
            return cover;
        } finally {
            // 封面已有副本（OSS 或本地），清理临时截图，不会丢失图片
            cleanuplocalFile(localScreenshotPath);
        }
    }

    /**
     * 清理本地文件
     * @param localFilePath 本地文件路径
     */
    private void cleanuplocalFile(String localFilePath){
        File localFile = new File(localFilePath);
        if (localFile.exists()){
            FileUtil.del(localFile);
            log.info("清理本地文件成功：{}", localFilePath);
        }
    }
}
