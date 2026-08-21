package top.littlewin.codespark.core.saver;

import cn.hutool.core.util.StrUtil;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

/**
 * 多文件代码保存器
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    @Override
    protected CodeGenTypeEnum genCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        // 保存 HTML 文件
        saveFile(baseDirPath, "index.html", result.getHtmlCode());
        // 保存 CSS 文件
        saveFile(baseDirPath, "style.css", result.getCssCode());
        // 保存 JavaScript 文件
        saveFile(baseDirPath, "script.js", result.getJsCode());
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.CODE_CONTENT_EMPTY);
        }
    }
}
