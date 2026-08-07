package top.littlewin.codespark.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;


/**
 * 抽象代码文件保存器
 * @param <T>
 */
public abstract class CodeFileSaverTemplate<T> {

    // 文件保存根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @return 保存的目录
     */
    public final File saveCode(T result){

        // 1. 校验输入
        validateInput(result);

        // 2. 构建单一目录
        String baseDirPath = buildUniqueDir();

        // 3. 保存文件（具体实现交给子类）
        saveFiles(result, baseDirPath);

        // 4. 返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 保存单个文件
     *
     * @param dirpath 目录路径
     * @param filename 文件名
     * @param content 文件内容
     */
    public static void saveFile(String dirpath, String filename, String content){
        if (StrUtil.isNotBlank(content)){
            String filePath = dirpath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }


    /**
     * 校验输入参数
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_雪花ID
     *
     * @return 目录路径
     */
    protected String buildUniqueDir(){
        String codeType = genCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存文件（具体实现交给子类）
     *
     * @param result 代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);

    /**
     * 获取代码生成类型
     *
     * @return 代码生成类型枚举
     */
    protected abstract CodeGenTypeEnum genCodeType();
}
