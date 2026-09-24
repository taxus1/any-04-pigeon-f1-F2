package com.somepro.domain.pigeon;

import com.somepro.common.exception.BizException;
import com.somepro.domain.pigeon.model.Band;
import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.Race;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 集鸽领域工厂：必填项校验 + 笼筐号去空白（纯领域）。
 * 档案存在 / 状态在赛 / 同场不重登是跨聚合校验，归应用层。
 */
class EntryCollectTest {

    private final LocalDateTime at = LocalDateTime.parse("2026-09-24T08:30:00");

    private Race race() {
        Race race = new Race();
        race.setId(1001L);
        race.setRaceCode("XF-2026-018");
        return race;
    }

    private Band band() {
        Band band = new Band();
        band.setId(201L);
        band.setBandCode("CHN2026-A-000201");
        band.setStatus("ACTIVE");
        return band;
    }

    @Test
    void validCollectSetsFields() {
        Entry e = Entry.collect(race(), band(), "A-12", at);
        assertEquals(1001L, e.getRaceId());
        assertEquals(201L, e.getBandId());
        assertEquals("A-12", e.getBasketNo());
        assertEquals(at, e.getEntryTime());
    }

    @Test
    void basketNoIsTrimmedAndMayBeNull() {
        assertEquals("A-1", Entry.collect(race(), band(), "  A-1  ", at).getBasketNo());
        assertNull(Entry.collect(race(), band(), null, at).getBasketNo());
    }

    @Test
    void missingEntryTimeRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> Entry.collect(race(), band(), "A-1", null));
        assertEquals(true, ex.getMessage().contains("集鸽时间"));
    }

    @Test
    void missingRaceOrBandRejected() {
        assertThrows(BizException.class, () -> Entry.collect(null, band(), "A-1", at));
        assertThrows(BizException.class, () -> Entry.collect(race(), null, "A-1", at));
    }
}
