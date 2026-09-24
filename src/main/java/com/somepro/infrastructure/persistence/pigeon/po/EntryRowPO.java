package com.somepro.infrastructure.persistence.pigeon.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 集鸽清单联表查询的投影 PO（不映射具体某张表，无 BasePO / 审计字段）。
 *
 * 由 EntryMapper 的自定义联表 SQL 填充：t_entry 取笼筐号/集鸽时间，
 * t_band 取足环号/鸽主。参与联表都显式带 del_flag = 0（@TableLogic 不拦截手写 SQL）。
 */
@Getter
@Setter
public class EntryRowPO {

    private Long entryId;

    private Long bandId;

    private String bandCode;

    private String ownerName;

    private String basketNo;

    private LocalDateTime entryTime;
}
