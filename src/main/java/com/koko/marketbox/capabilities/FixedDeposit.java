package com.koko.marketbox.capabilities;

import java.util.UUID;

/**
 * 一笔定期存款。
 *
 * 利率在存入时锁定，之后服主调整活期利率不影响已存入的定期。
 * 到期前取出只退本金、不计利息，作为提前支取的代价。
 */
public record FixedDeposit(UUID id, long principalCents, double dailyRatePercent,
                           long startTime, int days) {

    /** 到期时间戳（毫秒） */
    public long maturityTime() {
        return startTime + days * 86_400_000L;
    }

    public boolean isMatured(long now) {
        return now >= maturityTime();
    }

    /** 到期利息（分）。按单利计算，到期一次性给付 */
    public long interestCents() {
        return Math.round(principalCents * (dailyRatePercent / 100.0) * days);
    }

    /** 到期可领取总额（分） */
    public long totalCents() {
        return principalCents + interestCents();
    }

    /** 剩余毫秒数，已到期返回 0 */
    public long remainingMillis(long now) {
        return Math.max(0, maturityTime() - now);
    }
}
