package top.littlewin.codespark.job;

import cn.hutool.core.io.FileUtil;
import com.mybatisflex.core.paginate.Page;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地资源 → OSS 定时同步任务（绑定 aliyun.oss.enabled 开关：默认关闭，任务 Bean 不注册）。
 *
 * 正向迁移：DB 中仍是本地标识（local:...）的封面/头像 → 上传 OSS → 更新 DB 为 oss: 标识 → 删除本地文件。
 * 严格顺序：上传成功 → 改库成功 → 才删本地；任一步失败保留原样，下轮重试（重复上传无害，幂等）。
 *
 * 反向清理：OSS 孤儿对象 + 本地残留文件
 */
@Component
@ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
@Slf4j
public class LocalToOssSyncJob {

    /** 本地标识查询前缀 */
    private static final String LOCAL_COVER_PREFIX = "local:cover/%";
    private static final String LOCAL_AVATAR_PREFIX = "local:avatar/%";


    /** 孤儿清理回溯天数：覆盖任务连续未执行（发版停机/OSS 故障）的漏清窗口 */
    private static final int ORPHAN_SCAN_DAYS = 7;

    private static final int MIGRATE_BATCH_SIZE = 200;

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
        int residueCount = cleanLocalResidue();
        log.info("本地资源 → OSS 同步完成: cover={}, avatar={}, orphan={}, residue={}, 耗时={}ms",
                coverCount, avatarCount, orphanCount, residueCount, System.currentTimeMillis() - start);
    }

    /**
     * 正向迁移：本地标识的封面 → OSS
     */
    @SuppressWarnings("unchecked")
    private int syncLocalCovers() {

        int success = 0;
        int failed = 0;
        while (true) {
            // 固定第 1 页：成功迁移的记录不再匹配 local:% 自动离开结果集；
            // 只取 id + cover 两列，避免整行实体进内存
            Page<App> page = appService.page(Page.of(1, MIGRATE_BATCH_SIZE), QueryWrapper.create()
                    .select(App::getId, App::getCover)
                    .where(App::getCover).like(LOCAL_COVER_PREFIX));
            List<App> records = page.getRecords();
            if (records.isEmpty()) {
                break;
            }
            int batchSuccess = 0;
            for (App app : records) {
                if (migrateLocalFile(FileBizEnum.COVER, app.getId(), app.getCover())) {
                    success++;
                    batchSuccess++;
                } else {
                    failed++;
                }
            }
            log.info("封面迁移批次完成: batch={}, success={}", records.size(), batchSuccess);
            // 整批 0 成功：剩余全是失败项，再查第 1 页还是同一批 → 跳出防死循环
            if (batchSuccess == 0) {
                log.error("封面迁移整批失败，剩余 {} 条待下轮重试", page.getTotalRow());
                break;
            }
        }
        // 失败汇总：部分失败（如 200 条挂 3 条）没有整批失败日志，统一在这里聚合告警
        if (failed > 0) {
            log.error("本轮封面迁移存在失败项: failed={}, success={}，若连续多日出现请排查上方单条失败日志", failed, success);
        }
        return success;
    }

    /**
     * 正向迁移：本地标识的头像 → OSS
     *
     * @return 成功条数
     */
    @SuppressWarnings("unchecked")
    private int syncLocalAvatars() {
        int success = 0;
        int failed = 0;
        while (true) {
            // 固定第 1 页：成功迁移的记录不再匹配 local:% 自动离开结果集；
            // 只取 id + userAvatar 两列，避免整行实体进内存
            Page<User> page = userService.page(Page.of(1, MIGRATE_BATCH_SIZE), QueryWrapper.create()
                    .select(User::getId, User::getUserAvatar)
                    .where(User::getUserAvatar).like(LOCAL_AVATAR_PREFIX));
            List<User> records = page.getRecords();
            if (records.isEmpty()) {
                break;
            }
            int batchSuccess = 0;
            for (User user : records) {
                if (migrateLocalFile(FileBizEnum.AVATAR, user.getId(), user.getUserAvatar())) {
                    success++;
                    batchSuccess++;
                } else {
                    failed++;
                }
            }
            log.info("头像迁移批次完成: batch={}, success={}", records.size(), batchSuccess);
            // 整批 0 成功：剩余全是失败项，再查第 1 页还是同一批 → 跳出防死循环
            if (batchSuccess == 0) {
                log.error("头像迁移整批失败，剩余 {} 条待下轮重试", page.getTotalRow());
                break;
            }
        }
        // 失败汇总：部分失败（如 200 条挂 3 条）没有整批失败日志，统一在这里聚合告警
        if (failed > 0) {
            log.error("本轮头像迁移存在失败项: failed={}, success={}，若连续多日出现请排查上方单条失败日志", failed, success);
        }
        return success;
    }

    /**
     * 迁移单个本地标识文件到 OSS：
     * ① 解析并校验本地文件存在 → ② 上传 OSS → ③ 更新 DB 为 oss: 标识 → ④ 删除本地文件
     * 任一步失败返回 false（保持原样，下轮重试）。
     * @param biz 文件业务类型（头像 / 封面）
     * @param ownerId 业务ID（用户ID / 应用ID）
     * @param localKey 本地存储标识（cover / userAvatar）
     * @return 是否操作成功
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
            boolean updated = updateDbStorageKey(biz, ownerId, ossKey, localKey);
            if (!updated) {
                // 0 行：说明任务运行期间用户改了头像，本次迁移作废
                // 多传的那个 ossKey 变孤儿，明天 cleanOrphanOssObjects 兜底
                log.info("DB 值已被并发修改，放弃本次迁移: biz={}, ownerId={}", biz.getValue(), ownerId);
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
     *
     * @param biz 文件业务类型（头像 / 封面）
     * @param ownerId 业务ID（用户ID / 应用ID）
     * @param ossKey 上传到 OSS 后的存储标识
     * @param localKey 本地存储标识（cover / userAvatar）
     * @return 是否更新成功
     */
    private boolean updateDbStorageKey(FileBizEnum biz, Long ownerId, String ossKey, String localKey) {
        if (biz == FileBizEnum.COVER) {
            App updateApp = new App();
            updateApp.setCover(ossKey);
            return appService.update(updateApp, QueryWrapper.create()
                    .where(App::getId).eq(ownerId)
                    // 防止执行定时任务期间，cover 发生改变，导致新 cover 丢失
                    .and(App::getCover).eq(localKey));
        }

        User updateUser = new User();
        updateUser.setUserAvatar(ossKey);
        return userService.update(updateUser, QueryWrapper.create()
                .where(User::getId).eq(ownerId)
                .and(User::getUserAvatar).eq(localKey));
    }

    /**
     * 解析本地标识（local:{biz}/{yyyy}/{MM}/{dd}/{fileName}）为磁盘文件路径
     *
     * @param localKey 本地存储标识
     * @return 本地文件
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
            if (StrUtil.isBlank(part) || !part.matches("^[a-zA-Z0-9.\\-_]+$") || "..".equals(part)) {
                return false;
            }
        }
        return true;
    }


    /**
     * 清理 ORPHAN_SCAN_DAYS 天内，OSS 中的孤儿对象
     *
     * @return 清理掉的数量
     */
    @SuppressWarnings("unchecked")
    private int cleanOrphanOssObjects() {
        // DB 中所有 oss: 标识集合
        Set<String> referenced = new HashSet<>();
        appService.list(QueryWrapper.create()
                        .select(App::getCover).where(App::getCover).like("oss:%"))
                .forEach(app -> referenced.add(app.getCover()));
        userService.list(QueryWrapper.create()
                        .select(User::getUserAvatar).where(User::getUserAvatar).like("oss:%"))
                .forEach(user -> referenced.add(user.getUserAvatar()));

        int removed = 0;
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        // 回溯 7 天：21 日跑的时候扫 14~20 日所有日期目录，任务停跑几天也不漏
        for (int i = 1; i <= ORPHAN_SCAN_DAYS; i++) {
            String day = LocalDate.now().minusDays(i).format(dayFmt);
            for (FileBizEnum biz : FileBizEnum.values()) {
                String prefix = biz.getValue() + "/" + day + "/";
                List<String> keys = ossManager.listObjectKeysBefore(prefix,null);
                for (String key : keys) {
                    if (!referenced.contains(OssStorageStrategy.OSS_KEY_PREFIX + key)) {
                        log.warn("删除 OSS 孤儿对象: {}", key);
                        ossManager.deleteObject(key);
                        removed++;
                    }
                }
            }
        }

        log.info("OSS 孤儿对象清理完成: removed={}", removed);
        return removed;
    }

    /**
     * 本地残留清理：tmp/storage 下超过 24h 且 DB 无 local: 引用的文件 → 删除。
     * 兜底场景：migrateLocalFile 第④步删本地失败（DB 已改 oss:，下轮查不到）。
     */
    @SuppressWarnings("unchecked")
    private int cleanLocalResidue() {
        Set<String> referenced = new HashSet<>();
        appService.list(QueryWrapper.create()
                        .select(App::getCover).where(App::getCover).like("local:%"))
                .forEach(app -> referenced.add(app.getCover()));
        userService.list(QueryWrapper.create()
                        .select(User::getUserAvatar).where(User::getUserAvatar).like("local:%"))
                .forEach(user -> referenced.add(user.getUserAvatar()));

        long protectBefore = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        int removed = 0;
        for (FileBizEnum biz : FileBizEnum.values()) {
            File bizDir = new File(AppConstant.STORAGE_ROOT_DIR, biz.getValue());
            if (!bizDir.exists()) {
                continue;
            }
            for (File file : FileUtil.loopFiles(bizDir)) {

                // 1. 24h 内的新文件不动（可能正在走"保存→更新DB"的正常流程）
                if (file.lastModified() > protectBefore) {
                    continue;
                }

                // 2. 构造 DB 里存的标准 key
                String relative = bizDir.toPath().relativize(file.toPath())
                        .toString().replace(File.separatorChar, '/');
                String localKey = LocalStorageStrategy.LOCAL_KEY_PREFIX + biz.getValue() + "/" + relative;

                // 3. 删除不在集合的孤儿文件
                if (!referenced.contains(localKey)) {
                    FileUtil.del(file);
                    removed++;
                    log.warn("删除本地残留文件（DB 无引用）: {}", localKey);
                }
            }
        }
        log.info("本地残留清理完成: removed={}", removed);
        return removed;
    }

}
