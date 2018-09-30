import java.util.Random

def demoId = "finance-" + (10000 + new Random().nextInt(10000))
def demoConfigId = "${demoId}-config"

def trafficParrotId = "trafficparrot-" + (10000 + new Random().nextInt(10000))
def trafficParrotMappingsId = "${trafficParrotId}-mappings"

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
                            openshift.newApp("openshift/finance/build.json", "--name=${demoId}", "--param=APPLICATION_NAME=${demoId}")

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
                            echo "Deploy: ${trafficParrotId}"
                            openshift.newApp("openshift/trafficparrot/deploy.json", "--name=${trafficParrotId}", "--param=APPLICATION_NAME=${trafficParrotId}")

                            echo "Waiting on deploy for: ${trafficParrotId}"
                            def latestDeploymentVersion = openshift.selector('dc',"${trafficParrotId}").object().status.latestVersion
                            def rc = openshift.selector('rc', "${trafficParrotId}-${latestDeploymentVersion}")
                            rc.untilEach(1){
                                def rcMap = it.object()
                                return (rcMap.status.replicas.equals(rcMap.status.readyReplicas))
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
                            def importStatus = sh(returnStatus: true, script: "curl --fail --form 'files[]=@openshift/finance/markit.yaml' http://${managementRoute}/http/management/importMappings")
                            if (importStatus != 0) {
                                error('Import failed!')
                            }

                            def httpRoute = openshift.selector("route", "${trafficParrotId}-http").object().spec.host;
                            def checkStatus = sh(returnStatus: true, script: "curl --fail --verbose http://${httpRoute}/MODApis/Api/v2/Quote/json")
                            if (checkStatus != 0) {
                                error('Check failed!')
                            }
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
                            openshift.newApp("openshift/finance/deploy.json", "--name=${demoId}", "--param=APPLICATION_NAME=${demoId}")

                            echo "Waiting on deploy for: ${demoId}"
                            def latestDeploymentVersion = openshift.selector('dc',"${demoId}").object().status.latestVersion
                            def rc = openshift.selector('rc', "${demoId}-${latestDeploymentVersion}")
                            rc.untilEach(1){
                                def rcMap = it.object()
                                return (rcMap.status.replicas.equals(rcMap.status.readyReplicas))
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