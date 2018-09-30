# Using Traffic Parrot with OpenShift
These examples will show you how to use Traffic Parrot in an [OpenShift](https://www.openshift.com/) environment.

## Provision an OpenShift cluster
There are a number of ways you can do this, for example:
1. Locally using [Minishift](https://github.com/minishift/minishift)
1. Hosted in [Red Hat OpenShift Online](https://www.openshift.com/products/pricing/) (includes a free tier for personal use)
1. Hosted in [AWS](https://aws.amazon.com/quickstart/architecture/openshift/) (requires a [Red Hat subscription](https://www.redhat.com/wapps/ugc/register.html))
1. Hosted on premise (requires a [Red Hat subscription](https://www.redhat.com/wapps/ugc/register.html))

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

Set a local variable that contains the cluster registry, for example:
* `CLUSTER_REGISTRY=registry.<instance>.openshift.com` for OpenShift online
* `CLUSTER_REGISTRY=$(minishift openshift registry)` for Minishift


### Create a Traffic Parrot image
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

The default values are enough for this example project to work.

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

### Check the demo app
The demo app URL outside of the cluster is determined by the exposed route:
```
DEMO_URL=http://$(oc get route ${DEMO_ID} -o jsonpath='{.spec.host}')
```

Then, we can check that the demo application is able to talk to Traffic Parrot and return the mocked stock price:
```
curl -v ${DEMO_URL}/stock-quote-last-price
```

### Approve the preview in the pipeline


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
