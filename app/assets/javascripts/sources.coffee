$ ->
  ws = new WebSocket $("body").data("ws-url")

  ws.onopen = ->
    console.log("WebSocket connected for sources")
    country = getUrlParameter('country') || ''
    category = getUrlParameter('category') || ''
    language = getUrlParameter('language') || ''

    sendFilterRequest(country, category, language)

  ws.onmessage = (event) ->
    console.log("Received message:", event.data)
    try
      data = JSON.parse(event.data)
      if data.type == "sources"
        handleSourcesUpdate(data.data)
    catch error
      console.error("Error parsing message:", error)

  ws.onerror = (error) ->
    console.error("WebSocket error:", error)

  ws.onclose = ->
    console.log("WebSocket closed")

  sendFilterRequest = (country, category, language) ->
    message =
      type: "filter"
      country: country
      category: category
      language: language

    console.log("Sending filter request:", message)
    ws.send(JSON.stringify(message))

  handleSourcesUpdate = (sources) ->
    console.log("Received #{sources.length} sources")

    # Clear existing table body
    tbody = $("table tbody")
    tbody.empty()

    # Populate table with new sources
    if sources && sources.length > 0
      for source in sources
        row = """
          <tr>
            <td><strong><a href="/profile/#{encodeURIComponent(source.name)}/#{encodeURIComponent(source.id || source.name)}">#{escapeHtml(source.name)}</a></strong></td>
            <td>#{escapeHtml(source.description || '')}</td>
            <td>#{escapeHtml(source.category || '')}</td>
            <td>#{escapeHtml(source.language || '')}</td>
            <td>#{escapeHtml(source.country || '')}</td>
            <td><a href="#{escapeHtml(source.url)}" target="_blank">Visit</a></td>
          </tr>
        """
        tbody.append(row)

      # Update count
      $("p strong").first().text("Total sources: #{sources.length}")
    else
      tbody.append("<tr><td colspan='6'>No sources found</td></tr>")

  # Handle form submission
  $("form").on "submit", (e) ->
    e.preventDefault()

    country = $("#country").val() || ''
    category = $("#category").val() || ''
    language = $("#language").val() || ''

    sendFilterRequest(country, category, language)

  escapeHtml = (text) ->
    return '' unless text
    $('<div>').text(text).html()

  getUrlParameter = (name) ->
    name = name.replace(/[\[]/, '\\[').replace(/[\]]/, '\\]')
    regex = new RegExp('[\\?&]' + name + '=([^&#]*)')
    results = regex.exec(location.search)
    if results == null then '' else decodeURIComponent(results[1].replace(/\+/g, ' '))