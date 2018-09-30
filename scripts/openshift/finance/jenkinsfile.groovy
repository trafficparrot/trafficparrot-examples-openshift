import java.util.Random

def demoId = "finance-" + (10000 + new Random().nextInt(10000))
def demoConfigId = "${demoId}-config"

def trafficParrotId = "trafficparrot-" + (10000 + new Random().nextInt(10000))
def trafficParrotMappingsId = "${trafficParrotId}-mappings"

// TODO:
//1. upload the file to import

pipeline {
    agent any
    options {
        // set a timeout of 20 minutes for this pipeline
        timeout(time: 20, unit: 'MINUTES')
    }
    stages {
        stage('preamble') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Using project: ${openshift.project()}"
                            echo "Using demoId: ${demoId}"
                            echo "Using trafficParrotId: ${trafficParrotId}"
                            echo "Using trafficParrotMappingsId: ${trafficParrotMappingsId}"
                        }
                    }
                }
            }
        }
        stage('build-demo') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Start build for: ${demoId}"
                            openshift.newApp("scripts/openshift/finance/build.json", "--name=${demoId}", "--param=APPLICATION_NAME=${demoId}")

                            echo "Waiting for build ${demoId} to finish..."
                            def builds = openshift.selector("bc", demoId).related('builds')
                            builds.untilEach(1) {
                                return (it.object().status.phase == "Complete")
                            }
                            echo "${demoId} has been built!"
                        }
                    }
                }
            }
        }
        stage('deploy-trafficparrot') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Configure ${trafficParrotId} mappings"
                            openshift.create("configmap", trafficParrotMappingsId, "--from-file=scripts/openshift/trafficparrot/mappings")

                            echo "Deploy: ${trafficParrotId}"
                            openshift.newApp("scripts/openshift/trafficparrot/deploy.json", "--name=${trafficParrotId}", "--param=APPLICATION_NAME=${trafficParrotId}")

                            echo "Waiting on deploy for: ${trafficParrotId}"
                            openshift.selector("dc", trafficParrotId).related('pods').untilEach(1) {
                                return (it.object().status.phase == "Running")
                            }
                            echo "Deployed ${trafficParrotId}!"
                        }
                    }
                }
            } 
        }
        stage('import-openapi') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            def managementRoute = openshift.selector("route", "${trafficParrotId}-http-management").object().spec.host;
                            sh "curl -F 'files[]=@scripts/openshift/finance/markit.yaml' http://${managementRoute}/http/management/importMappings"

                            def httpRoute = openshift.selector("route", "${trafficParrotId}-http").object().spec.host;
                            sh "curl -v http://${httpRoute}/MODApis/Api/v2/Quote/json"
                        }
                    }
                }
            }
        }
        stage('deploy-demo') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Configure ${demoId} properties"
                            openshift.create("configmap", demoConfigId, "--from-literal=finance-application.properties=finance-application.markit.url=http://${trafficParrotId}:18081/MODApis/Api/v2/Quote/json")

                            echo "Deploy: ${demoId}"
                            openshift.newApp("scripts/openshift/finance/deploy.json", "--name=${demoId}", "--param=APPLICATION_NAME=${demoId}")

                            echo "Waiting on deploy for: ${demoId}"
                            openshift.selector("dc", demoId).related('pods').untilEach(1) {
                                return (it.object().status.phase == "Running")
                            }
                            echo "Deployed ${demoId}!"
                        }
                    }
                }
            } 
        } 
       stage('preview') {
            steps {
                script {
                    timeout(time: 10, unit: 'MINUTES') {
                        input message: "Does the deployment look good?"
                    }
                }
            } 
        }
    }
    post {
        always {
            script {
                openshift.withCluster() {
                    openshift.withProject() {
                        echo "Cleaning up ${demoId}"
                        openshift.selector("all", [ "app" : demoId ]).delete()

                        if (openshift.selector("configmap", demoConfigId).exists()) {
                            echo "Cleaning up ${demoConfigId}"
                            openshift.selector("configmap", demoConfigId).delete()
                        }

                        echo "Cleaning up ${trafficParrotId}"
                        openshift.selector("all", [ "app" : trafficParrotId ]).delete()

                        if (openshift.selector("configmap", trafficParrotMappingsId).exists()) {
                            echo "Cleaning up ${trafficParrotMappingsId}"
                            openshift.selector("configmap", trafficParrotMappingsId).delete()
                        }
                    }
                }
            }
        }
    }
} // pipeline