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
    input.path == ["me"]
}

# admin access on /api
allow {
  input.path[0] == "api"
  input.user.admin == true
}
