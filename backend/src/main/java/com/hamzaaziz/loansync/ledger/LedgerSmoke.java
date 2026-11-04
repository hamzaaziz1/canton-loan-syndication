package com.hamzaaziz.loansync.ledger;

import com.daml.ledger.api.v2.StateServiceGrpc;
import com.daml.ledger.api.v2.StateServiceOuterClass.GetLedgerEndRequest;
import com.daml.ledger.api.v2.admin.PartyManagementServiceGrpc;
import com.daml.ledger.api.v2.admin.PartyManagementServiceOuterClass.ListKnownPartiesRequest;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Connects to one participant's ledger API over TLS, pinning the network's dev CA. */
public final class LedgerSmoke {

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("LEDGER_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("LEDGER_PORT", "5011"));
        File ca = new File(System.getenv().getOrDefault("LEDGER_CA", "../canton/tls/ca/root-ca.crt"));

        var credentials = TlsChannelCredentials.newBuilder().trustManager(ca).build();
        ManagedChannel channel = Grpc.newChannelBuilderForAddress(host, port, credentials).build();
        try {
            var state = StateServiceGrpc.newBlockingStub(channel);
            long ledgerEnd = state.getLedgerEnd(GetLedgerEndRequest.getDefaultInstance()).getOffset();
            System.out.printf("connected to %s:%d over TLS, ledger end offset %d%n", host, port, ledgerEnd);

            var parties = PartyManagementServiceGrpc.newBlockingStub(channel)
                    .listKnownParties(ListKnownPartiesRequest.getDefaultInstance());
            parties.getPartyDetailsList().stream()
                    .filter(p -> p.getIsLocal())
                    .forEach(p -> System.out.println("  hosts " + p.getParty()));
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
