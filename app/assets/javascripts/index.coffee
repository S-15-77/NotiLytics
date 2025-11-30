$ ->
  # Wait for everything to be ready
  wsUrl = $("body").data("ws-url")

  unless wsUrl
    console.error("No WebSocket URL found!")
    return

  ws = null
  articlesByQuery = {}
  showSources = $("#showSources").is(":checked")

  connectWebSocket = ->
    try
      ws = new WebSocket wsUrl

      ws.onopen = ->
        console.log("WebSocket connected successfully")

      ws.onmessage = (event) ->
        console.log("Received:", event.data)
        try
          data = JSON.parse(event.data)

          if data.type == "initial"
            handleInitialResults(data)
          else if data.type == "update"
            handleUpdateResults(data)
          else if data.type == "sources"
            handleSources(data)
        catch error
          console.error("Error parsing message:", error)

      ws.onerror = (error) ->
        console.error("WebSocket error:", error)

      ws.onclose = ->
        console.log("WebSocket closed")
        setTimeout connectWebSocket, 3000
    catch error
      console.error("Failed to create WebSocket:", error)

  handleInitialResults = (data) ->
    query = data.query
    articles = data.articles
    readability = data.readability

    console.log("Initial results for: #{query}, #{articles.length} articles")

    $("#results-#{slugify(query)}").remove()

    articlesByQuery[query] = articles

    existingQueries = []
    $("#results-container > div").each ->
      id = $(this).attr("id")
      if id && id.startsWith("results-")
        existingQueries.push(id)

    while existingQueries.length >= 10
      oldestId = existingQueries.pop()
      $("##{oldestId}").remove()
      console.log("Removed oldest query from display: #{oldestId}")

    $("#message-header").html("Search Results for: #{escapeHtml(query)}")
    $("#welcome-message").hide()

    resultsHtml = buildResultsSection(query, articles, readability)
    $("#results-container").prepend(resultsHtml)

  handleUpdateResults = (data) ->
    query = data.query
    newArticles = data.articles

    console.log("!!! UPDATE RECEIVED !!!")
    console.log("Query:", query)
    console.log("Number of new articles:", newArticles.length)
    console.log("New articles:", newArticles)

    if articlesByQuery[query]
      articlesByQuery[query] = articlesByQuery[query].concat(newArticles)
    else
      articlesByQuery[query] = newArticles

    if newArticles.length > 0
      newArticlesHtml = buildArticlesHtml(newArticles)
      $("#results-#{slugify(query)} ul").prepend(newArticlesHtml)
      console.log("Added #{newArticles.length} articles to the DOM")
    else
      console.log("No new articles to display")

  handleSources = (data) ->
    console.log("Received sources:", data.data)

  buildResultsSection = (query, articles, readability) ->
    articlesHtml = buildArticlesHtml(articles)
    """
    <div id="results-#{slugify(query)}">
      <h4>Search: "#{escapeHtml(query)}" (10 latest results)</h4>
      <button onclick="window.open('/statistics/#{encodeURIComponent(query)}', '_blank')">Statistics</button>
      <p><strong>Average Flesch-Kincaid Grade Level:</strong> #{readability.avgGrade.toFixed(2)}</p>
      <p><strong>Average Flesch Reading Score:</strong> #{readability.avgScore.toFixed(2)}</p>
      <ul>
        #{articlesHtml}
      </ul>
      <hr>
    </div>
    """

  buildArticlesHtml = (articles) ->
    html = ""
    for article in articles
      d = new Date(article.publishedAt)
      yyyy = d.getFullYear()
      mm = String(d.getMonth() + 1).padStart(2, '0')
      dd = String(d.getDate()).padStart(2, '0')
      hh = String(d.getHours()).padStart(2, '0')
      min = String(d.getMinutes()).padStart(2, '0')
      formatted = "#{yyyy}-#{mm}-#{dd} #{hh}:#{min}"

      html += """
        <li>
          <strong><a href="#{escapeHtml(article.url)}" target="_blank">#{escapeHtml(article.title)}</a></strong><br>
      """

      if showSources
        html += """
          Source: <a href="#{escapeHtml(article.sourceUrl)}" target="_blank">#{escapeHtml(article.sourceName)}</a><br>
        """

      html += """
          Published: #{formatted}<br>
          Flesch-Kincaid Grade Level: 0.0<br>
          Flesch Reading Score: 0.0
        </li>
      """
    html

  slugify = (text) ->
    text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')

  escapeHtml = (text) ->
    return '' unless text
    $('<div>').text(text).html()

  # Initialize WebSocket connection first
  connectWebSocket()

  # Wait a moment for DOM to be fully ready, then attach form handler
  setTimeout ->
    formElement = $("#search-form")
    console.log("Attaching form handler, form found:", formElement.length > 0)

    formElement.on "submit", (e) ->
      e.preventDefault()
      query = $("#searchInput").val().trim()
      sortBy = $("input[name='sortBy']:checked").val() || "publishedAt"
      showSources = $("#showSources").is(":checked")

      console.log("Form submitted - Query: '#{query}', SortBy: #{sortBy}")

      if query
        if ws && ws.readyState == WebSocket.OPEN
          console.log("Sending search via WebSocket")
          message = JSON.stringify({type: "search", query: query, sortBy: sortBy})
          console.log("Message:", message)
          ws.send(message)
        else
          console.error("WebSocket not ready, state:", ws?.readyState)
          alert("WebSocket connection not ready. Please wait and try again.")
      else
        console.log("Empty query, not sending")

    $("#showSources").on "change", ->
      showSources = $(this).is(":checked")
      console.log("showSources changed to:", showSources)
  , 100