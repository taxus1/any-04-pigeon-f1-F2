package com.somepro.interfaces.rest.pigeon.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 集鸽登记入参（接口层）。
 *
 * @param raceCode  赛项编号（如 XF-2026-018）
 * @param bandCode  足环号
 * @param basketNo  笼筐号（可空，最长 16，与 t_entry.basket_no 对齐）
 * @param entryTime 收鸽时刻，按 yyyy-MM-dd HH:mm:ss 传（GMT+8）；不传取服务端当前时间
 */
public record EntryCheckInRequest(
        @NotBlank(message = "赛项编号不能为空")
        String raceCode,

        @NotBlank(message = "足环号不能为空")
        String bandCode,

        @Size(max = 16, message = "笼筐号最长 16 个字符")
        String basketNo,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime entryTime) {
}
