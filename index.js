"use strict"
const Express = require("express")
const BodyParser = require("body-parser")
const Request = require("request")
const Moment = require("moment")
const UserStore = new (require("./lib/user-store"))
const Capi = require("./lib/capi")
const Facebook = require("./lib/facebook")
const Messages = require("./lib/messages").Messages
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
  unknown(user) {
    Facebook.sendMessage(
      user.ID,
      buildButtonsAttachment(
        Messages.unknown(),
        [buildButton("postback", Messages.unknown_prompt(), buildPayload("help"))]
      )
    )
  },
  help(user) {
    sendMenu(
      user,
      Messages.help()
    )
  },
  menu(user) {
    sendMenu(
      user,
      Messages.menu()
    )
  },
  greeting(user) {
    Facebook.getFacebookUser(user.ID, (err, response, body) => {
      if (err) {
        console.log("Error retrieving user "+ user.ID +"from facebook: "+ JSON.stringify(err))
      } else {
        const fbData = JSON.parse(body)
        sendMenu(user, Messages.greeting() + " " + fbData.first_name + ". " + Messages.menu())
      }
    })
  },
  start(user) {
    Facebook.getFacebookUser(user.ID, (err, response, body) => {
      if (err) {
        console.log("Error retrieving user "+ user.ID +"from facebook: "+ JSON.stringify(err))
      } else {
        const fbData = JSON.parse(body)
        greetNewUser(user.ID, localeToFront(fbData.locale), fbData.first_name)
      }
    })
  },
  subscribe_yes(user) {
    Facebook.sendMessage(
      user.ID,
      buildButtonsAttachment(
        Messages.subscribe_yes(),
        [
          buildButton("postback", "6am", buildPayload("subscribe", {"time":6})),
          buildButton("postback", "7am", buildPayload("subscribe", {"time":7})),
          buildButton("postback", "8am", buildPayload("subscribe", {"time":8}))
        ]
      )
    )
  },
  subscribe_no(user) {
    sendMenu(
      user,
      Messages.subscribe_no()
    )
  },
  subscribe(user, payload) {
    const time = Moment(payload.time, "HH")
    //Get timezone from FB then update user's dynamo record
    Facebook.getFacebookUser(user.ID, (error, response, body) => {
      if (error) {
        console.log("Error retrieving user "+ user.ID +"from facebook: "+ JSON.stringify(error))
      } else {
        const timeString = time.format("HH:mm")

        const offset = JSON.parse(body)["timezone"]
        const timeStringUTC = getUTCTime(time, offset)

        UserStore.setNotificationTime(user.ID, offset, timeString, timeStringUTC, (err, data) => {
          if (err) {
            console.log("Error setting notification time for "+ user.ID +": "+ JSON.stringify(err))
          } else {
            user.notificationTime = timeString
            sendMenu(
              user,
              Messages.subscribed()
            )
          }
        })
      }
    })
  },
  manage_subscription(user) {
    if (user.notificationTime !== "-") {
      //Already subscribed
      Facebook.sendMessage(
        user.ID,
        buildButtonsAttachment(
          "Your edition is currently set to "+ frontToUserFriendly(user.front) +
          " and your morning briefing time is "+ user.notificationTime +
          ".\n\nWhat would you like to change?",
          [
            buildButton("postback", "Change time", buildPayload("subscribe_yes")),
            buildButton("postback", "Change edition", buildPayload("change_front_menu")),
            buildButton("postback", "Unsubscribe", buildPayload("unsubscribe")),
          ]
        )
      )
    } else {
      sendSubscribeQuestion(user.ID, Messages.subscribe_question())
    }
  },
  change_front_menu(user) {
    Facebook.sendMessage(
      user.ID,
      buildGenericAttachment([
        buildElement("UK edition", [buildButton("postback", "Set edition", buildPayload("change_front", {"front":"uk"}))]),
        buildElement("US edition", [buildButton("postback", "Set edition", buildPayload("change_front", {"front":"us"}))]),
        buildElement("Australian edition", [buildButton("postback", "Set edition", buildPayload("change_front", {"front":"au"}))]),
        buildElement("International edition", [buildButton("postback", "Set edition", buildPayload("change_front", {"front":"international"}))])
      ])
    )
  },
  change_front(user, payload) {
    UserStore.setFront(user.ID, payload.front, (err, data) => {
      if (err) {
        console.log("Error changing front for "+ user.ID +": "+ JSON.stringify(err))
      } else {
        Facebook.sendTextMessage(user.ID, "Your edition has been updated to "+ frontToUserFriendly(payload.front))
      }
    })
  },
  unsubscribe(user) {
    UserStore.unsubscribe(user.ID, (err, data) => {
      if (err) {
        console.log("Error unsubscribing for "+ user.ID +": "+ JSON.stringify(err))
      } else {
        Facebook.sendTextMessage(user.ID, Messages.unsubscribed())
      }
    })
  },
  morningBriefing(user) {
    //TODO - create a true morning briefing, not just editors-picks
    Facebook.sendTextMessage(
      user.ID,
      Messages.morning_briefing(),
      Events.headlines(user)
    )
  },
  headlines(user, payload) {
    const page = (payload && payload.page) ? payload.page : 0
    getAndSendCapiResults(user, "headlines", page)
  },
  most_popular(user, payload) {
    const page = (payload && payload.page) ? payload.page : 0
    getAndSendCapiResults(user, "most_popular", page)
  },
  share(user, payload) {
    Facebook.sendTextMessage(user.ID, payload.title +" - "+ payload.url)
  }
}

function greetNewUser(id, front, name) {
  UserStore.addUser(id, front, (err, data) => {
    if (err) {
      console.log("Error adding user "+ id +": "+ JSON.stringify(err))
    } else {
      sendSubscribeQuestion(
        id,
        Messages.greeting() + " " + name + "! " + Messages.welcome()
      )
    }
  })
}

function sendSubscribeQuestion(id, message) {
  Facebook.sendMessage(
    id,
    buildButtonsAttachment(
      message,
      [
        buildButton("postback", "Yes please", buildPayload("subscribe_yes")),
        buildButton("postback", "No thanks", buildPayload("subscribe_no"))
      ]
    )
  )
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

function frontToUserFriendly(front) {
  if (front === "au") {
    return "Australia"
  } else if (front === "international") {
    return "International"
  } else {
    return front.toUpperCase()
  }
}

//Returns the time adjusted by the offset as a string
function getUTCTime(time, offset) {
  //facebook timezone offset is a float in [-24, 24]
  const reg = offset.toString().match(/^(-?[0-9]*)(\.[0-9]*)?$/)
  const hours = reg[1]
  const mins = (typeof reg[2] !== "undefined") ? parseFloat(reg[2]) * 60 : 0
  const timeUTC = time.subtract(hours, "hours").subtract(mins, "minutes")
  return timeUTC.format("HH:mm")
}

function sendMenu(user, message) {
  const getSubButton = (isSubscribed) => {
    if (isSubscribed) {
      return buildButton("postback", "Manage subscription", buildPayload("manage_subscription"))
    } else {
      return buildButton("postback", "Subscribe", buildPayload("subscribe_yes"))
    }
  }

  Facebook.sendMessage(
    user.ID,
    buildButtonsAttachment(
      message,
      [
        buildButton("postback", "Headlines", buildPayload("headlines",{"page":0})),
        buildButton("postback", "Most popular", buildPayload("most_popular",{"page":0})),
        getSubButton(user.notificationTime !== "-")
      ]
    )
  )
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
    "title":"Read story"
  }
}
function buildQuickReply(title, payload) {
  return {
    "content_type": "text",
    "title": title,
    "payload": payload
  }
}

function buildElement(title, buttons, subtitle, imageUrl, itemUrl) {
  let element = {
    "title": title
  }
  if (typeof buttons !== "undefined") {
    element.buttons = buttons
  }
  if (typeof subtitle !== "undefined") {
    element.subtitle = subtitle
  }
  if (typeof imageUrl !== "undefined") {
    element.image_url = imageUrl
  }
  if (typeof itemUrl !== "undefined") {
    element.item_url = itemUrl
  }
  return element
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

function getAndSendCapiResults(user, type, page) {
  const sendCapiResults = (results) => {
    const elements = results.slice(page,page+LINK_COUNT).map(item => {
      return buildElement(
        item.webTitle, [
          buildButton("postback", "Share", buildPayload("share", {
            "url": item.webUrl + "?"+ CAMPAIGN_CODE_PARAM +"_share",
            "title": item.webTitle
          }))
        ],
        item.fields.standfirst.replace(/<.*?>/g, ""),
        getImageUrl(item),
        item.webUrl
      )
    })

    let att = buildGenericAttachment(elements)
    att.quick_replies = [
      buildQuickReply(
        "More stories",
        buildPayload(type, {"page":page + LINK_COUNT})
      )
    ]

    Facebook.sendMessage(user.ID, att)
  }

  if (type === "headlines") {
    Capi.getEditorsPicks(user.ID, user.front, sendCapiResults)
  } else if (type === "most_popular") {
    Capi.getMostViewed(user.ID, user.front, sendCapiResults)
  }
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
          if (error) {
            console.log("Error retrieving user "+ user.ID +"from facebook: "+ JSON.stringify(error))
          } else {
            notifyUser(user, JSON.parse(body))
          }
        })
      })
    }
  })
})

function notifyUser(dynamoData, fbData) {
  const latestOffset = fbData.timezone
  if (typeof latestOffset !== "undefined") {
    if (latestOffset === dynamoData.offsetHours) {
      Events.morningBriefing(dynamoData)
    } else {
      //User's timezone has changed
      if (latestOffset < dynamoData.offsetHours) {
        //We've missed the notification time for this new timezone, so push now
        Events.morningBriefing(dynamoData)
      }

      const timeStringUTC = getUTCTime(Moment(dynamoData.notificationTime, "HH:mm"), latestOffset)
      UserStore.setNotificationTime(dynamoData.ID, latestOffset, dynamoData.notificationTime, timeStringUTC, (err, data) => {
        if (err) {
          console.log("Error setting notification time for "+ dynamoData.ID +": "+ JSON.stringify(err))
        } else {
          console.log("Updated user's settings: "+ dynamoData.ID)
        }
      })
    }
  } else {
    console.log("No timezone found in facebook response: "+ JSON.stringify(response))
  }
}

function log(id, type, event, data) {
  console.log(JSON.stringify({
    "id": id,
    "event": event,
    "messageType": type,
    "timestamp": Moment.utc().toISOString(),
    "data": data
  }))
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

    //Is this an existing user?
    UserStore.getUser(id, (err, dynamoData) => {
      if (err) {
        console.log("Error looking up user "+ id +": "+ JSON.stringify(err))
      } else {

        if (dynamoData.Item) {
          handleExistingUser(id, event, dynamoData.Item)
        } else {
          /*
           * New user - in theory they should always arrive via the 'Get started' button,
           * and therefore on the 'start' event. But facebook messenger is a little
           * unreliable. Either way, let's give them the initial greeting and add them to
           * dynamodb.
           */
          log(id, "postback", "start", {})
          Events.start({"ID": id})
        }
      }
    })
  })
  res.sendStatus(200)
})

function handleExistingUser(id, event, userData) {
  if (event.message && event.message.text) {
    const text = event.message.text.trim().toLowerCase()

    if (typeof event.message.quick_reply !== "undefined") {
      //Facebook's Quick Reply buttons come back as text messages, but with a payload.
      const payload = JSON.parse(event.message.quick_reply.payload)

      log(id, "text", payload.event, {
        "text": event.message.text,
        "payload": payload
      })
      Events[payload.event](userData, payload)
    } else {
      const ev = getEventForTextMessage(text)
      log(id, "text", ev, {"text": event.message.text})
      Events[ev](userData)
    }

  } else if (event.postback) {

    const payload = JSON.parse(event.postback.payload)
    log(id, "postback", payload.event, {"payload": payload})

    if (Events[payload.event]) {
      if (payload.event === "start") {
        /*
         * This shouldn't generally happen, but it's possible for an existing
         * user to get the 'Get started' button in a web browser if they
         * delete the conversation first.
         */
        Events.greeting(userData)
      } else {
        Events[payload.event](userData, payload)
      }
    } else {
      Events.unknown(userData)
    }
  }
}

function getEventForTextMessage(text) {
  if (text.match(/^(hi|hello|ola|hey|salut)\s*[!?.]*$/)) {
    return "greeting"
  } else if (text.match(/^help|^what can you do/)) {
    return "help"
  } else if (text.match(/^menu/)) {
    return "menu"
  } else if (text.match(/^subscribe/)) {
    return "subscribe_yes"
  } else if (text.match(/^unsubscribe/)) {
    return "unsubscribe"
  } else if (text.includes("headlines")) {
    return "headlines"
  } else if (text.includes("popular")) {
    return "most_popular"
  } else {
    return "unknown"
  }
}

const listen = app.listen((process.env.PORT || 5000), () => {
  console.log("running on port", listen.address().port)
})

