package top.littlewin.codespark.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存 Key 生成工具类
 */
public class CacheKeyUtils {

    /**
     * 根据对象生成缓存 Key（ JSON + MD5 ）
     *
     * @param object 要生存 Key 的对象
     * @return MD5哈希后的缓存 Key
     */
    public static String generateCacheKey(Object object){

        if (object == null){
            return DigestUtil.md5Hex("null");
        }

        // 先转 JSON，在转 MD5
        String json = JSONUtil.toJsonStr(object);
        return DigestUtil.md5Hex(json);
    }
}
