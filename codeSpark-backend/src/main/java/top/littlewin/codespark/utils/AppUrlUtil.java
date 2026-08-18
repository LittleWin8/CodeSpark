package top.littlewin.codespark.utils;

import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

/**
 * 应用访问 URL 构造工具（与前端 src/utils/url.ts 的 getPreviewUrl 保持一致）
 */
public class AppUrlUtil {

    private AppUrlUtil() {
    }

    /**
     * 生成代码预览地址（供后端截图/内部访问使用）
     *
     * @param codeGenType 生成类型（html / multi_file / vue）
     * @param appId       应用 ID
     * @return 预览 URL；VUE 指向构建产物 dist/index.html（vite base './'，资源为相对路径）
     */
    public static String buildPreviewUrl(CodeGenTypeEnum codeGenType, Long appId) {
        String dirName = codeGenType.getValue() + "_" + appId;
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            return AppConstant.PREVIEW_BASE_URL + "/" + dirName + "/dist/index.html";
        }
        return AppConstant.PREVIEW_BASE_URL + "/" + dirName + "/";
    }
}