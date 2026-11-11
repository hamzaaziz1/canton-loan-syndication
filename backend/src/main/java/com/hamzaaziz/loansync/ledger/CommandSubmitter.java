package com.hamzaaziz.loansync.ledger;

import com.daml.ledger.api.v2.CommandServiceGrpc;
import com.daml.ledger.api.v2.CommandServiceOuterClass.SubmitAndWaitRequest;
import com.daml.ledger.api.v2.CommandsOuterClass.Commands;
import com.daml.ledger.javaapi.data.Command;
import com.daml.ledger.javaapi.data.codegen.Update;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Submits one or more updates as a single atomic transaction and waits for the
 * result. A caller-supplied command id makes the submission idempotent: the
 * participant deduplicates commands by id.
 */
@Component
public class CommandSubmitter {

    public record Submitted(String updateId, long offset) {}

    private final ManagedChannel channel;
    private final TokenInterceptor token;
    private final String user;

    public CommandSubmitter(ManagedChannel channel, TokenInterceptor token,
                            @Value("${loansync.ledger.user}") String user) {
        this.channel = channel;
        this.token = token;
        this.user = user;
    }

    public Submitted submit(String actAs, String commandId, List<? extends Update<?>> updates) {
        var commands = updates.stream()
                .flatMap(update -> update.commands().stream())
                .map(Command::toProtoCommand)
                .toList();
        var request = SubmitAndWaitRequest.newBuilder()
                .setCommands(Commands.newBuilder()
                        .setUserId(user)
                        .setCommandId(commandId == null || commandId.isBlank() ? UUID.randomUUID().toString() : commandId)
                        .addActAs(actAs)
                        .addAllCommands(commands))
                .build();
        var response = CommandServiceGrpc.newBlockingStub(channel).withInterceptors(token).submitAndWait(request);
        return new Submitted(response.getUpdateId(), response.getCompletionOffset());
    }
}
