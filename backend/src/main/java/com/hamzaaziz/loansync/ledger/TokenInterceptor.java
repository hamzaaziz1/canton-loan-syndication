package com.hamzaaziz.loansync.ledger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adds this instance's ledger API token to every call. The token file is
 * re-read on each call, so tokens refreshed by the token sink are picked up
 * without a restart. Canton caps token lifetime at five minutes.
 */
@Component
public class TokenInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Path tokenFile;

    public TokenInterceptor(@Value("${loansync.ledger.token-file}") String tokenFile) {
        this.tokenFile = Path.of(tokenFile);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions options, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            public void start(Listener<RespT> listener, Metadata headers) {
                headers.put(AUTHORIZATION, "Bearer " + currentToken());
                super.start(listener, headers);
            }
        };
    }

    private String currentToken() {
        try {
            return Files.readString(tokenFile).trim();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the ledger API token from " + tokenFile, e);
        }
    }
}
