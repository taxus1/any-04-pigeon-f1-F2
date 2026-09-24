package com.somepro.infrastructure.persistence.pigeon.converter;

import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.infrastructure.persistence.pigeon.po.EntryPO;
import com.somepro.infrastructure.persistence.pigeon.po.EntryRowPO;

/**
 * EntryPO（t_entry）↔ Entry（领域），以及集鸽清单投影 → 领域读模型。
 */
public final class EntryPoConverter {

    private EntryPoConverter() {
    }

    public static EntryPO toPo(Entry d) {
        EntryPO po = new EntryPO();
        po.setId(d.getId());
        po.setRaceId(d.getRaceId());
        po.setBandId(d.getBandId());
        po.setBasketNo(d.getBasketNo());
        po.setEntryTime(d.getEntryTime());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
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

    public static EntryRow toEntryRow(EntryRowPO po) {
        return new EntryRow(
                po.getEntryId(),
                po.getBandCode(),
                po.getOwnerName(),
                po.getBasketNo(),
                po.getEntryTime());
    }
}
