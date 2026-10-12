#!/usr/bin/env bash
set -euo pipefail
# Operator-owned command on an approved existing host. Initial provisioning is separate.
release=${1:?Tested commit SHA required}
[[ "$release" =~ ^[a-f0-9]{40}$ ]] || exit 2
root=/opt/rockey
# Exact argv allowed by deployment/rockey-deploy.sudoers.template; -n fails instead of prompting.
restart=(sudo -n /usr/bin/systemctl restart rockey.service)
archive="$root/incoming/$release.tgz"
next="$root/releases/$release"
[[ -f "$archive" && ! -e "$next" ]] || { echo 'Missing archive or existing release; refused.'; exit 2; }
# Confirm the scoped restart grant before changing anything.
sudo -n -l /usr/bin/systemctl restart rockey.service >/dev/null 2>&1 || { echo 'Scoped restart permission missing; refused before changes.'; exit 2; }
# Own pipeline regular files only; refuse traversal, symlinks and arbitrary top-level paths.
tar -tzf "$archive" | awk '($0 !~ /^\.\/(app[.]jar$|SHA256SUMS$|REVISION$|frontend(\/|$))/ && $0 != "./") || $0 ~ /(^|\/)\.\.($|\/)/ {bad=1} END {exit bad}'
tar -tvzf "$archive" | awk 'substr($0,1,1)!="-" && substr($0,1,1)!="d" {bad=1} END {exit bad}'
mkdir "$next"
tar -xzf "$archive" --no-same-owner --no-same-permissions -C "$next"
(cd "$next" && sha256sum --check SHA256SUMS)
[[ "$(cat "$next/REVISION")" == "$release" ]] || { echo 'Revision mismatch; refused.'; exit 2; }
previous=$(readlink -f "$root/current")
[[ "$previous" == "$root/releases/"* && -d "$previous" ]] || { echo 'Existing release required for rollback.'; exit 2; }
ln -s "$next" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
rollback() {
  trap - ERR
  ln -sfn "$previous" "$root/current.rollback"
  mv -Tf "$root/current.rollback" "$root/current"
  "${restart[@]}" || { echo 'Previous release link restored, but rockey.service restart failed; operator action required.'; exit 1; }
  echo 'Activation failed; previous application restored. Database was not changed.'
}
trap rollback ERR
"${restart[@]}"
healthy=false
for attempt in {1..30}; do
  status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:8080/api/auth/me || true)
  if [[ "$status" == 401 ]]; then healthy=true; break; fi
  sleep 2
done
if [[ "$healthy" != true ]]; then
  rollback
  exit 1
fi
trap - ERR
printf 'Activated release %s; full end-to-end checks still required.\n' "$release"
