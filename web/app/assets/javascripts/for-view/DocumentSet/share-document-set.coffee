require [
  'jquery'
], ($) ->
  $iframe = $('<iframe name="share-document-set" src="about:blank"></iframe>')

  timeout = null

  reset = ->
    if timeout?
      clearTimeout(timeout)
      timeout = null

  scheduleRefreshHeight = ->
    timeout ||= window.setTimeout(refreshHeight, 100)

  refreshHeight = ->
    timeout = null
    return scheduleRefreshHeight() if $iframe.attr('src') == 'about:blank'
    return scheduleRefreshHeight() if !$iframe.parent()[0].clientWidth
    height = $iframe[0].contentDocument?.body?.offsetHeight || 0
    return scheduleRefreshHeight() if !height
    $iframe.css(height: height)

  $ ->
    $modal = $('#sharing-options-modal')
    $modal.find('.modal-body').append($iframe)

    # Listen for the iframe to say it has resized itself.
    # We don't check security, since it doesn't matter how many times this
    # method is called.
    window.addEventListener('message', scheduleRefreshHeight, false)

    $(document).on 'click', 'a.show-sharing-settings', (e) ->
      e.preventDefault()
      documentSetId = $(e.currentTarget).attr('data-document-set-id')
      $iframe.attr('src', 'about:blank')
      $iframe.attr('src', "/documentsets/#{documentSetId}/users")
      $modal.modal('show')
      scheduleRefreshHeight()
