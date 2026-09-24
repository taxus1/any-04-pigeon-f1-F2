package com.somepro.domain.pigeon.model;

import java.time.LocalDateTime;

/**
 * 集鸽清单行（领域读模型）：按赛项对筐时展示。
 *
 * 来自 t_entry 联 t_band 的投影 —— 清单只翻这一场赛，每行告诉秘书
 * 「哪羽鸽子（足环号/鸽主）在哪个笼筐、什么时候收的」。
 * 纯数据、无行为、不可变，用 record。
 */
public record EntryRow(Long entryId,
                       Long bandId,
                       String bandCode,
                       String ownerName,
                       String basketNo,
                       LocalDateTime entryTime) {
}
