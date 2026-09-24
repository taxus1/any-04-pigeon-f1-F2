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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集鸽端到端验证（H2 MySQL 兼容模式，无需 Docker/MySQL）：
 * 在档校验、RETIRED/SUSPENDED 拒收、同场重复打回、跨场允许、清单按赛项隔离 + 笼筐排序 + 分页。
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
class PigeonEntryEndToEndTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String RACE_CODE = "XF-2026-018";
    private static final String OTHER_CODE = "XF-2026-019";

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

        LocalDateTime release = LocalDateTime.parse("2026-09-24T07:00:00");
        jdbc.update("INSERT INTO t_race(id, race_code, title, release_site, release_at, distance_km, status) "
                + "VALUES (1001, ?, '9月24日300公里', '鹤壁', ?, 300.000, 'DRAFT')", RACE_CODE, release);
        jdbc.update("INSERT INTO t_race(id, race_code, title, release_site, release_at, distance_km, status) "
                + "VALUES (1002, ?, '另一场', '新乡', ?, 200.000, 'DRAFT')", OTHER_CODE, release);
        insertBand(201L, "CHN2026-A-000201", "张三", "ACTIVE");
        insertBand(202L, "CHN2026-A-000202", "李四", "ACTIVE");
        insertBand(203L, "CHN2026-A-000203", "王五", "ACTIVE");
        insertBand(204L, "CHN2026-A-000204", "赵六", "SUSPENDED");
        insertBand(205L, "CHN2026-A-000205", "钱七", "RETIRED");
    }

    private void insertBand(long id, String code, String owner, String status) {
        jdbc.update("INSERT INTO t_band(id, band_code, band_year, owner_name, loft_city, status) "
                + "VALUES (?, ?, 2026, ?, '北京', ?)", id, code, owner, status);
    }

    private JsonNode checkIn(String raceCode, String bandCode, String basketNo, LocalDateTime entryTime)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("raceCode", raceCode);
        body.put("bandCode", bandCode);
        body.put("basketNo", basketNo);
        if (entryTime != null) {
            body.put("entryTime", FMT.format(entryTime));
        }
        String resp = web.post().uri("/api/pigeon/entries")
                .header("Authorization", BASIC)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(om.writeValueAsString(body))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        return om.readTree(resp);
    }

    private JsonNode listEntries(String raceCode, int pageNum, int pageSize) throws Exception {
        String resp = web.get().uri("/api/pigeon/races/" + raceCode + "/entries?pageNum=" + pageNum
                        + "&pageSize=" + pageSize)
                .header("Authorization", BASIC)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return om.readTree(resp);
    }

    private int countEntries(long raceId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_entry WHERE race_id = ?", Integer.class, raceId);
    }

    @Test
    void happyPath_entryLandsInDbWithBasketAndTime() throws Exception {
        LocalDateTime at = LocalDateTime.parse("2026-09-23T19:30:00");
        JsonNode ok = checkIn(RACE_CODE, "CHN2026-A-000201", "A-01", at);
        assertEquals(0, ok.get("code").asInt(), ok.toString());
        assertEquals("A-01", ok.get("data").get("basketNo").asText());
        assertEquals("2026-09-23 19:30:00", ok.get("data").get("entryTime").asText());
        assertNotNull(ok.get("data").get("id").asText());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT race_id, band_id, basket_no, entry_time FROM t_entry WHERE race_id = 1001");
        assertEquals(1001L, ((Number) row.get("race_id")).longValue());
        assertEquals(201L, ((Number) row.get("band_id")).longValue());
        assertEquals("A-01", row.get("basket_no"));
        assertNotNull(row.get("entry_time"));
    }

    @Test
    void entryTimeOmittedIsRecordedAsReceiveTime() throws Exception {
        JsonNode ok = checkIn(RACE_CODE, "CHN2026-A-000201", "A-02", null);
        assertEquals(0, ok.get("code").asInt(), ok.toString());
        assertNotNull(ok.get("data").get("entryTime").asText());
        assertNotNull(jdbc.queryForObject(
                "SELECT entry_time FROM t_entry WHERE race_id = 1001 AND band_id = 201", java.sql.Timestamp.class));
    }

    @Test
    void retiredAndSuspendedAreRejected() throws Exception {
        JsonNode suspended = checkIn(RACE_CODE, "CHN2026-A-000204", "A-03", null);
        assertEquals(1, suspended.get("code").asInt());
        assertTrue(suspended.get("msg").asText().contains("SUSPENDED"), suspended.get("msg").asText());

        JsonNode retired = checkIn(RACE_CODE, "CHN2026-A-000205", "A-04", null);
        assertEquals(1, retired.get("code").asInt());
        assertTrue(retired.get("msg").asText().contains("RETIRED"), retired.get("msg").asText());

        assertEquals(0, countEntries(1001L));
    }

    @Test
    void duplicateSameRaceRejectedButOtherRaceAllowed() throws Exception {
        // 同羽两场赛都报：都收
        assertEquals(0, checkIn(RACE_CODE, "CHN2026-A-000201", "A-01",
                LocalDateTime.parse("2026-09-23T19:00:00")).get("code").asInt());
        assertEquals(0, checkIn(OTHER_CODE, "CHN2026-A-000201", "C-09",
                LocalDateTime.parse("2026-09-23T20:00:00")).get("code").asInt());
        assertEquals(1, countEntries(1001L));
        assertEquals(1, countEntries(1002L));

        // 同羽同场再报一遍：打回，库里不多
        JsonNode dup = checkIn(RACE_CODE, "CHN2026-A-000201", "A-99",
                LocalDateTime.parse("2026-09-23T19:05:00"));
        assertEquals(1, dup.get("code").asInt());
        assertTrue(dup.get("msg").asText().contains("重复登记"), dup.get("msg").asText());
        assertEquals(1, countEntries(1001L));
        // 手滑那次的筐号没有覆盖原记录
        assertEquals("A-01", jdbc.queryForObject(
                "SELECT basket_no FROM t_entry WHERE race_id = 1001 AND band_id = 201", String.class));
    }

    @Test
    void unknownBandOrRaceRejected() throws Exception {
        JsonNode noBand = checkIn(RACE_CODE, "CHN2099-X-000000", "A-05", null);
        assertEquals(1, noBand.get("code").asInt());
        assertTrue(noBand.get("msg").asText().contains("足环不存在"));

        JsonNode noRace = checkIn("XF-9999-9999", "CHN2026-A-000201", "A-05", null);
        assertEquals(1, noRace.get("code").asInt());
        assertTrue(noRace.get("msg").asText().contains("赛项不存在"));

        assertEquals(0, countEntries(1001L));
    }

    @Test
    void entryListScopedToRaceOrderedByBasketAndPaged() throws Exception {
        LocalDateTime t = LocalDateTime.parse("2026-09-23T19:00:00");
        // 本场 3 羽，筐号乱序给，验排序
        checkIn(RACE_CODE, "CHN2026-A-000203", "A-03", t.plusMinutes(3));
        checkIn(RACE_CODE, "CHN2026-A-000201", "A-01", t.plusMinutes(1));
        checkIn(RACE_CODE, "CHN2026-A-000202", "A-02", t.plusMinutes(2));
        // 另一场 1 羽，验不串场
        checkIn(OTHER_CODE, "CHN2026-A-000201", "C-09", t);

        JsonNode page1 = listEntries(RACE_CODE, 1, 2);
        assertEquals(0, page1.get("code").asInt(), page1.toString());
        assertEquals(3, page1.get("data").get("total").asInt());
        assertEquals(2, page1.get("data").get("totalPages").asInt());
        JsonNode content1 = page1.get("data").get("content");
        assertEquals(2, content1.size());
        checkRow(content1.get(0), "CHN2026-A-000201", "张三", "A-01");
        checkRow(content1.get(1), "CHN2026-A-000202", "李四", "A-02");

        JsonNode page2 = listEntries(RACE_CODE, 2, 2);
        JsonNode content2 = page2.get("data").get("content");
        assertEquals(1, content2.size());
        checkRow(content2.get(0), "CHN2026-A-000203", "王五", "A-03");

        // 另一场清单只翻得到另一场自己的那笔
        JsonNode other = listEntries(OTHER_CODE, 1, 20);
        assertEquals(1, other.get("data").get("total").asInt());
        checkRow(other.get("data").get("content").get(0), "CHN2026-A-000201", "张三", "C-09");

        // 不存在的赛项
        JsonNode missing = listEntries("XF-9999-9999", 1, 20);
        assertEquals(1, missing.get("code").asInt());
        assertTrue(missing.get("msg").asText().contains("赛项不存在"));
    }

    @Test
    void blankRequiredFieldsRejected() throws Exception {
        String resp = web.post().uri("/api/pigeon/entries")
                .header("Authorization", BASIC)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        JsonNode json = om.readTree(resp);
        assertEquals(1, json.get("code").asInt());
        assertNotNull(json.get("msg").asText());
        assertEquals(0, countEntries(1001L));
    }

    private void checkRow(JsonNode row, String bandCode, String owner, String basketNo) {
        assertEquals(bandCode, row.get("bandCode").asText());
        assertEquals(owner, row.get("ownerName").asText());
        assertEquals(basketNo, row.get("basketNo").asText());
        assertNotNull(row.get("entryTime").asText());
    }
}
