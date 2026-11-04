package com.hamzaaziz.loansync.ledger;

import com.daml.ledger.api.v2.StateServiceGrpc;
import com.daml.ledger.api.v2.StateServiceOuterClass.GetActiveContractsRequest;
import com.daml.ledger.api.v2.StateServiceOuterClass.GetLedgerEndRequest;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.CumulativeFilter;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.EventFormat;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.Filters;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.TemplateFilter;
import com.daml.ledger.api.v2.admin.PartyManagementServiceGrpc;
import com.daml.ledger.api.v2.admin.PartyManagementServiceOuterClass.ListKnownPartiesRequest;
import com.daml.ledger.javaapi.data.CreatedEvent;
import com.daml.ledger.javaapi.data.Identifier;
import com.hamzaaziz.loansync.daml.syndication.allocation.Participation;
import com.hamzaaziz.loansync.daml.syndication.facility.FacilityAgreement;
import io.grpc.Grpc;
import io.grpc.TlsChannelCredentials;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** One party's facilities and participations, as seen through its own node, decoded into generated classes. */
public final class ActiveContracts {

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("LEDGER_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("LEDGER_PORT", "5011"));
        String hint = System.getenv().getOrDefault("PARTY", "AgentBank");
        File ca = new File(System.getenv().getOrDefault("LEDGER_CA", "../canton/tls/ca/root-ca.crt"));

        var channel = Grpc.newChannelBuilderForAddress(host, port,
                TlsChannelCredentials.newBuilder().trustManager(ca).build()).build();
        try {
            String party = PartyManagementServiceGrpc.newBlockingStub(channel)
                    .listKnownParties(ListKnownPartiesRequest.getDefaultInstance())
                    .getPartyDetailsList().stream()
                    .filter(p -> p.getIsLocal() && p.getParty().startsWith(hint + "::"))
                    .findFirst().orElseThrow(() -> new IllegalStateException(hint + " is not hosted on port " + port))
                    .getParty();

            var state = StateServiceGrpc.newBlockingStub(channel);
            long end = state.getLedgerEnd(GetLedgerEndRequest.getDefaultInstance()).getOffset();

            var filters = Filters.newBuilder()
                    .addCumulative(templateFilter(FacilityAgreement.TEMPLATE_ID))
                    .addCumulative(templateFilter(Participation.TEMPLATE_ID))
                    .build();
            var request = GetActiveContractsRequest.newBuilder()
                    .setActiveAtOffset(end)
                    .setEventFormat(EventFormat.newBuilder().putFiltersByParty(party, filters))
                    .build();

            int facilities = 0, participations = 0;
            var responses = state.getActiveContracts(request);
            while (responses.hasNext()) {
                var response = responses.next();
                if (!response.hasActiveContract()) continue;
                CreatedEvent event = CreatedEvent.fromProto(response.getActiveContract().getCreatedEvent());
                switch (event.getTemplateId().getEntityName()) {
                    case "FacilityAgreement" -> {
                        var c = FacilityAgreement.COMPANION.fromCreatedEvent(event);
                        facilities++;
                        if (facilities <= 2) System.out.printf("  facility %s  amount %s  drawn %s%n",
                                c.data.terms.facilityId, c.data.facilityAmount, c.data.drawn);
                    }
                    case "Participation" -> {
                        var c = Participation.COMPANION.fromCreatedEvent(event);
                        participations++;
                        if (participations <= 2) System.out.printf("  participation %s  lender %s  amount %s%n",
                                c.data.facilityId, c.data.lender.split("::")[0], c.data.amount);
                    }
                    default -> { }
                }
            }
            System.out.printf("%s at offset %d sees %d facilities and %d participations%n",
                    hint, end, facilities, participations);
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static CumulativeFilter templateFilter(Identifier templateId) {
        return CumulativeFilter.newBuilder()
                .setTemplateFilter(TemplateFilter.newBuilder().setTemplateId(templateId.toProto()))
                .build();
    }
}
