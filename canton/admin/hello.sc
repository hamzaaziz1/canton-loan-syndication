// Smoke test for the remote console: every participant answers
// and lists the parties it hosts.
participants.remote.foreach { p =>
  val hosted = p.parties.hosted().map(_.party.toString)
  println(s"${p.name}: ${p.id}")
  hosted.foreach(h => println(s"  hosts $h"))
}
