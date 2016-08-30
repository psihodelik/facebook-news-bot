"use strict"

module.exports = {
  getEditorsPicks,
  getMostViewed
}

const get = require('simple-get-promise').get
const asJson = require('simple-get-promise').asJson
const Moment = require("moment")

const API_KEY = process.env.API_KEY
const CAPI_BASE_URL = "http://content.guardianapis.com/"

const CACHE_STALE_MILLISECONDS = 30000
const CACHE = new Cache()

function Cache() {
  this._data = {}
}
Cache.prototype.get = function(url) {
  const item = this._data[url]
  if (typeof item !== "undefined" && Moment.utc().diff(item.timestamp) < item.staleMs) {
      return item.data
  } else {
      return null
  }
}
Cache.prototype.put = function(url, data, staleMs) {
  this._data[url] = {
    "timestamp": Moment.utc(),
    "data": data,
    "staleMs": staleMs
  }
}

function getEditorsPicks(id, front, callback) {
  doCapiQuery(id, "editors-picks", front, callback)
}

function getMostViewed(id, front, callback) {
  doCapiQuery(id, "most-viewed", front, callback)
}

function doCapiQuery(id, type, front, callback) {
  const url = CAPI_BASE_URL + 
              front + 
              "?page-size=0&show-"+ type +"=true"+
              "&show-elements=image&show-fields=standfirst,thumbnail"+
              "&api-key="+ API_KEY

  const cached = CACHE.get(url)
  if (cached !== null) {
    callback(cached)
  } else {
    get(url)
      .then(asJson)
      .then(json => {
        const fieldName = getFieldName(type)
        const data = json.response[fieldName]
        CACHE.put(url, data, CACHE_STALE_MILLISECONDS)
        callback(data)
      })
      .catch(error => { console.log("CAPI error: "+ error) })
  }
}

function getFieldName(type) {
  return type === "editors-picks" ? "editorsPicks" : "mostViewed"
}

