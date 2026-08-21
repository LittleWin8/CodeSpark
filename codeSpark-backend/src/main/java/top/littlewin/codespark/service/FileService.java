package top.littlewin.codespark.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.model.enums.FileBizEnum;

import java.io.File;

/**
 * 文件存储业务服务：业务层对文件操作的唯一入口。
 *
 * 定位 = 应用服务层（介于 {业务 Controller/Service} 与 {存储门面 FileStorageFacade} 之间）：
 * - 把存储细节（操作类型枚举、入参模型、校验器）封装在实现内部，业务层只需表达业务语义；
 * - 内部统一委托 {@code FileStorageFacade} 完成存储，业务层感知不到具体存储方式（OSS / 本地）。
 */
public interface FileService {

    /**
     * 上传用户头像（含文件校验），返回存储标识（oss:... 或 local:...），失败返回 null
     *
     * @param userId 用户 ID
     * @param file   上传的头像文件
     * @return 存储标识，保存失败返回 null
     */
    String saveAvatar(Long userId, MultipartFile file);

    /**
     * 保存应用封面（含文件校验，供系统截图等已落盘文件使用），返回存储标识，失败返回 null
     *
     * @param appId 应用 ID
     * @param file  磁盘上已存在的封面文件
     * @return 存储标识，保存失败返回 null
     */
    String saveCover(Long appId, File file);

    /**
     * 将存储标识解析为对外可访问 URL（oss: → 预签名 URL；local: → 本地静态地址；未知 → null）
     *
     * @param storageKey 存储标识
     * @return 可访问 URL，解析失败返回 null
     */
    String resolveUrl(String storageKey);

    /**
     * 读取文件资源（本地静态访问端点用；OSS 对象通过签名 URL 直接访问）
     *
     * @param biz         业务类型
     * @param relativeKey 相对 key（yyyy/MM/dd/fileName，不含业务类型前缀）
     * @return 文件资源，不可访问返回 null
     */
    Resource loadFile(FileBizEnum biz, String relativeKey);

    /**
     * 删除存储对象（按存储标识路由：oss: → 删 OSS 对象；local: → 删本地文件）
     *
     * @param storageKey 存储标识
     */
    void delete(String storageKey);
}