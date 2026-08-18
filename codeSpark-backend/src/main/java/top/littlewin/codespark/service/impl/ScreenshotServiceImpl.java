package top.littlewin.codespark.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.manager.OssManager;
import top.littlewin.codespark.service.ScreenshotService;
import top.littlewin.codespark.utils.WebScreenshotUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private OssManager ossManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl, Long appId) {

        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "截图的网址为空");
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        log.info("开始生成网页截图，URL：{}，appId={}", webUrl, appId);

        // 本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "生成网页截图失败");

        try {
            // 1. 优先上传 OSS，成功返回稳定标识（oss: 前缀 + 对象 key），读取时动态签名
            String ossKey = uploadScreenshotToOss(localScreenshotPath);
            if (StrUtil.isNotBlank(ossKey)) {
                log.info("截图已上传至 OSS：key={}，封面存稳定标识，读取时动态签名", ossKey);
                return "oss:" + ossKey;
            }
            // 2. OSS 失败：本地回退到 app_cover/{appId}，返回可直接访问的本地 URL（永不失效）
            String localCoverUrl = saveCoverToLocal(localScreenshotPath, appId);
            if (StrUtil.isBlank(localCoverUrl)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传到对象存储失败，且本地回退失败");
            }
            log.warn("OSS 上传失败，已本地回退为封面：{}", localCoverUrl);
            return localCoverUrl;
        } finally {
            // 成功（OSS 有副本）或本地回退（cover 目录有副本）后清理临时截图，均不会丢失图片
            cleanuplocalFile(localScreenshotPath);
        }
    }

    /**
     * OSS 上传失败时本地回退：拷贝到 tmp/app_cover/{appId} 并返回可访问 URL
     *
     * @param localScreenshotPath 本地截图路径
     * @param appId               应用 ID
     * @return 可访问的本地 URL（/api/file/cover/{appId}/{fileName}），失败返回 null
     */
    private String saveCoverToLocal(String localScreenshotPath, Long appId) {
        try {
            String dirPath = AppConstant.APP_COVER_ROOT_DIR + File.separator + appId;
            FileUtil.mkdir(dirPath);
            String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
            FileUtil.copy(new File(localScreenshotPath), new File(dirPath + File.separator + fileName), true);
            // 与 FileStorageServiceImpl 返回的 URL 格式保持一致，前端/后端展示零差异
            return "/api/file/cover/" + appId + "/" + fileName;
        } catch (Exception e) {
            log.error("本地回退保存封面失败: appId={}, path={}", appId, localScreenshotPath, e);
            return null;
        }
    }

    /**
     * 上传截图到对象存储
     *
     * @param loadScreenshotPath 本地截图路径
     * @return 上传成功的对象 key，失败返回 null
     */
    private String uploadScreenshotToOss(String loadScreenshotPath){
        if (StrUtil.isBlank(loadScreenshotPath)){
            return null;
        }

        File screenshotFiles = new File(loadScreenshotPath);

        if (!screenshotFiles.exists()){
            log.error("截图不存在：{}", loadScreenshotPath);
            return null;
        }

        // 生成 OSS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String ossKey = generateScreenshotKey(fileName);
        String ossUrl = ossManager.upLoadFile(ossKey, screenshotFiles);
        return StrUtil.isBlank(ossUrl) ? null : ossKey;
    }

    /**
     * 生成截图的对象存储键
     * 统一存到 cover/ 前缀下（cover/yyyy/MM/dd/xxx_compressed.jpg）
     *
     * @param fileName 文件名
     * @return
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("cover/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     * @param localFilePath 本地文件路径
     */
    private void  cleanuplocalFile(String localFilePath){
        File localFile = new File(localFilePath);
        if (localFile.exists()){
            FileUtil.del(localFile);
            log.info("清理本地文件成功：{}", localFilePath);
        }
    }
}
