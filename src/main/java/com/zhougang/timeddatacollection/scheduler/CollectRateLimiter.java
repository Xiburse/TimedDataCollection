package com.zhougang.timeddatacollection.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 采集限速器
 * <p>
 * 回采一年数据时请求量较大，上游接口可能限速，因此在相邻两次采集之间加入固定延迟。
 * 延迟时长由 timedata.collect.delay-ms 配置，默认 200 毫秒。
 */
@Component
public class CollectRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CollectRateLimiter.class);

    private final long delayMs;

    public CollectRateLimiter(@Value("${timedata.collect.delay-ms:200}") long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * 在相邻两次采集之间暂停，避免触发上游限速。
     * 被中断时恢复中断标记并提前结束等待，不抛出异常中断整个采集流程。
     */
    public void pause() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("采集间隔等待被中断，后续采集将继续执行");
        }
    }

}
