requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    'backbone':
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    'bootstrap-alert':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.alert'
    'bootstrap-collapse':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.collapse'
    'bootstrap-dropdown':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.dropdown'
    'bootstrap-modal':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    'bootstrap-transition':
      deps: [ 'jquery' ]
    'jquery.validate':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.validate'

  paths:
    'backbone': 'vendor/backbone'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-2-1-0'
    'jquery.validate': 'vendor/jquery.validate'
    underscore: 'vendor/underscore'

require [
  'for-view/DocumentSet/cancel-import-job'
  'for-view/DocumentSet/delete-document-set'
  'for-view/DocumentSet/share-document-set'
  'for-view/DocumentSet/watch-import-jobs'
  'for-view/DocumentSet/index'
  'elements/form-submit-with-feedback'
  'elements/form-with-confirm'
  'elements/twitter-bootstrap'
  'elements/logged-out-modal'
], ->
