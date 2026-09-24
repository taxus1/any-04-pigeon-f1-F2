package com.somepro.domain.pigeon.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 集鸽登记（领域层，赛鸽上下文）：一羽足环报进一场赛。
 *
 * 是「报到」的前置：只有集过鸽的鸽子回来才允许记一笔。
 * t_entry 上有 (race_id, band_id) 唯一约束，一场赛一羽足环至多一条集鸽记录。
 */
@Getter
@Setter
public class Entry extends BaseEntity {

    private Long id;

    private Long raceId;

    private Long bandId;

    /** 笼筐号。 */
    private String basketNo;

    private LocalDateTime entryTime;

    /**
     * 工厂方法：每笔集鸽在领域层过一遍自身不变量，非法输入直接抛 {@link BizException}（中文原因），
     * 绝不闷头入库。
     *
     * 「足环是否存在 / 是否在赛 / 同场是否已登过」属于跨聚合校验，由应用层先查再调本方法；
     * 本方法只负责单条集鸽记录自身的必填项。集鸽时间为空表示「以系统当前时间收鸽」，
     * 由应用层补上，不允许带着空时间落库。
     */
    public static Entry collect(Race race, Band band, String basketNo, LocalDateTime entryTime) {
        if (race == null || race.getId() == null) {
            throw new BizException("赛项不能为空");
        }
        if (band == null || band.getId() == null) {
            throw new BizException("足环不能为空");
        }
        if (entryTime == null) {
            throw new BizException("集鸽时间不能为空");
        }
        Entry entry = new Entry();
        entry.setRaceId(race.getId());
        entry.setBandId(band.getId());
        entry.setBasketNo(basketNo == null ? null : basketNo.trim());
        entry.setEntryTime(entryTime);
        return entry;
    }
}
