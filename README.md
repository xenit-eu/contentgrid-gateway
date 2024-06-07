# ContentGrid Gateway

An API Gateway based on [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway) with integration
with [Open Policy Agent](https://www.openpolicyagent.org) for ContentGrid projects.

## Local development

You can run the gateway locally using the following Gradle tasks:

* `./gradlew bootRun` will start the gateway locally with a minimal `bootRun` profile.
* `./gradlew runtimeBootRun` will start the gateway locally with the `bootRun` + `runtime` profiles.
* `./gradlew keycloakBootRun` will start the gateway locally with the `bootRun` + `keycloak` profiles.

### bootRun profile

This profile is intended for local development from your IDE.

It applies the following changes:

* starts the application on port `:9080`

* bootstraps test users:
    - `alice`/`alice` with a single `employer` authority
    - `bob`/`bob` with two `employer` and three `customer` authorities
    - `admin`/`admin` with the `admin` authority

* sets up a redirect from `/` to `/me`, displaying profile information.

### runtime profile

This profile is intended to configure the gateway as a ContentGrid Runtime Platform Gateway:

* enables Kubernetes API discovery for:
    - discovering ContentGrid applications from Kubernetes services
    - discovering ContentGrid application configuration (hostnames, cors, ...) from Kubernetes ConfigMaps
    - discovering ContentGrid application secrets (Keycloak) from Kubernetes Secrets
* dynamic routing to ContentGrid application deployments
* configures Thunx to with a dynamic authorization query

### keycloak profile

This profile is intended to integrate the gateway with a Keycloak container running on `http://172.17.0.1:8090/`.
Use this to set up all required services in containers:

```bash
docker-compose \
    -f docker-compose/docker-compose.yml \
    -f docker-compose/docker-compose-keycloak.yml \
    up -d opa keycloak
```

And, after verifying the keycloak container is listening, use `./gradlew keycloakBootRun` to start the gateway with the
keycloak profile.

This profile applies the following changes:

* Register a local keycloak as OIDC provider

## Configuration

### Testing properties

Testing configuration allows setting up local users for testing.

| Property                                 | Type           | Description              |
|------------------------------------------|----------------|--------------------------|
| `testing.bootstrap.enable`               | boolean        | Set up local test users  |
| `testing.bootstrap.users[*].username`    | string         | Username for a test user |
| `testing.bootstrap.users[*].authorities` | string of json | Claims for a test user   |

These users get bootstrapped into Spring with their password equal to their username. You can use the `/me` endpoint to
test whether this got parsed how you expected it to.

<details>
<summary>Example configuration</summary> 

```yaml
testing.bootstrap:
  enable: true
  users:
    - username: bob
      authorities: '{"contentgrid:admin": true}'
    - username: alice
      authorities: '{"someGroups": ["a", "b"], someValue: "foobar"}'
```

</details>

### Thunx/Open Policy Agent integration

The gateway integrates with [Thunx](https://github.com/xenit-eu/thunx) to authorize requests.

Thunx supports following properties for auto-configuring [Open Policy Agent](https://www.openpolicyagent.org/)
integration.

| Property          | Type   | Description                                               |
|-------------------|--------|-----------------------------------------------------------|
| `opa.service.url` | string | URL where Open Policy Agent is listening on               |
| `opa.query`       | string | Query to send to Open Policy Agent to authorize a request |

<details>
<summary>Example configuration</summary> 

Generally, this depends on the .rego file that is loaded into OPA.
In this example, the .rego file describes the package `gateway.example` wherein the rules are set for the `allow`
variable.

```yaml
opa:
  service:
    url: http://opa:8181
  query: "data.gateway.example.allow == true"
```

</details>

### CORS configurations

The gateway can configure per-domain [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) headers.

CORS configurations are configured below the configuration prefix `contentgrid.gateway.cors.configurations`

This property is a mapping from domain name
to [Spring `CorsConfiguration`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/cors/CorsConfiguration.html)
objects.

For domains that do not match any specific configuration, the default configuration
at `contentgrid.gateway.cors.configurations.default` is used.

<details>
<summary>Example configuration</summary> 

```yaml
contentgrid.gateway.cors.configurations:
  default:
    allowedOrigins:
      - https://example.org

  '[api.example.com]':
    allowedOrigins:
      - https://console.example.com
```

</details>

### Internal JWT signing

The gateway can generate signed JWTs for sending to its upstream services.

This way, upstream services only need to trust one set of signing keys, and the gateway will perform authentication
of the user-supplied authentication tokens. The gateway is also able to inject (or remove) additional attributes to
the user-supplied authentication token.

| Property                                        | Type             | Description                                                                        |
|-------------------------------------------------|------------------|------------------------------------------------------------------------------------|
| `contentgrid.gateway.jwt.signers.*.active-keys` | location pattern | File pattern to load active signing keys from (PEM format)                         |
| `contentgrid.gateway.jwt.signers.*.all-keys`    | location pattern | File pattern to load all signing keys from for signature verification (PEM format) |
| `contentgrid.gateway.jwt.signers.*.algorithms`  | list of strings  | Algorithms to use for signing JWTs (default: `RS256`)                              |

The location pattern is used with
the [Spring `ResourcePatternResolver`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html).
This supports loading multiple keys from different files specified with a pattern.

JWT signers are named, and can be configured on routes using the `LocallyIssuedJwt` filter.

Public keys for all signers are automatically made available in the `jwks` actuator, which is available on the
management port at `/actuator/jwks/<signer-name>`

<details>
<summary>Example configuration</summary>

This configures a signer named `my-signer` and uses it to sign JWTs going to the `my-service` service.

A JWK Set with the public keys is available on `/actuator/jwks/my-signer`. The upstream service should trust only this
JWK Set.

```yaml
contentgrid.gateway.jwt.signers:
  my-signer:
    active-keys: file:/secrets/my-signer/active.pem
    all-keys: file:/secrets/my-signer/*.pem
spring.cloud.gateway.routes:
  - id: my-service
    uri: http://my-service:8083/
    filters:
      - LocallyIssuedJwt=my-signer
    predicates:
      - Path=/**
```

</details>

### Runtime platform configuration

The runtime platform configuration will configure the gateway to:

- automatically discover ContentGrid applications and their configuration based on labels
- configure trusted issuers for every ContentGrid application
- configure Thunx integration for every ContentGrid application
- route traffic to the correct ContentGrid application by hostname

| Property                                                                               | Type             | Description                                                                              |
|----------------------------------------------------------------------------------------|------------------|------------------------------------------------------------------------------------------|
| `contentgrid.gateway.runtime-platform.enabled`                                         | boolean          | Enables runtime platform configuration                                                   |
| `servicediscovery.enabled`                                                             | boolean          | Enables service discovery in kubernetes                                                  |
| `servicediscovery.namespace`                                                           | string           | Kubernetes namespace to perform service discovery in (default: `default`)                |
| `servicediscovery.resync`                                                              | int              | Re-synchronisation interval of the service discovery informer in seconds (default: `60`) |
| `contentgrid.gateway.runtime-platform.endpoints.authentication.uri`                    | string           | URI to the authentication service (tokenmonger)                                          |
| `contentgrid.gateway.runtime-platform.endpoints.authentication.encryption.active-keys` | location pattern | File pattern to load active AES encryption keys from (16, 32 or 64 byte binary file)     |
| `contentgrid.gateway.runtime-platform.endpoints.authentication.encryption.all-keys`    | location pattern | File pattern to load all AES keys from for decryption (16, 32 or 64 byte binary file)    |

Additionally, following properties are required to be configured:

| Property                                                     | Description                                                                                          |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `opa.service.url`                                            | Must be configured to the shared OPA service that contains policies for all ContentGrid applications |
| `contentgrid.gateway.jwt.signers.apps.active-keys`           | The `apps` JWT Signer will be used for signing all requests to ContentGrid applications              |
| `contentgrid.gateway.jwt.signers.authentication.active-keys` | The `authentication` JWT Signer will be used for signing all requests to the authentication service  |


To discover ContentGrid applications, following kubernetes objects are required:

- One `Service` object with labels:
    - `app.kubernetes.io/managed-by=contentgrid`
    - `app.contentgrid.com/service-type=api`
    - `app.contentgrid.com/application-id`: Set to the application ID
    - `app.contentgrid.com/deployment-id`: Set to the deployment ID

- One or more `ConfigMap` or `Secret` objects with labels:
    - `app.kubernetes.io/managed-by=contentgrid`
    - `app.contentgrid.com/service-type=gateway`
    - `app.contentgrid.com/application-id`: Set to the application ID

The ContentGrid gateway will discover running ContentGrid applications using the `Service` object.
There can be multiple deployments of the same Application ID running at the same time, these are disambiguated
by the Deployment ID.

Routing requests to a specific Deployment ID is not implemented yet.
All requests will be routed to the same, arbitrarily selected Deployment ID.

`Service`s can have annotations for configuration:

| Annotation                             | Type   | Description                                                                  |
|----------------------------------------|--------|------------------------------------------------------------------------------|
| `authz.contentgrid.com/policy-package` | string | The OPA package that contains policies for evaluating access to this service |

The ContentGrid gateway will discover the configuration for ContentGrid applications using the `ConfigMap` and `Secret`
objects.
The contents of these objects are merged together.

"list"-type configurations are aggregated across all configuration objects.
"string"-type configurations should only be present in one configuration object, if multiple are present an arbitrary
value will be selected.

`ConfigMap`s and `Secret`s can contain following data for configuration:

| Configuration Key             | Type   | Description                                                                                                   |
|-------------------------------|--------|---------------------------------------------------------------------------------------------------------------|
| `contentgrid.routing.domains` | list   | Domainnames that will be routed to the application                                                            |
| `contentgrid.cors.origins`    | list   | Origins (protocol + domainname + port if not default) that will be allowed as CORS orgin for this application |
| `contentgrid.idp.issuer-uri`  | string | OpenID Connect issuer that is trusted for issuing tokens to this application                                  |
| `contentgrid.idp.client-id`   | string | OAuth Client Id to initiate OpenID Connect authentication from the gateway                                    |
| `contentgrid.idp.secret`      | string | OAuth Client Secret to initiate OpenID Connect authentication from the gateway                                |

