package com.hamzaaziz.loansync.ledger;

import com.daml.ledger.api.v2.admin.PartyManagementServiceGrpc;
import com.daml.ledger.api.v2.admin.PartyManagementServiceOuterClass.ListKnownPartiesRequest;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** The parties this instance acts for, resolved to full ids on this organisation's own node. */
@Component
public class Parties {

    private final ManagedChannel channel;
    private final List<String> hints;
    private volatile List<String> ids;

    public Parties(ManagedChannel channel, @Value("${loansync.parties}") List<String> hints) {
        this.channel = channel;
        this.hints = hints;
    }

    public List<String> hints() {
        return hints;
    }

    /** Fails if a configured party is not hosted on this node, rather than reading someone else's data. */
    public synchronized List<String> ids() {
        if (ids == null) {
            var local = PartyManagementServiceGrpc.newBlockingStub(channel)
                    .listKnownParties(ListKnownPartiesRequest.getDefaultInstance())
                    .getPartyDetailsList().stream()
                    .filter(p -> p.getIsLocal())
                    .map(p -> p.getParty())
                    .toList();
            ids = hints.stream()
                    .map(h -> local.stream().filter(p -> p.startsWith(h + "::")).findFirst()
                            .orElseThrow(() -> new IllegalStateException(h + " is not hosted on this participant")))
                    .toList();
        }
        return ids;
    }
}
