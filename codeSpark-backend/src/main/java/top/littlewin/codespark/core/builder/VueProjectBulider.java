package top.littlewin.codespark.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
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
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return BuildResult.fail("package.json 文件不存在");
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
     * 执行 npm install 命令
     */
    private String executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        return executeCommand(projectDir, buildCommand("npm install"), 300); // 5分钟超时
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