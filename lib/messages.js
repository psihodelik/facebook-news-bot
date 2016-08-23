"use strict"

const Messages = {
  unknown: randomiser([
    "I'm sorry I didn't understand that. My creators are working hard to make me smarter and more useful for you."
  ]),
  unknown_prompt: randomiser([
    "So, what can you do?"
  ]),
  help: randomiser([
    "I'm a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\n\nI can give you the headlines, most popular stories or deliver a morning briefing to you.\n\nHow can I help you?"
  ]),
  menu: randomiser([
    "How can I help?"
  ]),
  greeting: randomiser([
    "Hi there",
    "Hello",
    "Hi",
    "Hey",
    "Greetings"
  ]),
  welcome: randomiser([
    "I'm a virtual assistant created by the Guardian to keep you up-to-date with the latest news.\n\nWould you like me to deliver a daily morning briefing to you?"
  ]),
  subscribe_question: randomiser([
    "Would you like to subscribe to daily morning briefing?"
  ]),
  subscribe_yes: randomiser([
    "Great. When would you like your morning briefing delivered?"
  ]),
  susbcribe_no: randomiser([
    "No problem, maybe later then. You can subscribe to the morning briefing at anytime from the menu.\n\nWould you like the headlines or most popular stories?"
  ]),
  subscribed: randomiser([
    "Done. You will start to receiving the morning briefing.\n\nRemember, you can change your subscription to this at anytime from the menu. Would you like to see the headlines or most popular stories right now?"
  ]),
  unsubscribed: randomiser([
    "Done. You will no longer receive the morning briefing.\n\nYou can re-subscribe at anytime from the menu"
  ]),
  morning_briefing: randomiser([
    "Good morning! Check out your morning briefing",
    "Good morning! Your morning briefing is ready for you",
    "Good morning! Your morning briefing has arrived"
    "Good morning! Check out this morning's headline stories"
  ])
}

function randomiser(messages) {
  const r = ms => { return ms[Math.floor(Math.random()*ms.length)] }
  return r.bind(null, messages)
}

exports.Messages = Messages

