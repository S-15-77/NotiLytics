$ ->
  ws = new WebSocket $("body").data("ws-url")

  ws.onopen = ->
    console.log("WebSocket connected for sources")
    sendFilterRequest()

  ws.onmessage = (event) ->
    console.log("Received source update:", event.data)
    message = JSON.parse event.data
    if message.type is "sources"
      updateSourcesTableDynamically(message.data)

  ws.onerror = (error) ->
    console.error("WebSocket error:", error)

  ws.onclose = ->
    console.log("WebSocket closed")

  $("form").first().submit (event) ->
    event.preventDefault()
    sendFilterRequest()

  sendFilterRequest = ->
    filters =
      country: $("#country").val()
      category: $("#category").val()
      language: $("#language").val()

    console.log("Sending filter request:", filters)
    ws.send(JSON.stringify(filters))

  updateSourcesTableDynamically = (sources) ->
    tbody = $("table tbody")
    tbody.empty()

    if sources and sources.length > 0
      $.each sources, (index, source) ->
        row = $("<tr>")
        row.append($("<td>").html("<strong><a href='/profile/" + source.name + "/" + source.id + "'>" + source.name + "</a></strong>"))
        row.append($("<td>").text(source.description))
        row.append($("<td>").text(source.category))
        row.append($("<td>").text(source.language))
        row.append($("<td>").text(source.country))
        row.append($("<td>").html("<a href='" + source.url + "' target='_blank'>Visit</a>"))
        tbody.append(row)

      $("p strong").text("Total sources: " + sources.length)