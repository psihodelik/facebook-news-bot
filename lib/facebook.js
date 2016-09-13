"use strict"

module.exports = {
  sendTextMessage,
  sendMessage,
  getFacebookUser
}

const Request = require("request")

const FACEBOOK_URL = process.env.FACEBOOK_URL === "" ? "https://graph.facebook.com/v2.6" : process.env.FACEBOOK_URL
const ACCESS_TOKEN = process.env.ACCESS_TOKEN

function sendTextMessage(sender, message, callback) {
  sendMessage(sender, {text: message}, callback)
}

function sendMessage(sender, message, callback) {
  Request({
    url: FACEBOOK_URL + "/me/messages",
    qs: { access_token: ACCESS_TOKEN },
    method: 'POST',
    json: {
      recipient: { id:sender },
      message: message
    }
  }, (error, response, body) => {
    if (error) {
      console.log('Error sending message to facebook: ', error)
    } else if (response.body.error) {
      console.log('Error: ', response.body.error)
    } else if (typeof callback !== 'undefined') {
      callback(error, response, body)
    }
  })
}

function getFacebookUser(id, callback) {
  const url = FACEBOOK_URL + "/" + id
  Request({
    url: url,
    qs: { access_token: ACCESS_TOKEN },
    method: "GET"
  }, (error, response, body) => {
    if (error) {
      console.log("Error getting user data from facebook: ", error)
    } else {
      callback(error, response, body)
    }
  })
}

