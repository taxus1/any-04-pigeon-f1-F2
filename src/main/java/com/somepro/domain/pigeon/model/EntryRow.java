package com.somepro.domain.pigeon.model;

import java.time.LocalDateTime;

/**
 * 集鸽清单读模型（领域层值对象，不可变 record）：
 * 一行 = 集鸽登记 id + 足环号 + 鸽主 + 笼筐号 + 集鸽时间。
 *
 * 由基础设施层联表 t_entry / t_band 查出（只查指定赛项，别的场次不混进来），
 * 接口层原样转成 VO 输出，供秘书对筐。
 */
public record EntryRow(Long entryId,
                       String bandCode,
                       String ownerName,
                       String basketNo,
                       LocalDateTime entryTime) {
}
