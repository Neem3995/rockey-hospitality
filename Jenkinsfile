// Local configuration only; requires an approved Jenkins agent, Sonar integration and deployment host.
pipeline {
  agent { label 'rockey-java17-node22-linux' }
  options { disableConcurrentBuilds(); timestamps(); skipDefaultCheckout(true) }
  parameters {
    booleanParam(name: 'DEPLOY_RELEASE', defaultValue: false, description: 'Deploy only after explicit approval')
    string(name: 'SONAR_PROJECT_KEY', defaultValue: '', description: 'Approved project key')
    string(name: 'DEPLOY_HOST', defaultValue: '', description: 'Approved SSH hostname')
  }
  environment { ROCKEY_HARDENING_LIVE = 'false' }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('Runtime') { steps { sh 'java -version; node --version; npm --version; java -XshowSettings:properties -version 2>&1 | grep -q "java.specification.version = 17"' } }
    stage('Backend verify and coverage') { steps { sh 'sh mvnw -B verify' } }
    stage('Frontend checks and build') {
      steps { dir('frontend') { sh 'npm ci && npm run test:coverage -- --maxWorkers=1 && npm run lint && npm run typecheck && VITE_API_BASE_URL=/api npm run build' } }
    }
    stage('Contract and seed safety') { steps { sh 'python3 scripts/verify-submission.py && node --test scripts/seed-test.test.cjs && bash -n scripts/deploy-release.sh && bash -n deployment/activate-release.sh && node scripts/verify-hardening.cjs --audit-only && git diff --check' } }
    stage('Current source analysis') {
      steps {
        script { if (!params.SONAR_PROJECT_KEY.trim()) { error('Approved Sonar project key required') } }
        withSonarQubeEnv('rockey-sonar') {
          withEnv(["ROCKEY_SONAR_PROJECT_KEY=${params.SONAR_PROJECT_KEY}"]) {
            sh 'export SONAR_TOKEN="$SONAR_AUTH_TOKEN"; sh mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey="$ROCKEY_SONAR_PROJECT_KEY" -Dsonar.sources=src/main/java,frontend/src -Dsonar.tests=src/test/java,frontend/src/tests -Dsonar.exclusions=frontend/src/tests/** -Dsonar.javascript.lcov.reportPaths=frontend/coverage/lcov.info -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml'
          }
        }
      }
    }
    stage('Quality gate') { steps { timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } } }
    stage('Package release') {
      steps { sh 'release_dir="target/release-$BUILD_NUMBER"; test ! -e "$release_dir"; mkdir -p "$release_dir/frontend"; cp target/rockey-hospitality-0.0.1-SNAPSHOT.jar "$release_dir/app.jar"; cp -R frontend/dist/. "$release_dir/frontend/"; git rev-parse HEAD > "$release_dir/REVISION"; (cd "$release_dir"; sha256sum app.jar REVISION > SHA256SUMS; find frontend -type f -print0 | sort -z | xargs -0 sha256sum >> SHA256SUMS); tar -czf target/rockey-release.tgz -C "$release_dir" .' }
    }
    stage('Approved deployment') {
      when { expression { params.DEPLOY_RELEASE && env.BRANCH_NAME == 'main' } }
      steps {
        timeout(time: 15, unit: 'MINUTES') { input message: 'Approve deployment of this tested revision to the configured existing host?' }
        withCredentials([sshUserPrivateKey(credentialsId: 'rockey-deploy-ssh', keyFileVariable: 'ROCKEY_SSH_KEY', usernameVariable: 'ROCKEY_SSH_USER')]) {
          withEnv(["ROCKEY_DEPLOY_HOST=${params.DEPLOY_HOST}"]) { sh 'bash scripts/deploy-release.sh' }
        }
      }
    }
  }
  post {
    always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml' }
    success { archiveArtifacts artifacts: 'target/rockey-release.tgz,target/site/jacoco/jacoco.xml,frontend/coverage/lcov.info', fingerprint: true }
  }
}
