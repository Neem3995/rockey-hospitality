#!/usr/bin/env bash
set -euo pipefail
# Requires the Jenkins explicit approval stage. Never provisions a host or database.
: "${ROCKEY_DEPLOY_HOST:?Approved host required}" "${ROCKEY_SSH_USER:?SSH user required}" "${ROCKEY_SSH_KEY:?Private key file required}"
[[ "$ROCKEY_DEPLOY_HOST" =~ ^[A-Za-z0-9][A-Za-z0-9.-]+$ ]] || exit 2
[[ "$ROCKEY_SSH_USER" =~ ^[a-z_][a-z0-9_-]*$ ]] || exit 2
release=$(git rev-parse HEAD)
[[ "$release" =~ ^[a-f0-9]{40}$ ]] || exit 2
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || { echo 'Dirty tracked source; refused.'; exit 2; }
ssh_args=(-i "$ROCKEY_SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes)
scp "${ssh_args[@]}" target/rockey-release.tgz "$ROCKEY_SSH_USER@$ROCKEY_DEPLOY_HOST:/opt/rockey/incoming/$release.tgz"
ssh "${ssh_args[@]}" "$ROCKEY_SSH_USER@$ROCKEY_DEPLOY_HOST" /opt/rockey/bin/activate-release "$release"
