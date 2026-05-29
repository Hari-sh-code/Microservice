// ── Jenkinsfile ────────────────────────────────────────────────────────
// Place this file at the PROJECT ROOT (same level as API-Gateway/, Backend-Service/, Eureka-Server/)
//
// Prerequisites in Jenkins:
//   1. Credential ID "dockerhub-creds"  → your Docker Hub username + password
//   2. Plugins installed: Git, Pipeline, Docker Pipeline
//   3. Jenkins agent has Docker + kubectl available

pipeline {
    agent any

    // ── Change these two values to match your Docker Hub username ──────
    environment {
        DOCKERHUB_USER = "harishdocker2004"       // ← CHANGE THIS
        IMAGE_TAG      = "${env.BUILD_NUMBER}"
    }

    // ── Poll GitLab/Git every 5 minutes for new commits ────────────────
    triggers {
        pollSCM('H/5 * * * *')
    }

    stages {

        // ── STAGE 1: Get the code ──────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                echo "Code checked out — Build #${env.BUILD_NUMBER}"
            }
        }

        // ── STAGE 2: Build all 3 services with Gradle ─────────────────
        stage('Build All Services') {
            steps {
                echo "Building Eureka-Server..."
                dir('Eureka-Server') {
                    sh 'chmod +x gradlew && ./gradlew clean bootJar -x test'
                }

                echo "Building Backend-Service..."
                dir('Backend-Service') {
                    sh 'chmod +x gradlew && ./gradlew clean bootJar -x test'
                }

                echo "Building API-Gateway..."
                dir('API-Gateway') {
                    sh 'chmod +x gradlew && ./gradlew clean bootJar -x test'
                }
            }
        }

        // ── STAGE 3: Build Docker images for all 3 services ───────────
        stage('Docker Image Build') {
            steps {
                sh "docker build -t ${DOCKERHUB_USER}/eureka-server:${IMAGE_TAG}    ./Eureka-Server"
                sh "docker build -t ${DOCKERHUB_USER}/backend-service:${IMAGE_TAG}  ./Backend-Service"
                sh "docker build -t ${DOCKERHUB_USER}/api-gateway:${IMAGE_TAG}      ./API-Gateway"
                echo "All 3 Docker images built successfully."
            }
        }

        // ── STAGE 4: Push images to Docker Hub ────────────────────────
        stage('Push to Docker Hub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"

                    sh "docker push ${DOCKERHUB_USER}/eureka-server:${IMAGE_TAG}"
                    sh "docker push ${DOCKERHUB_USER}/backend-service:${IMAGE_TAG}"
                    sh "docker push ${DOCKERHUB_USER}/api-gateway:${IMAGE_TAG}"

                    echo "All images pushed to Docker Hub."
                }
            }
        }

        // ── STAGE 5: Deploy to Kubernetes (Minikube) ──────────────────
        stage('Deploy to Kubernetes') {
            steps {
                // Replace the IMAGE_TAG placeholder in all K8s manifests
                sh """
                    sed -i 's|IMAGE_TAG_PLACEHOLDER|${IMAGE_TAG}|g' k8s/eureka-server.yaml
                    sed -i 's|IMAGE_TAG_PLACEHOLDER|${IMAGE_TAG}|g' k8s/backend-service.yaml
                    sed -i 's|IMAGE_TAG_PLACEHOLDER|${IMAGE_TAG}|g' k8s/api-gateway.yaml
                """

                // Apply manifests — Eureka first, then the others
                sh "kubectl apply -f k8s/eureka-server.yaml"
                sh "kubectl rollout status deployment/eureka-server --timeout=90s"

                sh "kubectl apply -f k8s/backend-service.yaml"
                sh "kubectl apply -f k8s/api-gateway.yaml"
                sh "kubectl rollout status deployment/backend-service --timeout=90s"
                sh "kubectl rollout status deployment/api-gateway --timeout=90s"

                echo "All services deployed to Kubernetes."
            }
        }

        // ── STAGE 6: Sanity Check — is the API Gateway responding? ────
        stage('Sanity Check') {
            steps {
                sh """
                    echo "Waiting 15s for pods to stabilise..."
                    sleep 15

                    GATEWAY_URL=\$(minikube service api-gateway --url 2>/dev/null || echo "http://localhost:30080")
                    echo "Checking: \$GATEWAY_URL"

                    STATUS=\$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \$GATEWAY_URL || echo "000")
                    echo "HTTP Status: \$STATUS"

                    if [ "\$STATUS" = "200" ] || [ "\$STATUS" = "404" ]; then
                        echo "Sanity check PASSED — gateway is up."
                    else
                        echo "Sanity check FAILED (status=\$STATUS) — rolling back all deployments."
                        kubectl rollout undo deployment/api-gateway
                        kubectl rollout undo deployment/backend-service
                        kubectl rollout undo deployment/eureka-server
                        exit 1
                    fi
                """
            }
        }
    }

    // ── POST: Email on failure ─────────────────────────────────────────
    post {
        success {
            echo "Pipeline PASSED — Build #${env.BUILD_NUMBER} deployed successfully."
        }
        failure {
            echo "Pipeline FAILED — Build #${env.BUILD_NUMBER}. Check console output."
            // Uncomment after configuring SMTP in Jenkins:
            // mail to: 'your-email@example.com',
            //      subject: "BUILD FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //      body: "Console: ${env.BUILD_URL}"
        }
    }
}