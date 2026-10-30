// Governance: allowlist the existing participants, then restrict onboarding
// to allowlisted participants only. Signed by the synchronizer owner,
// sequencer1's namespace key. Safe to run more than once.
import com.digitalasset.canton.admin.api.client.data.OnboardingRestriction
import com.digitalasset.canton.topology.transaction.ParticipantPermission

val syncId = agent.synchronizers.id_of("loansync")
val owner  = sequencer1

// 1. allowlist first, so restricting cannot lock out existing members
Seq(agent, lendera, lenderb).foreach { p =>
  if (owner.topology.participant_synchronizer_permissions.find(syncId, p.id).isEmpty) {
    owner.topology.participant_synchronizer_permissions.propose(syncId, p.id, ParticipantPermission.Submission)
    println(s"allowlisted ${p.name}")
  } else {
    println(s"${p.name} already allowlisted")
  }
}

// 2. close onboarding to anyone not on the allowlist
val current = owner.topology.synchronizer_parameters.get_dynamic_synchronizer_parameters(syncId)
if (current.onboardingRestriction != OnboardingRestriction.RestrictedOpen) {
  owner.topology.synchronizer_parameters.propose_update(
    syncId, _.copy(onboardingRestriction = OnboardingRestriction.RestrictedOpen)
  )
  println("onboarding restriction set to RestrictedOpen")
} else {
  println("onboarding already RestrictedOpen")
}
