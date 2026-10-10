package com.study.ragagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.study.ragagent.domain.entity.SystemConfig;
import com.study.ragagent.mapper.SystemConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务
 * 读取/更新 system_config 表；本地短路缓存（20 秒 TTL）
 */
@Slf4j
@Service
public class SystemConfigService {

    private final SystemConfigMapper mapper;

    // key -> (value, expireAt)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL_MS = 20_000L;

    public SystemConfigService(SystemConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取配置，不存在则写入默认值并返回 */
    public String getConfig(String key, String defaultValue) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (entry.expireAt >= System.currentTimeMillis() && entry.value != null) {
                return entry.value;
            }
            cache.remove(key);
        }

        SystemConfig record = findByKey(key);
        String value;
        if (record != null && record.getConfigValue() != null) {
            value = record.getConfigValue();
        } else {
            value = defaultValue;
            // 缺省时写入，便于后台可见
            if (record == null && defaultValue != null) {
                SystemConfig insert = new SystemConfig();
                insert.setConfigKey(key);
                insert.setConfigValue(defaultValue);
                insert.setConfigType("DEFAULT");
                insert.setDescription("默认初始化");
                insert.setUpdatedAt(LocalDateTime.now());
                mapper.insert(insert);
            }
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + TTL_MS));
        return value;
    }

    /** 更新配置值；value 为 null 表示恢复代码默认（写入 NULL，读取时回退默认值）。
     *  MyBatis-Plus 的 updateById 忽略 null 字段，必须用 UpdateWrapper 显式置空。 */
    public void updateConfig(String key, String value) {
        if (findByKey(key) == null) {
            SystemConfig record = new SystemConfig();
            record.setConfigKey(key);
            record.setConfigValue(value);
            record.setConfigType("STRING");
            record.setUpdatedAt(LocalDateTime.now());
            mapper.insert(record);
        } else {
            mapper.update(null, new LambdaUpdateWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, key)
                    .set(SystemConfig::getConfigValue, value)
                    .set(SystemConfig::getUpdatedAt, LocalDateTime.now()));
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + TTL_MS));
    }

    /** 启动 seed：不存在才写入，不覆盖用户已改的值 */
    public void ensureDefault(String key, String value, String type, String description) {
        if (value == null || findByKey(key) != null) {
            return;
        }
        SystemConfig record = new SystemConfig();
        record.setConfigKey(key);
        record.setConfigValue(value);
        record.setConfigType(type);
        record.setDescription(description);
        record.setUpdatedAt(LocalDateTime.now());
        mapper.insert(record);
    }

    /** 后台配置列表：key/value/type/description */
    public List<Map<String, Object>> listAll() {
        List<SystemConfig> rows = mapper.selectList(null);
        rows.sort(Comparator.comparing(SystemConfig::getConfigKey));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemConfig row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", row.getConfigKey());
            item.put("value", row.getConfigValue());
            item.put("type", row.getConfigType());
            item.put("description", row.getDescription());
            item.put("updatedAt", row.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    public void clearCache() {
        cache.clear();
    }

    private SystemConfig findByKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
    }

    private record CacheEntry(String value, long expireAt) {}
}