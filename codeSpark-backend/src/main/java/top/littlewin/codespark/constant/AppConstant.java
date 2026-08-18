package top.littlewin.codespark.constant;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * 应用生成预览基础地址（后端截图访问用，与前端 src/utils/url.ts 的 PREVIEW_BASE_URL 对应；
     * 可通过环境变量 PREVIEW_BASE_URL 覆盖，默认本地 8080 + /api 上下文）
     */
    String PREVIEW_BASE_URL = System.getenv().getOrDefault("PREVIEW_BASE_URL", "http://localhost:8080/api/static");

    /**
     * 应用封面上传目录：tmp/app_cover/{appId}
     */
    String APP_COVER_ROOT_DIR = System.getProperty("user.dir") + "/tmp/app_cover";

}
