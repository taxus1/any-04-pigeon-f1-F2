package com.somepro.pigeon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真 MySQL 端到端验证：仅当环境存在可用的 SPRING_DATASOURCE_URL（MySQL）且
 * {@code pigeon.sql} 的 6 张表已建好时运行，否则整个测试类跳过。
 *
 * 测试不做任何 DDL（遵守「表结构别动」），只用 91xxxx/92xxxx/93xxxx 固定测试主键段
 * 的数据增删，先清后插，可重复执行且不影响历史数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration")
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
class PigeonMySqlEndToEndTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime RELEASE = LocalDateTime.parse("2026-09-23T07:00:00");
    private static final LocalDateTime CLOSE = LocalDateTime.parse("2026-09-23T18:00:00");
    private static final String BASIC = "Basic " + Base64.getEncoder()
            .encodeToString("admin:admin123".getBytes(StandardCharsets.UTF_8));

    private static final long RACE = 910001L;
    private static final long RACE_OTHER = 910002L;

    @Autowired
    private WebTestClient web;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeAll
    static void mysqlOnlyAndTablesReady() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        assumeTrue(url != null && url.startsWith("jdbc:mysql"), "环境无 MySQL，跳过真库端到端");
        try (Connection c = DriverManager.getConnection(url, "root", "root")) {
            for (String table : new String[]{
                    "t_band", "t_race", "t_entry", "t_clocking", "t_result"}) {
                try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), null, table,
                        new String[]{"TABLE"})) {
                    assumeTrue(rs.next(), "缺少表 " + table + "（pigeon.sql 未初始化），跳过真库端到端");
                }
            }
        }
    }

    private void exec(String sql, Object... args) {
        jdbc.update(sql, args);
    }

    @org.junit.jupiter.api.BeforeEach
    void cleanAndSeed() {
        jdbc.update("DELETE FROM t_result WHERE race_id IN (?, ?)", RACE, RACE_OTHER);
        jdbc.update("DELETE FROM t_clocking WHERE race_id IN (?, ?)", RACE, RACE_OTHER);
        jdbc.update("DELETE FROM t_entry WHERE race_id IN (?, ?)", RACE, RACE_OTHER);
        jdbc.update("DELETE FROM t_band WHERE id BETWEEN 920001 AND 920099");
        jdbc.update("DELETE FROM t_race WHERE id IN (?, ?)", RACE, RACE_OTHER);

        exec("INSERT INTO t_race(id, race_code, title, release_site, release_at, close_at, distance_km, status) "
                + "VALUES (?, 'XF-T-0001', '测试300公里', '鹤壁', ?, ?, 300.000, 'RELEASED')", RACE, RELEASE, CLOSE);
        exec("INSERT INTO t_race(id, race_code, title, release_site, release_at, distance_km, status) "
                + "VALUES (?, 'XF-T-0002', '测试另一场', '新乡', ?, 200.000, 'RELEASED')", RACE_OTHER, RELEASE);
        band(920001L, "CHN-T-000001", "张三", "ACTIVE");
        band(920002L, "CHN-T-000002", "李四", "ACTIVE");
        band(920003L, "CHN-T-000003", "王五", "ACTIVE");
        band(920004L, "CHN-T-000004", "赵六", "ACTIVE");
        band(920005L, "CHN-T-000005", "钱七", "SUSPENDED");
        entry(930001L, RACE, 920001L);
        entry(930002L, RACE, 920002L);
        entry(930003L, RACE, 920003L);
        entry(930005L, RACE, 920005L);
    }

    private void band(long id, String code, String owner, String status) {
        exec("INSERT INTO t_band(id, band_code, band_year, owner_name, loft_city, status) "
                + "VALUES (?, ?, 2026, ?, '北京', ?)", id, code, owner, status);
    }

    private void entry(long id, long race, long band) {
        exec("INSERT INTO t_entry(id, race_id, band_id, basket_no) VALUES (?, ?, ?, ?)",
                id, race, band, "B" + id);
    }

    private String clockJson(String code, LocalDateTime at, String source) throws Exception {
        return om.writeValueAsString(java.util.Map.of(
                "raceId", RACE, "bandCode", code, "clockAt", FMT.format(at), "source", source));
    }

    private JsonNode clock(String code, LocalDateTime at, String source) {
        try {
            String body = web.post().uri("/api/pigeon/clocking")
                    .header("Authorization", BASIC)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(clockJson(code, at, source))
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode score() {
        try {
            String body = web.post().uri("/api/pigeon/races/" + RACE + "/score")
                    .header("Authorization", BASIC)
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode rank(int pageNum, int pageSize) {
        try {
            String body = web.get().uri("/api/pigeon/races/" + RACE + "/rank?pageNum=" + pageNum + "&pageSize=" + pageSize)
                    .header("Authorization", BASIC)
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode entryPost(String raceCode, String bandCode, String basketNo, LocalDateTime at) {
        try {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("raceCode", raceCode);
            m.put("bandCode", bandCode);
            m.put("basketNo", basketNo);
            if (at != null) {
                m.put("entryTime", FMT.format(at));
            }
            String body = web.post().uri("/api/pigeon/entries")
                    .header("Authorization", BASIC)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(om.writeValueAsString(m))
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode entries(String raceCode, int pageNum, int pageSize) {
        try {
            String body = web.get().uri("/api/pigeon/races/" + raceCode + "/entries?pageNum=" + pageNum
                            + "&pageSize=" + pageSize)
                    .header("Authorization", BASIC)
                    .exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            return om.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mysqlEntryFlow() {
        LocalDateTime at = RELEASE.minusHours(12);

        // 在档 ACTIVE：赵六收进本场
        JsonNode ok = entryPost("XF-T-0001", "CHN-T-000004", "A-04", at);
        assertEquals(0, ok.get("code").asInt(), ok.toString());
        assertEquals("A-04", ok.get("data").get("basketNo").asText());
        assertEquals(FMT.format(at), ok.get("data").get("entryTime").asText());
        assertEquals("A-04", jdbc.queryForObject(
                "SELECT basket_no FROM t_entry WHERE race_id = ? AND band_id = 920004",
                String.class, RACE));

        // 同羽同场再报：打回，库行不增
        JsonNode dup = entryPost("XF-T-0001", "CHN-T-000004", "A-99", at.plusMinutes(5));
        assertEquals(1, dup.get("code").asInt());
        assertTrue(dup.get("msg").asText().contains("重复登记"));

        // 停赛 / 注销不收
        assertEquals(1, entryPost("XF-T-0001", "CHN-T-000005", "A-05", at).get("code").asInt());
        band(920006L, "CHN-T-000006", "周九", "RETIRED");
        JsonNode retired = entryPost("XF-T-0001", "CHN-T-000006", "A-06", at);
        assertEquals(1, retired.get("code").asInt());
        assertTrue(retired.get("msg").asText().contains("RETIRED"));

        // 不在档的足环 / 不存在的赛项编号
        JsonNode noBand = entryPost("XF-T-0001", "CHN-T-999999", "A-07", at);
        assertEquals(1, noBand.get("code").asInt());
        assertTrue(noBand.get("msg").asText().contains("足环不存在"));
        JsonNode noRace = entryPost("XF-T-9999", "CHN-T-000004", "A-07", at);
        assertEquals(1, noRace.get("code").asInt());
        assertTrue(noRace.get("msg").asText().contains("赛项不存在"));

        // 同羽报进另一场：允许，且两场清单互不串场
        assertEquals(0, entryPost("XF-T-0002", "CHN-T-000004", "C-04", at).get("code").asInt());
        JsonNode listRace = entries("XF-T-0001", 1, 100);
        assertEquals(0, listRace.get("code").asInt());
        // 预置 3 笔（001/002/003）+ 新收赵六 = 4
        assertEquals(4, listRace.get("data").get("total").asInt());
        JsonNode rows = listRace.get("data").get("content");
        assertEquals("A-04", rows.get(0).get("basketNo").asText());
        assertEquals("CHN-T-000004", rows.get(0).get("bandCode").asText());
        assertEquals("赵六", rows.get(0).get("ownerName").asText());

        JsonNode listOther = entries("XF-T-0002", 1, 100);
        assertEquals(1, listOther.get("data").get("total").asInt());
        assertEquals("C-04", listOther.get("data").get("content").get(0).get("basketNo").asText());

        // 审计字段由 MetaObjectHandler 填充
        assertEquals("admin", jdbc.queryForObject(
                "SELECT create_by FROM t_entry WHERE race_id = ? AND band_id = 920004",
                String.class, RACE));
    }

    @Test
    void mysqlFullFlow() {
        assertEquals(0, clock("CHN-T-000002", RELEASE.plusHours(4), "SCAN").get("code").asInt());
        assertEquals(0, clock("CHN-T-000001", RELEASE.plusHours(5), "MANUAL").get("code").asInt());
        assertEquals(0, clock("CHN-T-000003", RELEASE.plusHours(6), "SCAN").get("code").asInt());

        // 重复报到
        JsonNode dup = clock("CHN-T-000002", RELEASE.plusHours(5), "SCAN");
        assertEquals(1, dup.get("code").asInt());
        assertTrue(dup.get("msg").asText().contains("已报过到"));

        // 早于开笼 / 晚于关门（赵六补集鸽）
        entry(930004L, RACE, 920004L);
        assertEquals(1, clock("CHN-T-000004", RELEASE.minusSeconds(1), "SCAN").get("code").asInt());
        assertEquals(1, clock("CHN-T-000004", CLOSE.plusSeconds(1), "SCAN").get("code").asInt());
        assertEquals(0, clock("CHN-T-000004", CLOSE, "SCAN").get("code").asInt());

        // 停赛
        JsonNode suspended = clock("CHN-T-000005", RELEASE.plusHours(5), "SCAN");
        assertEquals(1, suspended.get("code").asInt());

        // 出成绩：MySQL DECIMAL(12,2) 必须真保留两位
        JsonNode sc = score();
        assertEquals(0, sc.get("code").asInt(), sc.toString());
        assertEquals(4, sc.get("data").size());
        // 直接查库验证标度与数值
        assertEquals(new BigDecimal("1250.00"), jdbc.queryForObject(
                "SELECT speed_mpm FROM t_result WHERE race_id = ? AND entry_id = 930002",
                BigDecimal.class, RACE));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT rank_no FROM t_result WHERE race_id = ? AND entry_id = 930002",
                Integer.class, RACE));

        // 重算覆盖：物理行数不翻倍，且仍只有一套
        score();
        assertEquals(Integer.valueOf(4), jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_result WHERE race_id = ?", Integer.class, RACE));

        // 榜与库一致 + 分页
        JsonNode page1 = rank(1, 2);
        assertEquals(4, page1.get("data").get("total").asInt());
        assertEquals(2, page1.get("data").get("totalPages").asInt());
        JsonNode c = page1.get("data").get("content");
        assertEquals("CHN-T-000002", c.get(0).get("bandCode").asText());
        assertEquals("李四", c.get(0).get("ownerName").asText());
        // JsonNode 把数字读成 double，用数值比对（原始 HTTP 报文实测为 1250.00）
        assertEquals(0, new BigDecimal("1250.00").compareTo(c.get(0).get("speedMpm").decimalValue()));
        assertEquals(1, c.get(0).get("rankNo").asInt());
        assertEquals("2026-09-23 11:00:00", c.get(0).get("clockAt").asText());

        // 时间格式带上秒、且全榜逐行对库
        JsonNode all = rank(1, 100).get("data").get("content");
        jdbc.query("SELECT r.rank_no, r.speed_mpm, b.band_code FROM t_result r "
                + "JOIN t_entry e ON e.id = r.entry_id JOIN t_band b ON b.id = e.band_id "
                + "WHERE r.race_id = ? ORDER BY r.rank_no", (ResultSet rs) -> {
            int idx = rs.getInt("rank_no") - 1;
            assertEquals(rs.getString("band_code"), all.get(idx).get("bandCode").asText());
            assertEquals(0, rs.getBigDecimal("speed_mpm").compareTo(
                    new BigDecimal(all.get(idx).get("speedMpm").asText())));
        }, RACE);
    }

}
