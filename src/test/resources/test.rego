package gateway.example

import rego.v1

default allow = false

# Allow GET /
allow if {
    input.method == "GET"
    input.path == []
}

# Allow GET /favicon.ico
allow if {
    input.method == "GET"
    input.path ==  ["favicon.ico"]
}

# Allow GET /me
allow if {
    input.method == "GET"
    input.path == ["me"]
}

# admin access on /api
allow if {
  input.path[0] == "api"
  input.user.admin == true
}
