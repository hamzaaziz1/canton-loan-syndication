package com.hamzaaziz.loansync.api;

import com.hamzaaziz.loansync.ledger.Parties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only views over this organisation's read model. Serves only what this organisation's node received. */
@RestController
@RequestMapping("/api")
public class ReadModelController {

    public record Status(String organisation, List<String> parties, Long ledgerOffset) {}

    public record Facility(String contractId, String facilityId, String borrower, String currency,
                           BigDecimal facilityAmount, BigDecimal drawn, LocalDate maturity, long createdOffset) {}

    public record Participation(String contractId, String facilityId, String lender, String currency,
                                BigDecimal amount, BigDecimal sharePercent, long createdOffset) {}

    private final JdbcTemplate jdbc;
    private final Parties parties;
    private final String organisation;

    public ReadModelController(JdbcTemplate jdbc, Parties parties, @Value("${loansync.org}") String organisation) {
        this.jdbc = jdbc;
        this.parties = parties;
        this.organisation = organisation;
    }

    @GetMapping("/status")
    public Status status() {
        var offsets = jdbc.queryForList("SELECT ledger_offset FROM checkpoint WHERE id = 1", Long.class);
        return new Status(organisation, parties.names(), offsets.isEmpty() ? null : offsets.getFirst());
    }

    @GetMapping("/facilities")
    public List<Facility> facilities() {
        return jdbc.query("""
                SELECT contract_id,
                       payload->'terms'->>'facilityId'          AS facility_id,
                       split_part(payload->>'borrower', '::', 1) AS borrower,
                       payload->'terms'->>'currency'            AS currency,
                       (payload->>'facilityAmount')::numeric    AS facility_amount,
                       (payload->>'drawn')::numeric             AS drawn,
                       (payload->'terms'->>'maturity')::date    AS maturity,
                       created_offset
                FROM contract
                WHERE template = 'Syndication.Facility:FacilityAgreement' AND archived_offset IS NULL
                ORDER BY created_offset DESC""",
                (rs, row) -> new Facility(rs.getString("contract_id"), rs.getString("facility_id"),
                        rs.getString("borrower"), rs.getString("currency"), rs.getBigDecimal("facility_amount"),
                        rs.getBigDecimal("drawn"), rs.getObject("maturity", LocalDate.class),
                        rs.getLong("created_offset")));
    }

    @GetMapping("/participations")
    public List<Participation> participations() {
        return jdbc.query("""
                SELECT contract_id,
                       payload->>'facilityId'                  AS facility_id,
                       split_part(payload->>'lender', '::', 1) AS lender,
                       payload->>'currency'                    AS currency,
                       (payload->>'amount')::numeric           AS amount,
                       round(100 * (payload->>'amount')::numeric / (payload->>'facilityAmount')::numeric, 2) AS share_percent,
                       created_offset
                FROM contract
                WHERE template = 'Syndication.Allocation:Participation' AND archived_offset IS NULL
                ORDER BY created_offset DESC""",
                (rs, row) -> new Participation(rs.getString("contract_id"), rs.getString("facility_id"),
                        rs.getString("lender"), rs.getString("currency"), rs.getBigDecimal("amount"),
                        rs.getBigDecimal("share_percent"), rs.getLong("created_offset")));
    }
}
