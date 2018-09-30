# Using Traffic Parrot with OpenShift
These examples will show you how to use Traffic Parrot in an [OpenShift](https://www.openshift.com/) environment.

## Provision an OpenShift cluster
There are a number of ways you can do this, for example:
1. Locally using [Minishift](https://github.com/minishift/minishift)
1. [OpenShift Origin](https://github.com/openshift/origin) open source edition hosted in [AWS](https://sysdig.com/blog/deploy-openshift-aws/)
1. [OpenShift Origin](https://github.com/openshift/origin) open source edition hosted on premise
1. Red Hat commercial edition hosted in [OpenShift Online](https://www.openshift.com/products/pricing/)
1. Red Hat commercial edition hosted in [AWS](https://aws.amazon.com/quickstart/architecture/openshift/) (requires a [Red Hat subscription](https://www.redhat.com/wapps/ugc/register.html))
1. Red Hat commercial edition hosted on premise (requires a [Red Hat subscription](https://www.redhat.com/wapps/ugc/register.html))


You will need 2GiB free to run the entire CI/CD demo including a Jenkins pipeline, demo application deployment and a Traffic Parrot deployment.

## Install required tools
1. You will need the `oc` [client tool](https://www.okd.io/download.html#oc-platforms) to issue commands to the cluster
1. You will need [Docker](https://docs.docker.com/install/#supported-platforms) to be able to build and push custom Docker images to the registry

## Log in to the OpenShift cluster

### OpenShift Online
Find your login by clicking on "Command Line Tools in the web console:

![Alt text](images/openshift-command-line-tools.png?raw=true "Command Line Tools")

Then copy the token:

![Alt text](images/openshift-copy-token.png?raw=true "Command Line Tools")

And login to the console:
```
oc login https://api.<instance>.openshift.com --token=<token>
```

### Minishift
You can use the following commands to log in with the default system user:
```
oc login
system
admin
```

## Use Traffic Parrot to virtualize an OpenAPI specification as part of a CI/CD pipeline
We will work with the project `trafficparrot-test`

```
oc new-project trafficparrot-test
```

If you want to start again at any point, you can do:
```
oc delete project trafficparrot-test
```

### Create a Traffic Parrot image
Set a local variable that contains the cluster registry, for example:
* `CLUSTER_REGISTRY=registry.<instance>.openshift.com` for OpenShift online
* `CLUSTER_REGISTRY=$(minishift openshift registry)` for Minishift

Now let's log in to the registry so that we can build and push our Traffic Parrot image:
```
docker login -u developer -p $(oc whoami -t) ${CLUSTER_REGISTRY}
```

We build the image locally and tag it ready to be used in the cluster registry.

Note that you must set build arguments:
1. `TRAFFIC_PARROT_ZIP` is a HTTP location or a local file location. You can [download a trial copy](https://trafficparrot.com/download.html?src=trafficparrot-examples-openshift).
1. `ACCEPT_LICENSE` should be set to `true` if you accept the terms of the [LICENSE](LICENSE)

```
docker build \
--build-arg TRAFFIC_PARROT_ZIP=<fill this in> \
--build-arg ACCEPT_LICENSE=<fill this in> \
--tag trafficparrot:4.1.6 \
--file openshift/trafficparrot/Dockerfile .
```

Next, we tag and push to the cluster registry:
```
docker tag trafficparrot:4.1.6 ${CLUSTER_REGISTRY}/trafficparrot-test/trafficparrot-image
docker push ${CLUSTER_REGISTRY}/trafficparrot-test/trafficparrot-image
```

### Install Jenkins
You will need Jenkins in the OpenShift cluster to provide CI/CD support for the pipeline.

The easiest way to do this is via the web console catalog:

![Alt text](images/openshift-jenkins.png?raw=true "Install Jenkins")

Change the memory to 750Mi, Jenkins is quite memory hungry. Accept the default values for everything else.

NOTE: It is best to wait at least 10 minutes for Jenkins to fully start up the first time. The UI will initially be unresponsive and return an "Application is not available" message.

### Import and run the pipeline
First we need the ability to build Java images:
```
oc create -f openshift/openjdk-s2i-imagestream.json
```

Now we can import the pipeline:
```
oc create -f openshift/finance/pipeline.yaml
```

Next, run the pipeline:

![Alt text](images/openshift-start-pipeline.png?raw=true "Start Pipeline")

The pipeline will:
1. Build the `finance-application` demo image
1. Deploy Traffic Parrot using the `trafficparrot-image` we pushed to the registry earlier
1. Import the OpenAPI definition [markit.yaml](openshift/finance/markit.yaml) into Traffic Parrot
1. Deploy the `finance-application`
1. Wait for you to preview the demo

![Alt text](images/openshift-preview-pipeline.png?raw=true "Preview Pipeline Step")

To preview the demo, click on the finance route:

![Alt text](images/openshift-routes.png?raw=true "Finance Route")

You should see this:

![Alt text](images/openshift-finance-app.png?raw=true "Finance App")

You can push the pipeline forwards by clicking on ![Alt text](images/openshift-preview-button.png?raw=true "Input Required") and then the button in Jenkins:

![Alt text](images/jenkins-pipeline-input.png?raw=true "Jenkins Input")

### What just happened?
Behind the scenes, we just demonstrated that the demo finance application was able to communicate with Traffic Parrot inside the cluster.

Traffic Parrot was configured by importing an OpenAPI definition [markit.yaml](openshift/finance/markit.yaml) using the [HTTP Management API](https://trafficparrot.com/documentation/4.1.x/openapi/index.html).

Have a look at the configuration files in this project to see how it is done. The key files are:
* [finance/pipeline.yaml](openshift/finance/pipeline.yaml) is the pipeline `BuildConfig`
* [finance/jenkinsfile.groovy](openshift/finance/jenkinsfile.groovy) is the Jenkins pipeline configuration
* [finance/build.json](openshift/finance/build.json) is the `BuildConfig` used to build the [finance application](https://github.com/wojciechbulaty/examples/tree/master/finance-application)
* [finance/deploy.json](openshift/finance/deploy.json) is the `Template` used to deploy the finance application
* [trafficparrot/Dockerfile](openshift/trafficparrot/Dockerfile) is the `Dockerfile` used to build the Traffic Parrot image
* [trafficparrot/deploy.json](openshift/trafficparrot/deploy.json) is the `Template` used to deploy Traffic Parrot

### Clean up
To clean up the pipeline:
```
oc delete bc finance-pipeline
```

To clean up the demo app:
```
oc delete all -l "app=${DEMO_ID}"
oc delete configmap ${DEMO_ID}-config
```

To clean up Traffic Parrot:
```
oc delete all -l "app=${TRAFFIC_PARROT_ID}"
oc delete imagestream ${TRAFFIC_PARROT_ID}
```
