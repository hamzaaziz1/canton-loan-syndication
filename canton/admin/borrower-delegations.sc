// Shows the borrower node's namespace delegations and which keys it holds.
// The root certificate's signer should be a key the node does not have.
val held = borrower.keys.secret.list().map(_.toString.replaceAll("\\s+", " ").take(160))
println("keys held by the node:")
held.foreach(k => println(s"  $k"))
println("namespace delegations in the node's store:")
borrower.topology.transactions.list().result
  .filter(_.transaction.mapping.code.toString.contains("Namespace"))
  .foreach(t => println(s"  ${t.transaction.mapping.toString.replaceAll("\\s+", " ").take(260)}"))
