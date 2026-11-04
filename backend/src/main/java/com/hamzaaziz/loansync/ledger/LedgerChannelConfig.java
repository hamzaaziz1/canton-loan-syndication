package com.hamzaaziz.loansync.ledger;

import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

/** One TLS channel to this organisation's own participant, verified against the network CA. */
@Configuration
public class LedgerChannelConfig {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel ledgerChannel(@Value("${loansync.ledger.host}") String host,
                                 @Value("${loansync.ledger.port}") int port,
                                 @Value("${loansync.ledger.ca}") File ca) throws IOException {
        var credentials = TlsChannelCredentials.newBuilder().trustManager(ca).build();
        return Grpc.newChannelBuilderForAddress(host, port, credentials).build();
    }
}
