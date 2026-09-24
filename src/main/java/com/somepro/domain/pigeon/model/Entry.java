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
 *
 * 跨聚合规则（足环要在档、RETIRED/SUSPENDED 不收、同场不重复）由应用层先查再调工厂；
 * 工厂只守单条登记自身的不变量。
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
     * 工厂方法：登记一羽赛鸽进一场赛。
     *
     * @param race      报进的赛项（应用层已确认存在）
     * @param band      报进的足环（应用层已确认在档且状态可收）
     * @param basketNo  笼筐号；空串归一为 null（库列可空）
     * @param entryTime 收鸽时刻；为空取当前时间
     */
    public static Entry checkIn(Race race, Band band, String basketNo, LocalDateTime entryTime) {
        if (race == null || race.getId() == null) {
            throw new BizException("赛项不能为空");
        }
        if (band == null || band.getId() == null) {
            throw new BizException("足环不能为空");
        }
        Entry entry = new Entry();
        entry.setRaceId(race.getId());
        entry.setBandId(band.getId());
        entry.setBasketNo(normalizeBasketNo(basketNo));
        entry.setEntryTime(entryTime == null ? LocalDateTime.now() : entryTime);
        return entry;
    }

    /** 笼筐号去首尾空白；全空归一为 null，避免库里空串与 null 两种「没填」并存。 */
    private static String normalizeBasketNo(String basketNo) {
        if (basketNo == null) {
            return null;
        }
        String trimmed = basketNo.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
