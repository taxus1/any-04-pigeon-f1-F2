package com.somepro.infrastructure.persistence.pigeon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.pigeon.po.EntryPO;
import com.somepro.infrastructure.persistence.pigeon.po.EntryRowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 集鸽登记 Mapper。
 *
 * 除 BaseMapper 能力外一条手写 SQL：
 * - 集鸽清单联表查询：t_entry 联 t_band，每张表显式带 del_flag = 0
 *   （@TableLogic 不拦截手写 SQL）；WHERE 只认 race_id，对筐绝不串到别的场次。
 */
@Mapper
public interface EntryMapper extends BaseMapper<EntryPO> {

    /**
     * 某场赛的集鸽清单：笼筐号升序，同筐按集鸽时间先后。
     * PageHelper 会在外面套 LIMIT，并自动生成 count。
     */
    @Select("""
            SELECT e.id          AS entryId,
                   b.id          AS bandId,
                   b.band_code   AS bandCode,
                   b.owner_name  AS ownerName,
                   e.basket_no   AS basketNo,
                   e.entry_time  AS entryTime
              FROM t_entry e
              INNER JOIN t_band b ON b.id = e.band_id AND b.del_flag = 0
             WHERE e.del_flag = 0
               AND e.race_id = #{raceId}
             ORDER BY e.basket_no ASC, e.entry_time ASC, e.id ASC
            """)
    List<EntryRowPO> selectEntryRows(@Param("raceId") Long raceId);
}
