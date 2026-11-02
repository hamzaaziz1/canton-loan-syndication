// Read-only: the sequencer connection every client currently uses.
def oneLine(a: Any): String = a.toString.replaceAll("\\s+", " ")
println(s"mediator1: ${oneLine(mediator1.sequencer_connection.get())}")
participants.remote.sortBy(_.name).foreach { p =>
  println(s"${p.name}: ${oneLine(p.synchronizers.config("loansync").map(_.sequencerConnections))}")
}
