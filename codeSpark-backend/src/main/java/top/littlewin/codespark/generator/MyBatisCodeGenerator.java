package top.littlewin.codespark.generator;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.mybatisflex.codegen.dialect.IDialect;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mybatis Flex 代码生成器
 */
public class MyBatisCodeGenerator {

    // 需要生成的表名
    private static final String[] TABLE_NAMES = {"user_token_usage", "user_quota_usage"};

    // 匹配 ${KEY:default} 占位符
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");

    public static void main(String[] args) {
        // 获取数据源信息
        Dict dict = YamlUtil.loadByPath("application.yml");
        Map<String, Object> dataSourceConfig = dict.getByPath("spring.datasource");
        // 生成器不经过 Spring，占位符需手动解析
        String url = resolvePlaceholder(String.valueOf(dataSourceConfig.get("url")));
        String username = resolvePlaceholder(String.valueOf(dataSourceConfig.get("username")));
        String password = resolvePlaceholder(String.valueOf(dataSourceConfig.get("password")));
        String driverClassName = String.valueOf(dataSourceConfig.get("driver-class-name"));

        // 配置数据源
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        // 创建配置内容
        GlobalConfig globalConfig = createGlobalConfig();

        // 通过 datasource 和 globalConfig 创建代码生成器（PostgreSQL 必须指定方言，否则无法识别主键）
        Generator generator = new Generator(dataSource, globalConfig, IDialect.POSTGRESQL);

        // 生成代码
        generator.generate();
    }

    // 详细配置见：https://mybatis-flex.com/zh/others/codegen.html
    public static GlobalConfig createGlobalConfig() {
        // 创建配置内容
        GlobalConfig globalConfig = new GlobalConfig();

        // 设置根包，建议先生成到一个临时目录下，生成代码后，再移动到项目目录下
        globalConfig.getPackageConfig()
                .setBasePackage("top.littlewin.codespark.genresult");


        // 设置表前缀和只生成哪些表，setGenerateTable 未配置时，生成所有表
        globalConfig.getStrategyConfig()
                .setGenerateTable(TABLE_NAMES)
                .setGenerateSchema("public")
                // 设置逻辑删除的默认字段名称
                .setLogicDeleteColumn("isDelete");

        // 设置生成 entity 并启用 Lombok
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21);

        // 设置生成 mapper
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();

        // 设置生成 service
        globalConfig.enableService();
        globalConfig.enableServiceImpl();

        // 设置生成 controller
        globalConfig.enableController();

        // 设置生成时间和字符串为空，避免多余的代码改动
        globalConfig.getJavadocConfig()
                .setAuthor("<a href=\"https://github.com/LittleWin8\">小稳</a>")
                .setSince("");
        return globalConfig;
    }

    /**
     * 解析 ${KEY:default} 占位符
     * <p>
     * 生成器通过 YamlUtil 直读 YAML，不经过 Spring 的占位符解析，
     * 因此需要手动解析：优先取环境变量/系统属性，取不到用默认值。
     *
     * @param value 原始配置值，如 ${DB_URL:jdbc:postgresql://localhost:5432/codespark}
     * @return 解析后的实际值
     */
    private static String resolvePlaceholder(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            String resolved = System.getenv(key);
            if (resolved == null) {
                resolved = System.getProperty(key);
            }
            if (resolved == null) {
                resolved = defaultValue != null ? defaultValue : "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
