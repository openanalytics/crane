pipeline {

    agent {
        kubernetes {
            yamlFile 'kubernetesPod.yaml'
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }

    stages {

        stage('build and deploy to nexus') {

            steps {

                container('builder') {

                    withCredentials([usernamePassword(credentialsId: 'oa-jenkins', usernameVariable: 'OA_NEXUS_USER', passwordVariable: 'OA_NEXUS_PWD')]) {
                        sh "./gradlew publish"
                    }
                    
                }
            }
        }
    }
}
