# Content Cloud Gateway

An API Gateway based on [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway) with integration with [Open Policy Agent](https://www.openpolicyagent.org) for Content Cloud projects.

## Local development

You can run the gateway locally using the following Gradle tasks:

* `./gradlew bootRun` will start the gateway locally with the `bootRun` profile.
* `./gradlew keycloakBootRun` will start the gateway locally with the `bootRun` + `keycloak` profile.

### bootRun profile

This profile is intended for local development from your IDE.

It applies the following changes:

* starts the application on port `:9080`

* bootstraps test users:
  - `alice`/`alice` with a single `employer` authority
  - `bob`/`bob` with two `employer` and three `customer` authorities
  - `admin`/`admin` with the `admin` authority

* sets up a redirect from `/` to `/me`, displaying profile information.

### keycloak profile

This profile is intended to integrate the gateway with a Keycloak container running on `http://172.17.0.1:8090/`.
Use this to set up all required services in containers:

```bash
docker-compose \
    -f docker-compose/docker-compose.yml \
    -f docker-compose/docker-compose-keycloak.yml \
    up -d opa keycloak
```
And, after verifying the keycloak container is listening, use `./gradlew keycloakBootRun` to start the gateway with the keycloak profile.

This profile applies the following changes:

* Register a local keycloak as OIDC provider

## Configuration

Content Cloud Gateway is driven by a number of Spring properties. Examples of how to set these properties can be found in `docker-compose.yml` and `application-bootRun.yml`.

Example for bootstrapping users:

```yaml
testing:
  bootstrap:
    enable: true
    users:
    - username: alice
      authorities: '{"someGroups": ["a", "b"], someValue: "foobar"}'
    - username: admin
      authorities: '{"admin": "true"}'
```

These users get bootstrapped into Spring with their password equal to their username. The `authorities` field must contain a json object with the different authority fields mapping to a string or list of strings. You can use the `/me` endpoint to test whether this got parsed how you expected it to.

Example integration with Open Policy Agent:

```yaml
opa:
  service:
    url: http://opa:8181
  query: "data.gateway.example.allow == true"
```

This is used for pointing out how the gateway can reach OPA, as well as what query it should try to evaluate for user requests. Generally, this depends on the .rego file that is loaded into OPA. In this example, the .rego file describes the package `gateway.example` wherein the rules are set for the `allow` variable.
