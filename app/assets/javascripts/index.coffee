$ ->
  wsUrl = $("body").data("ws-url")

  unless wsUrl
    console.error("No WebSocket URL found!")
    return

  ws = null
  showSources = $("#showSources").is(":checked")

  connectWebSocket = ->
    try
      ws = new WebSocket wsUrl

      ws.onopen = ->
        console.log("WebSocket connected")

      ws.onmessage = (event) ->
        #console.log("MESSAGE RECEIVED:", event.data)
        try
          data = JSON.parse(event.data)
          #console.log("PARSED DATA:", data)

          if data.type == "initial"
            handleInitialResults(data)
          else if data.type == "update"
            handleUpdateResults(data)
          else if data.type == "history"
            handleHistoryUpdate(data)
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

    $("#results-#{slugify(query)}").remove()

    #Show message header and hide welcome when new search
    $("#message-header").html("Search Results for: #{escapeHtml(query)}")
    $("#welcome-message").hide()

    #Add new results at the top when new query
    resultsHtml = buildResultsSection(query, articles, readability)
    $("#results-container").prepend(resultsHtml)

    #We keep only the 10 most recent queries as per the instructions
    existingQueries = $("#results-container > div[id^='results-']")
    if existingQueries.length > 10
      existingQueries.slice(10).remove()

  handleUpdateResults = (data) ->
    query = data.query
    newArticles = data.articles

    if newArticles.length > 0
      newArticlesHtml = buildArticlesHtml(newArticles)
      $("#results-#{slugify(query)} ul").prepend(newArticlesHtml)

      #We update the count in the header if it exists
      sectionTitle = $("#results-#{slugify(query)} h4")
      if sectionTitle.length > 0
        currentText = sectionTitle.text()
        queryMatch = currentText.match(/^Search: "([^"]+)"/)
        if queryMatch
          currentCount = $("#results-#{slugify(query)} ul li").length
          sectionTitle.text("Search: \"#{queryMatch[1]}\" (#{currentCount} latest results)")

  handleHistoryUpdate = (data) ->
    queries = data.queries
    return unless queries && queries.length > 0

    $("#results-container").empty()

    for queryData in queries
      query = queryData.query
      articles = queryData.articles
      readability = queryData.readability

      resultsHtml = buildResultsSection(query, articles, readability)
      $("#results-container").append(resultsHtml)

    if queries.length > 0
      $("#welcome-message").hide()
      $("#message-header").show()

  handleSources = (data) ->
    # Handle sources data if needed

  buildResultsSection = (query, articles, readability) ->
    articlesHtml = buildArticlesHtml(articles)
    articleCount = articles.length
    """
    <div id="results-#{slugify(query)}">
      <h4>Search: "#{escapeHtml(query)}" (#{articleCount} latest results)</h4>
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

      kincaidGrade = if article.kincaidGrade? then article.kincaidGrade.toFixed(2) else "0.00"
      readingScore = if article.readingScore? then article.readingScore.toFixed(2) else "0.00"

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
          Flesch-Kincaid Grade Level: #{kincaidGrade}<br>
          Flesch Reading Score: #{readingScore}
        </li>
      """
    html

  slugify = (text) ->
    text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')

  escapeHtml = (text) ->
    return '' unless text
    $('<div>').text(text).html()

  # Initialize WebSocket connection
  connectWebSocket()

  # Attach form handler
  setTimeout ->
    formElement = $("#search-form")

    formElement.on "submit", (e) ->
      e.preventDefault()
      query = $("#searchInput").val().trim()
      sortBy = $("input[name='sortBy']:checked").val() || "publishedAt"
      showSources = $("#showSources").is(":checked")

      console.log("Form submitted - Query: '#{query}', SortBy: #{sortBy}")

      if query
        if ws && ws.readyState == WebSocket.OPEN
          message = JSON.stringify({type: "search", query: query, sortBy: sortBy})
          ws.send(message)
        else
          alert("WebSocket connection not ready. Please wait and try again.")

    $("#showSources").on "change", ->
      showSources = $(this).is(":checked")
      $("li").each ->
        sourceInfo = $(this).find("a[href]:eq(1)").parent()
        if showSources
          sourceInfo.show()
        else
          sourceInfo.hide()
  , 100