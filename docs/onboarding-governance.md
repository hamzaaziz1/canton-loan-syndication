# Onboarding governance

How a participant joins `loansync`, who decides, and how one is removed. Written from the borrower participant's onboarding on 29 and 30 October, and its re-onboarding under an offline root key on 2 November.

## Who decides

The synchronizer's namespace `1220561e...` is a decentralized namespace: a set of owner namespaces and a threshold. On this network it has one owner, sequencer1's namespace `122074e9...`, with threshold 1. Every governance action below is a topology transaction signed by that owner, stored by every member of the synchronizer, and checkable by any of them.

## The rules

The dynamic parameter `onboarding restriction` decides who may join:

| Value | Meaning |
|---|---|
| `UnrestrictedOpen` | any participant that can reach the sequencer can join by signing its own trust certificate |
| `RestrictedOpen` | only participants the owners have granted a `ParticipantSynchronizerPermission` can join |
| `UnrestrictedLocked`, `RestrictedLocked` | no new participants for now |

`loansync` started as `UnrestrictedOpen`: agent, lendera and lenderb joined without any approval. Since 29 October it has been `RestrictedOpen`. On Canton 3.6, tightening the restriction on a running synchronizer was accepted. The owners allowlisted the three existing participants first, so none of them lost access when the rule changed.

## Onboarding a new participant

Two roles: the joining operator and the synchronizer owner.

1. **Joining operator: create the node's identity.** Preferably with an offline root key, as in [identity and trust](identity-and-trust.md): start the node with `init.identity.type = manual`, run the ceremony, import the certificates. The node then has a participant id such as `PAR::borrower::1220002df084...`.
2. **Joining operator: send the participant id to the owner** over a channel the owner can authenticate. The fingerprint is the hash of the node's root key, so a permission granted to it cannot be used by another node with the same name.
3. **Owner: verify and grant.** Confirm the fingerprint through a second channel, then sign the permission (`canton/admin/grant-borrower.sc` is the template). Choose the level the role needs: `Submission`, `Confirmation` or `Observation`.
4. **Joining operator: let the node join.** The bootstrap retries every 15 seconds while it is refused, so the node joins on its next attempt after the grant, then uploads and vets the Daml packages and allocates its parties.
5. **Both: verify.** `make console SCRIPT=trust-chain` should show the participant's `SynchronizerTrustCertificate`, its `ParticipantSynchronizerPermission` and its `VettedPackages`.

## What a refusal looks like

On the joining node:

```
borrower: connecting to loansync at http://synchronizer:5008
INITIAL_ONBOARDING_ERROR(9,...): FAILED_PRECONDITION
  Unable to register onboarding topology transactions
```

The synchronizer does not register the refused node's identity at all, so it never appears in the synchronizer's topology. At info level the synchronizer logs nothing about it either: when onboarding fails, read the joining node's logs.

## Removing a participant

- **Owner: revoke the permission** with `participant_synchronizer_permissions.revoke` (`canton/admin/retire-old-borrower.sc`). A participant without a permission cannot rejoin a restricted synchronizer.
- **Joining operator, before decommissioning: retire the identity while its keys still exist.** Move or retire the parties it hosts, and remove its own trust certificate. On 2 November the borrower node's first identity, `PAR::borrower::12200c22...`, was wiped with its database before this was done. Its own statements, signed by keys that no longer exist, will stay in the topology permanently. Revoking its permission is what made it harmless.

## Changing the governance itself

Adding owners, or raising the threshold, means a new `DecentralizedNamespaceDefinition` authorised by the current owners. This network has not exercised that. A production synchronizer would have owners from several organisations and a threshold above one, so that admitting or removing a member needs more than one signature.

## Timeline on this network

| Date | Event |
|---|---|
| 26 October | `loansync` bootstrapped as `UnrestrictedOpen`; agent, lendera and lenderb join with no approval |
| 29 October | the owner allowlists all three, then sets `RestrictedOpen`; the demo and the privacy proof still pass |
| 29 October | the borrower participant is refused with `INITIAL_ONBOARDING_ERROR` |
| 30 October | the owner grants `PAR::borrower::12200c22...`; it joins within one 15-second retry |
| 2 November | the borrower node is rebuilt under an offline root key as `PAR::borrower::1220002df084...`, refused until granted, and the retired identity's permission is revoked |
