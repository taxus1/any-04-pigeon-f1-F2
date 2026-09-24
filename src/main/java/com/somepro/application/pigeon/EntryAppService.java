package com.somepro.application.pigeon;

import com.somepro.common.exception.BizException;
import com.somepro.domain.pigeon.model.Band;
import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.pigeon.model.Race;
import com.somepro.domain.pigeon.repository.BandRepository;
import com.somepro.domain.pigeon.repository.EntryRepository;
import com.somepro.domain.pigeon.repository.RaceRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 集鸽用例（应用层）。开赛之前会员把鸽子送来，秘书录一笔。
 *
 * 登记编排顺序，任何一关不过都抛 {@link BizException} 给出说法，绝不闷头入库：
 * 1. 赛项编号查得到赛项；
 * 2. 足环号在档案里查得到；
 * 3. 足环在赛（ACTIVE）—— 注销 RETIRED / 停赛 SUSPENDED 一律不收；
 * 4. 同一羽报同一场只登一次（应用层先查，t_entry.uk_race_band 库侧并发兜底）；
 * 5. 领域工厂过自身必填项，落库。
 *
 * 集鸽清单 {@link #pageByRace} 只翻指定一场赛，别的场次不混进来。
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
     * 录一笔集鸽。
     *
     * @param raceCode  赛项编号（如 XF-2026-018）
     * @param bandCode  足环号
     * @param basketNo  笼筐号（可空）
     * @param entryTime 收鸽时间；空表示以系统当前时间收鸽
     */
    public Mono<Entry> collect(String raceCode, String bandCode, String basketNo, LocalDateTime entryTime) {
        if (raceCode == null || raceCode.isBlank()) {
            return Mono.error(new BizException("赛项编号不能为空"));
        }
        if (bandCode == null || bandCode.isBlank()) {
            return Mono.error(new BizException("足环号不能为空"));
        }
        LocalDateTime receivedAt = entryTime != null ? entryTime : LocalDateTime.now();

        return raceRepository.findByRaceCode(raceCode.trim())
                .switchIfEmpty(Mono.error(new BizException("赛项不存在：" + raceCode.trim())))
                .flatMap(race ->
                        // 2. 足环档案里得有
                        bandRepository.findByBandCode(bandCode.trim())
                                .switchIfEmpty(Mono.error(new BizException("足环不存在：" + bandCode.trim())))
                                // 3. 在赛才收（RETIRED / SUSPENDED 打回）
                                .flatMap(this::requireActiveBand)
                                // 4. 同一羽报同一场只登一次
                                .flatMap(band -> entryRepository.findByRaceAndBand(race.getId(), band.getId())
                                        .flatMap(existing -> Mono.<Band>error(new BizException(
                                                "足环 " + band.getBandCode() + " 已报进赛项 " + race.getRaceCode()
                                                        + "（笼筐 " + existing.getBasketNo() + "），重复登记无效")))
                                        .switchIfEmpty(Mono.just(band)))
                                // 5. 领域工厂过必填项，落库（uk_race_band 并发兜底）
                                .flatMap(band -> {
                                    Entry entry = Entry.collect(race, band, basketNo, receivedAt);
                                    return entryRepository.save(entry);
                                }));
    }

    /**
     * 集鸽清单分页：秘书对筐只翻这一场赛。赛项不存在给明确说法。
     */
    public Mono<PageResult<EntryRow>> pageByRace(String raceCode, int pageNum, int pageSize) {
        if (raceCode == null || raceCode.isBlank()) {
            return Mono.error(new BizException("赛项编号不能为空"));
        }
        if (pageNum < 1 || pageSize < 1) {
            return Mono.error(new BizException("页码与每页条数必须为正整数"));
        }
        return raceRepository.findByRaceCode(raceCode.trim())
                .switchIfEmpty(Mono.error(new BizException("赛项不存在：" + raceCode.trim())))
                .flatMap(race -> entryRepository.pageByRace(race.getId(), pageNum, pageSize));
    }

    private Mono<Band> requireActiveBand(Band band) {
        if (!band.isActive()) {
            String reason = "RETIRED".equals(band.getStatus()) ? "已注销" : "已停赛";
            return Mono.error(new BizException(
                    "足环 " + band.getBandCode() + " " + reason + "（" + band.getStatus() + "），不接受集鸽"));
        }
        return Mono.just(band);
    }
}
