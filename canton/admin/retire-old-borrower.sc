// Governance: revoke permissions held by retired borrower identities. The node
// was rebuilt under an offline root key, so its previous identity must not be
// able to rejoin. Signed by the synchronizer owner.
val syncId  = agent.synchronizers.id_of("loansync")
val current = borrower.id
sequencer1.topology.participant_synchronizer_permissions.list(store = syncId)
  .map(_.item.participantId)
  .filter(p => p.toString.contains("::borrower::") && p != current)
  .foreach { old =>
    sequencer1.topology.participant_synchronizer_permissions.revoke(syncId, old)
    println(s"revoked $old")
  }
println(s"current borrower identity: $current")
