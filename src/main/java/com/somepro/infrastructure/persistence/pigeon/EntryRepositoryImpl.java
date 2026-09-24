package com.somepro.infrastructure.persistence.pigeon;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.pigeon.model.Entry;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.pigeon.repository.EntryRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.pigeon.converter.EntryPoConverter;
import com.somepro.infrastructure.persistence.pigeon.po.EntryPO;
import com.somepro.infrastructure.persistence.pigeon.po.EntryRowPO;
import com.somepro.infrastructure.persistence.pigeon.support.PigeonBlockingRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 集鸽登记仓储适配器。
 */
@Repository
public class EntryRepositoryImpl extends PigeonBlockingRepository implements EntryRepository {

    private final EntryMapper entryMapper;

    public EntryRepositoryImpl(EntryMapper entryMapper) {
        this.entryMapper = entryMapper;
    }

    @Override
    public Mono<Entry> save(Entry entry) {
        return blocking(() -> {
            EntryPO po = EntryPoConverter.toPo(entry);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
            }
            try {
                entryMapper.insert(po);
            } catch (DuplicateKeyException e) {
                // 并发集鸽：应用层的「先查后插」之间被另一笔抢先，uk_race_band 兜底
                throw new BizException("该羽赛鸽本场赛的集鸽登记已存在，重复登记无效");
            }
            return EntryPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Entry> findByRaceAndBand(Long raceId, Long bandId) {
        return blocking(() -> {
            EntryPO po = entryMapper.selectOne(Wrappers.<EntryPO>lambdaQuery()
                    .eq(EntryPO::getRaceId, raceId)
                    .eq(EntryPO::getBandId, bandId)
                    .last("LIMIT 1"));
            return po == null ? null : EntryPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Entry> findById(Long id) {
        return blocking(() -> {
            EntryPO po = entryMapper.selectById(id);
            return po == null ? null : EntryPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<EntryRow>> pageByRace(Long raceId, int pageNum, int pageSize) {
        return this.<PageResult<EntryRow>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                List<EntryRowPO> rows = entryMapper.selectEntryRows(raceId);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<EntryRow> content = rows.stream()
                        .map(EntryPoConverter::toEntryRow)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                // PageHelper 靠 ThreadLocal 传参，boundedElastic 线程复用，必须清理
                PageHelper.clearPage();
            }
        });
    }
}
