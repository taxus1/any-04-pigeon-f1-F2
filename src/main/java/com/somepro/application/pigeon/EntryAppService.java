package com.somepro.application.pigeon;

import com.somepro.common.exception.BizException;
import com.somepro.domain.pigeon.model.Band;
import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.pigeon.repository.BandRepository;
import com.somepro.domain.pigeon.repository.EntryRepository;
import com.somepro.domain.pigeon.repository.RaceRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 集鸽用例（应用层）：开赛前把会员送来的鸽子登记进一场赛。
 *
 * 编排顺序，任何一关不过都抛 {@link BizException} 给出说法，绝不闷头入库：
 * 1. 赛项编号存在；
 * 2. 足环在档案里；
 * 3. 状态可收：RETIRED（注销）/ SUSPENDED（停赛）一律不收；
 * 4. 同一羽同一场赛只登一次，手滑报两遍打回；
 * 5. 领域工厂建单（收鸽时刻缺省取当前时间），落库（uk_race_band 库侧并发兜底）。
 */
@Service
public class EntryAppService {

    private final RaceRepository raceRepository;
    private final BandRepository bandRepository;
    private final EntryRepository entryRepository;

    public EntryAppService(RaceRepository raceRepository,
                           BandRepository bandRepository,
                           EntryRepository entryRepository) {
        this.raceRepository = raceRepository;
        this.bandRepository = bandRepository;
        this.entryRepository = entryRepository;
    }

    /**
     * 登记一笔集鸽。
     *
     * @param raceCode  赛项编号（如 XF-2026-018）
     * @param bandCode  足环号
     * @param basketNo  笼筐号（可空）
     * @param entryTime 收鸽时刻；为空取当前时间
     */
    public Mono<Entry> checkIn(String raceCode, String bandCode, String basketNo, LocalDateTime entryTime) {
        if (raceCode == null || raceCode.isBlank()) {
            return Mono.error(new BizException("赛项编号不能为空"));
        }
        if (bandCode == null || bandCode.isBlank()) {
            return Mono.error(new BizException("足环号不能为空"));
        }

        // 1. 赛项存在（按编号）
        return raceRepository.findByRaceCode(raceCode.trim())
                .switchIfEmpty(Mono.error(new BizException("赛项不存在：" + raceCode)))
                .flatMap(race ->
                        // 2. 足环在档
                        bandRepository.findByBandCode(bandCode.trim())
                                .switchIfEmpty(Mono.error(new BizException("足环不存在：" + bandCode)))
                                // 3. RETIRED / SUSPENDED 不收
                                .flatMap(this::requireEnterable)
                                // 4. 同一羽同一场赛只登一次
                                .flatMap(band -> entryRepository.findByRaceAndBand(race.getId(), band.getId())
                                        .flatMap(existing -> Mono.<Band>error(new BizException(
                                                "足环 " + band.getBandCode() + " 已报进赛项 " + race.getRaceCode()
                                                        + "（集鸽时间 " + existing.getEntryTime() + "），重复登记无效")))
                                        .switchIfEmpty(Mono.just(band)))
                                // 5. 建单落库（uk_race_band 并发兜底）
                                .flatMap(band -> entryRepository.save(
                                        Entry.checkIn(race, band, basketNo, entryTime))));
    }

    /**
     * 集鸽清单：按赛项编号翻页，只出这一场赛的登记，别的场次不混进来。
     */
    public Mono<PageResult<EntryRow>> pageEntries(String raceCode, int pageNum, int pageSize) {
        if (raceCode == null || raceCode.isBlank()) {
            return Mono.error(new BizException("赛项编号不能为空"));
        }
        if (pageNum < 1 || pageSize < 1) {
            return Mono.error(new BizException("页码与每页条数必须为正整数"));
        }
        return raceRepository.findByRaceCode(raceCode.trim())
                .switchIfEmpty(Mono.error(new BizException("赛项不存在：" + raceCode)))
                .flatMap(race -> entryRepository.pageByRace(race.getId(), pageNum, pageSize));
    }

    /** 注销（RETIRED）与停赛（SUSPENDED）的足环都不收，各自给明确说法。 */
    private Mono<Band> requireEnterable(Band band) {
        if ("RETIRED".equals(band.getStatus())) {
            return Mono.error(new BizException(
                    "足环 " + band.getBandCode() + " 已注销（RETIRED），不接受集鸽"));
        }
        if ("SUSPENDED".equals(band.getStatus())) {
            return Mono.error(new BizException(
                    "足环 " + band.getBandCode() + " 已停赛（SUSPENDED），不接受集鸽"));
        }
        if (!band.isActive()) {
            return Mono.error(new BizException(
                    "足环 " + band.getBandCode() + " 当前状态为 " + band.getStatus() + "，不接受集鸽"));
        }
        return Mono.just(band);
    }
}
