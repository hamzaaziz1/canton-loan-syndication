package com.hamzaaziz.loansync.ledger;

import com.daml.ledger.api.v2.StateServiceGrpc;
import com.daml.ledger.api.v2.StateServiceOuterClass.GetLedgerEndRequest;
import com.daml.ledger.api.v2.admin.PartyManagementServiceGrpc;
import com.daml.ledger.api.v2.admin.PartyManagementServiceOuterClass.ListKnownPartiesRequest;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.MetadataUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Probes one participant's ledger API over TLS, with the access token in
 * LEDGER_TOKEN if set, and reports the outcome of a normal call and an admin call.
 */
public final class LedgerSmoke {

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("LEDGER_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("LEDGER_PORT", "5011"));
        File ca = new File(System.getenv().getOrDefault("LEDGER_CA", "../canton/tls/ca/root-ca.crt"));
        String token = System.getenv("LEDGER_TOKEN");

        var channel = Grpc.newChannelBuilderForAddress(host, port,
                TlsChannelCredentials.newBuilder().trustManager(ca).build()).build();
        try {
            var headers = new Metadata();
            if (token != null && !token.isBlank()) {
                headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token.trim());
            }
            var withToken = MetadataUtils.newAttachHeadersInterceptor(headers);
            var state = StateServiceGrpc.newBlockingStub(channel).withInterceptors(withToken);
            var partyAdmin = PartyManagementServiceGrpc.newBlockingStub(channel).withInterceptors(withToken);

            System.out.printf("port %d, token %s%n", port, token == null || token.isBlank() ? "none" : "present");
            attempt("ledger end", () -> "offset " + state.getLedgerEnd(GetLedgerEndRequest.getDefaultInstance()).getOffset());
            attempt("list parties (admin)", () -> partyAdmin.listKnownParties(ListKnownPartiesRequest.getDefaultInstance())
                    .getPartyDetailsCount() + " parties");
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void attempt(String what, Supplier<String> call) {
        try {
            System.out.printf("  %-22s ok, %s%n", what, call.get());
        } catch (StatusRuntimeException e) {
            System.out.printf("  %-22s refused: %s%n", what, e.getStatus().getCode());
        }
    }
}
