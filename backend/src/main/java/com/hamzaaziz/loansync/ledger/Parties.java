package com.hamzaaziz.loansync.ledger;

import com.daml.ledger.api.v2.admin.UserManagementServiceGrpc;
import com.daml.ledger.api.v2.admin.UserManagementServiceOuterClass.ListUserRightsRequest;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The parties this instance acts for: whatever its own ledger API user may act
 * as, as granted on this organisation's node. The node decides, not a config file.
 */
@Component
public class Parties {

    private final ManagedChannel channel;
    private final TokenInterceptor token;
    private final String user;
    private volatile List<String> ids;

    public Parties(ManagedChannel channel, TokenInterceptor token, @Value("${loansync.ledger.user}") String user) {
        this.channel = channel;
        this.token = token;
        this.user = user;
    }

    public synchronized List<String> ids() {
        if (ids == null) {
            var granted = UserManagementServiceGrpc.newBlockingStub(channel).withInterceptors(token)
                    .listUserRights(ListUserRightsRequest.newBuilder().setUserId(user).build())
                    .getRightsList().stream()
                    .filter(right -> right.hasCanActAs())
                    .map(right -> right.getCanActAs().getParty())
                    .sorted()
                    .toList();
            if (granted.isEmpty()) {
                throw new IllegalStateException("ledger user " + user + " may not act as any party on this node");
            }
            ids = granted;
        }
        return ids;
    }

    /** Display names: the part of each party id before the namespace. */
    public List<String> names() {
        return ids().stream().map(id -> id.split("::")[0]).toList();
    }
}
