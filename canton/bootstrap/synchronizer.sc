// Bootstraps the synchronizer once. After the first run its identity and
// topology live in postgres, so on restart this is a no-op.
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
}
