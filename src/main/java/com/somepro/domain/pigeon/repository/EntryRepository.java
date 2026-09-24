package com.somepro.domain.pigeon.repository;

import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 集鸽登记仓储端口。
 *
 * - 报到用例只读：{@link #findByRaceAndBand} 确认「集过鸽」；
 * - 集鸽用例：{@link #save} 登记一笔（uk_race_band 库侧并发兜底）；
 * - 对筐清单：{@link #pageByRace} 只翻某一场赛的集鸽记录，不串场。
 */
public interface EntryRepository {

    /**
     * 按赛项 + 足环查集鸽记录；没集过鸽返回空信号。
     */
    Mono<Entry> findByRaceAndBand(Long raceId, Long bandId);

    Mono<Entry> findById(Long id);

    /**
     * 登记一笔集鸽。同一羽报同一场重复登时，库侧 uk_race_band 抛唯一键冲突，
     * 由适配器翻译成业务异常。
     */
    Mono<Entry> save(Entry entry);

    /**
     * 按赛项分页查集鸽清单（联足环档案取足环号/鸽主），只含本场赛。
     * 排序：笼筐号升序、同筐按集鸽时间先后。
     */
    Mono<PageResult<EntryRow>> pageByRace(Long raceId, int pageNum, int pageSize);
}
