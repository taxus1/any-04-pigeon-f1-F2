package com.somepro.domain.pigeon.repository;

import com.somepro.domain.pigeon.model.Race;
import reactor.core.publisher.Mono;

/**
 * 训放赛项仓储端口。算分速需要开笼时间与空距，报到需要时间窗，集鸽按赛项编号定位赛项。
 */
public interface RaceRepository {

    Mono<Race> findById(Long id);

    /** 按赛项编号（如 XF-2026-018）查赛项；不存在返回空信号。 */
    Mono<Race> findByRaceCode(String raceCode);
}
