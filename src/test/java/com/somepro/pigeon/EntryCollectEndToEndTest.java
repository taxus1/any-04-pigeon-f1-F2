package com.somepro.pigeon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集鸽端到端验证（H2 MySQL 兼容模式）：
 * 登记四关（赛项编号 / 档案存在 / 在赛状态 / 同场不重登）+ 清单按赛项对筐不串场。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pigeon_entry;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:schema-h2.sql",
                "pagehelper.helper-dialect=h2",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
        })
@AutoConfigureWebTestClient
@ActiveProfiles("h2")
class EntryCollectEndToEndTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime COLLECT_AT = LocalDateTime.parse("2026-09-24T08:30:00");

    @Autowired
    private WebTestClient web;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final String BASIC = "Basic " + Base64.getEncoder()
            .encodeToString("admin:admin123".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void seed() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE t_band (
                    id BIGINT NOT NULL PRIMARY KEY, band_code VARCHAR(32) NOT NULL,
                    band_year INT NOT NULL, owner_name VARCHAR(64) NOT NULL,
                    loft_city VARCHAR(64), status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    del_flag TINYINT NOT NULL DEFAULT 0,
                    create_by VARCHAR(64), create_time DATETIME,
                    update_by VARCHAR(64), update_time DATETIME,
                    CONSTRAINT uk_band_code UNIQUE (band_code))
                """);
        jdbc.execute("""
                CREATE TABLE t_race (
                    id BIGINT NOT NULL PRIMARY KEY, race_code VARCHAR(32) NOT NULL,
                    title VARCHAR(128) NOT NULL, release_site VARCHAR(128) NOT NULL,
                    release_at DATETIME NOT NULL, close_at DATETIME,
                    distance_km DECIMAL(8,3) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
                    del_flag TINYINT NOT NULL DEFAULT 0,
                    create_by VARCHAR(64), create_time DATETIME,
                    update_by VARCHAR(64), update_time DATETIME,
                    CONSTRAINT uk_race_code UNIQUE (race_code))
                """);
        jdbc.execute("""
                CREATE TABLE t_entry (
                    id BIGINT NOT NULL PRIMARY KEY, race_id BIGINT NOT NULL, band_id BIGINT NOT NULL,
                    basket_no VARCHAR(16), entry_time DATETIME,
                    del_flag TINYINT NOT NULL DEFAULT 0,
                    create_by VARCHAR(64), create_time DATETIME,
                    update_by VARCHAR(64), update_time DATETIME,
                    CONSTRAINT uk_race_band UNIQUE (race_id, band_id))
                """);
        jdbc.execute("""
                CREATE TABLE t_clocking (
                    id BIGINT NOT NULL PRIMARY KEY, entry_id BIGINT NOT NULL, race_id BIGINT NOT NULL,
                    clock_at DATETIME NOT NULL, source VARCHAR(16) NOT NULL DEFAULT 'SCAN',
                    del_flag TINYINT NOT NULL DEFAULT 0,
                    create_by VARCHAR(64), create_time DATETIME,
                    update_by VARCHAR(64), update_time DATETIME,
                    CONSTRAINT uk_entry UNIQUE (entry_id))
                """);
        jdbc.execute("""
                CREATE TABLE t_result (
                    id BIGINT NOT NULL PRIMARY KEY, race_id BIGINT NOT NULL, entry_id BIGINT NOT NULL,
                    speed_mpm DECIMAL(12,2) NOT NULL, rank_no INT,
                    del_flag TINYINT NOT NULL DEFAULT 0,
                    create_by VARCHAR(64), create_time DATETIME,
                    update_by VARCHAR(64), update_time DATETIME,
                    CONSTRAINT uk_race_entry UNIQUE (race_id, entry_id))
                """);

        jdbc.update("INSERT INTO t_race(id, race_code, title, release_site, release_at, distance_km, status) "
                + "VALUES (1001, 'XF-2026-018', '9月24日300公里', '鹤壁', ?, 300.000, 'DRAFT')", COLLECT_AT.plusHours(2));
        jdbc.update("INSERT INTO t_race(id, race_code, title, release_site, release_at, distance_km, status) "
                + "VALUES (1002, 'XF-2026-019', '另一场', '新乡', ?, 200.000, 'DRAFT')", COLLECT_AT.plusHours(2));
        band(201L, "CHN2026-A-000201", "张三", "ACTIVE");
        band(202L, "CHN2026-A-000202", "李四", "ACTIVE");
        band(203L, "CHN2026-A-000203", "王五", "RETIRED");
        band(204L, "CHN2026-A-000204", "赵六", "SUSPENDED");
    }

    private void band(long id, String code, String owner, String status) {
        jdbc.update("INSERT INTO t_band(id, band_code, band_year, owner_name, loft_city, status) "
                + "VALUES (?, ?, 2026, ?, '北京', ?)", id, code, owner, status);
    }

    private JsonNode collect(Map<String, Object> body) {
        try {
            String resp = web.post().uri("/api/pigeon/entries")
                    .header("Authorization", BASIC)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(om.writeValueAsString(body))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode collect(String raceCode, String bandCode, String basket, LocalDateTime at) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("raceCode", raceCode);
        body.put("bandCode", bandCode);
        if (basket != null) {
            body.put("basketNo", basket);
        }
        if (at != null) {
            body.put("entryTime", FMT.format(at));
        }
        return collect(body);
    }

    private JsonNode list(String raceCode, int pageNum, int pageSize) {
        try {
            String resp = web.get()
                    .uri("/api/pigeon/races/entries?raceCode=" + raceCode
                            + "&pageNum=" + pageNum + "&pageSize=" + pageSize)
                    .header("Authorization", BASIC)
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void activeBandCollectedLandsOneRow() {
        JsonNode ok = collect("XF-2026-018", "CHN2026-A-000201", "A-12", COLLECT_AT);
        assertEquals(0, ok.get("code").asInt(), ok.toString());
        assertEquals("A-12", ok.get("data").get("basketNo").asText());
        assertEquals("2026-09-24 08:30:00", ok.get("data").get("entryTime").asText());
        assertNotNull(ok.get("data").get("id").asText());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT race_id, band_id, basket_no, entry_time FROM t_entry WHERE race_id = 1001");
        assertEquals(1001L, ((Number) row.get("race_id")).longValue());
        assertEquals(201L, ((Number) row.get("band_id")).longValue());
        assertEquals("A-12", row.get("basket_no"));
    }

    @Test
    void missingEntryTimeDefaultsToServerNow() {
        JsonNode ok = collect("XF-2026-018", "CHN2026-A-000201", "A-13", null);
        assertEquals(0, ok.get("code").asInt(), ok.toString());
        assertNotNull(ok.get("data").get("entryTime").asText());
    }

    @Test
    void retiredAndSuspendedBandsRejected() {
        JsonNode retired = collect("XF-2026-018", "CHN2026-A-000203", "A-1", COLLECT_AT);
        assertEquals(1, retired.get("code").asInt());
        assertTrue(retired.get("msg").asText().contains("RETIRED"), retired.get("msg").asText());

        JsonNode suspended = collect("XF-2026-018", "CHN2026-A-000204", "A-2", COLLECT_AT);
        assertEquals(1, suspended.get("code").asInt());
        assertTrue(suspended.get("msg").asText().contains("SUSPENDED"), suspended.get("msg").asText());

        assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_entry", Integer.class));
    }

    @Test
    void unknownBandAndUnknownRaceRejected() {
        JsonNode noBand = collect("XF-2026-018", "CHN2099-X-000000", "A-1", COLLECT_AT);
        assertEquals(1, noBand.get("code").asInt());
        assertTrue(noBand.get("msg").asText().contains("足环不存在"));

        JsonNode noRace = collect("XF-2026-9999", "CHN2026-A-000201", "A-1", COLLECT_AT);
        assertEquals(1, noRace.get("code").asInt());
        assertTrue(noRace.get("msg").asText().contains("赛项不存在"));

        assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_entry", Integer.class));
    }

    @Test
    void duplicateEntrySameRaceRejectedButOtherRaceAllowed() {
        assertEquals(0, collect("XF-2026-018", "CHN2026-A-000201", "A-1", COLLECT_AT).get("code").asInt());

        // 手滑报两遍：打回，库里不多
        JsonNode dup = collect("XF-2026-018", "CHN2026-A-000201", "A-9", COLLECT_AT.plusMinutes(5));
        assertEquals(1, dup.get("code").asInt());
        assertTrue(dup.get("msg").asText().contains("重复登记"), dup.get("msg").asText());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_entry WHERE race_id = 1001 AND band_id = 201", Integer.class));

        // 同一羽报另一场赛：允许（不同场次不算重复）
        JsonNode otherRace = collect("XF-2026-019", "CHN2026-A-000201", "B-1", COLLECT_AT);
        assertEquals(0, otherRace.get("code").asInt(), otherRace.toString());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_entry WHERE race_id = 1002", Integer.class));
    }

    @Test
    void blankFieldsRejectedAtValidationLayer() {
        JsonNode resp = collect(java.util.Map.of("raceCode", "", "bandCode", "CHN2026-A-000201"));
        assertEquals(1, resp.get("code").asInt());
    }

    @Test
    void entryListScopedByRaceAndPagedByBasket() {
        // 本场赛 2 羽，另一场赛 1 羽
        collect("XF-2026-018", "CHN2026-A-000202", "B-02", COLLECT_AT.plusMinutes(2));
        collect("XF-2026-018", "CHN2026-A-000201", "B-01", COLLECT_AT.plusMinutes(1));
        collect("XF-2026-019", "CHN2026-A-000201", "C-01", COLLECT_AT);

        JsonNode page = list("XF-2026-018", 1, 20);
        assertEquals(0, page.get("code").asInt(), page.toString());
        assertEquals(2, page.get("data").get("total").asInt());
        JsonNode rows = page.get("data").get("content");
        assertEquals(2, rows.size());
        // 按笼筐号升序：B-01 在 B-02 前，别场的 C-01 不混进来
        assertEquals("B-01", rows.get(0).get("basketNo").asText());
        assertEquals("CHN2026-A-000201", rows.get(0).get("bandCode").asText());
        assertEquals("张三", rows.get(0).get("ownerName").asText());
        assertEquals("B-02", rows.get(1).get("basketNo").asText());
        assertEquals("李四", rows.get(1).get("ownerName").asText());

        // 翻另一场：只看得到那一场的
        JsonNode other = list("XF-2026-019", 1, 20);
        assertEquals(1, other.get("data").get("total").asInt());
        assertEquals("C-01", other.get("data").get("content").get(0).get("basketNo").asText());

        // 分页
        JsonNode tiny = list("XF-2026-018", 1, 1);
        assertEquals(2, tiny.get("data").get("totalPages").asInt());
        assertEquals(1, tiny.get("data").get("content").size());
    }

    @Test
    void entryListOnMissingRaceGivesBizMessage() {
        JsonNode resp = list("XF-NOPE", 1, 20);
        assertEquals(1, resp.get("code").asInt());
        assertTrue(resp.get("msg").asText().contains("赛项不存在"));
    }
}
