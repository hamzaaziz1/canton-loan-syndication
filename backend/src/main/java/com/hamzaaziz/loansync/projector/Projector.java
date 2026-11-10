package com.hamzaaziz.loansync.projector;

import com.daml.ledger.api.v2.TransactionFilterOuterClass.CumulativeFilter;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.EventFormat;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.Filters;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.TransactionFormat;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.TransactionShape;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.UpdateFormat;
import com.daml.ledger.api.v2.TransactionFilterOuterClass.WildcardFilter;
import com.daml.ledger.api.v2.TransactionOuterClass.Transaction;
import com.daml.ledger.api.v2.UpdateServiceGrpc;
import com.daml.ledger.api.v2.UpdateServiceOuterClass.GetUpdatesRequest;
import com.daml.ledger.javaapi.data.CreatedEvent;
import com.hamzaaziz.loansync.ledger.Parties;
import com.hamzaaziz.loansync.ledger.TokenInterceptor;
import io.grpc.ManagedChannel;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Streams this organisation's updates into the read model. Each transaction's
 * creates, archives and the new checkpoint commit in one database transaction,
 * so a restart resumes exactly where the read model ends.
 */
@Component
public class Projector {

    private static final Logger log = LoggerFactory.getLogger(Projector.class);

    private final ManagedChannel channel;
    private final TokenInterceptor token;
    private final Parties parties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private volatile boolean running = true;
    private Thread worker;

    public Projector(ManagedChannel channel, TokenInterceptor token, Parties parties, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.channel = channel;
        this.token = token;
        this.parties = parties;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker = Thread.ofVirtual().name("projector").start(this::runUntilStopped);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    private void runUntilStopped() {
        while (running) {
            try {
                streamFrom(savedOffset());
            } catch (RuntimeException e) {
                if (!running) return;
                log.warn("update stream interrupted, resuming from the checkpoint in 5s: {}", e.getMessage());
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    private long savedOffset() {
        var rows = jdbc.queryForList("SELECT ledger_offset FROM checkpoint WHERE id = 1", Long.class);
        return rows.isEmpty() ? 0L : rows.getFirst();
    }

    private void streamFrom(long offset) {
        var everything = Filters.newBuilder()
                .addCumulative(CumulativeFilter.newBuilder().setWildcardFilter(WildcardFilter.getDefaultInstance()))
                .build();
        var events = EventFormat.newBuilder().setVerbose(true);
        parties.ids().forEach(party -> events.putFiltersByParty(party, everything));

        var request = GetUpdatesRequest.newBuilder()
                .setBeginExclusive(offset)
                .setUpdateFormat(UpdateFormat.newBuilder().setIncludeTransactions(TransactionFormat.newBuilder()
                        .setEventFormat(events)
                        .setTransactionShape(TransactionShape.TRANSACTION_SHAPE_ACS_DELTA)))
                .build();

        log.info("projecting updates for {} from offset {}", parties.names(), offset);
        var updates = UpdateServiceGrpc.newBlockingStub(channel).withInterceptors(token).getUpdates(request);
        while (running && updates.hasNext()) {
            var update = updates.next();
            if (update.hasTransaction()) {
                apply(update.getTransaction());
            } else if (update.hasOffsetCheckpoint()) {
                saveOffset(update.getOffsetCheckpoint().getOffset());
            }
        }
    }

    private void apply(Transaction transaction) {
        long offset = transaction.getOffset();
        tx.executeWithoutResult(status -> {
            for (var event : transaction.getEventsList()) {
                if (event.hasCreated()) {
                    insert(CreatedEvent.fromProto(event.getCreated()), offset);
                } else if (event.hasArchived()) {
                    jdbc.update("UPDATE contract SET archived_offset = ? WHERE contract_id = ? AND archived_offset IS NULL",
                            offset, event.getArchived().getContractId());
                }
            }
            saveOffset(offset);
        });
    }

    private void insert(CreatedEvent created, long offset) {
        var template = created.getTemplateId().getModuleName() + ":" + created.getTemplateId().getEntityName();
        jdbc.update("""
                INSERT INTO contract (contract_id, template, payload, signatories, observers, created_offset)
                VALUES (?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (contract_id) DO NOTHING""",
                created.getContractId(), template, ValueJson.toJson(created.getArguments()),
                created.getSignatories().toArray(String[]::new), created.getObservers().toArray(String[]::new),
                offset);
    }

    private void saveOffset(long offset) {
        jdbc.update("""
                INSERT INTO checkpoint (id, ledger_offset, updated_at) VALUES (1, ?, now())
                ON CONFLICT (id) DO UPDATE SET ledger_offset = EXCLUDED.ledger_offset, updated_at = now()""",
                offset);
    }
}
