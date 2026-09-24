package com.somepro.interfaces.rest.pigeon.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 集鸽登记入参（接口层）。
 *
 * @param raceCode  赛项编号（如 XF-2026-018）
 * @param bandCode  足环号
 * @param basketNo  笼筐号（可空）
 * @param entryTime 收鸽时间，按 yyyy-MM-dd HH:mm:ss 传（GMT+8）；不传则以系统当前时间收鸽
 */
public record CollectRequest(
        @NotBlank(message = "赛项编号不能为空")
        String raceCode,

        @NotBlank(message = "足环号不能为空")
        String bandCode,

        String basketNo,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime entryTime) {
}
