pipeline {
agent any

environment {
    DOCKERHUB_USER = "harishdocker2004"
    IMAGE_TAG = "${env.BUILD_NUMBER}"
}

triggers {
    pollSCM('H/5 * * * *')
}

stages {

    stage('Checkout') {
        steps {
            checkout scm
            echo "Code checked out — Build #${env.BUILD_NUMBER}"
        }
    }

    stage('Build All Services') {
        steps {
            dir('Eureka-Server') {
                sh 'chmod +x gradlew && ./gradlew clean bootJar -x test'
            }

            dir('Backend-Service') {
                sh 'chmod +x gradlew && ./gradlew clean bootJar -x test'
            }

            dir('API-Gateway') {
                sh 'chmod +x gradlew && ./gradlew clean bootJar -x test'
            }
        }
    }

    stage('Docker Image Build') {
        steps {
            sh "docker build -t ${DOCKERHUB_USER}/eureka-server:${IMAGE_TAG} ./Eureka-Server"
            sh "docker build -t ${DOCKERHUB_USER}/backend-service:${IMAGE_TAG} ./Backend-Service"
            sh "docker build -t ${DOCKERHUB_USER}/api-gateway:${IMAGE_TAG} ./API-Gateway"
        }
    }

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
            }
        }
    }
}

post {
    success {
        echo "Pipeline PASSED — Build #${env.BUILD_NUMBER}"
    }

    failure {
        echo "Pipeline FAILED — Build #${env.BUILD_NUMBER}"
    }
}

}