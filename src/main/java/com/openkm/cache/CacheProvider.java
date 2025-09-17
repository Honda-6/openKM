package com.openkm.cache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;

import org.ehcache.core.internal.statistics.DefaultStatisticsService;
import org.ehcache.core.statistics.CacheStatistics;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ehcache 3.x CacheProvider
 */
public class CacheProvider {
    private static final CacheProvider INSTANCE = new CacheProvider();

    private final CacheManager cacheManager;
    private final Map<String, Cache<?, ?>> caches=new HashMap<>();
    DefaultStatisticsService statisticsService = new DefaultStatisticsService();
    private CacheProvider() {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .using(statisticsService)
                .withCache("defaultCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class, Object.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(1000, EntryUnit.ENTRIES)
                                        .offheap(10, MemoryUnit.MB)
                        )
                ).build(true);
    }

    public static CacheProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Create or get cache by name
     */
    @SuppressWarnings("unchecked")
    public synchronized <K, V> Cache<K, V> getCache(String name) {
        Cache<K, V> cache = (Cache<K, V>) caches.get(name);
        if (cache == null) {
            cache = (Cache<K, V>) cacheManager.createCache(name,
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(
                            Object.class, Object.class,
                            ResourcePoolsBuilder.heap(1000)
                    )
            );
            caches.put(name, cache);
        }
        return cache;
    }

    /**
     * Get all cache names
     */
    public Set<String> getOkmCacheNames() {
        return caches.keySet();
    }

    /**
     * Clear all caches
     */
    public void clearAll() {
        caches.values().forEach(Cache::clear);
    }

    /**
     * Enable or disable statistics for all caches
     */
    public void enableStatistics(boolean enabled) {
        // for (Cache<?, ?> cache : caches.values()) {
        //     if (cache instanceof Ehcache<?, ?> ehCache) {
        //         ehCache.getRuntimeConfiguration().setStatisticsEnabled(enabled);
        //     }
        // }
    }

    /**
     * Clear all cache statistics
     */
    public void clearStatistics() {
        // caches.keySet().forEach(statisticsService::clearStatistics);
        // for (Cache<?, ?> cache : caches.values()) {
        //     if (cache instanceof Ehcache<?, ?> ehCache) {
        //         ehCache.getStatistics().clear();
        //     }
        // }
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics(String name) {
    //     Cache<?, ?> cache = caches.get(name);
    //     if (cache instanceof Ehcache<?, ?> ehCache) {
    //         return ehCache.getStatistics();
    //     }
    //     return null;
            return statisticsService.getCacheStatistics(name);

    }

    public void getManager() {
    }
}
