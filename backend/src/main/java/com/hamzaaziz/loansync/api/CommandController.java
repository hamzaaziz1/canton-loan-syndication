package com.hamzaaziz.loansync.api;

import com.daml.ledger.javaapi.data.codegen.Update;
import com.hamzaaziz.loansync.daml.syndication.allocation.FundingCall;
import com.hamzaaziz.loansync.daml.syndication.allocation.Participation;
import com.hamzaaziz.loansync.daml.syndication.facility.DrawdownRequest;
import com.hamzaaziz.loansync.daml.syndication.facility.FacilityAgreement;
import com.hamzaaziz.loansync.ledger.CommandSubmitter;
import com.hamzaaziz.loansync.ledger.CommandSubmitter.Submitted;
import io.grpc.StatusRuntimeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The write side. Each endpoint turns one business action into one atomic
 * ledger transaction, submitted as the signed-in person's own party. Lookups
 * are limited to what that party can see; the ledger re-checks everything.
 */
@RestController
@RequestMapping("/api")
public class CommandController {

    public record DrawdownAmount(@NotNull @Positive BigDecimal amount) {}

    public record DrawdownRequestView(String contractId, String facilityId, BigDecimal amount, long createdOffset) {}

    public record FundingCallView(String contractId, String facilityId, String drawdownRef, String lender,
                                  String currency, BigDecimal amount, long createdOffset) {}

    private record Facility(String contractId, String borrower) {}

    private record PendingRequest(String facilityId, BigDecimal amount, String agent) {}

    private final JdbcTemplate jdbc;
    private final CurrentUser user;
    private final CommandSubmitter ledger;

    public CommandController(JdbcTemplate jdbc, CurrentUser user, CommandSubmitter ledger) {
        this.jdbc = jdbc;
        this.user = user;
        this.ledger = ledger;
    }

    @GetMapping("/drawdown-requests")
    public List<DrawdownRequestView> drawdownRequests() {
        return jdbc.query("""
                SELECT contract_id, payload->>'facilityId' AS facility_id,
                       (payload->>'amount')::numeric AS amount, created_offset
                FROM contract
                WHERE template = 'Syndication.Facility:DrawdownRequest' AND archived_offset IS NULL
                  AND ? = ANY(signatories || observers)
                ORDER BY created_offset DESC""",
                (rs, row) -> new DrawdownRequestView(rs.getString("contract_id"), rs.getString("facility_id"),
                        rs.getBigDecimal("amount"), rs.getLong("created_offset")),
                user.partyId());
    }

    @GetMapping("/funding-calls")
    public List<FundingCallView> fundingCalls() {
        return jdbc.query("""
                SELECT contract_id, payload->>'facilityId' AS facility_id, payload->>'drawdownRef' AS drawdown_ref,
                       split_part(payload->>'lender', '::', 1) AS lender, payload->>'currency' AS currency,
                       (payload->>'amount')::numeric AS amount, created_offset
                FROM contract
                WHERE template = 'Syndication.Allocation:FundingCall' AND archived_offset IS NULL
                  AND ? = ANY(signatories || observers)
                ORDER BY created_offset DESC""",
                (rs, row) -> new FundingCallView(rs.getString("contract_id"), rs.getString("facility_id"),
                        rs.getString("drawdown_ref"), rs.getString("lender"), rs.getString("currency"),
                        rs.getBigDecimal("amount"), rs.getLong("created_offset")),
                user.partyId());
    }

    /** The borrower asks to draw on its facility. */
    @PostMapping("/facilities/{facilityId}/drawdown-requests")
    public Submitted requestDrawdown(@PathVariable String facilityId, @Valid @RequestBody DrawdownAmount body,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var facility = openFacility(facilityId);
        var party = actAs(facility.borrower());
        var update = new FacilityAgreement.ContractId(facility.contractId()).exerciseRequestDrawdown(body.amount());
        return ledger.submit(party, idempotencyKey, List.of(update));
    }

    /**
     * The agent approves a drawdown and, in the same transaction, calls each
     * lender's pro-rata share. The borrower is an informee of the approval
     * only; each lender of its own funding call only.
     */
    @PostMapping("/drawdown-requests/{contractId}/approve")
    public Submitted approveDrawdown(@PathVariable String contractId,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var requests = jdbc.query("""
                SELECT payload->>'facilityId' AS facility_id, (payload->>'amount')::numeric AS amount,
                       payload->>'agent' AS agent
                FROM contract
                WHERE contract_id = ? AND template = 'Syndication.Facility:DrawdownRequest' AND archived_offset IS NULL
                  AND ? = ANY(signatories || observers)""",
                (rs, row) -> new PendingRequest(rs.getString("facility_id"), rs.getBigDecimal("amount"),
                        rs.getString("agent")), contractId, user.partyId());
        if (requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no pending drawdown request " + contractId);
        }
        var request = requests.getFirst();
        var party = actAs(request.agent());
        var facility = openFacility(request.facilityId());
        var participations = jdbc.queryForList("""
                SELECT contract_id FROM contract
                WHERE template = 'Syndication.Allocation:Participation' AND archived_offset IS NULL
                  AND payload->>'facilityId' = ? AND ? = ANY(signatories || observers)""",
                String.class, request.facilityId(), party);
        if (participations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "facility " + request.facilityId() + " has no participations");
        }

        String drawdownRef = request.facilityId() + "/DD-" + contractId.substring(0, 12);
        List<Update<?>> updates = new ArrayList<>();
        updates.add(new DrawdownRequest.ContractId(contractId)
                .exerciseApprove(new FacilityAgreement.ContractId(facility.contractId())));
        participations.forEach(p -> updates.add(
                new Participation.ContractId(p).exerciseCallFunds(drawdownRef, request.amount())));
        return ledger.submit(party, idempotencyKey, updates);
    }

    /** A lender confirms it has funded its share. */
    @PostMapping("/funding-calls/{contractId}/confirm")
    public Submitted confirmFunded(@PathVariable String contractId,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var lenders = jdbc.queryForList("""
                SELECT payload->>'lender' FROM contract
                WHERE contract_id = ? AND template = 'Syndication.Allocation:FundingCall' AND archived_offset IS NULL
                  AND ? = ANY(signatories || observers)""",
                String.class, contractId, user.partyId());
        if (lenders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no open funding call " + contractId);
        }
        var party = actAs(lenders.getFirst());
        return ledger.submit(party, idempotencyKey, List.of(new FundingCall.ContractId(contractId).exerciseConfirmFunded()));
    }

    /** The ledger rejected a command: report its reason rather than a stack trace. */
    @ExceptionHandler(StatusRuntimeException.class)
    public ProblemDetail ledgerRejected(StatusRuntimeException e) {
        return LedgerRejections.toProblem(e);
    }

    private Facility openFacility(String facilityId) {
        var found = jdbc.query("""
                SELECT contract_id, payload->>'borrower' AS borrower FROM contract
                WHERE template = 'Syndication.Facility:FacilityAgreement' AND archived_offset IS NULL
                  AND payload->'terms'->>'facilityId' = ? AND ? = ANY(signatories || observers)""",
                (rs, row) -> new Facility(rs.getString("contract_id"), rs.getString("borrower")),
                facilityId, user.partyId());
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no open facility " + facilityId);
        }
        if (found.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "facility id " + facilityId + " is not unique: " + found.size() + " open facilities share it");
        }
        return found.getFirst();
    }

    /** The person may act only as their own party. */
    private String actAs(String party) {
        if (!party.equals(user.partyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    user.name() + " acts as " + user.partyName() + ", not " + party.split("::")[0]);
        }
        return party;
    }
}
