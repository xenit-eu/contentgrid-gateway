package gateway.example

default allow = false

# Allow GET /
allow {
    input.method == "GET"
    input.path == []
}

# Allow GET /favicon.ico
allow {
    input.method == "GET"
    input.path ==  ["favicon.ico"]
}

# Allow GET /me
allow {
    input.method == "GET"
    # count(input.path) = 1
    input.path[0] = "me"
}

# admin access on /api
allow {
  input.path[0] == "api"
  input.user.admin == "true"
}

# admin access on /actuator (/info, /health, /metrics, and /prometheus don't pass through opa)
allow {
    input.path[0] == "actuator"
    input.user.admin == "true"
}