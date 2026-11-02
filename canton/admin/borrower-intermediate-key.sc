// Offline root ceremony, online site: the waiting borrower node generates its
// intermediate namespace key and prints the public half. The private half
// never leaves the node. Reuses the key if it already exists.
val name = "borrower-intermediate"

val key = borrower.keys.public.list()
  .find(_.name.map(_.toString).exists(_.contains(name)))
  .map(_.publicKey)
  .getOrElse(borrower.keys.secret.generate_signing_key(
    name  = name,
    usage = com.digitalasset.canton.crypto.SigningKeyUsage.NamespaceOnly))

val bytes = borrower.keys.public.download(key.id)
println(s"INTERMEDIATE_KEY_ID=${key.id.unwrap}")
println("INTERMEDIATE_PUB_B64=" + java.util.Base64.getEncoder.encodeToString(bytes.toByteArray))
val digest = java.security.MessageDigest.getInstance("SHA-256")
  .digest(bytes.toByteArray).map("%02x".format(_)).mkString
println(s"INTERMEDIATE_PUB_SHA256=$digest")
