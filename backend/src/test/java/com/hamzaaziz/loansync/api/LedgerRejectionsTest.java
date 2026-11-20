package com.hamzaaziz.loansync.api;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerRejectionsTest {

    private static ProblemDetail rejected(Status.Code code, String description) {
        return LedgerRejections.toProblem(Status.fromCode(code).withDescription(description).asRuntimeException());
    }

    @Test
    void aDamlAssertionBecomesItsOwnMessage() {
        // captured from Canton 3.6 when a drawdown exceeded the available amount
        var raw = "DAML_FAILURE(9,1e37fcb2): Interpretation error: Error: User failure: UNHANDLED_EXCEPTION/"
                + "DA.Exception.AssertionFailed:AssertionFailed (error category 9): exceeds available amount";
        var problem = rejected(Status.Code.FAILED_PRECONDITION, raw);
        assertEquals(409, problem.getStatus());
        assertEquals("exceeds available amount", problem.getDetail());
        assertEquals(raw, problem.getProperties().get("ledgerError"));
    }

    @Test
    void anArchivedContractAsksThePersonToRefresh() {
        var problem = rejected(Status.Code.NOT_FOUND, "CONTRACT_NOT_FOUND(11,ab12cd34): Contract could not be found");
        assertEquals(409, problem.getStatus());
        assertEquals("The contract was already used or archived. Refresh and try again.", problem.getDetail());
    }

    @Test
    void aRepeatedIdempotencyKeyIsReportedAsAlreadySubmitted() {
        var problem = rejected(Status.Code.ALREADY_EXISTS, "DUPLICATE_COMMAND(10,ab12cd34): A command with the given command id has already been processed");
        assertEquals(409, problem.getStatus());
        assertEquals("This action was already submitted.", problem.getDetail());
    }

    @Test
    void anUnreachableLedgerIsABadGatewayAndKeepsItsText() {
        var problem = rejected(Status.Code.UNAVAILABLE, "io exception");
        assertEquals(502, problem.getStatus());
        assertEquals("io exception", problem.getDetail());
    }
}
