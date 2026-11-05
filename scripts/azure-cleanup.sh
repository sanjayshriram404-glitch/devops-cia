#!/usr/bin/env bash
# Deletes every Azure resource created by azure-setup.sh (stops all charges).
set -euo pipefail
RG="${RG:-rg-cia2-cicd}"
az group delete --name "$RG" --yes --no-wait
echo "Deletion of $RG started. Check with: az group show -n $RG"
