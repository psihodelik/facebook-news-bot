"use strict"

const Moment = require("moment")
const Messages = require("./messages").Messages
const Capi = require("./capi")
const UserStore = require("./user-store")
const Facebook = require("./facebook")
const localeToFront = require("./helpers").localeToFront
const frontToUserFriendly = require("./helpers").frontToUserFriendly
const getUTCTime = require("./helpers").getUTCTime
const getImageUrl = require("./helpers").getImageUrl
const LINK_COUNT = require("./helpers").LINK_COUNT

const CAMPAIGN_CODE_PARAM = "CMP=fb_newsbot"

exports.Events = {
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
  morning_briefing(user) {
    //TODO - create a true morning briefing, not just headlines
    Facebook.sendTextMessage(
      user.ID,
      Messages.morning_briefing(),
      this.headlines(user)
    )
  },
  headlines(user, payload) {
    const offset = (payload && payload.offset) ? payload.offset : 0
    getAndSendCapiResults(user, "headlines", offset, (payload ? payload.topic : null))
  },
  most_popular(user, payload) {
    const offset = (payload && payload.offset) ? payload.offset : 0
    getAndSendCapiResults(user, "most_popular", offset, (payload ? payload.topic : null))
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
        buildButton("postback", "Headlines", buildPayload("headlines",{"offset":0})),
        buildButton("postback", "Most popular", buildPayload("most_popular",{"offset":0})),
        getSubButton(user.notificationTime !== "-")
      ]
    )
  )
}

function getAndSendCapiResults(user, type, offset, topic) {

  const sendCapiResults = (results) => {
    const elements = results.map(item => {
      return buildElement(
        item.webTitle, [
          buildButton("postback", "Share", buildPayload("share", {
            "url": item.webUrl + "?"+ CAMPAIGN_CODE_PARAM +"_share",
            "title": item.webTitle
          }))
        ],
        item.fields.standfirst ? item.fields.standfirst.replace(/<.*?>/g, "") : "",
        getImageUrl(item),
        item.webUrl
      )
    })

    let att = buildGenericAttachment(elements)
    att.quick_replies =
      [buildQuickReply(
        (topic ? "More "+ topic.toLowerCase() : "More stories"),
        buildPayload(type, {"offset":offset + LINK_COUNT, "topic": topic})
      )].concat(buildTopicQuickReplies(type, user.front, topic))

    Facebook.sendMessage(user.ID, att)
  }

  if (type === "headlines") {
    Capi.getHeadlines(user.ID, user.front, offset, topic, sendCapiResults)
  } else if (type === "most_popular") {
    Capi.getMostViewed(user.ID, user.front, offset, topic, sendCapiResults)
  }
}

function buildTopicQuickReplies(type, front, currentTopic) {
  const quickTopicsByFront = {
    "uk": ["Politics", "Football", "Business", "Sport"],
    "us": ["Politics", "Sport", "Business"],
    "au": ["Sport", "Business", "Culture"],
    "international": ["Sport", "Business", "Technology"]
  }

  return quickTopicsByFront[front]
    .filter(topic => topic.toLowerCase() != currentTopic)
    .map(topic => {
      return buildQuickReply(
        topic,
        buildPayload(type, {"topic": topic.toLowerCase(), "offset": 0})
      )
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
    element.item_url = itemUrl + "?" + CAMPAIGN_CODE_PARAM
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
