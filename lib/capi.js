"use strict"

module.exports = {
  getEditorsPicks,
  getMostViewed
}

const get = require('simple-get-promise').get
const asJson = require('simple-get-promise').asJson

const API_KEY = process.env.API_KEY
const CAPI_BASE_URL = "http://content.guardianapis.com/"

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
  
  get(url)
    .then(asJson)
    .then(json => {
      const fieldName = type === "editors-picks" ? "editorsPicks" : "mostViewed"
      callback(id, json.response[fieldName])
    })
    .catch(error => { console.log("CAPI error: "+ error) })
}

