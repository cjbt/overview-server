requirejs.config({
  baseUrl: '/assets/javascripts'

  #enforceDefine: true

  shim: {
    'backbone': {
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    }
    'base64': { exports: 'Base64' }
    'bootstrap-alert': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.alert'
    }
    'bootstrap-collapse': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.collapse'
    }
    'bootstrap-dropdown': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.dropdown'
    }
    'bootstrap-modal': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    }
    'bootstrap-tab': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    }
    'bootstrap-transition': {
      deps: [ 'jquery' ]
    }
    'jquery.validate': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.validate'
    }
    spectrum: {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.spectrum'
    }
    md5: { exports: 'CryptoJS.MD5' }
  }

  paths: {
    'backbone': 'vendor/backbone'
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-2-1-0'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    'jquery.validate': 'vendor/jquery.validate'
    md5: 'vendor/md5'
    spectrum: 'vendor/spectrum'
    underscore: 'vendor/underscore'
  }
})

require [
  'for-view/DocumentCloudImportJob/new'
  'elements/logged-out-modal'
], ->
