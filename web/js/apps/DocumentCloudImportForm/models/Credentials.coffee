define [ 'backbone', 'base64' ], (Backbone, Base64) ->
  class Credentials extends Backbone.Model
    defaults:
      email: undefined
      password: undefined

    toAuthHeaders: ->
      email = @get('email')
      password = @get('password')

      if email && password
        { Authorization: "Basic #{Base64.Base64.encode64("#{email}:#{password}")}" }
      else
        undefined

    toPostData: ->
      email = @get('email')
      password = @get('password')
      if email && password
        { email: email, password: password }
      else
        undefined

    isComplete: ->
      email = @get('email')
      password = @get('password')
      !!(email && password)
