define [ 'backbone' ], (Backbone) ->
  # Lists the things we can do with the current document
  #
  # The attributes are all ternary: null means "we don't know".
  class CurrentCapabilities extends Backbone.Model
    defaults:
      canShowDocument: null # "no document loaded"
