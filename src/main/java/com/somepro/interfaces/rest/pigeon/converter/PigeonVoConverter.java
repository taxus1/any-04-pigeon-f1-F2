package com.somepro.interfaces.rest.pigeon.converter;

import com.somepro.domain.pigeon.model.Clocking;
import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.pigeon.model.RaceResult;
import com.somepro.domain.pigeon.model.RankRow;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.pigeon.vo.ClockingVO;
import com.somepro.interfaces.rest.pigeon.vo.EntryRowVO;
import com.somepro.interfaces.rest.pigeon.vo.EntryVO;
import com.somepro.interfaces.rest.pigeon.vo.PageVO;
import com.somepro.interfaces.rest.pigeon.vo.RaceResultVO;
import com.somepro.interfaces.rest.pigeon.vo.RankRowVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 领域对象 → 对外 VO（接口层唯一转换入口）。
 * 内部字段（delFlag / 审计字段）不进 API 契约。
 */
public final class PigeonVoConverter {

    private PigeonVoConverter() {
    }

    /**
     * 分速对外统一两位小数。
     * 领域算出来就是 2 位；但名次榜走联表投影，JDBC 驱动对 DECIMAL 列可能给出 1 位标度
     * （实测 MySQL 驱动投影返回 1250.0），这里兜底补齐，保证「分速保留两位小数」的 API 契约稳定。
     */
    private static BigDecimal scale2(BigDecimal speed) {
        return speed == null ? null : speed.setScale(2, RoundingMode.HALF_UP);
    }

    public static ClockingVO toClockingVo(Clocking d) {
        return new ClockingVO(d.getId(), d.getRaceId(), d.getEntryId(), d.getClockAt(), d.getSource());
    }

    public static EntryVO toEntryVo(Entry d) {
        return new EntryVO(d.getId(), d.getRaceId(), d.getBandId(), d.getBasketNo(), d.getEntryTime());
    }

    public static EntryRowVO toEntryRowVo(EntryRow row) {
        return new EntryRowVO(row.entryId(), row.bandCode(), row.ownerName(),
                row.basketNo(), row.entryTime());
    }

    public static PageVO<EntryRowVO> toEntryPageVo(PageResult<EntryRow> page) {
        List<EntryRowVO> content = page.content().stream()
                .map(PigeonVoConverter::toEntryRowVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }

    public static RaceResultVO toResultVo(RaceResult d) {
        return new RaceResultVO(d.getEntryId(), scale2(d.getSpeedMpm()), d.getRankNo());
    }

    public static List<RaceResultVO> toResultVoList(List<RaceResult> list) {
        return list.stream().map(PigeonVoConverter::toResultVo).collect(Collectors.toList());
    }

    public static RankRowVO toRankVo(RankRow row) {
        return new RankRowVO(row.bandCode(), row.ownerName(), row.clockAt(),
                scale2(row.speedMpm()), row.rankNo());
    }

    public static PageVO<RankRowVO> toRankPageVo(PageResult<RankRow> page) {
        List<RankRowVO> content = page.content().stream()
                .map(PigeonVoConverter::toRankVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
