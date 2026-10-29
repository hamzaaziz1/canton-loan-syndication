// Read-only: the synchronizer's dynamic parameters, including the onboarding
// rules, and whether each participant holds an explicit permission.
val syncId = agent.synchronizers.id_of("loansync")

println("dynamic synchronizer parameters:")
println(sequencer1.topology.synchronizer_parameters.get_dynamic_synchronizer_parameters(syncId))

println("\nexplicit participant permissions:")
participants.remote.sortBy(_.name).foreach { p =>
  val perm = sequencer1.topology.participant_synchronizer_permissions.find(syncId, p.id)
  println(s"  ${p.name}: $perm")
}
