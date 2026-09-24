package com.somepro.interfaces.rest.pigeon.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集鸽清单行（接口层）：秘书对筐时看一行 —— 哪羽鸽子、哪个笼筐、什么时候收的。
 */
public record EntryRowVO(Long entryId,
                        String bandCode,
                        String ownerName,
                        String basketNo,
                        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                        LocalDateTime entryTime) implements Serializable {
}
