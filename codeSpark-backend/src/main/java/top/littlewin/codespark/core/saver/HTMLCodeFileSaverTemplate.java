package top.littlewin.codespark.core.saver;

import cn.hutool.core.util.StrUtil;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

/**
 * HTML 代码保存器
 */
public class HTMLCodeFileSaverTemplate extends CodeFileSaverTemplate<HTMLCodeResult> {

    @Override
    protected CodeGenTypeEnum genCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFiles(HTMLCodeResult result, String baseDirPath) {
        saveFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    @Override
    protected void validateInput(HTMLCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空");
        }
    }


}
