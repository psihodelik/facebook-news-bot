"use strict"

const Messages = {
  unknown: randomiser([
    "Sorry, I don't know what you mean."
  ]),
  help: randomiser([
    "I'm a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\n\nI can give you the headlines, most popular stories or deliver a morning briefing to you.\nHow can I help you?"
  ]),
  menu: randomiser([
    "How can I help?"
  ]),
  greeting: randomiser([
    "Hi there"
  ]),
  welcome: randomiser([
    "I'm a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\n\nWould you like me to deliver a daily morning briefing to you?"
  ]),
  subscribe_yes: randomiser([
    "Great. When would you like your morning briefing delivered?"
  ]),
  susbcribe_no: randomiser([
    "Ok, maybe later then. You can subscribe to the morning briefing at anytime from the menu.\n\nWould you like the headlines or most popular stories?"
  ]),
  subscribed: randomiser([
    "Fantastic. You can change your subscription at anytime by asking for 'help' or 'menu'.\n\nWould you like the headlines or most popular stories?"
  ]),
  unsubscribed: randomiser([
    "You will no longer receive the daily morning briefing.\n\nIf you ever want to re-subscribe, you can do so from the menu"
  ]),
  morning_briefing: randomiser([
    "Good morning. Here is the morning briefing:"
  ])
}

function randomiser(messages) {
  const r = ms => { return ms[Math.floor(Math.random()*ms.length)] }
  return r.bind(null, messages)
}

exports.Messages = Messages

