#!/usr/bin/env bash
# Lance les tests d'integration bases sur Testcontainers (JpaGameRepositoryIntegrationTest,
# StompFlowIntegrationTest, ...) sur la VM Docker distante, faute de Docker local sur les
# machines de dev Windows du projet. Voir CLAUDE.md ("Docker distant") pour le contexte.
#
# Usage :
#   scripts/test-integration-remote.sh                # toute la suite (mvnw test)
#   scripts/test-integration-remote.sh com.guesschess.infrastructure.websocket.StompFlowIntegrationTest
#   scripts/test-integration-remote.sh com.guesschess.**IntegrationTest   # plusieurs classes (glob Maven)
#
# A lancer depuis la racine du repo (utilise pom.xml, mvnw, .mvn, src/main, src/test relatifs
# au repertoire courant).

set -uo pipefail

REMOTE_HOST="ubuntu@35.180.147.199"
REMOTE_KEY="$HOME/.ssh/guesschess-dev-docker.pem"
REMOTE_DIR="guesschess-verify-$$"
IMAGE_TAG="guesschess-mvn-runner:25"
TEST_FILTER="${1:-}"

ssh_remote() {
  ssh -i "$REMOTE_KEY" -o ConnectTimeout=10 "$REMOTE_HOST" "$@"
}

echo "==> Image runner (Java 25 + unzip/curl pour le wrapper Maven), construite une seule fois et reutilisee..."
ssh_remote "docker image inspect $IMAGE_TAG >/dev/null 2>&1 || { echo 'FROM eclipse-temurin:25-jdk' | cat - <(echo 'RUN apt-get update -qq && apt-get install -y -qq unzip curl && rm -rf /var/lib/apt/lists/*') | docker build -t $IMAGE_TAG -; }"
if [ $? -ne 0 ]; then
  echo "Echec de construction/verification de l'image runner distante." >&2
  exit 1
fi

echo "==> Copie du backend (pom.xml, mvnw, .mvn, src/main, src/test) vers ~/$REMOTE_DIR sur la VM..."
tar -cf - pom.xml mvnw .mvn src/main src/test \
  | ssh_remote "mkdir -p ~/$REMOTE_DIR && tar -xf - -C ~/$REMOTE_DIR && chmod +x ~/$REMOTE_DIR/mvnw"
if [ $? -ne 0 ]; then
  echo "Echec de la copie des sources vers la VM." >&2
  exit 1
fi

MVN_GOAL="test"
if [ -n "$TEST_FILTER" ]; then
  MVN_GOAL="-Dtest=$TEST_FILTER -DfailIfNoTests=false test"
fi

echo "==> Execution ($([ -n "$TEST_FILTER" ] && echo "filtre: $TEST_FILTER" || echo "suite complete"))..."
# --network host : indispensable pour que ce conteneur (qui lance a son tour, via le socket
# Docker de l'hote monte, des conteneurs Testcontainers "freres" sur ce meme hote) les
# atteigne via leurs ports publies sur "localhost" - sans ca, "localhost" pointerait vers le
# namespace reseau isole du conteneur runner, pas vers celui de l'hote ou les ports sont reellement publies.
ssh_remote "docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v ~/$REMOTE_DIR:/app \
  -v ~/.m2:/root/.m2 \
  -w /app \
  $IMAGE_TAG \
  bash -c './mvnw -q $MVN_GOAL'"
STATUS=$?

echo "==> Nettoyage de la copie distante (fichiers appartenant a root dans le conteneur, d'ou le passage par un conteneur pour le rm)..."
ssh_remote "docker run --rm -v /home/ubuntu:/host alpine rm -rf /host/$REMOTE_DIR" >/dev/null 2>&1

if [ $STATUS -eq 0 ]; then
  echo "==> OK : tous les tests selectionnes sont passes."
else
  echo "==> ECHEC : voir la sortie Maven ci-dessus (code de sortie $STATUS)." >&2
fi
exit $STATUS
