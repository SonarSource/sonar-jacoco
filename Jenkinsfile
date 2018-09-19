@Library('SonarSource@1.6') _

pipeline {
    agent {
        label 'linux'
    }
    parameters {
        string(name: 'GIT_SHA1', description: 'Git SHA1 (provided by travisci hook job)')
        string(name: 'CI_BUILD_NAME', defaultValue: 'sonar-jacoco', description: 'Build Name (provided by travisci hook job)')
        string(name: 'CI_BUILD_NUMBER', description: 'Build Number (provided by travisci hook job)')
        string(name: 'GITHUB_BRANCH', defaultValue: 'master', description: 'Git branch (provided by travisci hook job)')
        string(name: 'GITHUB_REPOSITORY_OWNER', defaultValue: 'SonarSource', description: 'Github repository owner(provided by travisci hook job)')
    }
    stages {
        stage('Notify') {
            steps {
                sendAllNotificationQaStarted()
            }
        }
        stage('QA') {
            parallel {
                stage('plugin-dev') {
                    agent {
                        label 'linux'
                    }
                    steps {
                        runTests "DEV"
                    }
                }
                stage('plugin-lts') {
                    agent {
                        label 'linux'
                    }
                    steps {
                        runTests "LATEST_RELEASE[6.7]"
                    }
                }
            }
            post {
                always {
                    sendAllNotificationQaResult()
                }
            }
        }
        stage('Promote') {
            steps {
                repoxPromoteBuild()
            }
            post {
                always {
                    sendAllNotificationPromote()
                }
            }
        }
    }
}

def runTests(String sqRuntimeVersion) {
    withQAEnv {
        sh "./gradlew -DbuildNumber=${params.CI_BUILD_NUMBER} -Dsonar.runtimeVersion=${sqRuntimeVersion} " +
                "-Dorchestrator.artifactory.apiKey=${env.ARTIFACTORY_PRIVATE_API_KEY}  --console plain --no-daemon --info -PintegrationTests=true build test"
    }
}

def withQAEnv(def body) {
    checkout scm
    withCredentials([string(credentialsId: 'ARTIFACTORY_PRIVATE_API_KEY', variable: 'ARTIFACTORY_PRIVATE_API_KEY'),
                     usernamePassword(credentialsId: 'ARTIFACTORY_PRIVATE_USER', passwordVariable: 'ARTIFACTORY_PRIVATE_PASSWORD', usernameVariable: 'ARTIFACTORY_PRIVATE_USERNAME')]) {
        wrap([$class: 'Xvfb']) {
            body.call()
        }
    }
}

