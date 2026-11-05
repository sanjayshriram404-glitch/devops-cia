#!/usr/bin/env bash
# One-time creation of the Azure resources used by the pipeline.
# Run from Git Bash after `az login`.  Cost: AKS free tier + 1 small node + ACR Basic.
# Delete everything afterwards with scripts/azure-cleanup.sh.
set -euo pipefail

RG="${RG:-rg-cia2-cicd}"
LOCATION="${LOCATION:-centralindia}"
ACR_NAME="${ACR_NAME:-cia2acr$RANDOM}"        # must be globally unique, lowercase letters/digits
AKS_NAME="${AKS_NAME:-aks-cia2-cicd}"
NODE_SIZE="${NODE_SIZE:-Standard_B2s}"

echo ">> Registering resource providers"
az provider register --namespace Microsoft.ContainerService --wait
az provider register --namespace Microsoft.ContainerRegistry --wait

echo ">> Resource group $RG ($LOCATION)"
az group create --name "$RG" --location "$LOCATION" -o table

echo ">> Container registry $ACR_NAME"
az acr create --resource-group "$RG" --name "$ACR_NAME" --sku Basic --admin-enabled true -o table

echo ">> AKS cluster $AKS_NAME (takes 5-10 minutes)"
az aks create \
  --resource-group "$RG" \
  --name "$AKS_NAME" \
  --tier free \
  --node-count 1 \
  --node-vm-size "$NODE_SIZE" \
  --generate-ssh-keys \
  -o table

echo ">> Local kubectl context"
az aks get-credentials --resource-group "$RG" --name "$AKS_NAME" --overwrite-existing
kubectl get nodes -o wide

echo
echo "=================  Values for GitHub (Settings > Secrets and variables > Actions)  ================="
echo "Variable ACR_LOGIN_SERVER = $(az acr show -n "$ACR_NAME" --query loginServer -o tsv)"
echo "Variable ACR_USERNAME     = $(az acr credential show -n "$ACR_NAME" --query username -o tsv)"
echo "Secret   ACR_PASSWORD     : az acr credential show -n $ACR_NAME --query 'passwords[0].value' -o tsv"
echo "Secret   KUBE_CONFIG      : az aks get-credentials -g $RG -n $AKS_NAME --admin --file - | base64 -w0"
