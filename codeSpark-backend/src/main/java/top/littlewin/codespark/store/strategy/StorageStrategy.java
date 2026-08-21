package top.littlewin.codespark.store.strategy;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.model.enums.FileBizEnum;

import java.io.File;

/**
 * 文件存储策略统一接口
 *
 * 每一种策略负责一种具体的存储后端（如阿里云 OSS、本地磁盘，未来可扩展 MinIO、S3 等）
 * 职责边界：
 *   只负责自身存储后端的「保存 / 解析 URL / 读取 / 删除」实现；
 * 存储标识为两态、格式统一（与 OSS 对象 key 结构对齐）：
 *   OSS：oss:biz/yyyy/MM/dd/fileName（如 oss:cover/2026/08/19/xxx.jpg）
 *   本地：local:biz/yyyy/MM/dd/fileName（如 local:avatar/2026/08/19/xxx.jpg
 */
public interface StorageStrategy {

    /**
     * 该策略对应的存储标识前缀（如 oss: / local:），用于日志与标识路由
     *
     * @return 前缀常量
     */
    String getKeyPrefix();

    /**
     * 该策略当前是否可用：
     * - OSS：开启开关 + AccessKey 已配置（运行时故障由执行器捕获并降级）；
     * - 本地：恒可用。
     *
     * @return true 表示可参与本轮保存尝试
     */
    boolean isAvailable();

    /**
     * 该策略能否处理此存储标识（按前缀匹配，供 URL 解析 / 删除路由使用）
     *
     * @param storageKey 存储标识
     * @return true 表示该标识归属本策略
     */
    boolean supports(String storageKey);

    /**
     * 保存上传文件（MultipartFile），返回稳定存储标识，失败返回 null
     *
     * @param biz     业务类型（头像/封面）
     * @param ownerId 归属 ID（头像为用户 ID，封面为应用 ID，仅日志/标识溯源用）
     * @param file    上传文件
     * @return 存储标识（oss:... 或 local:...），失败返回 null
     */
    String saveFile(FileBizEnum biz, Long ownerId, MultipartFile file);

    /**
     * 保存已落盘文件（如截图临时文件），返回稳定存储标识，失败返回 null
     *
     * @param biz     业务类型
     * @param ownerId 归属 ID
     * @param file    本地文件
     * @return 存储标识，失败返回 null
     */
    String saveFile(FileBizEnum biz, Long ownerId, File file);

    /**
     * 将存储标识解析为对外可访问 URL：
     *
     * @param storageKey 存储标识
     * @return 可访问 URL，解析失败返回 null
     */
    String resolveUrl(String storageKey);

    /**
     * 读取文件资源（静态访问用，带路径穿越防护），不可访问返回 null
     * OSS 对象通过签名 URL 直接访问，本方法对 OSS 返回 null
     *
     * @param biz         业务类型
     * @param relativeKey 相对 key（yyyy/MM/dd/fileName，不含业务类型前缀）
     * @return 文件资源，不可访问返回 null
     */
    Resource loadFile(FileBizEnum biz, String relativeKey);

    /**
     * 删除存储对象（按自身标识类型处理，失败仅记日志）
     *
     * @param storageKey 存储标识
     */
    void delete(String storageKey);
}