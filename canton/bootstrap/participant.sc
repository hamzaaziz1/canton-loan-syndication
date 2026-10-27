// Generic participant bootstrap, configured by environment:
//   SYNCHRONIZER_URL  sequencer public API to connect to
//   HOSTED_PARTIES    comma-separated parties this node hosts
// Every step checks before it acts, so restarts are safe.
def main(): Unit = {
  val node    = participants.local.head
  val syncUrl = sys.env.getOrElse("SYNCHRONIZER_URL", "http://synchronizer:5008")
  val parties = sys.env.getOrElse("HOSTED_PARTIES", "").split(",").map(_.trim).filter(_.nonEmpty)

  utils.retry_until_true { node.health.initialized() }

  if (node.synchronizers.list_registered().isEmpty) {
    println(s"${node.name}: connecting to loansync at $syncUrl")
    node.synchronizers.connect("loansync", syncUrl)
  }
  utils.retry_until_true { node.synchronizers.active("loansync") }

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
