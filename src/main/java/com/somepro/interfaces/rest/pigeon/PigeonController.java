package com.somepro.interfaces.rest.pigeon;

import com.somepro.common.Result;
import com.somepro.application.pigeon.ClockingAppService;
import com.somepro.application.pigeon.EntryAppService;
import com.somepro.application.pigeon.RaceResultAppService;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.pigeon.model.EntryRow;
import com.somepro.domain.pigeon.model.RankRow;
import com.somepro.interfaces.rest.pigeon.converter.PigeonVoConverter;
import com.somepro.interfaces.rest.pigeon.vo.ClockInRequest;
import com.somepro.interfaces.rest.pigeon.vo.ClockingVO;
import com.somepro.interfaces.rest.pigeon.vo.EntryCheckInRequest;
import com.somepro.interfaces.rest.pigeon.vo.EntryRowVO;
import com.somepro.interfaces.rest.pigeon.vo.EntryVO;
import com.somepro.interfaces.rest.pigeon.vo.PageVO;
import com.somepro.interfaces.rest.pigeon.vo.RaceResultVO;
import com.somepro.interfaces.rest.pigeon.vo.RankRowVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 赛鸽训放线（用户接口层）：
 * 1. POST /api/pigeon/entries                     集鸽登记（开赛前收鸽记一笔）
 * 2. GET  /api/pigeon/races/{raceCode}/entries    集鸽清单（按赛项，秘书对筐）
 * 3. POST /api/pigeon/clocking                    归巢报到录入
 * 4. POST /api/pigeon/races/{raceId}/score        出成绩（算分速、排名次；重算覆盖）
 * 5. GET  /api/pigeon/races/{raceId}/rank         名次榜分页
 *
 * Controller 只做协议适配与 VO 转换，业务编排在应用层，领域对象不直接出参。
 */
@RestController
@RequestMapping("/api/pigeon")
public class PigeonController {

    private final EntryAppService entryAppService;
    private final ClockingAppService clockingAppService;
    private final RaceResultAppService raceResultAppService;

    public PigeonController(EntryAppService entryAppService,
                            ClockingAppService clockingAppService,
                            RaceResultAppService raceResultAppService) {
        this.entryAppService = entryAppService;
        this.clockingAppService = clockingAppService;
        this.raceResultAppService = raceResultAppService;
    }

    /** 1. 集鸽登记：哪羽鸽（足环号）报进哪场赛（赛项编号）、笼筐号、收鸽时间。 */
    @PostMapping("/entries")
    public Mono<Result<EntryVO>> checkIn(@Valid @RequestBody Mono<EntryCheckInRequest> requestMono) {
        return requestMono
                .flatMap(req -> entryAppService.checkIn(
                        req.raceCode(), req.bandCode(), req.basketNo(), req.entryTime()))
                .map(PigeonVoConverter::toEntryVo)
                .map(Result::ok);
    }

    /** 2. 集鸽清单：按赛项编号翻页，只出这一场赛的登记。 */
    @GetMapping("/races/{raceCode}/entries")
    public Mono<Result<PageVO<EntryRowVO>>> entries(@PathVariable String raceCode,
                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        Mono<PageResult<EntryRow>> page = entryAppService.pageEntries(raceCode, pageNum, pageSize);
        return page
                .map(PigeonVoConverter::toEntryPageVo)
                .map(Result::ok);
    }

    /** 3. 归巢报到：集过鸽的鸽子回来记一笔。 */
    @PostMapping("/clocking")
    public Mono<Result<ClockingVO>> clockIn(@Valid @RequestBody Mono<ClockInRequest> requestMono) {
        return requestMono
                .flatMap(req -> clockingAppService.clockIn(
                        req.raceId(), req.bandCode(), req.clockAt(), req.source()))
                .map(PigeonVoConverter::toClockingVo)
                .map(Result::ok);
    }

    /** 4. 出成绩：分速 + 名次；以最新一次为准（旧成绩整事务覆盖）。 */
    @PostMapping("/races/{raceId}/score")
    public Mono<Result<List<RaceResultVO>>> score(@PathVariable Long raceId) {
        return raceResultAppService.scoreRace(raceId)
                .map(PigeonVoConverter::toResultVoList)
                .map(Result::ok);
    }

    /** 5. 名次榜：按赛项翻页，每行足环号/鸽主/归巢时刻/分速/名次。 */
    @GetMapping("/races/{raceId}/rank")
    public Mono<Result<PageVO<RankRowVO>>> rank(@PathVariable Long raceId,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        Mono<PageResult<RankRow>> page = raceResultAppService.pageRank(raceId, pageNum, pageSize);
        return page
                .map(PigeonVoConverter::toRankPageVo)
                .map(Result::ok);
    }
}
