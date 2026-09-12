package top.littlewin.codespark.preset;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.constant.AppConstant;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 预置产物落盘：目录规则与真实生成一致（CODE_OUTPUT_ROOT_DIR/{codeGenType}_{appId}）
 */
@Slf4j
@Component
public class PresetFileWriter {

    public File write(String codeGenType, Long appId, Map<String, String> files) {
        File root = new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType + "_" + appId);
        if (root.exists()) {
            FileUtil.del(root);
        }
        FileUtil.mkdir(root);
        files.forEach((relativePath, content) -> {
            File target = new File(root, relativePath);
            File parent = target.getParentFile();
            if (parent != null) {
                FileUtil.mkdir(parent);
            }
            FileUtil.writeString(content, target, StandardCharsets.UTF_8);
        });
        log.info("预置产物落盘完成: appId={}, dir={}, files={}", appId, root.getAbsolutePath(), files.size());
        return root;
    }
}
