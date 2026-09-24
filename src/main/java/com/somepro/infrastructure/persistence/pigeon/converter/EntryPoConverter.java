package com.somepro.infrastructure.persistence.pigeon.converter;

import com.somepro.domain.pigeon.model.Entry;
import com.somepro.infrastructure.persistence.pigeon.po.EntryPO;

/**
 * EntryPO（t_entry）↔ Entry（领域）。
 */
public final class EntryPoConverter {

    private EntryPoConverter() {
    }

    public static Entry toDomain(EntryPO po) {
        Entry d = new Entry();
        d.setId(po.getId());
        d.setRaceId(po.getRaceId());
        d.setBandId(po.getBandId());
        d.setBasketNo(po.getBasketNo());
        d.setEntryTime(po.getEntryTime());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }

    public static EntryPO toPo(Entry d) {
        EntryPO po = new EntryPO();
        po.setId(d.getId());
        po.setRaceId(d.getRaceId());
        po.setBandId(d.getBandId());
        po.setBasketNo(d.getBasketNo());
        po.setEntryTime(d.getEntryTime());
        return po;
    }
}
