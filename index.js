"use strict"
const Express = require("express")
const BodyParser = require("body-parser")
const Request = require("request")
const Moment = require("moment")
const UserStore = new (require("./lib/user-store"))
const Capi = require("./lib/capi")
const Facebook = require("./lib/facebook")
const Schedule = require("node-schedule")

const VERIFY_TOKEN = process.env.VERIFY_TOKEN
const ACCESS_TOKEN = process.env.ACCESS_TOKEN

const DEFAULT_IMAGE_URL = process.env.DEFAULT_IMAGE_URL
const FACEBOOK_URL = process.env.FACEBOOK_URL === "" ? "https://graph.facebook.com/v2.6" : process.env.FACEBOOK_URL
const CAMPAIGN_CODE_PARAM = "CMP=fb_newsbot"

const LINK_COUNT = 5

const app = Express()
app.use(BodyParser.urlencoded({extended: false}))

app.use(BodyParser.json())

const Events = {
  unknown(id) {
    Facebook.sendMessage(
      id,
      buildButtonsAttachment(
        "Sorry, I don't know what you mean.",
        [buildButton("postback", "So, what can you do?", buildPayload("help"))]
      )
    )
  },
  help(id) {
    sendMenu(
      id,
      "I'm a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\n\nI can give you the headlines, trending news or deliver a morning briefing to you.\nHow can I help you?"
    )
  },
  menu(id) {
    sendMenu(
      id,
      "How can I help?"
    )
  },
  start(id) {
    //Is this an existing user?
    UserStore.getUser(id, (err, data) => {
      if (err) {
        console.log("Error looking up user "+ id +": "+ JSON.stringify(err))
      } else {
        Facebook.getFacebookUser(id, (err, response, body) => {
          if (err) {
            console.log("Error retrieving user "+ id +"from facebook: "+ JSON.stringify(err))
          } else {
            const name = JSON.parse(body)["first_name"]
            if (data.Item) {
              sendMenu(id, "Hi there "+ name +", how can I help you?")
            } else {
              greetNewUser(id, name)
            }
          }
        })
      }
    })
  },
  subscribe_yes(id) {
    Facebook.sendMessage(
      id,
      buildButtonsAttachment(
        "Great. When would you like your morning briefing delivered?",
        [
          buildButton("postback", "6am", buildPayload("subscribe", {"time":6})),
          buildButton("postback", "7am", buildPayload("subscribe", {"time":7})),
          buildButton("postback", "8am", buildPayload("subscribe", {"time":8}))
        ]
      )
    )
  },
  subscribe_no(id) {
    sendMenu(
      id,
      "Ok, maybe later then. You can subscribe to the morning briefing at anytime from the menu.\n\nWould you like the headlines or trending news?"
    )
  },
  subscribe(id, payload) {
    const time = Moment(payload.time, "HH")
    //Get timezone from FB then update user's dynamo record
    Facebook.getFacebookUser(id, (error, response, body) => {
      const timeString = time.format("HH:mm")

      const offset = JSON.parse(body)["timezone"]
      const timeStringUTC = getUTCTime(time, offset)

      UserStore.setNotificationTime(id, offset, timeString, timeStringUTC, (err, data) => {
        if (err) {
          console.log("Error setting notification time for "+ id +": "+ JSON.stringify(err))
        } else {
          sendMenu(
            id,
            "Fantastic. You can change your subscription at anytime by asking for 'help' or 'menu'.\n\nWould you like the headlines or trending news?"
          )
        }
      })
    })
  },
  manage_subscription(id) {
    Facebook.sendMessage(
      id,
      buildButtonsAttachment(
        "What would you like to change?",
        [
          buildButton("postback", "Change time", buildPayload("subscribe_yes")),
          buildButton("postback", "Unsubscribe", buildPayload("unsubscribe")),
        ]
      )
    )
  },
  unsubscribe(id) {
    UserStore.unsubscribe(id, (err, data) => {
      if (err) {
        console.log("Error unsubscribing for "+ id +": "+ JSON.stringify(err))
      } else {
        Facebook.sendTextMessage(id, "You will no longer receive the daily morning briefing.\n\nIf you ever want to re-subscribe, you can do so from the menu")
      }
    })
  },
  morningBriefing(id) {
    //TODO - create a true morning briefing, not just editors-picks
    Facebook.sendTextMessage(
      id,
      "Good morning. Here is the morning briefing:",
      Events.headlines(id)
    )
  },
  headlines(id, payload) {
    const page = (payload && payload.page) ? payload.page : 0
    getAndSendCapiResults(id, "headlines", page)
  },
  trending(id, payload) {
    const page = (payload && payload.page) ? payload.page : 0
    getAndSendCapiResults(id, "trending", page)
  }
}

function greetNewUser(id, name) {
  UserStore.addUser(id, (err, data) => {
    if (err) {
      console.log("Error adding user "+ id +": "+ JSON.stringify(err))
    } else {
      Facebook.sendMessage(
        id,
        buildButtonsAttachment(
          "Hi there "+ name+ ", I'm a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\n\nCan I deliver you a daily morning briefing?",
          [
            buildButton("postback", "Yes please", buildPayload("subscribe_yes")),
            buildButton("postback", "No thanks", buildPayload("subscribe_no"))
          ]
        )
      )
    }
  })
}

function localeToFront(locale) {
  //http://fbdevwiki.com/wiki/Locales
  if (locale === "en_GB") {
    return "uk"
  } else if (locale === "en_US") {
    return "us"
  } else if (locale === "en_UD") {
    return "au"
  } else {
    return "international"
  }
}

//Returns the time adjusted by the offset as a string
function getUTCTime(time, offset) {
  //facebook timezone offset is a float in [-24, 24]
  const reg = offset.toString().match(/^([0-9]*)(\.[0-9]*)?$/)
  const hours = reg[1]
  const mins = (typeof reg[2] !== "undefined") ? parseFloat(reg[2]) * 60 : 0
  const timeUTC = time.subtract(hours, "hours").subtract(mins, "minutes")
  return timeUTC.format("HH:mm")
}

function sendMenu(id, message) {
  UserStore.getUser(id, (err, data) => {
    if (err) {
      console.log("Error looking up user "+ id +": "+ JSON.stringify(err))
    } else {
      const getSubButton = (isSubscribed) => {
        if (isSubscribed) {
          return buildButton("postback", "Manage subscription", buildPayload("manage_subscription"))
        } else {
          return buildButton("postback", "Subscribe", buildPayload("subscribe_yes"))
        }
      }

      Facebook.sendMessage(
        id,
        buildButtonsAttachment(
          message,
          [
            buildButton("postback", "Headlines", buildPayload("headlines",{"page":0})),
            buildButton("postback", "Trending", buildPayload("trending",{"page":0})),
            getSubButton(data.Item.notificationTime !== "-")
          ]
        )
      )
    }
  })
}

function buildPayload(event, data) {
  if (typeof data !== "undefined") {
    data.event = event
    return JSON.stringify(data)
  } else {
    return JSON.stringify({"event":event})
  }
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
function buildQuickReply(title, payload) {
  return {
    "content_type": "text",
    "title": title,
    "payload": payload
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

function getAndSendCapiResults(id, type, page) {
  const sendCapiResults = (id, results) => {
    const elements = results.slice(page,page+LINK_COUNT).map(item => {
      return buildElement(
        item.webTitle,
        item.fields.standfirst.replace(/<.*?>/g, ""),
        getImageUrl(item),
        [buildLinkButton(item.webUrl)]
      )
    })

    let att = buildGenericAttachment(elements)
    att.quick_replies = [
      buildQuickReply(
        "More stories",
        buildPayload(type, {"page":page + LINK_COUNT})
      )
    ]

    Facebook.sendMessage(id, att)
  }

  Facebook.getFacebookUser(id, (error, response, body) => {
    if (error) {
      console.log("Error getting facebook user "+ id +": "+ JSON.stringify(error))
    } else {
      const front = localeToFront(JSON.parse(body)["locale"])
      if (type === "headlines") {
        Capi.getEditorsPicks(id, front, sendCapiResults)
      } else {
        Capi.getMostViewed(id, front, sendCapiResults)
      }
    }
  })
}

// Look for image with width 1000, otherwise the next widest
function getImageUrl(item) {
  let imageUrl = DEFAULT_IMAGE_URL
  if ("elements" in item) {
    const element = getMainElement(item.elements)
    if (typeof element !== "undefined") {
      if ("assets" in element && element.assets.length > 0) {
        element.assets.reduce((widest, asset) => {
          const width = parseInt(asset.typeData.width)
          if (width <= 1000 && width > widest) {
            imageUrl = asset.file
            return width
          } else {
            return widest
          }
        }, 0)
      }
    }
  }
  return imageUrl
}

function getMainElement(elements) {
  const mainElement = elements.find(element => {
    return ("relation" in element && element.relation === "main")
  })
  if (typeof mainElement !== "undefined") {
    return mainElement
  } else {
    return elements[0]
  }
}

/*
 *  Every 15 mins (the smallest interval between timezones), query the dynamo table
 *  for users with a notificationTimeUTC equal to current UTC time, and push to them.
 *  Also check if their timezone has changed since the user was last handled, and
 *  update if so.
 */
const recRule = new Schedule.RecurrenceRule()
recRule.minute = [0, 15, 30, 45]
Schedule.scheduleJob(recRule, () => {
  const now = Moment.utc().format("HH:mm")
  console.log("Looking for users with time: " + now)

  UserStore.notifyUsers(now, (err, data) => {
    if (err) {
      console.log("Error querying users: "+ err)
    } else {
      console.log("Found "+ data.Items.length +" users")
      data.Items.forEach(user => {
        Facebook.getFacebookUser(user.ID, (error, response, body) => {

          const latestOffset = JSON.parse(body)["timezone"]
          if (latestOffset === user.offsetHours) {
            Events.morningBriefing(user.ID)
          } else {
            //User's timezone has changed
            if (latestOffset < user.offsetHours) {
              //We've missed the notification time for this new timezone, so push now
              Events.morningBriefing(user.ID)
            }

            const timeStringUTC = getUTCTime(Moment(user.notificationTime, "HH:mm"), latestOffset)
            UserStore.setNotificationTime(user.ID, latestOffset, user.notificationTime, timeStringUTC, (err, data) => {
              if (err) {
                console.log("Error setting notification time for "+ id +": "+ JSON.stringify(err))
              } else {
                console.log("Updated user's settings: "+ id)
              }
            })
          }
        })
      })
    }
  })
})

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
      console.log("Received message: '"+ text +"', from user "+ id)

      if (text.match(/^(hi|hello|ola|hey|salut)\s*[!?.]*$/)) {
        Events.start(id)
      } else if (text.match(/^help|^what can you do/)) {
        Events.help(id)
      } else if (text.match(/^menu/)) {
        Events.menu(id)
      } else if (text.match(/^subscribe/)) {
        Events.subscribe_yes(id)
      } else if (text.match(/^unsubscribe/)) {
        Events.unsubscribe(id)
      } else if (text.includes("headlines")) {
        Events.headlines(id)
      } else if (text.includes("trending")) {
        Events.trending(id)
      } else if (text === "more stories") {
        //Facebook's Quick Reply buttons come back as text messages, but with a payload.
        if (typeof event.message.quick_reply !== "undefined") {
          const payload = JSON.parse(event.message.quick_reply.payload)
          Events[payload.event](id, payload)
        }
      } else {
        Events.unknown(id)
      }

    } else if (event.postback) {
      
      const payload = JSON.parse(event.postback.payload)
      console.log("Received payload: '"+ JSON.stringify(payload) +"', from user "+ id)

      if (Events[payload.event]) {
        Events[payload.event](id, payload)
      } else {
        Events.unknown(id)
      }
    }
  })
  res.sendStatus(200)
})

const listen = app.listen((process.env.PORT || 5000), () => {
  console.log("running on port", listen.address().port)
})

