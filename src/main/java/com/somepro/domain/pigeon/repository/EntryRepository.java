package com.somepro.domain.pigeon.repository;

import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 集鸽登记仓储端口。
 * - 集鸽用例：登记（save）、查重（findByRaceAndBand）、按赛项翻清单（pageByRace）；
 * - 报到用例：findByRaceAndBand 确认「集过鸽」，findById 供补查。
 */
public interface EntryRepository {

    /**
     * 登记一笔集鸽。并发重复登记由 t_entry.uk_race_band 在库侧兜底，
     * 撞键时仓储抛出带中文原因的 BizException。
     */
    Mono<Entry> save(Entry entry);

    /**
     * 按赛项 + 足环查集鸽记录；没集过鸽返回空信号。应用层据此拦重复登记，
     * 报到用例据此确认「这羽鸽这场赛集过鸽」。
     */
    Mono<Entry> findByRaceAndBand(Long raceId, Long bandId);

    Mono<Entry> findById(Long id);

    /**
     * 按赛项翻集鸽清单（联 t_band 带足环号/鸽主）。只查这一场赛，
     * 按笼筐号升序、同筐按集鸽时间升序，供秘书对筐。
     */
    Mono<PageResult<EntryRow>> pageByRace(Long raceId, int pageNum, int pageSize);
}
