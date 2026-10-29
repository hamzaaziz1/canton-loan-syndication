// The synchronizer's topology state: every signed statement that makes up
// the network's trust chain, grouped by mapping type. Read-only.
def oneLine(a: Any, max: Int = 260): String = {
  val s = a.toString.replaceAll("\\s+", " ")
  if (s.length > max) s.take(max) + " ..." else s
}

val syncId = agent.synchronizers.id_of("loansync")
println(s"synchronizer: $syncId")

val txs = agent.topology.transactions.list(store = syncId).result
txs.groupBy(_.transaction.mapping.code.toString).toSeq.sortBy(_._1).foreach { case (code, group) =>
  println(s"\n========== $code (${group.size})")
  group.foreach { t =>
    println(s"  ${oneLine(t.transaction.mapping)}")
    println(s"    signatures: ${oneLine(t.transaction.signatures, 300)}")
  }
}
