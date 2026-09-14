package top.littlewin.codespark.ai.tools;

import cn.hutool.json.JSONObject;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 工具基类
 * 定义所有工具的通用接口，统一各工具的名称、展示与历史格式化逻辑。
 */
public abstract class BaseTool {

    /**
     * 项目内禁止 AI 触碰的顶层目录：
     * node_modules 里的文件会在构建期被加载执行（npm run build 实际跑的是
     * node_modules/.bin/vite，vite.config.js 又会 import @vitejs/plugin-vue），
     * 而 --ignore-scripts 只禁 npm 生命周期钩子、根目录配置清扫也不递归进来；
     * dist 与 .git 一并禁止，避免污染构建产物与版本库。
     */
    private static final Set<String> BLOCKED_TOP_DIRS = Set.of("node_modules", "dist", ".git");

    /**
     * 修复：把 AI 传的相对路径收敛到 code_output/vue_{appId} 内。
     * 拒绝绝对路径，normalize 后必须仍落在 root 内，否则抛 PARAMS_ERROR。
     */
    protected Path resolveInProject(Long appId, String rel) {

        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR, "非法路径");
        ThrowUtils.throwIf(rel == null || rel.isBlank(), ErrorCode.PARAMS_ERROR, "非法路径");
        ThrowUtils.throwIf(rel.contains("\0"), ErrorCode.PARAMS_ERROR, "非法路径");

        String trimmed = rel.trim();
        // 拒绝绝对路径（含 Unix / 与 Windows 盘符，Paths.isAbsolute 会覆盖）
        // 同时拒绝 ~ 开头（防以后有人做 home 目录展开）与 \ 开头
        if (trimmed.startsWith("~") || trimmed.startsWith("\\")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法路径");
        }

        Path relPath = Paths.get(trimmed);
        if (relPath.isAbsolute()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法路径");
        }

        Path root = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_" + appId)
                .toAbsolutePath().normalize();
        Path target = root.resolve(trimmed).normalize();

        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法路径");
        }
        // 修复：禁止写入 node_modules / dist / .git 等顶层目录。
        // target.getNameCount() > root.getNameCount() 时才存在 root 下的首段，
        // 等于 root 自身（如 rel="."）时跳过，避免 getName 越界。
        if (target.getNameCount() > root.getNameCount()
                && BLOCKED_TOP_DIRS.contains(target.getName(root.getNameCount()).toString())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法路径");
        }
        return target;
    }

    /**
     * 精确文件名黑名单（全小写）。注意 package-lock.json 既不以 .lock 结尾，
     * 也不能靠后缀覆盖，必须显式列出。
     */
    private static final Set<String> BLOCKED_EXACT_NAMES = Set.of(
            "package.json",
            ".npmrc",
            "package-lock.json",
            "npm-shrinkwrap.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            ".babelrc",
            ".babelrc.json",
            ".postcssrc",
            ".postcssrc.json"
    );

    /**
     * 修复：禁止 AI 写/改构建敏感文件（写了配合 npm install / vite build 即 RCE）。
     * 除包描述与 lockfile 外，还覆盖构建期会被 node 加载执行的配置模块
     * （postcss / tailwind / unocss / babel），--ignore-scripts 管不到这类加载。
     */
    protected boolean isBlockedWriteFile(Path target) {
        String fileName = target.getFileName() == null
                ? "" : target.getFileName().toString();
        return isBlockedWriteFileName(fileName);
    }

    /**
     * 同上规则的文件名版本（大小写不敏感），供构建器清扫项目根目录复用，
     * 与 isBlockedWriteFile 保持单点一致，避免两处黑名单漂移。
     */
    public static boolean isBlockedWriteFileName(String fileName) {
        if (fileName == null) {
            return true;
        }
        String name = fileName.toLowerCase();
        // 只取文件名部分，兼容偶发传入的相对路径
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (BLOCKED_EXACT_NAMES.contains(name)) {
            return true;
        }
        if (name.startsWith("vite.config.")
                || name.startsWith("postcss.config.")
                || name.startsWith("tailwind.config.")
                || name.startsWith("unocss.config.")
                || name.startsWith("uno.config.")
                || name.startsWith("babel.config.")
                || name.equals(".babelrc.js")
                || name.startsWith(".babelrc.")
                || name.startsWith(".postcssrc.")) {
            return true;
        }
        // *.lock / *-lock.json：yarn.lock、pnpm-lock 等
        return name.endsWith(".lock") || name.endsWith("-lock.json");
    }

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的显示名称
     * 默认由英文名推导（驼峰转空格并首字母大写，如 modifyFile -> Modify File）；
     * 子类可覆写为本地化名称（如"修改文件"），用于 [选择工具] 等用户可见标记
     *
     * @return 工具显示名称
     */
    public String getDisplayName() {
        String spaced = getToolName().replaceAll("([a-z])([A-Z])", "$1 $2");
        if (spaced.isEmpty()) {
            return spaced;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库/历史）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    /**
     * 从工具参数中提取前端展示用的文件路径（用于 SSE 工具事件 path 字段）；无路径返回 null
     *
     * @param arguments 工具调用参数
     * @return 文件相对路径，无路径返回 null
     */
    public abstract String getToolPath(JSONObject arguments);
}