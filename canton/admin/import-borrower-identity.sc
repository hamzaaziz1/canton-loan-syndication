// Offline root ceremony, online site: import the certificates signed by the
// offline root key. The node takes that root's namespace without ever holding
// the root's private key.
if (!borrower.health.initialized()) {
  borrower.topology.init_id(
    identifier      = "borrower",
    delegationFiles = Seq("/app/certs/root_namespace.cert", "/app/certs/intermediate_namespace.cert"))
  println("imported identity from the offline root ceremony")
} else {
  println("node already has an identity")
}
utils.retry_until_true { borrower.health.initialized() }
println(s"BORROWER_ID=${borrower.id}")
