package com.somepro.infrastructure.persistence.pigeon;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.somepro.domain.pigeon.model.Race;
import com.somepro.domain.pigeon.repository.RaceRepository;
import com.somepro.infrastructure.persistence.pigeon.converter.RacePoConverter;
import com.somepro.infrastructure.persistence.pigeon.po.RacePO;
import com.somepro.infrastructure.persistence.pigeon.support.PigeonBlockingRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 训放赛项仓储适配器。
 */
@Repository
public class RaceRepositoryImpl extends PigeonBlockingRepository implements RaceRepository {

    private final RaceMapper raceMapper;

    public RaceRepositoryImpl(RaceMapper raceMapper) {
        this.raceMapper = raceMapper;
    }

    @Override
    public Mono<Race> findById(Long id) {
        return blocking(() -> {
            RacePO po = raceMapper.selectById(id);
            return po == null ? null : RacePoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Race> findByRaceCode(String raceCode) {
        return blocking(() -> {
            RacePO po = raceMapper.selectOne(Wrappers.<RacePO>lambdaQuery()
                    .eq(RacePO::getRaceCode, raceCode)
                    .last("LIMIT 1"));
            return po == null ? null : RacePoConverter.toDomain(po);
        });
    }
}
