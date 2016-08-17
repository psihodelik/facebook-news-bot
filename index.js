"use strict"
const Express = require("express")
const BodyParser = require("body-parser")
const Request = require('request')
const UserStore = new (require("./lib/user-store"))
const get = require('simple-get-promise').get
const asJson = require('simple-get-promise').asJson

const VERIFY_TOKEN = process.env.VERIFY_TOKEN
const ACCESS_TOKEN = process.env.ACCESS_TOKEN
const API_KEY = process.env.API_KEY

const DEFAULT_IMAGE_URL = process.env.DEFAULT_IMAGE_URL
const FACEBOOK_URL = process.env.FACEBOOK_URL === "" ? "https://graph.facebook.com/v2.6/me/messages" : process.env.FACEBOOK_URL

const CAPI_BASE_URL = "http://content.guardianapis.com/"
const CAMPAIGN_CODE_PARAM = "CMP=fb_newsbot"

const app = Express()
app.use(BodyParser.urlencoded({extended: false}))

app.use(BodyParser.json())

const Events = {
  unknown(id) {
    sendMessage(
      id,
      buildButtonsAttachment(
        "Sorry! I’m pretty stupid at the moment. My creators are training me to understand and do more.",
        [buildButton("postback", "So, what can you do?", "help")]
      )
    )
  },
  help(id) {
    sendMessage(
      id,
      buildMenu("I’m a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\nI can give you the headlines, trending news or deliver a morning digest to you.\nHow can I help you?")
    )
  },
  menu(id) {
    sendMessage(
      id,
      buildMenu("How can I help?")
    )
  },
  start(id) {
    UserStore.addUser(id)
    sendMessage(
      id,
      buildButtonsAttachment(
        "Hi there {name}, I’m a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\nCan I deliver you the headlines each morning?",
        [
          buildButton("postback", "Yes, that would be great", "subscribe_yes"),
          buildButton("postback", "No thanks", "subscribe_no")
        ]
      )
    )
  },
  greeting(id) {
    sendMessage(
      id,
      buildMenu("Hi there {name}, how can I help you?")
    )
  },
  subscribe_yes(id) {
    sendMessage(
      id,
      buildButtonsAttachment(
        "Great. When would you like it delivered?",
        [
          buildButton("postback", "6am", "subscribe_6"),
          buildButton("postback", "7am", "subscribe_7"),
          buildButton("postback", "8am", "subscribe_8")
        ]
      )
    )
  },
  subscribe_no(id) {
    sendMessage(
      id,
      buildMenu("Ok, maybe later then. You can subscribe to the morning briefing at anytime by saying 'subscribe' or asking for 'help'.\nWould you like the headlines or trending news?")
    )
  },
  subscribe(id, time) {
    UserStore.setNotificationTime(id, time)
    sendMessage(
      id,
      buildMenu("Fantastic. You can change your subscription at anytime by asking for 'help' or 'menu'.\nWould you like the headlines or trending news?")
    )
  },
  headlines(id) {
    getEditorsPicks(id)
  },
  trending(id) {
    getMostViewed(id)
  }
}

function buildMenu(message) {
  return buildButtonsAttachment(
    message,
    [
      buildButton("postback", "Headlines", "headlines"),
      buildButton("postback", "Trending", "trending"),
      buildButton("postback", "Subscribe to morning digest", "subscribe_yes")  //TODO - only if not subscribed
    ]
  )
}

function sendTextMessage(sender, message, callback) {
  sendMessage(sender, {text: message}, callback)
}

function sendMessage(sender, message, callback) {
  let json = JSON.stringify(message)

  Request({
    url: FACEBOOK_URL,
    qs: { access_token: ACCESS_TOKEN},
    method: 'POST',
    json: {
      recipient: { id:sender },
      message: message
    }
  }, (error, response, body) => {
    if (error) {
      console.log('Error sending message to facebook: ', error);
    } else if (response.body.error) {
      console.log('Error: ', response.body.error);
    } else if (typeof callback !== 'undefined') {
      callback(error, response, body)
    }
  })
}

function buildButton(type, title, payload) {
  return {
    "type": type,
    "title": title,
    "payload": payload
  }
}
function buildLinkButton(url) {
  return {
    "type":"web_url",
    "url":url + "?" + CAMPAIGN_CODE_PARAM,
    "title":"Read article"
  }
}

function buildElement(title, subtitle, imageUrl, buttons) {
  return {
    "image_url":imageUrl,
    "title":title,
    "subtitle":subtitle,
    "buttons": buttons
  }
}

function buildButtonsAttachment(text, buttons) {
  return {
    "attachment":{
      "type":"template",
      "payload":{
        "template_type":"button",
        "text":text,
        "buttons": buttons
      }
    }
  }
}

function buildGenericAttachment(elements) {
  return {
    "attachment":{
      "type":"template",
      "payload":{
        "template_type":"generic",
        "elements": elements
      }
    }
  }
}

function getEditorsPicks(id) {
  doCapiQuery(id, "editors-picks")
}

function getMostViewed(id) {
  doCapiQuery(id, "most-viewed")
}

function doCapiQuery(id, type) {
  const url = CAPI_BASE_URL + "uk-news?page-size=0&show-"+ type +"=true&show-elements=image&show-fields=standfirst,thumbnail&api-key="+ API_KEY
  console.log("Requesting: "+ url)

  get(url)
    .then(asJson)
    .then(json => {

      const fieldName = type === "editors-picks" ? "editorsPicks" : "mostViewed"
      const elements = json.response[fieldName].slice(0,4).map(item => {
        return buildElement(
          item.webTitle,
          item.fields.standfirst.replace(/<.*?>/g, ""),
          getImageUrl(item),
          [buildLinkButton(item.webUrl)]
        )
      })

      sendMessage(id, buildGenericAttachment(elements))
    })
    .catch(error => { console.log("CAPI error: "+ error) })
}

// Look for image with width 1000, otherwise the next widest
function getImageUrl(item) {
  let imageUrl = DEFAULT_IMAGE_URL
  let widest = 0
  if ("elements" in item) {
    let element = getMainElement(item.elements)
    if (typeof element !== "undefined") {
      if ("assets" in element && element.assets.length > 0) {
        for (let assetIdx in element.assets) {
          let asset = element.assets[assetIdx]
          let width = parseInt(asset.typeData.width)
          if (width <= 1000 && width > widest) {
            imageUrl = asset.file
            widest = width
            if (width === 1000) {
              break
            }
          }
        }
      }
    }
  }
  return imageUrl
}
function getMainElement(elements) {
  return elements.find(element => {
    return ("relation" in element && element.relation === "main")
  })
}

// Verification GET
app.get("/webhook/", (req, res) => {
  if (req.query["hub.verify_token"] === VERIFY_TOKEN) {
    console.log("Verified: "+ JSON.stringify(req.query))
    res.send(req.query["hub.challenge"])
  } else {
    console.log("Not verified: "+ JSON.stringify(req.query))
    res.send("Error, wrong token")
  }
})

app.get("/status", (req, res) => {
  res.sendStatus(200)
})

app.post("/webhook/", (req, res) => {
  const events = req.body.entry[0].messaging

  events.map(event => {
    const id = event.sender.id

    if (event.message && event.message.text) {
      const text = event.message.text.trim().toLowerCase()
      console.log("message: "+ text)

      if (text.match(/^(hi|hello|ola|hey|salut)\s*[!?.]*$/)) {
        if (UserStore.userExists(id)) {
          Events.greeting(id)
        } else {
          Events.start(id)
        }
      } else if (text.match(/^help *|^what can you do */)) {
        Events["help"](id)
      } else if (text.match(/^menu */)) {
        Events["menu"](id)
      } else {
        Events["unknown"](id)
      }

    } else if (event.postback) {
      const data = event.postback.payload
      console.log("postback: "+ data)
      if (Events[data]) {
        Events[data](id)
      } else {
        //Have we received a subscription?
        const matches = data.match(/^subscribe_([0-9])$/)
        if (matches.length > 1) {
          Events["subscribe"](id, matches[1])
        } else {
          Events["unknown"](id)
        }
      }
    }
  })
  res.sendStatus(200)
})

const listen = app.listen((process.env.PORT || 5000), () => {
  console.log("running on port", listen.address().port)
})

