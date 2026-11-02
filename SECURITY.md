# Security

## Commit signing

Every commit on `main` from November 2026 onwards is signed with an SSH signing key, and the branch rejects unsigned commits and force pushes. GitHub shows these commits as Verified. A commit attributed to me without that badge did not come from me.

## Opening this repository

This repository does not ship a `.vscode/tasks.json`. If a clone of it, or of any repository, contains a task that runs on folder open, do not trust the workspace in VS Code or Cursor until you have read what that task executes.

## Reporting a problem

Please use GitHub's private vulnerability reporting on this repository rather than a public issue.
