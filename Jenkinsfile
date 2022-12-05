/* groovylint-disable LineLength, NestedBlockDepth, CompileStatic */
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
        stage('Build spring-aws dependency') {
            steps {
                container('builder') {
                    dir("../spring-aws") {
                        git branch: 'backport_353_to_2_4_2', url: 'https://github.com/LEDfan/spring-cloud-aws'

                        configFileProvider([configFile(fileId: 'maven-settings-rsb', variable: 'MAVEN_SETTINGS_RSB')]) {

                            sh 'mvn -B -s $MAVEN_SETTINGS_RSB -U clean install -DskipTests=true'

                        }
                    }
                }
            }
        }

        stage('build and deploy to nexus') {
            steps {
                container('builder') {

                    configFileProvider([configFile(fileId: 'maven-settings-rsb', variable: 'MAVEN_SETTINGS_RSB')]) {

                        sh 'mvn -B -s $MAVEN_SETTINGS_RSB -U clean install deploy -DskipTests=true'

                    }

                }
            }
        }
    }
}
