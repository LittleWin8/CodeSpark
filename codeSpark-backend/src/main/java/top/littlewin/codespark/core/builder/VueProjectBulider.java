package top.littlewin.codespark.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import top.littlewin.codespark.ai.tools.BaseTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 构建 VUE 项目
 */
@Slf4j
@Component
public class VueProjectBulider {

    /**
     * 修复：package.json / vite.config.js 由服务端可信模板统一提供，
     * AI（经 writeFile，已被 P0-2 禁止）不得决定依赖与构建脚本。
     * 模板与 codegen-vue-system-prompt.txt 的参考配置保持一致。
     */
    private static final String TRUSTED_PACKAGE_JSON = """
            {
              "name": "codespark-vue-app",
              "version": "1.0.0",
              "private": true,
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "vue": "^3.3.4",
                "vue-router": "^4.2.4"
              },
              "devDependencies": {
                "@vitejs/plugin-vue": "^4.2.3",
                "vite": "^4.4.5"
              }
            }
            """;

    private static final String TRUSTED_VITE_CONFIG_JS = """
            import { defineConfig } from 'vite'
            import vue from '@vitejs/plugin-vue'
            import { fileURLToPath, URL } from 'node:url'
            
            // 由平台统一提供，AI 不得修改：base './' 支持子路径部署，hash 路由无需服务端重写
            export default defineConfig({
              base: './',
              plugins: [vue()],
              resolve: {
                alias: {
                  '@': fileURLToPath(new URL('./src', import.meta.url))
                }
              }
            })
            """;

    /**
     * 私有 npm 源（可选）。为空则用 npm 默认源；
     * 生产建议设为内网私有源并在源侧做依赖白名单。
     */
    @Value("${codespark.build.npm-registry:}")
    private String npmRegistry;

    /**
     * 异步构建 Vue 项目，构建结束（成功/失败）后回调 onComplete
     *
     * @param projectPath 项目路径
     * @param onComplete  构建完成回调（成功或失败都会回调，可为 null）
     */
    public void buildProjectAsync(String projectPath, Consumer<BuildResult> onComplete){
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    BuildResult result;
                    try {
                        result = buildProject(projectPath);
                    } catch (Exception e){
                        log.error("异步构建 Vue 项目时发生异常：{}", e.getMessage(), e);
                        result = BuildResult.fail("构建异常: " + e.getMessage());
                    }
                    if (onComplete != null) {
                        onComplete.accept(result);
                    }
                });
    }

    /**
     * 构建 Vue 项目。
     *
     * 若 dist 已存在且比源码新（上次构建产物仍在、源码未改动），直接复用跳过构建，避免重复
     * npm install / build 消耗时间与资源；否则清空旧 dist 后重新构建，保证构建期间/失败时
     * 预览地址处于 404，避免旧产物误展示。
     *
     * @param projectPath 项目根目录路径
     * @return 构建结果（成功与否 + 失败原因）
     */
    public BuildResult buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return BuildResult.fail("项目目录不存在: " + projectPath);
        }

        // 修复：构建前强制用可信模板覆写 package.json / vite.config.js，
        // AI 写入的（或缺失的）构建描述一律作废；同时删掉 AI 可能留下的
        // lockfile / .npmrc，防止 pin 恶意 tarball 或改源。
        try {
            ensureTrustedBuildFiles(projectDir);
        } catch (Exception e) {
            log.error("写入可信构建模板失败: {}", e.getMessage(), e);
            return BuildResult.fail("写入可信构建模板失败");
        }
        File distDir = new File(projectDir, "dist");
        // 复用已是最新的 dist，跳过构建
        if (distDir.exists() && isDistUpToDate(projectDir, distDir)) {
            log.info("dist 已是最新，跳过构建: {}", projectPath);
            return BuildResult.success();
        }
        // 需要重建：先清空旧 dist，保证构建期间/失败时预览地址为 404，避免旧产物误展示
        FileUtil.del(distDir);
        log.info("开始构建 Vue 项目: {}", projectPath);
        // 执行 npm install
        String installError = executeNpmInstall(projectDir);
        if (installError != null) {
            log.error("npm install 执行失败: {}", installError);
            return BuildResult.fail("npm install 执行失败: " + installError);
        }
        // 执行 npm run build
        String buildError = executeNpmBuild(projectDir);
        if (buildError != null) {
            log.error("npm run build 执行失败: {}", buildError);
            return BuildResult.fail("npm run build 执行失败: " + buildError);
        }
        // 验证 dist 目录是否生成
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return BuildResult.fail("构建完成但 dist 目录未生成");
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return BuildResult.success();
    }

    /**
     * 判断 dist 是否已是最新（dist 的最后修改时间不早于所有源码文件的最大修改时间）
     *
     * 源码目录排除 node_modules / dist / .git，避免 npm install 等无关文件误判为"源码改动"。
     */
    private boolean isDistUpToDate(File projectDir, File distDir) {
        // dist文件夹最后修改时间
        long distLastModified = distDir.lastModified();
        long maxSourceModified = maxSourceModified(projectDir);
        return maxSourceModified <= distLastModified;
    }

    /**
     * 递归计算源码目录内所有文件的最后修改时间最大值（排除 node_modules / dist / .git）
     */
    private long maxSourceModified(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0L;
        }
        long max = 0L;
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                if ("node_modules".equals(name) || "dist".equals(name) || ".git".equals(name)) {
                    continue;
                }
                max = Math.max(max, maxSourceModified(child));
            } else {
                max = Math.max(max, child.lastModified());
            }
        }
        return max;
    }


    /**
     * 修复：用可信模板覆写构建描述文件，并清扫项目根目录下 AI 残留的
     * 构建期文件（lockfile / .npmrc / 会被 vite/postcss/babel 加载的配置模块）。
     * 只扫根目录一层，不递归，避免误伤 src/** 下的业务代码；只删文件，不碰目录。
     */
    private void ensureTrustedBuildFiles(File projectDir) {

        // 1. 判断 package.json 与 vite.config.js 模板是否一致，不一致则用模板覆盖
        writeIfChanged(new File(projectDir, "package.json"), TRUSTED_PACKAGE_JSON);
        writeIfChanged(new File(projectDir, "vite.config.js"), TRUSTED_VITE_CONFIG_JS);

        // 2. 清除项目下其他配置文件
        File[] children = projectDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isFile() || isTrustedBuildFile(child.getName())) {
                continue;
            }
            if (BaseTool.isBlockedWriteFileName(child.getName())) {
                FileUtil.del(child);
                log.info("已删除 AI 残留构建文件: {}", child.getAbsolutePath());
            }
        }
    }

    /** 刚写入的可信模板本身也在黑名单里，清扫时必须排除。 */
    private boolean isTrustedBuildFile(String fileName) {
        return fileName.equalsIgnoreCase("package.json")
                || fileName.toLowerCase().startsWith("vite.config.");
    }

    /**
     * 内容一致就不写，避免无条件覆写刷新 mtime。
     * isDistUpToDate 靠文件 mtime 判断 dist 是否最新，每次都重写会导致
     * “dist 已是最新，跳过构建”分支永久失效，每轮对话都全量重建。
     */
    private void writeIfChanged(File file, String content) {
        if (file.isFile()
                && content.equals(FileUtil.readString(file, StandardCharsets.UTF_8))) {
            return;
        }
        FileUtil.writeString(content, file, StandardCharsets.UTF_8);
    }

    /**
     * 执行 npm install 命令。
     * 修复：--ignore-scripts 禁掉所有 lifecycle 脚本（postinstall 等即 RCE 点），
     * --no-audit --no-fund 减少出站请求；命令串由服务端常量组装，不含 AI 输入。
     */
    private String executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        StringBuilder command = new StringBuilder("npm install --ignore-scripts --no-audit --no-fund");
        if (StrUtil.isNotBlank(npmRegistry)) {
            command.append(" --registry=").append(npmRegistry.trim());
        }
        return executeCommand(projectDir, buildCommand(command.toString()), 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private String executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        return executeCommand(projectDir, buildCommand("npm run build"), 180); // 3分钟超时
    }

    /**
     * 根据操作系统组装可直接用于 ProcessBuilder 的指令数组。
     * Windows 下 npm 需用 npm.cmd，并经 cmd.exe /c 执行；类 Unix 下经 sh -c 执行。
     * 注意：依赖进程 PATH 中能找到 npm/node（nvm 用户需保证启动后端的环境已加载 nvm）。
     *
     * @param command 完整命令字符串，如 "npm install" / "npm run build"
     * @return 可直接传给 ProcessBuilder 的指令数组
     */
    private String[] buildCommand(String command) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        if (isWindows) {
            String winCommand = command;
            if (winCommand.equals("npm") || winCommand.startsWith("npm ")) {
                winCommand = "npm.cmd" + winCommand.substring("npm".length());
            }
            return new String[]{"cmd.exe", "/c", winCommand};
        }
        return new String[]{"sh", "-c", command};
    }

    /**
     * 在指定目录执行 shell 命令，同步等待执行完成（带超时）。
     *
     * @param workingDir     工作目录（项目根目录）
     * @param cmdArray       buildCommand 组装好的指令数组
     * @param timeoutSeconds 超时秒数
     * @return 成功返回 null，失败返回错误信息（超时 / 非零退出码 / IO 异常）
     */
    private String executeCommand(File workingDir, String[] cmdArray, int timeoutSeconds) {

        // 1. 检查命令
        if (cmdArray == null || cmdArray.length == 0) {
            return "执行命令为空";
        }
        log.info("执行命令: {}，工作目录: {}，超时: {}s", Arrays.toString(cmdArray), workingDir.getAbsolutePath(), timeoutSeconds);

        // 2. 执行命令
        ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);
        processBuilder.directory(workingDir);
        // 合并标准输出和错误输出，便于统一采集日志和排查
        processBuilder.redirectErrorStream(true);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            log.error("启动命令失败: {}", e.getMessage(), e);
            return "启动命令失败: " + e.getMessage();
        }

        // 用虚拟线程持续消费进程输出，避免输出缓冲区写满导致进程阻塞；
        // 同时把日志实时打印，方便排查 npm install / build 失败原因
        StringBuilder output = new StringBuilder();
        Thread readerThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append(System.lineSeparator());
                    }
                    log.info("[vue-build] {}", line);
                }
            } catch (IOException e) {
                log.warn("读取命令输出异常: {}", e.getMessage());
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "命令执行被中断: " + Arrays.toString(cmdArray);
        }
        if (!finished) {
            process.destroyForcibly();
            log.error("命令执行超时（超过 {} 秒）: {}", timeoutSeconds, Arrays.toString(cmdArray));
            return "命令执行超时（超过 " + timeoutSeconds + " 秒）: " + Arrays.toString(cmdArray);
        }
        // 等输出消费线程收尾，最多再等 5 秒
        try {
            readerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String outputStr;
            synchronized (output) {
                outputStr = output.toString();
            }
            // 只保留尾部日志，避免错误信息过长
            String tail = StrUtil.maxLength(outputStr.trim(), 2000);
            log.error("命令执行失败，退出码: {}，命令: {}，输出: {}", exitCode, Arrays.toString(cmdArray), tail);
            return "命令执行失败（退出码 " + exitCode + "）: " + tail;
        }
        return null;
    }

}