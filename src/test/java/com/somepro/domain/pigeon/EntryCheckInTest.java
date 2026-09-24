package com.somepro.domain.pigeon;

import com.somepro.common.exception.BizException;
import com.somepro.domain.pigeon.model.Band;
import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.Race;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 集鸽领域工厂：单条登记自身的不变量（纯领域）。
 * 「足环在档 / 状态可收 / 同场不重复」属跨聚合校验，在应用层，不在本工厂。
 */
class EntryCheckInTest {

    private Race race() {
        Race race = new Race();
        race.setId(99L);
        race.setRaceCode("XF-2026-018");
        return race;
    }

    private Band band() {
        Band band = new Band();
        band.setId(3L);
        band.setBandCode("CHN2026-A-000201");
        band.setStatus("ACTIVE");
        return band;
    }

    @Test
    void checkInFillsRaceBandAndBasket() {
        LocalDateTime at = LocalDateTime.parse("2026-09-22T19:30:00");
        Entry e = Entry.checkIn(race(), band(), " A-12 ", at);
        assertEquals(99L, e.getRaceId());
        assertEquals(3L, e.getBandId());
        assertEquals("A-12", e.getBasketNo());
        assertEquals(at, e.getEntryTime());
    }

    @Test
    void blankBasketNormalizedToNull() {
        assertNull(Entry.checkIn(race(), band(), "   ", null).getBasketNo());
        assertNull(Entry.checkIn(race(), band(), null, null).getBasketNo());
    }

    @Test
    void missingEntryTimeDefaultsToNow() {
        Entry e = Entry.checkIn(race(), band(), "B-01", null);
        assertNotNull(e.getEntryTime());
    }

    @Test
    void missingRaceOrBandRejected() {
        assertThrows(BizException.class, () -> Entry.checkIn(null, band(), "B-01", null));
        assertThrows(BizException.class, () -> Entry.checkIn(race(), null, "B-01", null));
        Race noId = race();
        noId.setId(null);
        assertThrows(BizException.class, () -> Entry.checkIn(noId, band(), "B-01", null));
    }
}
