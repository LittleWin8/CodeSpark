package top.littlewin.codespark.job;

import jakarta.annotation.Resource;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.manager.OssManager;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.FileBizEnum;
import top.littlewin.codespark.service.AppService;
import top.littlewin.codespark.service.FileService;
import top.littlewin.codespark.service.UserService;
import top.littlewin.codespark.store.strategy.LocalStorageStrategy;
import top.littlewin.codespark.store.strategy.OssStorageStrategy;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地资源 → OSS 定时同步任务（绑定 aliyun.oss.enabled 开关：默认关闭，任务 Bean 不注册）。
 * <p>
 * 正向迁移：DB 中仍是本地标识（local:...）的封面/头像 → 上传 OSS → 更新 DB 为 oss: 标识 → 删除本地文件。
 * 严格顺序：上传成功 → 改库成功 → 才删本地；任一步失败保留原样，下轮重试（重复上传无害，幂等）。
 * <p>
 * 反向清理：OSS 中 cover/、avatar/ 前缀下、修改时间超过 24 小时、且 DB 无引用的孤儿对象 → 删除。
 * （24 小时保护窗口，防止误删当日上传但尚未落库的对象）
 */
@Component
@ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
@Slf4j
public class LocalToOssSyncJob {

    /** 本地标识查询前缀 */
    private static final String LOCAL_COVER_PREFIX = "local:cover/%";
    private static final String LOCAL_AVATAR_PREFIX = "local:avatar/%";

    @Resource
    private OssStorageStrategy ossStorageStrategy;

    @Resource
    private OssManager ossManager;

    @Resource
    private FileService fileService;

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    /**
     * 每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void syncLocalToOss() {
        if (!ossStorageStrategy.isAvailable()) {
            log.warn("OSS 不可用（未启用或未配置 AccessKey），跳过本地资源同步");
            return;
        }
        log.info("开始本地资源 → OSS 定时同步");
        long start = System.currentTimeMillis();
        int coverCount = syncLocalCovers();
        int avatarCount = syncLocalAvatars();
        int orphanCount = cleanOrphanOssObjects();
        log.info("本地资源 → OSS 同步完成: cover={}, avatar={}, orphan={}, 耗时={}ms",
                coverCount, avatarCount, orphanCount, System.currentTimeMillis() - start);
    }

    /**
     * 正向迁移：本地标识的封面 → OSS
     */
    private int syncLocalCovers() {
        List<App> apps = appService.list(QueryWrapper.create()
                .where(App::getCover).like(LOCAL_COVER_PREFIX));
        int success = 0;
        for (App app : apps) {
            if (migrateLocalFile(FileBizEnum.COVER, app.getId(), app.getCover())) {
                success++;
            }
        }
        log.info("封面迁移完成: total={}, success={}", apps.size(), success);
        return success;
    }

    /**
     * 正向迁移：本地标识的头像 → OSS
     */
    private int syncLocalAvatars() {
        List<User> users = userService.list(QueryWrapper.create()
                .where(User::getUserAvatar).like(LOCAL_AVATAR_PREFIX));
        int success = 0;
        for (User user : users) {
            if (migrateLocalFile(FileBizEnum.AVATAR, user.getId(), user.getUserAvatar())) {
                success++;
            }
        }
        log.info("头像迁移完成: total={}, success={}", users.size(), success);
        return success;
    }

    /**
     * 迁移单个本地标识文件到 OSS：
     * ① 解析并校验本地文件存在 → ② 上传 OSS → ③ 更新 DB 为 oss: 标识 → ④ 删除本地文件
     * 任一步失败返回 false（保持原样，下轮重试）。
     */
    private boolean migrateLocalFile(FileBizEnum biz, Long ownerId, String localKey) {
        if (StrUtil.isBlank(localKey)) {
            return false;
        }
        try {
            // ① 解析本地标识 → 磁盘文件
            File localFile = resolveLocalFile(localKey);
            if (localFile == null || !localFile.exists()) {
                log.warn("本地文件不存在，跳过迁移: biz={}, ownerId={}, key={}", biz.getValue(), ownerId, localKey);
                return false;
            }
            // ② 直接上传 OSS（不走门面回退：要么上 OSS，要么保持原样等下轮）
            String ossKey = ossStorageStrategy.saveFile(biz, ownerId, localFile);
            if (StrUtil.isBlank(ossKey)) {
                log.warn("上传 OSS 失败，下轮重试: biz={}, ownerId={}", biz.getValue(), ownerId);
                return false;
            }
            // ③ 更新 DB：成功才允许删本地（严格顺序，先改库后删文件）
            if (!updateDbStorageKey(biz, ownerId, ossKey)) {
                log.error("更新 DB 失败，下轮重试: biz={}, ownerId={}, ossKey={}", biz.getValue(), ownerId, ossKey);
                return false;
            }
            // ④ 删除本地文件（DB 已指向 OSS，新请求走签名 URL，本地副本可安全删除）
            fileService.delete(localKey);
            log.info("本地资源迁移 OSS 成功: biz={}, ownerId={}, {} -> {}", biz.getValue(), ownerId, localKey, ossKey);
            return true;
        } catch (Exception e) {
            log.error("迁移本地资源异常: biz={}, ownerId={}, key={}", biz.getValue(), ownerId, localKey, e);
            return false;
        }
    }

    /**
     * 更新 DB 中的存储标识（App.cover 或 User.userAvatar）
     */
    private boolean updateDbStorageKey(FileBizEnum biz, Long ownerId, String ossKey) {
        if (biz == FileBizEnum.COVER) {
            App updateApp = new App();
            updateApp.setId(ownerId);
            updateApp.setCover(ossKey);
            return appService.updateById(updateApp);
        }
        User updateUser = new User();
        updateUser.setId(ownerId);
        updateUser.setUserAvatar(ossKey);
        return userService.updateById(updateUser);
    }

    /**
     * 解析本地标识（local:{biz}/{yyyy}/{MM}/{dd}/{fileName}）为磁盘文件路径
     */
    private File resolveLocalFile(String localKey) {
        if (StrUtil.isBlank(localKey) || !localKey.startsWith(LocalStorageStrategy.LOCAL_KEY_PREFIX)) {
            return null;
        }
        String body = localKey.substring(LocalStorageStrategy.LOCAL_KEY_PREFIX.length());
        int slash = body.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        FileBizEnum biz = FileBizEnum.getEnumByValue(body.substring(0, slash));
        String relativeKey = body.substring(slash + 1);
        if (biz == null || !isSafeRelativeKey(relativeKey)) {
            return null;
        }
        return new File(AppConstant.STORAGE_ROOT_DIR + File.separator + biz.getValue() + File.separator + relativeKey);
    }

    /** 相对 key 安全校验（yyyy/MM/dd/fileName 四段） */
    private boolean isSafeRelativeKey(String relativeKey) {
        String[] parts = relativeKey.split("/");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (StrUtil.isBlank(part) || !part.matches("^[a-zA-Z0-9.\\-_]+$")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 反向清理：OSS 中无 DB 引用的孤儿对象（仅清理修改时间超过 24 小时的，防误删）
     */
    private int cleanOrphanOssObjects() {
        // DB 中所有 oss: 标识集合
        Set<String> referenced = new HashSet<>();
        appService.list(QueryWrapper.create().where(App::getCover).like("oss:%"))
                .forEach(app -> referenced.add(app.getCover()));
        userService.list(QueryWrapper.create().where(User::getUserAvatar).like("oss:%"))
                .forEach(user -> referenced.add(user.getUserAvatar()));

        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        int removed = 0;
        for (FileBizEnum biz : FileBizEnum.values()) {
            List<String> keys = ossManager.listObjectKeysBefore(biz.getValue() + "/", cutoff);
            for (String key : keys) {
                if (!referenced.contains(OssStorageStrategy.OSS_KEY_PREFIX + key)) {
                    log.warn("删除 OSS 孤儿对象: {}", key);
                    ossManager.deleteObject(key);
                    removed++;
                }
            }
        }
        log.info("OSS 孤儿对象清理完成: removed={}", removed);
        return removed;
    }
}
