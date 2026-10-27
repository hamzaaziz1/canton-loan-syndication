// Bootstraps the synchronizer once. After the first run its identity and
// topology live in postgres, so on restart this is a no-op.
// Writes /tmp/ready at the end; the compose healthcheck waits for it.
def main(): Unit = {
  utils.retry_until_true {
    sequencer1.health.is_running() && mediator1.health.is_running()
  }
  if (!sequencer1.health.initialized()) {
    println("bootstrapping synchronizer loansync")
    bootstrap.synchronizer_local("loansync")
  } else {
    println("synchronizer already initialised, skipping bootstrap")
  }
  java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/ready"), "ok")
}
