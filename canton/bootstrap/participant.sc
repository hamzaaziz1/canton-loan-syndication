// Generic participant bootstrap, configured by environment:
//   SYNCHRONIZER_URL  sequencer public API to connect to
//   HOSTED_PARTIES    comma-separated parties this node hosts
// Every step checks before it acts, so restarts are safe.
// Uses plain try/catch: importing scala.util breaks the console's script
// compiler, because the canton jar also ships Scala 3 files in that package.
def main(): Unit = {
  val node    = participants.local.head
  val syncUrl = sys.env.getOrElse("SYNCHRONIZER_URL", "http://synchronizer:5008")
  val parties = sys.env.getOrElse("HOSTED_PARTIES", "").split(",").map(_.trim).filter(_.nonEmpty)
  def isActive: Boolean =
    try node.synchronizers.active("loansync") catch { case _: Throwable => false }

  utils.retry_until_true { node.health.initialized() }

  // On a restricted synchronizer, joining fails until the owners grant this
  // node a ParticipantSynchronizerPermission. Stay up and retry rather than
  // exit, so the operator can still reach the admin API meanwhile.
  while (!isActive) {
    try {
      if (node.synchronizers.list_registered().isEmpty) {
        println(s"${node.name}: connecting to loansync at $syncUrl")
        node.synchronizers.connect("loansync", syncUrl)
      } else {
        node.synchronizers.reconnect("loansync")
      }
    } catch { case _: Throwable => () }
    if (!isActive) {
      println(s"${node.name}: not admitted to loansync yet, retrying in 15s")
      Thread.sleep(15000)
    }
  }
  println(s"${node.name}: active on loansync")

  // uploading the same package twice is a no-op
  node.dars.upload("/app/dars/loan-model.dar")

  parties.foreach { p =>
    if (node.parties.hosted(filterParty = p).isEmpty) {
      val id = node.parties.enable(p)
      println(s"${node.name}: allocated $id")
    } else {
      println(s"${node.name}: $p already hosted")
    }
  }
}
