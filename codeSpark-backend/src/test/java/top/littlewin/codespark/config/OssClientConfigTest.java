package top.littlewin.codespark.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.BucketSummary;
import com.aliyun.sdk.service.oss2.models.ListBucketsRequest;
import com.aliyun.sdk.service.oss2.models.ListBucketsResult;
import com.aliyun.sdk.service.oss2.paginator.ListBucketsIterable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * OSS 客户端连通性测试：AK 已配置则列 bucket 验证连通性；未配置则打印提示跳过。
 * 注意：不关闭共享单例客户端（由容器 @PreDestroy 统一释放）。
 */
@SpringBootTest
class OssClientConfigTest {

    /** 与 OssManager 同款注入方式：OSS 未启用/未配置 AK 时容器注册 null Bean，required=false 兼容两种场景 */
    @Autowired(required = false)
    private OSSClient ossClient;

    @Test
    void ossClient() {
        if (ossClient == null) {
            System.out.println("未配置 AccessKey（OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET），跳过 OSS 连通性测试");
            return;
        }

        ListBucketsIterable paginator = ossClient.listBucketsPaginator(
                ListBucketsRequest.newBuilder()
                        .build());

        for (ListBucketsResult result : paginator) {
            for (BucketSummary info : result.buckets()) {
                System.out.printf("bucket: name:%s, region:%s, storageClass:%s\n", info.name(), info.region(), info.storageClass());
            }
        }
    }
}
