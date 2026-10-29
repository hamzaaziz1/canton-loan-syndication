// Identity report: who each node is, which public keys it holds, and the
// topology statements in its store. Read-only.
def oneLine(a: Any, max: Int = 320): String = {
  val s = a.toString.replaceAll("\\s+", " ")
  if (s.length > max) s.take(max) + " ..." else s
}

def section(title: String): Unit = println(s"\n========== $title")

sequencers.remote.foreach { s =>
  section(s"${s.name}: ${s.id}")
  println("public keys:")
  s.keys.public.list().foreach(k => println(s"  ${oneLine(k)}"))
  println("topology:")
  s.topology.transactions.list().result.foreach(t => println(s"  ${oneLine(t.transaction.mapping)}"))
}

mediators.remote.foreach { m =>
  section(s"${m.name}: ${m.id}")
  println("public keys:")
  m.keys.public.list().foreach(k => println(s"  ${oneLine(k)}"))
}

participants.remote.sortBy(_.name).foreach { p =>
  section(s"${p.name}: ${p.id}")
  println("public keys:")
  p.keys.public.list().foreach(k => println(s"  ${oneLine(k)}"))
  println("connected synchronizers:")
  p.synchronizers.list_connected().foreach(c => println(s"  ${oneLine(c)}"))
}
