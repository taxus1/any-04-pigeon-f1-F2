package com.somepro.interfaces.rest.pigeon.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集鸽清单行（接口层）：足环号、鸽主、笼筐号、集鸽时间。
 * 数据来自 t_entry 联 t_band 查询，按赛项过滤，供秘书对筐。
 */
public record EntryRowVO(String bandCode,
                         String ownerName,
                         String basketNo,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                         LocalDateTime entryTime) implements Serializable {
}
