package com.somepro.interfaces.rest.pigeon.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集鸽登记回执（接口层）：只暴露允许外部看到的字段。
 */
public record EntryVO(Long id,
                     Long raceId,
                     Long bandId,
                     String basketNo,
                     @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                     LocalDateTime entryTime) implements Serializable {
}
