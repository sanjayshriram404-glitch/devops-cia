# CIA 2: End-to-End CI/CD Pipeline on Azure

A Spring Boot web app built with **Maven**, packaged with **Docker**, pushed to **Azure Container Registry (ACR)**, and deployed to **Azure Kubernetes Service (AKS)** by **Ansible**. **GitHub Actions** runs all of it on every push to `main`.

```
git push ─► GitHub Actions
             ├─ 1 build-test : checkout ─► mvn clean verify (compile, 8 unit tests, package jar)
             ├─ 2 docker     : docker build ─► tag :<sha> & :latest ─► push to ACR
             └─ 3 deploy     : kubeconfig ─► ansible-playbook deploy.yml ─► AKS rolling update
                                ─► rollout status ─► curl http://<EXTERNAL-IP>/api/info
```

## Repository layout

| Path | Purpose |
|---|---|
| `src/main/java/...` | Spring Boot app (`/`, `/api/info`, `/api/greet`, `/actuator/health`) |
| `src/test/java/...` | JUnit 5 + MockMvc unit tests |
| `pom.xml` | Maven build |
| `Dockerfile`, `.dockerignore` | Runtime image (Temurin 17 JRE, non-root UID 10001) |
| `k8s/` | Namespace, Deployment (2 replicas, probes, limits), LoadBalancer Service |
| `ansible/` | `deploy.yml` playbook, `inventory.ini`, `requirements.yml` |
| `.github/workflows/ci-cd.yml` | The pipeline |
| `scripts/azure-setup.sh` / `azure-cleanup.sh` | Create / delete the Azure resources |

## 1. Run locally

```bash
mvn -B clean verify
java -jar target/cicd-demo.jar          # http://localhost:8080
docker build -t cicd-demo:local .
docker run --rm -p 8080:8080 cicd-demo:local
```

## 2. Create Azure resources (Git Bash)

```bash
az login
bash scripts/azure-setup.sh
```

Or run the commands one at a time:

```bash
az group create -n rg-cia2-cicd -l centralindia
az acr create -g rg-cia2-cicd -n <acrname> --sku Basic --admin-enabled true
az aks create -g rg-cia2-cicd -n aks-cia2-cicd --tier free --node-count 1 --node-vm-size Standard_B2s_v2 --generate-ssh-keys
az aks get-credentials -g rg-cia2-cicd -n aks-cia2-cicd
kubectl get nodes
```

## 3. Push to GitHub

```bash
git add .
git commit -m "Initial CI/CD pipeline"
git remote add origin https://github.com/<user>/cia2-cicd-pipeline.git
git push -u origin main
```

## 4. Pipeline settings in GitHub

**Settings → Secrets and variables → Actions**

| Type | Name | Value |
|---|---|---|
| Variable | `ACR_LOGIN_SERVER` | `az acr show -n <acrname> --query loginServer -o tsv` |
| Variable | `ACR_USERNAME` | `az acr credential show -n <acrname> --query username -o tsv` |
| Secret | `ACR_PASSWORD` | `az acr credential show -n <acrname> --query "passwords[0].value" -o tsv` |
| Secret | `KUBE_CONFIG` | `az aks get-credentials -g rg-cia2-cicd -n aks-cia2-cicd --admin --file - \| base64 -w0` |

Then **Actions → CI/CD Pipeline → Run workflow** (or push any commit).

## 5. Verify the deployment

```bash
az acr repository show-tags -n <acrname> --repository cicd-demo -o table
kubectl -n cicd-demo get deploy,pods,svc -o wide
kubectl -n cicd-demo rollout history deployment/cicd-demo
kubectl -n cicd-demo get svc cicd-demo -o jsonpath="{.status.loadBalancer.ingress[0].ip}"
curl http://<EXTERNAL-IP>/api/info
```

## 6. Change and redeploy demo

Edit `src/main/resources/application.properties`:

```properties
app.version=1.1.0
app.message=Version 1.1.0 - change shipped through the pipeline
```

```bash
git commit -am "Bump to v1.1.0"
git push
```

The pipeline runs on its own, pushes image `:<new-sha>`, and Ansible rolls the Deployment. Refresh `http://<EXTERNAL-IP>/`: the version badge and commit change.

```bash
kubectl -n cicd-demo get pods -o jsonpath="{..image}"
```

## 7. Clean up (stops Azure charges)

```bash
bash scripts/azure-cleanup.sh
```

## Running Ansible manually

Ansible doesn't run natively on Windows, so use WSL, Linux, or a container:

```bash
pip install ansible-core kubernetes
cd ansible
ansible-galaxy collection install -r requirements.yml
ansible-playbook deploy.yml -e image=<acr>.azurecr.io/cicd-demo:<tag> -e build_sha=<tag> \
  -e registry_server=<acr>.azurecr.io -e registry_username=<user> -e registry_password=<pw>
```
