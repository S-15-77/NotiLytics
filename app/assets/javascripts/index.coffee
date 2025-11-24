$ ->
  ws = new WebSocket $("body").data("ws-url")

  ws.onopen = ->
    console.log("WebSocket connected")

  ws.onmessage = (event) ->
    console.log("Received:", event.data)

  ws.onerror = (error) ->
    console.error("WebSocket error:", error)

  ws.onclose = ->
    console.log("WebSocket closed")