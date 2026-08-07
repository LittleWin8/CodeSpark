package top.littlewin.codespark.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 文件保存器
 */
@Deprecated
public class CodeFileSaver {

    // 文件保存根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 保存 HTML 代码
     *
     * @param result 大模型生成的内容
     * @return
     */
    public static File saveHTMLCodeResult(HTMLCodeResult result){
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        saveFile(baseDirPath, "index.html", result.getHtmlCode());
        return new File(baseDirPath);
    }


    /**
     * 保存多文件代码
     *
     * @param result 大模型生成的内容
     * @return
     */
    public static File saveMutiFileCodeResult(MultiFileCodeResult result){
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        saveFile(baseDirPath, "index.html", result.getHtmlCode());
        saveFile(baseDirPath, "style.css", result.getCssCode());
        saveFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_雪花ID
     *
     * @param bizType
     * @return
     */
    private static String buildUniqueDir(String bizType){
        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存单个文件
     *
     * @param dirpath
     * @param filename
     * @param content
     */
    private static void  saveFile(String dirpath, String filename, String content){
        String filePath = dirpath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
