// Governance: the synchronizer owner admits the borrower's participant.
// Signed by sequencer1's namespace key, the only owner of loansync.
// The permission names the participant by id, which includes the hash of its
// root key, so it cannot be claimed by another node using the same name.
import com.digitalasset.canton.topology.transaction.ParticipantPermission

val syncId = agent.synchronizers.id_of("loansync")
if (sequencer1.topology.participant_synchronizer_permissions.find(syncId, borrower.id).isEmpty) {
  sequencer1.topology.participant_synchronizer_permissions.propose(syncId, borrower.id, ParticipantPermission.Submission)
  println(s"granted Submission to ${borrower.id}")
} else {
  println(s"${borrower.id} already allowlisted")
}
