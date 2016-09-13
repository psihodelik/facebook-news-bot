"use strict"
const Express = require("express")
const BodyParser = require("body-parser")
const Events = require("./lib/events").Events
const log = require("./lib/helpers").log
const UserStore = require("./lib/user-store")
const Scheduler = new (require("./lib/scheduler"))

const VERIFY_TOKEN = process.env.VERIFY_TOKEN

const app = Express()
app.use(BodyParser.urlencoded({extended: false}))

app.use(BodyParser.json())

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
  if (text.match(/^(hi|hello|ola|hey|salut|ello|whats up)\s*[!?.]*$/)) {
    return "greeting"
  } else if (text.match(/^help|^what can you do/)) {
    return "help"
  } else if (text.match(/^menu/)) {
    return "menu"
  } else if (text.match(/^subscribe|^can you send me a morning briefing/)) {
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

const listen = app.listen((process.env.PORT || 5000), () => {
  console.log("running on port", listen.address().port)
})

