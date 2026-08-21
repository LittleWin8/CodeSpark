package top.littlewin.codespark.store.strategy;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.model.enums.FileBizEnum;

import java.io.File;
import java.util.Comparator;
import java.util.List;

/**
 * 文件存储流程编排执行器。
 *
 * 职责：
 *   按优先级持有全部 {@link StorageStrategy}（Spring 按  @Order 注入，OSS 优先、本地兜底，
 *   未来可扩展 MinIO / S3 等策略；
 *   统一编排「保存」流程：依次调用可用策略，捕获存储异常 / 空结果并自动降级到下一策略；
 *   统一路由「URL 解析 / 读取 / 删除」：按存储标识前缀找到归属策略后委托执行；
 *   不含任何业务逻辑，业务层只与门面交互，感知不到具体存储方式。
 */
@Component
@Slf4j
public class StorageExecutor {

    /** 有序策略列表：由 Spring 按 @Order 注入（OSS 最高优先级 → 本地最低优先级做兜底） */
    private final List<StorageStrategy> strategies;

    public StorageExecutor(List<StorageStrategy> strategies) {
        // 防御性排序：即使某策略实现未标注 @Order，也按声明顺序兜底（未标注视为最低优先级）
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(StorageExecutor::orderOf))
                .toList();
    }

    /** 读取策略的排序值：取 @Order 注解值，缺省视为最低优先级 */
    private static int orderOf(StorageStrategy strategy) {
        Order order = strategy.getClass().getAnnotation(Order.class);
        return order != null ? order.value() : Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 保存上传文件（MultipartFile）：
     * 依次尝试可用策略，OSS 优先；OSS 异常或失败时自动降级到本地，全部失败返回 null
     *
     * @param biz     业务类型
     * @param ownerId 归属 ID
     * @param file    上传文件
     * @return 存储标识（oss:... 或 local:...），全部策略失败返回 null
     */
    public String saveFile(FileBizEnum biz, Long ownerId, MultipartFile file) {
        return doSave(biz, ownerId, strategy -> strategy.saveFile(biz, ownerId, file));
    }

    /**
     * 保存已落盘文件（File）
     *
     * @param biz     业务类型
     * @param ownerId 归属 ID
     * @param file    本地文件
     * @return 存储标识，全部策略失败返回 null
     */
    public String saveFile(FileBizEnum biz, Long ownerId, File file) {
        return doSave(biz, ownerId, strategy -> strategy.saveFile(biz, ownerId, file));
    }

    /**
     * 将存储标识解析为对外可访问 URL（按前缀路由到归属策略）：
     * - oss: → 预签名 URL；local: → 本地静态访问 URL；未知格式 → null（前端占位图兜底）
     *
     * @param storageKey 存储标识
     * @return 可访问 URL，解析失败返回 null
     */
    public String resolveUrl(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return null;
        }
        for (StorageStrategy strategy : strategies) {
            if (strategy.supports(storageKey)) {
                try {
                    return strategy.resolveUrl(storageKey);
                } catch (Exception e) {
                    log.warn("解析存储 URL 异常: strategy={}, storageKey={}",
                            strategy.getClass().getSimpleName(), storageKey, e);
                    return null;
                }
            }
        }
        log.warn("未知存储标识，无法解析 URL: {}", storageKey);
        return null;
    }

    /**
     * 读取文件资源（静态访问用）：依次尝试各策略，返回第一个可访问的资源，全部不可访问返回 null
     *
     * @param biz         业务类型
     * @param relativeKey 相对 key（yyyy/MM/dd/fileName，不含业务类型前缀）
     * @return 文件资源，不可访问返回 null
     */
    public Resource loadFile(FileBizEnum biz, String relativeKey) {
        for (StorageStrategy strategy : strategies) {
            try {
                Resource resource = strategy.loadFile(biz, relativeKey);
                if (resource != null) {
                    return resource;
                }
            } catch (Exception e) {
                log.warn("读取存储资源异常: strategy={}, biz={}, relativeKey={}",
                        strategy.getClass().getSimpleName(), biz.getValue(), relativeKey, e);
            }
        }
        return null;
    }

    /**
     * 按存储标识前缀路由删除（oss: → 删 OSS 对象；local: → 删本地文件；未知标识忽略）
     *
     * @param storageKey 存储标识
     */
    public void delete(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return;
        }
        for (StorageStrategy strategy : strategies) {
            if (strategy.supports(storageKey)) {
                try {
                    strategy.delete(storageKey);
                } catch (Exception e) {
                    log.warn("删除存储对象异常: strategy={}, storageKey={}",
                            strategy.getClass().getSimpleName(), storageKey, e);
                }
                return;
            }
        }
        log.warn("未知存储标识，跳过删除: {}", storageKey);
    }

    /**
     * 首选存储策略（当前为 OSS）是否可用：供运维 / 定时同步任务判断，业务层不应依赖具体存储方式
     *
     * @return true 表示最高优先级策略可用
     */
    public boolean isPrimaryStrategyAvailable() {
        return !strategies.isEmpty() && strategies.get(0).isAvailable();
    }

    /**
     * 保存编排主流程：依次调用可用策略，捕获异常 / 空结果并自动降级
     */
    private String doSave(FileBizEnum biz, Long ownerId, StorageSaver saver) {
        for (StorageStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                log.debug("存储策略不可用，跳过: {}", strategy.getClass().getSimpleName());
                continue;
            }
            try {
                String key = saver.save(strategy);
                if (StrUtil.isNotBlank(key)) {
                    log.debug("文件保存成功: strategy={}, key={}", strategy.getClass().getSimpleName(), key);
                    return key;
                }
                log.warn("存储策略保存返回空，降级下一策略: biz={}, ownerId={}, strategy={}",
                        biz.getValue(), ownerId, strategy.getClass().getSimpleName());
            } catch (Exception e) {
                // OSS 服务异常/不可用时捕获并自动降级到本地存储
                log.error("存储策略保存异常，降级下一策略: biz={}, ownerId={}, strategy={}",
                        biz.getValue(), ownerId, strategy.getClass().getSimpleName(), e);
            }
        }
        log.error("所有存储策略均保存失败: biz={}, ownerId={}", biz.getValue(), ownerId);
        return null;
    }

    /** 保存动作抽象：复用同一套降级编排 */
    @FunctionalInterface
    private interface StorageSaver {
        String save(StorageStrategy strategy);
    }
}