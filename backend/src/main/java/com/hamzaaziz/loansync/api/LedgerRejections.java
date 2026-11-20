package com.hamzaaziz.loansync.api;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.regex.Pattern;

/**
 * Turns a ledger rejection into a problem a person can read. A Daml assertion's
 * own message becomes the detail; the full ledger error is kept alongside it
 * in "ledgerError" for operators.
 */
final class LedgerRejections {

    // ...UNHANDLED_EXCEPTION/DA.Exception.AssertionFailed:AssertionFailed (error category 9): exceeds available amount
    private static final Pattern ASSERTION = Pattern.compile("AssertionFailed \\(error category \\d+\\): (.+)", Pattern.DOTALL);

    private LedgerRejections() {
    }

    static ProblemDetail toProblem(StatusRuntimeException e) {
        var status = e.getStatus();
        var description = status.getDescription() == null ? status.getCode().name() : status.getDescription();

        var assertion = ASSERTION.matcher(description);
        if (assertion.find()) {
            return problem(HttpStatus.CONFLICT, "Rejected by the Daml model", assertion.group(1).trim(), description);
        }
        if (description.startsWith("CONTRACT_NOT_FOUND")) {
            return problem(HttpStatus.CONFLICT, "Contract no longer active",
                    "The contract was already used or archived. Refresh and try again.", description);
        }
        if (description.startsWith("DUPLICATE_COMMAND")) {
            return problem(HttpStatus.CONFLICT, "Already submitted", "This action was already submitted.", description);
        }
        var httpStatus = switch (status.getCode()) {
            case INVALID_ARGUMENT, FAILED_PRECONDITION, NOT_FOUND, ALREADY_EXISTS, ABORTED -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return problem(httpStatus, "Rejected by the ledger", description, description);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String ledgerError) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("ledgerError", ledgerError);
        return problem;
    }
}
